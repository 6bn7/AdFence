# 第三方声明 / Third-Party Notices

本软件包含或编译了以下第三方内容。其版权归各自作者所有，按各自许可使用。

---

## 1. anti-AD 广告过滤列表

- 来源：https://github.com/privacy-protection-tools/anti-AD
- 用途：`assets/adlib.txt` 由该项目的公开域名列表**编译**而来
  （处理方式：剔除注释与失效条目 → 按域名后缀去重 → 剔除易误伤的平台/推送/登录类条目）
- 许可：**MIT License**

```
MIT License

Copyright (c) 2017-2019 gently

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

> 如需更新域名列表：从上游获取最新列表后，按上述处理方式重新生成 `assets/adlib.txt` 并重新构建。

---

## 2. Android SDK

构建产物包含/依赖 Android SDK 的公开接口（`android.jar`），按 Android SDK 的许可条款使用。
运行时不包含任何第三方二进制库（本项目**零第三方依赖**）。

---

## 3. 商标与名称

文中出现的任何应用名、公司名、商标、域名，仅用于**事实性技术说明**（例如说明某域名属于某广告体系）。
本项目的版权方与这些主体**不存在隶属、赞助或背书关系**。
