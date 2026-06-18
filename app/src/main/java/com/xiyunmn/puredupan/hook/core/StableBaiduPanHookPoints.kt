package com.xiyunmn.puredupan.hook.core

/**
 * 百度网盘固定（非混淆）类名和方法名 Hook 点。
 *
 * 这些是网盘应用中经过逆向验证的稳定类名，
 * 不需要动态符号扫描即可直接 Hook。
 */
object StableBaiduPanHookPoints {
    /** 主 Activity 类名 */
    const val MAIN_ACTIVITY = "com.baidu.netdisk.ui.MainActivity"
    const val HOME_ACTIVITY = "com.baidu.netdisk.homepage.HomeActivity"
    const val NEW_ABOUT_ME_ACTIVITY = "com.baidu.netdisk.ui.aboutme.NewAboutMeActivity"

    /** "我的" 页面 - 游戏中心 Fragment */
    const val ABOUT_ME_GAME_CENTER_FRAGMENT =
        "com.baidu.netdisk.operation.ui.fragment.game.AboutMeGameCenterFragment"

    /** 首页底部 AI Tab 控制 (Kotlin 顶层函数类) */
    const val AI_CLOUD_TAB_AMIS_KT = "com.baidu.netdisk.main.model.data.tool.AiCloudTabAmisKt"

    /** 首页顶部搜索框 Fragment (AI 控件所在) */
    const val HOME_SEARCHBOX_FRAGMENT = "com.baidu.netdisk.home25ai.fragment.HomeSearchboxFragment"
    const val HOME25AI_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_home25ai.Home25aiContext\$Companion"
    const val HOME25AI_LOAD_HOME_BANNER_METHOD = "loadHomeBanner"

    /** 热启动广告管理器 */
    const val ADVERTISE_HOT_START_MANAGER = "com.baidu.netdisk.advertise.AdvertiseHotStartManager"

    /** 冷启动开屏管理器 */
    const val SPLASH_LIFE_HOLDER_CONTAINER =
        "com.baidu.netdisk.advertise.splash.SplashLifeHolderContainer"
    const val SPLASH_LIFE_HOLDER_CONTAINER_LOAD_AD_METHOD = "loadAD"
    const val SPLASH_MANAGER = "com.baidu.netdisk.advertise.splash.SplashManager"
    const val SPLASH_MANAGER_START_SHOW_COLD_SPLASH_AD_METHOD = "startShowColdSplashAd"
    const val SPLASH_MANAGER_LIMIT_COUNT_AD_SHOW_METHOD = "limitCountAdShow"
    const val SPLASH_AD_FINISH_LISTENER = "com.baidu.netdisk.advertise.splash.IAdFinishListener"
    const val SPLASH_AD_FINISH_LISTENER_ON_AD_FINISH_METHOD = "onAdFinish"
    const val SPLASH_LIFE_HOLDER_CONTAINER_DESTROY_METHOD = "destroyContainer"
    const val ANDROIDX_LIFECYCLE_OWNER = "androidx.lifecycle.LifecycleOwner"
    const val ADVERTISE_NOTCH_SCREEN_UTILS = "com.baidu.netdisk.advertise.utils.NotchScreenUtils"
    const val BUSINESS_NOTCH_SCREEN_UTILS = "com.baidu.netdisk.business.extension.NotchScreenUtils"
    const val NOTCH_SCREEN_UTILS_NOTCH_FULL_SCREEN_METHOD = "notchFullScreen"
    const val BUSINESS_ACTIVITY_KT = "com.baidu.netdisk.business.extension.ActivityKt"
    const val ACTIVITY_KT_SET_P_FULL_SCREEN_METHOD = "setPFullScreen"

    /** 宿主状态栏/皮肤工具 */
    const val STATUS_BAR_UTILS = "com.netdisk.themeskin.utils.StatusBarUtils"
    const val STATUS_BAR_UTILS_IMMERSE_STATUS_BAR_METHOD = "immerseStatusBar"

    /** 运营弹窗 */
    const val BUSINESS_OP_DIALOG = "com.baidu.netdisk.ui.operation.BusinessOPDialog"
    const val BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD = "showDialog"
    const val BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD = "onCreateView"

    /** 软件更新弹窗 */
    const val VERSION_UPDATE_HELPER = "com.baidu.netdisk.ui.versionupdate.VersionUpdateHelper"
    const val VERSION_UPDATE_HELPER_SHOW_LC_VERSION_DIALOG_METHOD = "showLCVersionDialog"

    /** 全屏备份引导弹窗 Activity */
    const val NEW_QUICK_SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.NewQuickSettingsActivity"
    const val NEW_QUICK_SETTINGS_ACTIVITY_ON_CREATE_METHOD = "onCreate"

    /** 幸运券包 / 生活产品活动弹窗 */
    const val RECEIVE_COUPON_DIALOG_V3 =
        "com.baidu.netdisk.business.guide.dialog.lifeproduct.ReceiveCouponDialogV3"
    const val DIALOG_SHOW_METHOD = "show"

    /** 专属图标全屏引导弹窗 */
    const val SVIP_ICON_GUIDE = "com.baidu.netdisk.ui.svipicon.SvipIconGuide"
    const val SVIP_ICON_GUIDE_SHOW_GUIDE_METHOD = "showGuide"

    /** 共享页推送通知半屏引导弹窗 */
    const val SHARE_TAB_PUSH_GUIDE_NORMAL_DIALOG =
        "com.baidu.netdisk.ui.cloudp2p.pushguide.ShareTabPushGuideNormalDialog"
    const val SHARE_TAB_PUSH_GUIDE_ON_START_METHOD = "onStart"

    /** 文件页应用商店评分引导弹窗 */
    const val APP_STORE_REVIEW_DIALOG = "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewDialog"
    const val APP_STORE_REVIEW_DIALOG_SHOW_METHOD = "show"
    const val APP_STORE_REVIEW_SHOW_STRATEGY =
        "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewShowStrategy"
    const val APP_STORE_REVIEW_SHOW_CENTER_DIALOG_METHOD = "showCenterDialog"

    /** 文件页照片自动备份底部引导条 */
    const val FILE_TAB_BOTTOM_BAR_FACTORY =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.FileTabBottomBarFactory"
    const val FILE_TAB_BOTTOM_BAR_FACTORY_CREATE_METHOD = "create"
    const val ALBUM_BACKUP_BAR_VIEW =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.AlbumBackupBarView"
    const val ALBUM_BACKUP_BAR_VIEW_INIT_UI_METHOD = "initUI"

    /** "我的" 页面 Activity — 模块设置长按入口所在 */
    const val ABOUT_ME_ACTIVITY = "com.baidu.netdisk.ui.aboutme.AboutMeActivity"

    /** 网盘设置页 — 夜间模式开关所在 */
    const val SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.SettingsActivity"

    /** "我的" 页面底部 Fragment (横幅广告 / 我的服务 所在) */
    const val ABOUT_ME_BOTTOM_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeBottomFragment"
    const val ABOUT_ME_TOP_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragment"
    const val ABOUT_ME_TOP_FRAGMENT_HETEROMO =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragmentHeteromo"
    const val ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD = "onViewCreated"

    /** 首页悬浮球 Fragment — 逻辑层拦截数据回调 */
    const val NEW_HOME_FAB_FRAGMENT =
        "com.baidu.netdisk.homepage.ui.fab.NewHomePageFabFragment"

    /** 悬浮球运营数据响应类 — onOperationActivitySuccess 的参数类型 */
    const val POPUP_RESPONSE = "com.baidu.netdisk.operation.io.PopupResponse"

    /** 自研换肤引擎配置类 — isDefaultSkin 所在类 */
    const val SKIN_CONFIG_CLASS = "com.netdisk.themeskin.SkinConfig"

    /** 全局 Activity 基类 — 挂载 onResume/onConfigurationChanged 实现实时换肤 */
    const val BASE_ACTIVITY = "com.baidu.netdisk.BaseActivity"

    /** 主动换肤入口 (Kotlin 顶层函数类) — changeSkin(String, SkinLoaderListener) */
    const val CHANGE_SKIN_KT = "com.baidu.netdisk.themskin.ChangeSkinKt"

    const val ABOUT_ME_ACTIVITY_ON_CREATE_METHOD = "onCreate"
    const val MAIN_ACTIVITY_SHOW_OR_HIDE_NEW_TIPS_METHOD = "showOrHideNewTips"
    const val MAIN_ACTIVITY_PRESENTER = "com.baidu.netdisk.ui.presenter.MainActivityPresenter"
    const val MAIN_ACTIVITY_PRESENTER_DRAW_UPDATE_INDICATOR_METHOD = "drawUpdateIndicator"

    /** Performance optimize - garbage clean component service registration */
    const val GRABAGECLEAN_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_grabageclean.GrabagecleanContext\$Companion"
    const val GRABAGECLEAN_REGISTER_GARBAGE_CLEAN_SERVICE_METHOD = "registerGarbageCleanService"
    const val DATAPACK_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_datapack.DatapackContext\$Companion"
    const val DATAPACK_REGISTER_SOCKET_METHOD = "registerSocket"
    const val AIGC_CLOUD_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_aigc_cloud.AigcCloudContext\$Companion"
    const val AIGC_UPDATE_WIDGET_FROM_CACHE_METHOD = "updateAigcWidgetFromCache"
    const val AIGC_UPDATE_WIDGET_BY_DATA_METHOD = "updateAigcWidgetByData"
    const val AIGC_UNZIP_CLOUD_ZIP_METHOD = "unzipAigcCloudZip"
    const val ADVERTISE_SDK = "com.baidu.netdisk.advertise.AdvertiseSDK"
    const val ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD = "downloadVideoFrontAd"
    const val SWAN_APP_PRELOAD_HELPER =
        "com.baidu.swan.apps.process.messaging.service.SwanAppPreloadHelper"
    const val SWAN_CLIENT_PUPPET =
        "com.baidu.swan.apps.process.messaging.service.SwanClientPuppet"
    const val SWAN_PRELOAD_TRY_PRELOAD_METHOD = "tryPreload"
    const val SWAN_PRELOAD_TRY_PRELOAD_IF_KEEP_ALIVE_METHOD = "tryPreloadIfKeepAlive"
    const val SWAN_PRELOAD_START_SERVICE_FOR_PRELOAD_NEXT_METHOD = "startServiceForPreloadNext"
    const val CLIENT_COMPUTE_MANAGER = "com.baidu.netdisk.service.ClientComputeManager"
    const val CLIENT_COMPUTE_MANAGER_INIT_METHOD = "init"
    const val THUMBNAIL_OPERATOR_UTIL =
        "com.baidu.netdisk.terminalcalc.compress.service.operator.ThumbnailOperatorUtil"
    const val THUMBNAIL_OPERATOR_UTIL_ADD_JOB_METHOD = "addJob"
    const val TERMINALCALC_COMPRESS_BEAN =
        "com.baidu.netdisk.terminalcalc.compress.service.CompressBean"
    const val CONFIG_COMPRESS_IMAGE = "com.baidu.netdisk.base.storage.config.ConfigCompressImage"
    const val INCENTIVE_BUSINESS_SERVICE =
        "com.baidu.netdisk.platform.business.incentive.service.BusinessService"
    const val INCENTIVE_BUSINESS_SERVICE_ON_CREATE_METHOD = "onCreate"
    const val INCENTIVE_BUSINESS_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    const val INCENTIVE_BUSINESS_SERVICE_ON_BIND_METHOD = "onBind"
    const val NETDISK_MEDIA_BROWSER_SERVICE = "com.baidu.netdisk.service.NetdiskMediaBrowserService"
    const val AUDIO_PLAY_SERVICE = "com.baidu.netdisk.audio.service.AudioPlayService"
    const val MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService"
    const val AUDIO_PLAY_SERVICE_BIND_ACTION = "com.baidu.netdisk.ui.preview.player.ACTION_BIND_PLAY_SERVICE"
    const val NETDISK_MEDIA_BROWSER_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    const val NETDISK_MEDIA_BROWSER_SERVICE_ON_GET_ROOT_METHOD = "onGetRoot"
    const val AUDIO_PLAY_SERVICE_ON_BIND_METHOD = "onBind"
    const val FLOAT_VIEW_STARTUP_TASK = "com.baidu.netdisk.startup.task.FloatViewStartupTask"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD = "initAudioCircleView"
    const val AUDIO_CIRCLE_VIEW_HELPER = "com.baidu.netdisk.audio.ui.view.AudioCircleViewHelper"
    const val AUDIO_CIRCLE_VIEW_MANAGER = "com.baidu.netdisk.audio.ui.view.AudioCircleViewManager"
    const val AUDIO_CIRCLE_VIEW_HELPER_INIT_METHOD = "init"
    const val AUDIO_CIRCLE_VIEW_MANAGER_BIND_PLAYER_SERVICE_METHOD = "bindPlayerService"
    const val ICON_DOWNLOAD_MANAGER = "com.baidu.netdisk.base.utils.IconDownloadManager"
    const val ICON_DOWNLOAD_MANAGER_START_DOWNLOAD_METHOD = "startDownload"
    const val KOTLIN_FUNCTION2 = "kotlin.jvm.functions.Function2"
    const val GUIDE_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_guide.GuideContext\$Companion"
    const val GUIDE_CONTEXT_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD = "requireB2FGuidanceDialogData"
    const val GUIDE_APIS_KT = "com.baidu.netdisk.GuideApisKt"
    const val GUIDE_APIS_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD = "requireB2FGuidanceDialogData"
    const val AD_SDK_SERVICE_ON_CREATE_METHOD = "onCreate"
    const val AD_SDK_SERVICE_ON_BIND_METHOD = "onBind"
    const val AD_SDK_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    val AD_SDK_DOWNLOAD_SERVICE_CLASSES = listOf(
        "com.qq.e.comm.DownloadService",
        "com.byazt.zs.ApiDownloadHandlerService",
        "com.beizi.ad.DownloadService",
        "com.ubix.ssp.open.comm.DownloadService",
        "com.octopus.ad.DownloadService",
    )
    const val GAME_CENTER_VIEW_MODEL = "com.baidu.netdisk.ui.aboutme.viewmodel.GameCenterViewModel"
    const val GAME_CENTER_CAN_SHOW_METHOD = "gameCenterCanShow"
    const val GAME_CENTER_FETCH_CONFIG_METHOD = "fetchGameCenterConfig"
    const val OEM_PUSH_SERVICE_ON_CREATE_METHOD = "onCreate"
    const val OEM_PUSH_SERVICE_ON_START_METHOD = "onStart"
    const val OEM_PUSH_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    const val OEM_PUSH_SERVICE_ON_BIND_METHOD = "onBind"
    const val OEM_PUSH_SERVICE_ON_HANDLE_INTENT_METHOD = "onHandleIntent"
    const val OEM_PUSH_RECEIVER_ON_RECEIVE_METHOD = "onReceive"
    const val OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD = "onMessageReceived"
    const val OEM_PUSH_ON_NEW_TOKEN_METHOD = "onNewToken"
    const val OEM_PUSH_ON_TOKEN_ERROR_METHOD = "onTokenError"
    val OEM_PUSH_ON_START_COMMAND_SERVICE_CLASSES = listOf(
        "com.heytap.msp.push.service.DataMessageCallbackService",
        "com.heytap.msp.push.service.CompatibleDataMessageCallbackService",
        "com.baidu.techain.push.HWPushMsgService",
        "com.huawei.hms.support.api.push.service.HmsMsgService",
        "com.vivo.push.sdk.service.CommandService",
        "com.xiaomi.push.service.XMPushService",
    )
    val OEM_PUSH_ON_CREATE_SERVICE_CLASSES = listOf(
        "com.vivo.push.sdk.service.CommandService",
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.push.service.XMJobService",
    )
    val OEM_PUSH_ON_START_SERVICE_CLASSES = listOf(
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.MessageHandleService",
        "com.xiaomi.push.service.XMPushService",
    )
    val OEM_PUSH_ON_BIND_SERVICE_CLASSES = listOf(
        "com.huawei.hms.support.api.push.service.HmsMsgService",
        "com.baidu.techain.push.HWPushMsgService",
    )
    val OEM_PUSH_ON_HANDLE_INTENT_SERVICE_CLASSES = listOf(
        "com.meizu.cloud.pushsdk.NotificationService",
    )
    val OEM_PUSH_RECEIVER_CLASSES = listOf(
        "com.xiaomi.mipush.sdk.PushMessageReceiver",
        "com.xiaomi.push.service.receivers.PingReceiver",
        "com.huawei.hms.support.api.push.PushReceiver",
        "com.huawei.hms.support.api.push.PushMsgReceiver",
        "com.vivo.push.sdk.BasePushMessageReceiver",
        "com.vivo.push.sdk.PushServiceReceiver",
        "com.meizu.cloud.pushsdk.MzPushMessageReceiver",
        "com.meizu.cloud.pushsdk.MzPushSystemReceiver",
    )
    val OEM_PUSH_HUAWEI_MESSAGE_SERVICE_CLASSES = listOf(
        "com.baidu.techain.push.HWPushMsgService",
    )
    val OEM_PUSH_HONOR_MESSAGE_SERVICE_CLASSES = listOf(
        "com.baidu.techain.push.HonorPushMsgService",
    )

    /** Performance optimize - dynamic plugin auto download/install decisions */
    const val DYNAMIC_PLUGIN_MODEL = "com.baidu.netdisk.dynamic.base.model.DynamicPlugin"
    const val DYNAMIC_PLUGIN_IS_AUTO_DOWNLOAD_METHOD = "isAutoDownload"
    const val DYNAMIC_PLUGIN_IS_AUTO_INSTALL_METHOD = "isAutoInstall"
    val DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DOWNLOADER_CLASSES = listOf(
        "com.baidu.netdisk.dynamic.ocrscan.OCRScanModelDownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCREnhanceModelDownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSODownloader",
        "com.baidu.netdisk.dynamic.image2office.Image2OfficeDownloader",
        "com.baidu.netdisk.dynamic.imagebodyidentify.ImageBodyIdentifyDownloader",
        "com.baidu.netdisk.dynamic.imagesdk.ImageRecogDownloader",
        "com.baidu.netdisk.dynamic.facedetect.FaceDetectDownloader",
    )
    val DYNAMIC_PLUGIN_AUTO_INSTALL_EXECUTOR_CLASSES = listOf(
        "com.baidu.netdisk.dynamic.ocrscan.OCRScanModelV2Executor",
        "com.baidu.netdisk.dynamic.ocrscan.OCREnhanceModelExecutor",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSOExecutor",
        "com.baidu.netdisk.dynamic.image2office.Image2OfficeExecutor",
        "com.baidu.netdisk.dynamic.imagebodyidentify.ImageBodyIdentifyExecutor",
        "com.baidu.netdisk.dynamic.imagesdk.ImageRecogExecutor",
        "com.baidu.netdisk.dynamic.facedetect.FaceDetectExecutor",
    )
}
