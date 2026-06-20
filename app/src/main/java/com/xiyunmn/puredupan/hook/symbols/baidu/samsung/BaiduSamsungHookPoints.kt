package com.xiyunmn.puredupan.hook.symbols.baidu.samsung

/**
 * Stable hook points verified against Baidu Netdisk Samsung host.
 */
internal object BaiduSamsungHookPoints {
    const val DEFAULT_MAIN_ACTIVITY = "com.baidu.netdisk.ui.DefaultMainActivity"
    const val NAVIGATE_ACTIVITY = "com.baidu.netdisk.ui.Navigate"

    const val NEW_FEED_HOME_TITLE_BAR_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHTitleBarFragment"
    const val HOME_UPLOAD_ENTRY = "upload_file_entry"
    const val HOME_UPLOAD_ENTRY_GUIDE_END = "upload_file_entry_guide_end"
    const val ABOUT_ME_SCAN_ICON = "self_qrcode_scan_icon"
    const val ABOUT_ME_QRCODE_ENTRANCE_ICON = "self_qrcode_entrance_icon"
    const val ABOUT_ME_QRCODE_ENTRANCE_ICON_NEW_POS = "self_qrcode_entrance_icon_new_pos"

    val FEED_FRAGMENT_CLASSES = listOf(
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHFeedFragment",
        "com.baidu.netdisk.feedhome.ui.view.fragment.FHFeedFragment",
    )
    const val HOME_STORY_CARD_VIEW =
        "com.baidu.netdisk.newstory.ui.view.home.HomeStoryCardView"
    val HOME_SAVE_CARD_VIEWS = listOf(
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeSaveCardView",
    )
    val HOME_RECENT_CARD_VIEWS = listOf(
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeRecentCardView",
    )

    const val ABOUT_ME_TOP_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragment"
    const val ABOUT_ME_USER_CENTER_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.UserCenterFragment"
    const val ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD = "onViewCreated"

    const val SPLASH_MANAGER =
        "com.baidu.netdisk.advertise.splash.SplashManager"
    const val ADVERTISE_HOT_START_MANAGER =
        "com.baidu.netdisk.advertise.AdvertiseHotStartManager"
    const val ADVERTISE_SDK =
        "com.baidu.netdisk.advertise.AdvertiseSDK"
    const val ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD = "downloadVideoFrontAd"

    const val BUSINESS_OP_DIALOG =
        "com.baidu.netdisk.ui.operation.BusinessOPDialog"
    const val BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD = "showDialog"
    const val BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD = "onCreateView"

    const val TRANSFER_APIS =
        "com.baidu.netdisk.ui.transfer.component.TransferApis"
    const val FLOW_ALERT_DIALOG_MANAGER =
        "com.baidu.netdisk.ui.manager.FlowAlertDialogManager"
    const val DIALOG_CTR_LISTENER =
        "com.baidu.netdisk.ui.manager.DialogCtrListener"
    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD =
        "showNonWiFiAlertDownloadDialog"
    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_BOTTOM_DIALOG_METHOD =
        "showNonWiFiAlertDownloadBottomDialog"
    const val DIALOG_CTR_LISTENER_ON_OK_METHOD = "onOkBtnClick"

    const val GUIDE_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_guide.GuideContext\$Companion"
    const val GUIDE_APIS_KT = "com.baidu.netdisk.GuideApisKt"
    const val GUIDE_REQUIRE_B2F_GUIDANCE_DIALOG_DATA_METHOD = "requireB2FGuidanceDialogData"

    const val FLOAT_VIEW_STARTUP_TASK = "com.baidu.netdisk.startup.task.FloatViewStartupTask"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD = "initAudioCircleView"

    const val SWAN_APP_PRELOAD_HELPER =
        "com.baidu.swan.apps.process.messaging.service.SwanAppPreloadHelper"
    const val SWAN_CLIENT_PUPPET =
        "com.baidu.swan.apps.process.messaging.service.SwanClientPuppet"
    const val SWAN_PRELOAD_TRY_PRELOAD_METHOD = "tryPreload"
    const val SWAN_PRELOAD_TRY_PRELOAD_IF_KEEP_ALIVE_METHOD = "tryPreloadIfKeepAlive"
    const val SWAN_PRELOAD_START_SERVICE_FOR_PRELOAD_NEXT_METHOD = "startServiceForPreloadNext"

    const val AD_SDK_SERVICE_ON_CREATE_METHOD = "onCreate"
    const val AD_SDK_SERVICE_ON_BIND_METHOD = "onBind"
    const val AD_SDK_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    val AD_SDK_DOWNLOAD_SERVICE_CLASSES = listOf(
        "com.qq.e.comm.DownloadService",
        "com.ss.android.socialbase.downloader.downloader.CSJDownloadService",
        "com.ss.android.socialbase.downloader.downloader.CSJIndependentProcessDownloadService",
        "com.beizi.ad.DownloadService",
        "com.ubix.ssp.open.comm.DownloadService",
        "com.baidu.swan.game.ad.downloader.core.AdDownloadService",
    )

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

    const val INCENTIVE_BUSINESS_SERVICE =
        "com.baidu.netdisk.platform.business.incentive.service.BusinessService"
    const val INCENTIVE_BUSINESS_SERVICE_ON_CREATE_METHOD = "onCreate"
    const val INCENTIVE_BUSINESS_SERVICE_ON_START_COMMAND_METHOD = "onStartCommand"
    const val INCENTIVE_BUSINESS_SERVICE_ON_BIND_METHOD = "onBind"

    const val CLIENT_COMPUTE_MANAGER = "com.baidu.netdisk.service.ClientComputeManager"
    const val CLIENT_COMPUTE_MANAGER_INIT_METHOD = "init"
    const val THUMBNAIL_OPERATOR_UTIL =
        "com.baidu.netdisk.terminalcalc.compress.service.operator.ThumbnailOperatorUtil"
    const val THUMBNAIL_OPERATOR_UTIL_ADD_JOB_METHOD = "addJob"
    const val TERMINALCALC_COMPRESS_BEAN =
        "com.baidu.netdisk.terminalcalc.compress.service.CompressBean"
    const val CONFIG_COMPRESS_IMAGE = "com.baidu.netdisk.base.storage.config.ConfigCompressImage"

    const val DYNAMIC_PLUGIN_MODEL = "com.baidu.netdisk.dynamic.base.model.DynamicPlugin"
    const val DYNAMIC_PLUGIN_IS_AUTO_DOWNLOAD_METHOD = "isAutoDownload"
    const val DYNAMIC_PLUGIN_IS_AUTO_INSTALL_METHOD = "isAutoInstall"
    val DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DOWNLOADER_CLASSES = listOf(
        "com.baidu.netdisk.dynamic.ocrscan.OCRScanModelDownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCREnhanceModelDownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSODownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCRModelRecogDownloader",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSORecogDownloader",
        "com.baidu.netdisk.dynamic.image2office.Image2OfficeDownloader",
        "com.baidu.netdisk.dynamic.imagebodyidentify.ImageBodyIdentifyDownloader",
    )
    val DYNAMIC_PLUGIN_AUTO_INSTALL_EXECUTOR_CLASSES = listOf(
        "com.baidu.netdisk.dynamic.ocrscan.OCRScanModelV2Executor",
        "com.baidu.netdisk.dynamic.ocrscan.OCREnhanceModelExecutor",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSOExecutor",
        "com.baidu.netdisk.dynamic.ocrscan.OCRModelRecogExecutor",
        "com.baidu.netdisk.dynamic.ocrscan.OCRSORecogExecutor",
        "com.baidu.netdisk.dynamic.image2office.Image2OfficeExecutor",
        "com.baidu.netdisk.dynamic.imagebodyidentify.ImageBodyIdentifyExecutor",
    )

    const val BASE_ACTIVITY = "com.baidu.netdisk.BaseActivity"
    const val SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.SettingsActivity"
    const val CHANGE_SKIN_KT = "com.baidu.netdisk.themskin.ChangeSkinKt"
    const val SKIN_LOADER_LISTENER = "com.netdisk.themeskin.listener.SkinLoaderListener"
    const val SETTINGS_ITEM_VIEW = "com.baidu.netdisk.ui.widget.SettingsItemView"

    const val ABOUT_ME_GAME_CENTER_FRAGMENT =
        "com.baidu.netdisk.operation.ui.fragment.game.AboutMeGameCenterFragment"
    const val GAME_CENTER_VIEW_MODEL =
        "com.baidu.netdisk.ui.aboutme.viewmodel.GameCenterViewModel"
    const val GAME_CENTER_AMIS_OPEN_METHOD = "isAmisOpen"
    const val GAME_CENTER_FETCH_CONFIG_METHOD = "fetchGameCenterConfig"
    const val ANDROIDX_FRAGMENT_MANAGER = "androidx.fragment.app.FragmentManager"
    const val ANDROIDX_LIFECYCLE_OWNER = "androidx.lifecycle.LifecycleOwner"

    const val OEM_PUSH_ON_CREATE_METHOD = "onCreate"
    const val OEM_PUSH_ON_START_COMMAND_METHOD = "onStartCommand"
    const val OEM_PUSH_ON_START_METHOD = "onStart"
    const val OEM_PUSH_ON_BIND_METHOD = "onBind"
    const val OEM_PUSH_ON_HANDLE_INTENT_METHOD = "onHandleIntent"
    const val OEM_PUSH_ON_RECEIVE_METHOD = "onReceive"
    const val OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD = "onMessageReceived"
    const val OEM_PUSH_ON_NEW_TOKEN_METHOD = "onNewToken"
    const val OEM_PUSH_ON_TOKEN_ERROR_METHOD = "onTokenError"
    const val OEM_PUSH_PROCESS_MESSAGE_METHOD = "processMessage"
    const val OEM_PUSH_HEYTAP_DATA_MESSAGE = "com.heytap.msp.push.mode.DataMessage"
    const val OEM_PUSH_HMS_REMOTE_MESSAGE = "com.huawei.hms.push.RemoteMessage"
    const val OEM_PUSH_HONOR_MESSAGE = "com.hihonor.push.sdk.HonorPushDataMsg"

    val OEM_PUSH_ON_START_COMMAND_SERVICE_CLASSES = listOf(
        "com.huawei.hms.support.api.push.service.HmsMsgService",
        "com.baidu.techain.push.HWPushMsgService",
        "com.heytap.msp.push.service.DataMessageCallbackService",
        "com.heytap.msp.push.service.CompatibleDataMessageCallbackService",
        "com.xiaomi.push.service.XMPushService",
        "com.vivo.push.sdk.service.CommandService",
    )
    val OEM_PUSH_ON_CREATE_SERVICE_CLASSES = listOf(
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.push.service.XMJobService",
        "com.vivo.push.sdk.service.CommandService",
    )
    val OEM_PUSH_ON_START_SERVICE_CLASSES = listOf(
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.MessageHandleService",
    )
    val OEM_PUSH_ON_BIND_SERVICE_CLASSES = listOf(
        "com.huawei.hms.support.api.push.service.HmsMsgService",
        "com.baidu.techain.push.HWPushMsgService",
        "com.heytap.msp.push.service.DataMessageCallbackService",
        "com.heytap.msp.push.service.CompatibleDataMessageCallbackService",
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.push.service.XMJobService",
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.MessageHandleService",
        "com.vivo.push.sdk.service.CommandService",
    )
    val OEM_PUSH_ON_HANDLE_INTENT_SERVICE_CLASSES = listOf(
        "com.meizu.cloud.pushsdk.NotificationService",
    )
    val OEM_PUSH_RECEIVER_CLASSES = listOf(
        "com.huawei.hms.support.api.push.PushMsgReceiver",
        "com.huawei.hms.support.api.push.PushReceiver",
        "com.baidu.techain.push.MIUIPushReceiver",
        "com.baidu.techain.push.MZPushReceiver",
        "com.baidu.techain.push.VIVOPushReceiver",
        "com.xiaomi.push.service.receivers.PingReceiver",
        "com.xiaomi.mipush.sdk.PushMessageReceiver",
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

    val OEM_PUSH_HEYTAP_DATA_MESSAGE_SERVICE_CLASSES = listOf(
        "com.heytap.msp.push.service.DataMessageCallbackService",
        "com.heytap.msp.push.service.CompatibleDataMessageCallbackService",
    )

    const val P2P_DOWNLOAD_GUARD_SERVICE =
        "com.baidu.netdisk.p2p.NetdiskDownloadGuardService"
    const val P2P_SERVICE_PROXY =
        "com.baidu.netdisk.p2p.P2PServiceProxy"
    const val P2P_DOWNLOAD_SERVICE =
        "com.baidu.netdisk.p2p.NetdiskDownloadService"
}
