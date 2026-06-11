# PureDuPan

<a href="https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest"><img alt="GitHub all releases" src="https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/total?label=Downloads"></a>
<a href="https://github.com/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook/releases/latest"><img alt="GitHub latest release" src="https://img.shields.io/github/v/release/Xposed-Modules-Repo/com.xiyunmn.puredupan.hook"></a>

PureDuPan 是一个面向百度网盘的 Xposed 净化模块，用于屏蔽广告弹窗、简化界面、定制部分页面元素，并提供谨慎可选的性能优化项。

当前模块基于现代 libxposed API 101 开发，目标作用域为：

```text
百度网盘 com.baidu.netdisk
```

当前仓库逆向验证基线：`百度网盘 13.25.2`。

## 安装与入口

1. 在支持 libxposed API 101 的框架中安装并启用本模块。
2. 将模块作用域设置为 `com.baidu.netdisk`。
3. 重启百度网盘。
4. 模块设置入口（任选其一）：
   - 进入百度网盘「我的」页面，长按右上角”扫一扫”图标
   - 进入百度网盘「首页」，长按右上角”+”按钮
5. 部分功能默认隐藏，点击版本号5次以解锁。

未 root 设备可使用 [NPatch](https://github.com/7723mod/NPatch) 或其它支持 libxposed API 101 的免 root 框架。

## 主要功能

### 内容屏蔽

- 去除冷启动开屏、热重载、切后台返回时的广告入口。
- 屏蔽应用内运营弹窗、幸运券包等活动弹窗。
- 屏蔽软件更新弹窗。
- 屏蔽全屏备份引导页和 SVIP 图标引导。
- 屏蔽切换到「共享」页时的推送通知引导。
- 屏蔽切换到「文件」页时的评分引导弹窗。

### 首页与分享页定制

- 隐藏「首页」顶部搜索框区域的动态推广。
- 隐藏首页搜索框提示词。
- 隐藏首页搜索框 AIGC 图标和相关动画视图。
- 隐藏首页信息流上方“开启推荐”提示。
- 隐藏首页信息流头部“回忆”“转存”“最近”等卡片。
- 移除「共享」页右下角广告悬浮窗。

### 我的页定制

- 隐藏顶部滚动时出现的续费提示。
- 移除游戏中心入口。
- 移除底部横幅广告。
- 隐藏“我的服务”入口。
- 隐藏任务/金币中心气泡、签到红点、AI 点数资产区。
- 可按文本隐藏“管理空间”“领奖励”“账号、退出”“明星皮肤上线啦”等入口。

### 会员卡片定制

- 替换会员卡片背景图。
- 调整背景图模糊、缩放、旋转和偏移。
- 调整会员卡片背景视图尺寸。
- 隐藏会员卡片运营入口、权益项、权益栏、SVIP 等级、SVIP 状态和续费按钮。
- 移除会员卡片点击跳转，或改为点击查看当前自定义背景图。
- 预览背景图时可保存原图到相册。

### 底栏定制

- 将底栏 AI 入口替换为「会员」入口。
- 移除底栏「共享」红色角标。
- 自定义隐藏底部 Tab（可任意组合）：
  - 文件
  - 共享
  - 会员
  - 首页
  - 我的
- 模块会自动确保至少保留一个底部 Tab，避免隐藏全部入口导致无法导航。

### 夜间模式

- 可让百度网盘夜间模式跟随系统深浅色。
- 同步宿主设置页夜间模式开关状态。
- 处理底栏「我的」头像在换肤后的刷新。

### 性能优化

**注意**：性能优化功能默认全部关闭，需在模块设置中手动开启。建议逐项开启并观察实际影响，部分功能可能影响宿主正常使用。

可选优化项（共13项）：

1. **跳过清理组件服务注册** - 阻止垃圾清理服务后台注册
2. **跳过流量包权益 Socket 注册** - 阻止 Datapack 组件 Socket 注册
3. **阻断 AIGC 小组件后台刷新** - 阻止 AIGC 小组件后台更新、缓存刷新和资源解压
4. **阻断动态插件自动下载** - 阻止 OCR、模型、Flutter App 等动态插件自动下载和安装
5. **阻断 OEM 厂商推送服务** - 阻止小米、华为、OPPO、VIVO 等推送组件唤起（同时覆盖主进程和 :pushservice 进程）
6. **阻断视频广告素材预下载** - 阻止视频前贴广告素材预下载
7. **阻断广告 SDK 服务初始化** - 阻止多个广告 SDK 的下载服务初始化
8. **阻断 Swan 小程序预加载** - 阻止 Swan 小程序运行时预加载
9. **阻断缩略图端计算服务** - 阻止缩略图压缩服务初始化和任务提交
10. **阻断激励业务服务** - 阻止激励业务后台服务唤起
11. **阻断音频媒体服务自动唤起** - 阻止音频播放服务自动启动，同时保留用户主动播放音频时的正常功能
12. **阻断启动期图标资源下载** - 阻止启动期异步下载图标资源
13. **阻断 B2F 引导弹窗数据预拉取** - 阻止启动期预拉取 B2F 引导/运营弹窗数据

每项优化均可独立开启/关闭，互不影响。如遇异常可单独关闭对应项。

### 调试

- 可在模块设置末尾的“调试 / 日志和快照”中开启详细 Hook 日志和配置快照，便于排查模块状态。
- 日志写入百度网盘 APP 缓存目录下的 `wangpanhook/logs/`，可在模块设置的“调试 / 清理日志”中手动删除。

## 兼容性

当前主要按 `百度网盘 13.25.2` 验证。由于百度网盘内部类名、方法名和页面结构可能随版本变化，部分功能在其它版本上可能失效或需要重新适配。

如果某项功能失效，请先确认：

- 百度网盘版本。
- 模块版本。
- 对应开关是否启用并已重启宿主。
- 详细日志中是否有 `WangPanHook` 相关失败信息。

## 构建

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release 签名可通过 Gradle 属性配置：

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

未配置 release keystore 时，release 产物会是未签名输出。

## 反馈

反馈问题时建议提供：

- 百度网盘版本号和模块版本号。
- 失效功能和复现步骤。
- 是否冷启动、热重载、切后台返回或特定页面触发。
- 开启详细日志后的 `WangPanHook` 日志。

[提交 issue / PR](https://github.com/xiyunmn/PureDuPan)

## 致谢

本项目基于 [ForbidAd4TieBa](https://github.com/aikavvak12una/ForbidAd4TieBa) 的模块结构思路重构开发。

## 免责声明

本模块仅供学习与技术研究使用，请勿用于任何违反法律法规或平台规则的用途。

使用本模块可能导致应用异常、功能不可用、账号风险或其它不可预期后果。安装和使用前请自行审查源码并确认模块行为符合预期，作者不对使用本模块造成的任何后果承担责任。
