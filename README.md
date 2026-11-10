# PureDuPan

[![Release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook?style=flat&labelColor=30363d&color=3867d6&label=Release&logo=github&logoColor=white)](https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/total?style=flat&labelColor=30363d&color=16806a&label=Downloads)](https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest)
[![Stars](https://img.shields.io/github/stars/xiyunmn/PureDuPan?style=flat&labelColor=30363d&color=9d6b00&label=Stars)](https://github.com/xiyunmn/PureDuPan/stargazers)
[![License](https://img.shields.io/badge/License-GPL--3.0-7860a8?style=flat&labelColor=30363d&color=7860a8)](https://github.com/xiyunmn/PureDuPan/blob/main/LICENSE)
<br>
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-358259?style=flat&labelColor=30363d&color=358259&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![libxposed API 102](https://img.shields.io/badge/libxposed-API%20102-526fad?style=flat&labelColor=30363d&color=526fad)](https://github.com/libxposed/api)
[![arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-b45309?style=flat&labelColor=30363d&color=b45309)](https://developer.android.com/ndk/guides/abis#arm64-v8a)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&labelColor=30363d&color=7f52ff&logo=kotlin&logoColor=white)](https://kotlinlang.org/)

PureDuPan 是一个百度网盘 Xposed 净化模块，提供广告屏蔽、页面定制、主题跟随系统、存储路径重定向等功能。

当前版本：`1.5.2`

[稳定版下载](https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest) · [CI 版下载](https://github.com/xiyunmn/PureDuPan/actions/workflows/build-ci.yml?query=branch%3Amain) · [查看源码](https://github.com/xiyunmn/PureDuPan) · [反馈问题](https://github.com/xiyunmn/PureDuPan/issues)

> [!CAUTION]
> **免责声明**
>
> 本项目为独立开发的**非官方工具**，与百度及其关联公司无隶属或合作关系。相关名称、商标归各自权利人所有。
>
> 本模块仅供学习与技术研究使用。使用可能导致**应用异常、功能不可用、账号风险**等后果，请自行评估使用风险，并遵守适用法律及相关服务条款。
>
> 本模块按现状提供。**在法律允许的范围内，作者不对使用或无法使用本模块造成的损失承担责任**；使用者因违法、违规或不当使用产生的责任，由使用者自行承担。

[支持范围](#支持范围) · [安装与入口](#安装与入口) · [功能概览](#功能概览) · [排查与反馈](#排查与反馈) · [构建](#构建)

## 支持范围

模块基于 **libxposed API 102**，支持**热重载**；仅支持 `arm64-v8a`，按宿主包名分别适配。

| 宿主 | 主要适配版本 |
| --- | --- |
| 百度网盘 | `13.25.2` ~ `13.36.7` |
| 百度网盘三星版 | `13.25.2` ~ `13.36.7` |
| 百度网盘国际版 | `13.11.15` |

<details open>
<summary>宿主包名</summary>

```text
百度网盘         com.baidu.netdisk
百度网盘国际版   com.baidu.drive.app
百度网盘三星版   com.baidu.netdisk.samsung
```

</details>

## 安装与入口

日常使用建议选择**稳定版**。**CI 版**包含最新源码改动，供尝鲜和问题验证；需登录 GitHub，在成功构建的 Artifacts 区域下载。

1. **安装并启用模块**，在框架作用域中勾选需要净化的宿主。
2. **重启宿主**，或强制停止后重新打开。
3. **打开模块设置**：首页长按右上角“+”，或我的页长按右上角“扫一扫”。
4. 部分功能默认隐藏，连续点击模块版本号 **5 次**可解锁。

设置**自动保存**，部分功能需**重启宿主**后生效。

未 root 设备可尝试 [NPatch](https://github.com/7723mod/NPatch) 或 [LSPatch（JingMatrix）](https://github.com/JingMatrix/LSPatch) 等框架，需使用支持 libxposed API 102 的版本。

## 功能概览

### 广告与弹窗

- 去除冷启动、热启动和切屏广告。
- 屏蔽运营、更新、评分和通知提示弹窗。
- 屏蔽全屏备份引导、SVIP 图标引导、共享页推送引导。
- 在“弹窗屏蔽”中移除首页、共享页的广告悬浮窗；可用子项按宿主显示。
- 移除下载弹窗，未连接 Wi-Fi 时可继续创建下载任务。

### 页面定制

- **首页**：隐藏工具栏、搜索框提示词，以及推荐、回忆、转存、最近等板块。
- **首页列表**：调整“最近”显示数量，设置“转存/订阅”纵向列表及项目数量。
- **搜索页**：隐藏 AI 入口、推荐提示词、历史记录、智能推荐、SVIP 横幅等。
- **文件页**：隐藏相册备份提示栏、底部数据安全提示和转存成功弹窗中的 SVIP 优惠广告卡片。
- **下载页**：隐藏游戏推荐浮窗动画、推广广告和底部会员推广区域；单选已下载文件或文件夹时，可用其它应用打开。
- **我的页**：隐藏游戏中心、服务入口、横幅广告、签到红点、AI 点数资产、任务中心气泡及部分入口文案。

国内版、三星版还可隐藏首页顶部推广和搜索框 AIGC 图标。

### 会员卡片

- **背景**：更换图片，支持模糊、缩放、旋转、镜像和拖动调整。
- **外观**：调整卡片尺寸，分别调暗浅色、深色模式下的背景。
- **内容**：隐藏运营入口、权益项、权益栏、SVIP 等级、SVIP 状态和续费按钮。
- **交互**：可移除卡片点击跳转；长按卡片可查看自定义背景原图并保存到相册。

### 底栏 Tab

- 隐藏指定底栏 Tab，模块会**保留至少一项**。
- **国内版、三星版**：可隐藏 AIGC、会员入口，也可将底栏 AI 入口替换为会员入口。
- **国际版**：提供首页、文件、共享、我的四项。
- 移除共享 Tab 红色角标，并可从宿主底栏选项中指定默认启动 Tab。

### 夜间模式

- 支持**跟随系统深浅色**。
- 启动图可跟随系统深浅色；适配系统启动画面需额外勾选“**系统界面（SystemUI）**”作用域。

### 拓展功能

- **自动签到**：默认关闭，可能存在账号或设备风险。
- **视频倍速**：解除非会员在线视频倍速入口限制，不伪造会员身份。
- **视频画质**：放开客户端画质入口，普通 1080P 仍可能受服务端限制。
- **存储路径重定向**：可为不同宿主选择 SAF 公共下载目录，并按需启用公共根目录保护。
- **配置备份**：通过“管理模块设置”导出或导入配置。

### 性能优化

**默认关闭**。建议逐项开启，遇到异常可单独关闭；可用项目按宿主版本显示。

## 排查与反馈

百度网盘不同版本的类名、页面结构和混淆程度会变化，部分功能可能需要重新适配。

### 功能失效时

1. **核对版本和开关**：确认宿主类型、宿主版本和模块版本，检查功能是否启用，并重启宿主。
2. **查看 DexKit 状态**：在模块设置中确认解析是否成功；设备指纹信息也可在设置中查看。
3. **查看详细日志**：开启“输出详细日志”，检查是否有 `WangPanHook` 相关失败信息。

DexKit 默认启用，模块或宿主变化后会自动预热，也可手动全量扫描。

日志位于宿主缓存目录的 `wangpanhook/logs/`。长按“输出详细日志”可复制路径，通过“管理日志”可导出或清理。

### 提交反馈

**[反馈问题 →](https://github.com/xiyunmn/PureDuPan/issues)**　·　**[提交 PR →](https://github.com/xiyunmn/PureDuPan/pulls)**

反馈时请提供**宿主类型与版本、模块版本**，说明失效功能和复现步骤，并附相关日志或 DexKit 状态截图。

## 构建

<details>
<summary>查看构建命令与签名配置</summary>

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release 签名通过 Gradle 属性配置：

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

未配置签名时生成未签名的 Release APK。

</details>

## 致谢

- **[ForbidAd4TieBa](https://github.com/aikavvak12una/ForbidAd4TieBa)**：模块结构参考。
- **[DexKit](https://github.com/LuckyPray/DexKit)**：混淆目标解析。
