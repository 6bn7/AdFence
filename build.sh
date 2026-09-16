#!/usr/bin/env bash
# AdFence 构建脚本：javac -> D8 -> aapt2 link -> 打包 classes.dex -> 签名
#
# 依赖：JDK（javac/keytool）、Android SDK 的 build-tools（aapt2 / d8 / apksigner）、python
# 无需 Gradle，无需任何第三方库。
#
# SDK 查找顺序：$ANDROID_HOME -> $ANDROID_SDK_ROOT -> ./sdk -> ~/Android/Sdk
# 签名：默认自动生成 keystore/release.keystore 与随机口令（存 keystore/.pass，已 gitignore）
#       想用自己的钥匙：KEYSTORE=/path/to.jks KEYSTORE_PASS=你的口令 ./build.sh
# 版本号：读同目录 version.env（单一来源，别再改别处）
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

[ -f version.env ] && . ./version.env
VERSION_NAME="${VERSION_NAME:-2.6}"
VERSION_CODE="${VERSION_CODE:-17}"

# ---------- 找 SDK ----------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -z "$SDK" ] && [ -d "$ROOT/sdk" ] && SDK="$ROOT/sdk"
[ -z "$SDK" ] && [ -d "$HOME/Android/Sdk" ] && SDK="$HOME/Android/Sdk"
[ -z "$SDK" ] && { echo "错误：找不到 Android SDK。请设置 ANDROID_HOME，或把 SDK 软链到 ./sdk"; exit 1; }

BT="${BT_DIR:-$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)}"
if [ -z "$BT" ]; then
  # 兼容"手工解压的 SDK"：某个子目录里直接放着 aapt2
  BT="$(ls -d "$SDK"/*/ 2>/dev/null | while read -r d; do
          { [ -x "${d}aapt2" ] || [ -x "${d}aapt2.exe" ]; } && echo "${d%/}"
        done | tail -1)"
fi
AJ="${ANDROID_JAR:-$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)}"
[ -z "$AJ" ] && AJ="$(ls "$SDK"/*/android.jar 2>/dev/null | head -1)"
[ -z "$BT" ] && { echo "错误：找不到 build-tools。请设 BT_DIR=/path/to/build-tools/x.y.z"; exit 1; }
[ -z "$AJ" ] && { echo "错误：找不到 android.jar。请设 ANDROID_JAR=/path/to/android.jar"; exit 1; }

tool() {   # 兼容 Windows(.exe/.bat) 与 Linux/macOS
  local n="$1"
  if [ -x "$BT/$n" ]; then echo "$BT/$n"
  elif [ -x "$BT/$n.exe" ]; then echo "$BT/$n.exe"
  else echo "$BT/$n.bat"; fi
}
AAPT2="$(tool aapt2)"
APKSIGNER="$(tool apksigner)"
KEYTOOL="$(command -v keytool || echo keytool)"

win() { command -v cygpath >/dev/null 2>&1 && cygpath -w "$1" || echo "$1"; }
PY=""
for c in python3 python py; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c 'print(1)' >/dev/null 2>&1; then PY="$c"; break; fi
done
[ -z "$PY" ] && { echo "错误：需要可用的 python3（用于打包 dex）"; exit 1; }

echo "SDK  : $SDK"
echo "BT   : $BT"
echo "版本 : $VERSION_NAME ($VERSION_CODE)"

rm -rf build
mkdir -p build/classes build/dex

echo "--- javac ---"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$(win "$AJ")" \
      -d build/classes $(find src -name '*.java')
echo "    编译出 $(ls build/classes/com/local/adfence/ | wc -l) 个 class"

echo "--- d8 ---"
java -cp "$(win "$BT/lib/d8.jar")" com.android.tools.r8.D8 \
     --release --min-api 26 --lib "$(win "$AJ")" \
     --output "$(win build/dex)" $(find build/classes -name '*.class')
ls -l build/dex/

echo "--- aapt2 link ---"
"$AAPT2" link -o build/base.apk -I "$(win "$AJ")" \
    -A assets \
    --manifest AndroidManifest.xml \
    --min-sdk-version 26 --target-sdk-version 33 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

echo "--- 打包 classes.dex + assets ---"
"$PY" - <<'PY'
import zipfile, os
src, dex, dst = 'build/base.apk', 'build/dex/classes.dex', 'build/withdex.apk'
zin = zipfile.ZipFile(src)
zout = zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zout.writestr(it, zin.read(it.filename))
zout.write(dex, 'classes.dex')
zout.close()
print('    ok %.0f KB' % (os.path.getsize(dst) / 1024))
PY

echo "--- zipalign（4 字节对齐，签名前执行）---"
ZIPALIGN="$(tool zipalign)"
"$ZIPALIGN" -f -p 4 build/withdex.apk build/aligned.apk
ls -lh build/aligned.apk

echo "--- keystore ---"
KS="${KEYSTORE:-keystore/release.keystore}"
PASSFILE="keystore/.pass"
mkdir -p keystore
if [ ! -f "$KS" ]; then
  if [ -n "${KEYSTORE_PASS:-}" ]; then
    PASS="$KEYSTORE_PASS"
  else
    PASS="$("$PY" -c "import secrets,string;print(''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(24)))")"
    printf '%s\n' "$PASS" > "$PASSFILE"
    chmod 600 "$PASSFILE" 2>/dev/null || true
    echo "    已生成随机口令 → keystore/.pass（已 gitignore：别丢，丢了就不能原地升级）"
  fi
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias adfence -keyalg RSA -keysize 2048 \
      -validity 10000 -storepass "$PASS" -keypass "$PASS" -storetype PKCS12 \
      -dname "CN=AdFence,OU=Local,O=Local,C=CN" 2>&1 | tail -1
else
  PASS="${KEYSTORE_PASS:-$(cat "$PASSFILE" 2>/dev/null || true)}"
  [ -z "$PASS" ] && { echo "错误：已有 $KS 但找不到口令，请用 KEYSTORE_PASS=xxx 指定"; exit 1; }
fi

echo "--- sign ---"
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
    --ks-key-alias adfence --out build/adfence.apk build/aligned.apk
"$APKSIGNER" verify --print-certs build/adfence.apk | head -4
ls -lh build/adfence.apk
echo
echo "完成：build/adfence.apk"
echo "安装：adb install -r build/adfence.apk"
