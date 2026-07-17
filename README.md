# PureDuPan

<a href="https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest"><img alt="GitHub all releases" src="https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/total?label=Downloads"></a>
<a href="https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest"><img alt="GitHub latest release" src="https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook"></a>

PureDuPan 是一个百度网盘 Xposed 净化模块，提供广告屏蔽、页面精简、会员卡片定制、夜间模式、下载页定制、自动签到、视频选项解锁和部分性能优化。

项目源码：https://github.com/xiyunmn/PureDuPan

## 支持范围

模块基于 libxposed API 101 开发，按宿主包名隔离适配：

```text
百度网盘         com.baidu.netdisk
百度网盘国际版   com.baidu.drive.app
百度网盘三星版   com.baidu.netdisk.samsung
```

当前主要适配：

- 百度网盘：`13.25.2` ~ `13.28.9`
- 百度网盘国际版：`13.11.8`
- 百度网盘三星版：`13.25.2` ~ `13.28.9`

## 安装与入口

1. 在支持 libxposed API 101 的框架中安装模块。
2. 在作用域中勾选需要净化的百度网盘宿主。
3. 强制停止或重启对应宿主。
4. 打开模块设置：
   - 首页长按右上角“+”
   - 我的页长按右上角“扫一扫”

未 root 设备可尝试 [NPatch](https://github.com/7723mod/NPatch) 等支持 libxposed API 101 的框架。

## 功能概览

### 广告与弹窗

- 去除冷启动、热启动和切屏广告。
- 屏蔽应用内运营弹窗、更新弹窗、评分弹窗、通知提示弹窗。
- 屏蔽全屏备份引导、SVIP 图标引导、共享页推送引导。
- 移除下载弹窗，未连接 Wi-Fi 时可继续创建下载任务。

### 页面定制

- 首页：隐藏搜索推广、AIGC 图标、工具栏、轮播图、推荐提示、回忆/转存/最近等卡片。
- 搜索页：隐藏 AI 入口、推荐提示词、历史记录、智能推荐、SVIP 横幅等。
- 文件页：隐藏底部数据安全提示。
- 下载页：隐藏游戏推荐浮窗动画、推广广告、底部会员推广区域。
- 共享页：移除广告悬浮窗和相册备份提示栏。
- 我的页：隐藏游戏中心、横幅广告、服务入口、签到红点、AI 点数资产、任务中心气泡和部分入口文案。

### 会员卡片

- 更换会员卡片背景，支持模糊、缩放、旋转和偏移。
- 调整会员卡片尺寸。
- 隐藏运营入口、权益项、权益栏、SVIP 等级、SVIP 状态、续费按钮。
- 可移除会员卡片点击跳转，或改为点击查看自定义背景图。

### 底栏 Tab

- 隐藏指定底栏 Tab。
- 隐藏 AI / AIGC 入口。
- 国内版、三星版可将底栏 AI 入口替换为会员入口。
- 移除共享 Tab 红色角标。
- 模块会保留至少一个 Tab，避免无法导航。

### 夜间模式

- 国内版、三星版支持跟随系统深浅色。
- 国际版可恢复夜间模式开关，并支持跟随系统。

### 拓展功能

- 自动签到：默认关闭，可能存在账号或设备风险。
- 移除首页右滑事件：国际版阻止首页右滑进入负一屏。
- 解锁视频倍速：解除非会员在线视频倍速入口限制，不伪造会员身份。
- 解锁视频画质：放开客户端画质入口，普通 1080P 仍可能受服务端限制。

### 性能优化

性能优化默认关闭。建议逐项开启，遇到异常可单独关闭。可用项目会按宿主版本显示。

## 调试

- 可在模块设置中开启详细日志。
- DexKit 默认启用，模块或宿主变化后会自动预热，也可手动全量扫描。
- 日志位于宿主缓存目录的 `wangpanhook/logs/`。
- DexKit 状态和设备指纹信息可在设置中查看。

## 兼容性说明

百度网盘不同版本的类名、页面结构和混淆程度会变化，部分功能可能需要重新适配。

国内版和三星版可能出现弱混淆或强混淆分支，国际版默认按高混淆处理。模块会优先使用数据层、渲染层和 DexKit 定位。

功能失效时请确认：

- 宿主类型和版本。
- 模块版本。
- 开关是否启用，宿主是否重启。
- DexKit 状态是否成功。
- 详细日志中是否有 `WangPanHook` 相关失败信息。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release 签名通过 Gradle 属性配置：

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

未配置签名时，release 产物为未签名输出。

## 反馈

反馈问题时请提供：

- 百度网盘宿主类型、宿主版本和模块版本。
- 失效功能与复现步骤。
- 相关日志或 DexKit 状态截图。

[提交 issue / PR](https://github.com/xiyunmn/PureDuPan)

## 致谢

本项目参考 [ForbidAd4TieBa](https://github.com/aikavvak12una/ForbidAd4TieBa) 的模块结构思路，并使用 [DexKit](https://github.com/LuckyPray/DexKit) 辅助混淆目标解析。

## 免责声明

本模块仅供学习与技术研究使用。使用本模块可能导致应用异常、功能不可用、账号风险或其它不可预期后果，请自行承担使用风险。
