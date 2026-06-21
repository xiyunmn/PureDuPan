package com.xiyunmn.puredupan.hook.symbols.baidu.cn

/**
 * Stable hook points verified against the mainland Baidu Netdisk host.
 */
internal object BaiduCnHookPoints {
    const val ADVERTISE_HOT_START_MANAGER = "com.baidu.netdisk.advertise.AdvertiseHotStartManager"

    const val BUSINESS_OP_DIALOG = "com.baidu.netdisk.ui.operation.BusinessOPDialog"
    const val BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD = "showDialog"
    const val BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD = "onCreateView"

    const val VERSION_UPDATE_HELPER = "com.baidu.netdisk.ui.versionupdate.VersionUpdateHelper"
    const val VERSION_UPDATE_HELPER_SHOW_LC_VERSION_DIALOG_METHOD = "showLCVersionDialog"

    const val NEW_QUICK_SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.NewQuickSettingsActivity"
    const val NEW_QUICK_SETTINGS_ACTIVITY_ON_CREATE_METHOD = "onCreate"

    const val RECEIVE_COUPON_DIALOG_V3 =
        "com.baidu.netdisk.business.guide.dialog.lifeproduct.ReceiveCouponDialogV3"
    const val DIALOG_SHOW_METHOD = "show"

    const val SVIP_ICON_GUIDE = "com.baidu.netdisk.ui.svipicon.SvipIconGuide"
    const val SVIP_ICON_GUIDE_SHOW_GUIDE_METHOD = "showGuide"

    const val SHARE_TAB_PUSH_GUIDE_NORMAL_DIALOG =
        "com.baidu.netdisk.ui.cloudp2p.pushguide.ShareTabPushGuideNormalDialog"
    const val SHARE_TAB_PUSH_GUIDE_ON_START_METHOD = "onStart"

    const val APP_STORE_REVIEW_DIALOG = "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewDialog"
    const val APP_STORE_REVIEW_DIALOG_SHOW_METHOD = "show"
    const val APP_STORE_REVIEW_SHOW_STRATEGY =
        "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewShowStrategy"
    const val APP_STORE_REVIEW_SHOW_CENTER_DIALOG_METHOD = "showCenterDialog"

    const val ABOUT_ME_TOP_FRAGMENT_HETEROMO =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragmentHeteromo"
    const val ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD = "onViewCreated"

    const val BASE_ACTIVITY = "com.baidu.netdisk.BaseActivity"
    const val SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.SettingsActivity"
    const val CHANGE_SKIN_KT = "com.baidu.netdisk.themskin.ChangeSkinKt"
    const val SKIN_LOADER_LISTENER = "com.netdisk.themeskin.listener.SkinLoaderListener"
    const val SETTINGS_ITEM_VIEW = "com.baidu.netdisk.ui.widget.SettingsItemView"

    const val ABOUT_ME_GAME_CENTER_FRAGMENT =
        "com.baidu.netdisk.operation.ui.fragment.game.AboutMeGameCenterFragment"
    const val GAME_CENTER_VIEW_MODEL = "com.baidu.netdisk.ui.aboutme.viewmodel.GameCenterViewModel"
    const val GAME_CENTER_CAN_SHOW_METHOD = "gameCenterCanShow"
    const val GAME_CENTER_FETCH_CONFIG_METHOD = "fetchGameCenterConfig"
    const val ANDROIDX_LIFECYCLE_OWNER = "androidx.lifecycle.LifecycleOwner"

    const val AI_CLOUD_TAB_AMIS_KT = "com.baidu.netdisk.main.model.data.tool.AiCloudTabAmisKt"

    const val FILE_TAB_BOTTOM_BAR_FACTORY =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.FileTabBottomBarFactory"
    const val FILE_TAB_BOTTOM_BAR_FACTORY_CREATE_METHOD = "create"
    const val ALBUM_BACKUP_BAR_VIEW =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.AlbumBackupBarView"
    const val ALBUM_BACKUP_BAR_VIEW_INIT_UI_METHOD = "initUI"

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

    const val FLOAT_VIEW_STARTUP_TASK = "com.baidu.netdisk.startup.task.FloatViewStartupTask"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD = "initAudioCircleView"

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
