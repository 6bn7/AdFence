# AdFence · 广告围栏

> 📱 **这是手机版（Android）** ｜ 作者是**新手**，边学边做，代码和文档难免有不周到、写得笨的地方，欢迎指正。
> 🐞 **遇到误拦、崩溃、拦不住，或者有任何建议，请到 [Issues](../../issues) 反馈**。
> 反馈时最好带上：**App 版本号**（主界面标题就有）、**手机型号 + Android 版本**、**被拦或被漏的域名**、以及你当时在做什么。
> 我会尽量回应，但**不保证及时**——这是个人业余项目。

**免 root 的 Android 广告域名拦截工具** —— 本地 VpnService 做 DNS 层过滤，配合公开广告域名清单、广告产物清理与可视化排查。**不需要 root，不修改任何第三方 App。**

> ## ⚠️ 免责声明（详细版，请务必读完）
>
> **一、这是什么。** AdFence 是一个跑在**你自己手机**上的本地工具。它在**域名解析层**工作：命中已知广告/追踪域名时解析为 `0.0.0.0`（连接立即失败），其它查询原样转发给你的上游 DNS。除此之外它**不做别的事**。
>
> **二、这不做什么。** 不修改、不注入、不 hook、不破解任何第三方应用；不绕过任何付费、会员、订阅机制；不采集、不上传、不转卖任何个人数据；不拦截或篡改非 DNS 流量。
>
> **三、可能造成什么后果。** 屏蔽域名**可能导致部分应用功能异常**：登录、支付、推送通知、图片/视频加载、直播、地图、崩溃上报等都可能受影响；某些应用带完整性校验，可能给出异常提示。App 内提供了「**例外（放行）**」机制，发现误拦可以一键恢复（立即生效），但**是否影响你的正常使用，需要你自己判断**。
>
> **四、删除功能有风险，请先看清楚再授权。** App 的清理功能会**删除文件**。虽然已刻意把范围收窄到「已知广告目录名 / SDK 前缀 + 广告素材扩展名」，并做了**回收站（24 小时可找回）**、**全路径留痕**和**「清理前先问我」**开关，但**因删除文件造成的任何损失仍由使用者自行承担**。建议第一次使用时保持「清理前先问我」为开启状态，确认它只删该删的东西。
>
> **五、隐私。** 本软件不联网回传任何数据（唯一的网络行为是转发 DNS 查询）。日志保存在**应用私有目录**（其它应用无法读取），且**默认只记录被拦截的域名**；开启「全量记录」后才会记录全部域名——那等同于你的上网足迹，**导出或分享前请自行判断**。
>
> **六、法律与服务条款。** **广告拦截的合法性因国家/地区/司法辖区而异。** 部分应用的用户协议可能禁止屏蔽广告或以广告为对价的功能。请**仅在你拥有或有权管理的设备上**使用，并在**当地法律允许的范围内**使用；由此产生的任何后果由使用者自行承担。作者不提供任何法律意见。
>
> **七、无担保与责任限制。** 本软件按「**现状**」提供，不附带任何明示或默示担保。在适用法律允许的最大范围内，**作者与贡献者不对**因使用或无法使用本软件而产生的任何直接、间接、偶然、特殊或后果性损害（包括但不限于数据丢失、功能中断、设备问题、第三方索赔）**承担责任**，即使已被告知该等损害的可能性。
>
> **八、第三方与归属。** 内置广告域名列表编译自 [anti-AD](https://github.com/privacy-protection-tools/anti-AD)（MIT 许可），版权归原作者，署名见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目与任何第三方应用/公司/域名所有者**无隶属、无赞助、无背书关系**；文中出现的应用名、公司名、商标仅为**事实性技术说明**。
>
> 完整条款见 [DISCLAIMER.md](DISCLAIMER.md) 与 [LICENSE](LICENSE)。下载、安装或使用即表示你已阅读并同意以上全部内容；**不同意请不要使用**。

---

## 它是什么

一个**本地 DNS 过滤器**。基于 Android 的 `VpnService`，把自己变成一个"只接管 DNS 的本地 VPN"：

- 命中广告域名清单 → 返回 `0.0.0.0`（连接立即失败，广告拉不到素材）
- 未命中 → 原样转发给你的上游 DNS，**其他流量完全不经过本应用**
- 附带：广告产物清理（SAF 只授权一个目录）、广告样本留样、拦截日志与逐条排查

**它不是**：不是去广告外挂，不是修改别人 App 的工具，不是万能的。

## 拦得住什么 / 拦不住什么

诚实说明——这决定它能不能帮到你：

| 场景 | 能否拦住 |
|---|---|
| App 通过域名请求广告接口 / 素材 CDN | ✅ 能（DNS 层直接掐断） |
| 网页 / WebView 里的广告域名 | ✅ 能 |
| 已知广告 SDK 的埋点、上报 | ✅ 能 |
| **App 内部已缓存的广告配置/素材** | ❌ 拦不了（不再发网络请求了；可清缓存，但清不掉 App 内部数据区的） |
| **App 自己实现 HTTPDNS 拿 IP 后直连** | ❌ 拦不了（DNS 层看不见） |
| **广告与正常内容同域名** | ❌ 拦不了（拦了会连带正常功能挂掉） |
| 跨 App 的 `Android/data` 目录清理 | ❌ 免 root 做不到（Android 11+ FUSE 限制），需配合电脑端 adb 脚本 |

## 功能

- **域名拦截**：内置广告域名库（由 [anti-AD](https://github.com/privacy-protection-tools/anti-AD) 列表编译，约 9.9 万条，可一键停用）+ 手工规则 + 自加规则 + 例外（放行，优先级最高）
- **拦截详情**：逐条 DNS 记录，**点任意一条**给出人话结论（属于哪个广告体系、同一时刻还有谁在活动），并可一键「标为广告」/「不是广告 → 放行」
- **广告样本**：清理广告素材前先留一份图片/视频，在 App 里直接看到"原广告长什么样"，人工确认后再决定拦或放
- **清理留痕**：每次删除写日志（时间/类型/大小/完整路径），「清理记录」页可查；可选「清理前先问我」
- **诊断模式**：「只抓目标App」——只看某一个 App 的 DNS，方便溯源它的广告链路
- **可配置**：目标 App 包名、上游 DNS（可换本地/自建解析器）

## 安全

本项目经过一次第三方安全审计，并已按结论修复。当前实现上的关键安全设计：

| 项 | 做法 |
|---|---|
| DNS 应答校验 | 转发用 **已连接（`connect()`）** 的 socket，内核只投递对端报文；再显式校验**事务 ID / 来源地址与端口 / QR 位**，防止链路上伪造应答被回写给发起查询的应用 |
| 转发可用性 | 转发在线程池异步执行，**并发上限 64** + 背压；单上游超时 1.5 秒（原先同步阻塞 3×4 秒会卡死全设备 DNS） |
| 文件访问 | **不申请**「所有文件访问」；清理走 SAF，只能碰用户显式授权的一个目录 |
| 删除安全 | 默认**「清理前先问我」**；匹配规则刻意收窄（zip/txt/doc/pdf 不碰、中文名目录不碰）；非媒体文件删除前先进**回收站（24 小时）**；每次删除写全路径日志 |
| 隐私 | DNS 日志写在**应用私有目录**，且**默认只记录被拦域名**（全量记录需手动开启）；不采集、不上传 |
| 规则完整性 | **不从外部存储加载规则**（避免低版本 Android 上的规则注入）；规则只经 App 界面增删 |
| 输入校验 | 域名做字符白名单校验（同时防日志注入）；丢弃含压缩指针或多问题段的畸形查询；留样文件名做 basename 净化 + 路径校验（防路径穿越） |
| 并发 | 规则集合使用并发安全实现，避免域名库载入窗口内的漏判 |
| 签名 | 仓库**不含任何密钥**；构建时本地自动生成随机口令的钥匙（`keystore/` 已 gitignore） |

> 历史审计发现（H-2/H-3/M-1~M-4/L-1~L-6）的处理见 `assets/changelog.txt` 中 v2.7 条目与提交记录。
> **尚未实现**：上游 DoT/DoH 支持（当前为明文 UDP 53）。若你的设备原本使用加密 DNS，启用本应用后该段链路为明文——这是本项目的已知取舍，上游地址可在设置里换成你自己的解析器。

## 权限

只要两个，没有别的：

| 权限 | 为什么需要 |
|---|---|
| `INTERNET` | 把未命中的 DNS 查询转发给上游解析器（不转发的话全机 DNS 会断） |
| `FOREGROUND_SERVICE` | VPN 必须常驻前台服务（Android 硬性要求，会显示一条通知） |

- **不申请**「所有文件访问」：清理功能走 **SAF**，你只授权**一个目录**（默认 Download），App 就只能碰这一个目录
- 无相机/位置/通讯录/短信/电话/存储（传统）等权限
- 所有 Activity 除启动页外均 `exported=false`；VPN 服务额外要求 `BIND_VPN_SERVICE`（签名级）
- `allowBackup=false`

## 隐私

- **不采集、不上传、不联网回传**。代码里唯一的网络行为是 DNS 转发（`DatagramSocket` + `protect()`），没有 HTTP 客户端、没有 WebView、没有动态加载、没有命令执行
- 所有规则、例外、标记记录、清理记录都存在**本机**
- 注意：`adfence.log` 与「拦截详情」会记录域名，属于你的上网足迹，**导出/分享前请自行判断**

## 构建

需要：JDK（`javac`/`keytool`）、Android SDK 的 `build-tools`（`aapt2`/`d8`/`apksigner`）、Python。**不需要 Gradle，不需要任何第三方依赖。**

```bash
export ANDROID_HOME=/path/to/android-sdk     # 或把 SDK 软链到 ./sdk
./build.sh                                   # 产物：build/adfence.apk
```

- 首次构建会**自动生成签名钥匙** `keystore/release.keystore`，随机口令存在 `keystore/.pass`（**已 gitignore，别丢**：丢了就不能原地升级）
- 用自己的钥匙：`KEYSTORE=/path/to.jks KEYSTORE_PASS=你的口令 ./build.sh`
- 版本号在 `version.env`，单一来源

安装：

```bash
adb install -r build/adfence.apk
```

> 升级安装会**杀掉正在运行的服务**（系统行为），装完需在 App 里重新点一次「启动拦截」。
> 注意：本仓库自行构建的 APK 使用**你自己生成**的签名，与任何其他渠道的同包名 APK **不能互相覆盖安装**——从别的版本切过来需要先卸载（会清掉规则与授权，需重新配置）。
> 若要持续发布更新，请**妥善保留** `keystore/release.keystore` 与 `keystore/.pass`：换了钥匙，老用户就无法升级到新版本。

## 使用

1. 打开 App → **启动拦截** → 首次会弹出系统的 VPN 授权，选「允许」
2. （可选）**设置**：填目标 App 包名（用于诊断模式）、换上游 DNS
3. （可选）**授权清理目录**：选 `Download`，即可自动清理落到公开目录的广告产物
4. 发现误伤 → 「拦截详情」里点那一条 → **不是广告 → 放行**（立即生效）
5. 想溯源某 App 的广告 → 设置里填它的包名 → 开「只抓目标App」→ 复现 → 看日志

## 目录结构

```
src/com/local/adfence/
  FenceVpnService.java   VpnService 核心：DNS 拦截 + 转发
  Rules.java             规则表（域名库 / 手工 / 自加 / 例外）
  Saf.java               SAF 目录访问（替代「所有文件访问」权限）
  CacheCleaner.java      广告产物清理（收窄匹配 + 留痕 + 先问后删）
  SampleStore.java       广告素材留样
  MainActivity / LogActivity / RulesActivity / GalleryActivity /
  HistoryActivity / ChangelogActivity / ConfigActivity
  AdHints.java           域名 → 人话结论（广告体系归属提示）
  Config.java / Stats.java / Util.java
assets/
  adlib.txt              内置广告域名库（由 anti-AD 列表编译）
  changelog.txt          更新内容
docs/rules.example.txt   手机端可追加规则的样例
```

## 第三方与许可

- 本项目：**MIT**（见 [LICENSE](LICENSE)）
- 广告域名列表：[anti-AD](https://github.com/privacy-protection-tools/anti-AD)（MIT）—— 编译自其公开列表，署名见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 免责声明全文：[DISCLAIMER.md](DISCLAIMER.md)

---

## English (short version)

**AdFence** is a root-free Android ad-domain blocker: a `VpnService` that hijacks **DNS only** (a single route to a fake resolver), answers `0.0.0.0` for domains on its blocklist, and forwards everything else to your upstream DNS. Other traffic never enters the app.

- Blocklist: compiled from [anti-AD](https://github.com/privacy-protection-tools/anti-AD) (MIT), ~99k domains, one-tap disable
- Also: ad-artifact cleanup via **SAF** (single user-granted folder — *no* `MANAGE_EXTERNAL_STORAGE`), creative samples for human review, per-domain plain-language verdicts, audit log, per-app diagnostic mode
- Permissions: only `INTERNET` + `FOREGROUND_SERVICE`
- Build: `ANDROID_HOME=/path/to/sdk ./build.sh` (no Gradle, no third-party deps)
- **Limits**: DNS-visible ads only. Cannot block ads already cached inside an app, HTTPDNS/IP-direct SDKs, or ads sharing a domain with real content.
- License: MIT. **Use at your own risk** — see [DISCLAIMER.md](DISCLAIMER.md). Ad blocking may violate some services' terms of use and may break app features; local law varies by country.
