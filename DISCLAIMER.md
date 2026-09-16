# 免责声明 / DISCLAIMER

**AdFence**（下称"本软件"）是一个**运行在 Android 手机上的**、在**域名解析层**过滤广告域名的开源工具，由个人**以业余时间、新手水平**开发维护。

下载、安装、运行或以任何方式使用本软件，即表示你已阅读、理解并同意以下全部条款。**如果你不同意，请不要使用。**

> 使用中遇到误拦、崩溃、拦不住或任何问题，欢迎到仓库的 **Issues** 反馈（附上 App 版本号、手机型号 + Android 版本、相关域名）。作者会尽量回应，但不承诺响应时限与修复时间。

---

## 1. 使用范围

- 本软件**仅供个人学习、研究与自用**，请在你**拥有或有权管理的设备**上使用。
- 将本软件用于他人设备、企业环境、或任何商业分发前，请自行评估并取得必要授权。
- 本软件为**开源软件**，按 **MIT 许可**发布，你可自由使用、修改、再分发，但须保留版权声明与许可文本。

## 2. 本软件做什么、不做什么

**做**：在**本机**的域名解析层，把已知广告/追踪域名解析为 `0.0.0.0`，从而阻断其素材与接口请求；其余查询原样转发给上游 DNS。

**不做**：
- 不修改、不注入、不 hook 任何第三方应用；
- 不破解、不绕过任何付费或会员机制；
- 不读取、不采集、不上传他人或本人的任何数据；
- 不拦截、不篡改非 DNS 流量（其路由不进入本应用）。

## 3. 可能造成的后果（请自行评估）

- 屏蔽域名**可能导致部分应用功能异常**：登录、支付、推送、图片/视频加载、直播、地图、崩溃上报等。本软件提供**例外（放行）**机制以便恢复，但**你需要自行判断并承担**由此产生的任何影响。
- 部分应用有**完整性校验或风控**，使用本软件可能触发异常提示。
- 本软件的清理功能会**删除文件**。虽然已刻意收窄匹配范围（仅广告目录名/前缀 + 广告产物扩展名，中文名目录不碰）并提供删除留痕与"先问后删"开关，但你仍应在使用前确认其行为符合你的预期。**因删除文件造成的损失由使用者自行承担。**

## 4. 法律与合规

- **广告拦截的合法性因国家、地区、司法辖区而异。** 部分应用的用户协议可能明确禁止屏蔽广告或以广告为对价的功能。使用者应自行确认在当地法律及应用协议下的合规性，并承担相应后果。
- 本软件不提供任何法律意见。如有疑问，请咨询专业人士。

## 5. 无担保

本软件按**"现状"（AS IS）**提供，不附带任何明示或默示的担保，包括但不限于对**适销性、特定用途适用性、不侵权、无错误或不中断运行**的担保。

## 6. 责任限制

在适用法律允许的最大范围内，**作者与本项目的任何贡献者，均不对**因使用或无法使用本软件而产生的任何**直接、间接、偶然、特殊、惩罚性或后果性损害**（包括但不限于利润损失、数据丢失、设备损坏、业务中断、第三方索赔）**承担责任**，无论其基于合同、侵权或其他法理，即使已被告知该类损害的可能性。

## 7. 第三方内容

- 内置广告域名列表编译自 [anti-AD](https://github.com/privacy-protection-tools/anti-AD)（MIT 许可），其版权归原作者所有，署名见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 本项目与上述第三方、以及任何被拦截域名的所有者**无隶属、无赞助、无背书关系**。
- 文中提及的任何第三方应用/公司名称/商标，仅用于事实性技术说明，权利归各自所有者。

## 8. 变更

本免责声明可能随版本更新而调整，恕不另行单独通知。以仓库中本文件的最新版本为准。

---

# English

**AdFence** is an open-source **DNS-layer filtering** tool for Android, released under the MIT License.

By downloading, installing or using this software you agree to the following. **If you do not agree, do not use it.**

1. **Personal use only.** Use it on devices you own or are authorized to manage. You are responsible for compliance in your jurisdiction; ad blocking is regulated differently from country to country, and using it may violate certain applications' terms of service.
2. **What it does.** It resolves known advertising/tracking domains to `0.0.0.0` **on your own device** and forwards all other DNS queries upstream. Non-DNS traffic does not enter the app.
3. **What it does not do.** It does not modify, inject into, hook, or crack any third-party application, and it collects and transmits **no** personal data.
4. **Possible consequences.** Blocking domains may break features (login, payments, push, media loading, live streaming). An exception/allow mechanism is provided, but you are responsible for any impact. The cleanup feature **deletes files**; matching is deliberately narrow and every deletion is logged, yet you remain responsible for the outcome.
5. **No warranty.** The software is provided **"AS IS"**, without warranty of any kind, express or implied.
6. **Limitation of liability.** To the maximum extent permitted by law, the authors and contributors shall **not be liable** for any direct, indirect, incidental, special, punitive or consequential damages arising from the use of, or inability to use, this software.
7. **Third-party content.** The bundled blocklist is compiled from [anti-AD](https://github.com/privacy-protection-tools/anti-AD) (MIT). This project is not affiliated with, sponsored by, or endorsed by any third party or any domain owner.
8. **Changes.** This disclaimer may be updated with new releases; the version in this repository is authoritative.
