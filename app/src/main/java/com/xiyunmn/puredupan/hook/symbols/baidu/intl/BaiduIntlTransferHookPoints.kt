package com.xiyunmn.puredupan.hook.symbols.baidu.intl

/**
 * Stable transfer hook points verified against Baidu Netdisk international host.
 */
internal object BaiduIntlTransferHookPoints {
    const val TRANSFER_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_transfer.TransferContext\$Companion"
    const val TRANSFER_APIS =
        "com.baidu.netdisk.ui.transfer.component.TransferApis"
    const val FLOW_ALERT_DIALOG_MANAGER =
        "com.baidu.netdisk.ui.manager.FlowAlertDialogManager"
    const val FLOW_ALERT_TRANSFER_FILE_DIALOG =
        "com.baidu.netdisk.ui.manager.FlowAlertTransferFileDialog"
    const val DIALOG_CTR_LISTENER =
        "com.baidu.netdisk.ui.manager.DialogCtrListener"
    const val KOTLIN_FUNCTION0 =
        "kotlin.jvm.functions.Function0"
    const val MAIN_CREATE_OBJECT_API =
        "com.baidu.netdisk.main.provider.MCreateObjectApi"
    const val FILE_DOWNLOAD_PUSH_COMMON_API_COMP_MANAGER =
        "com.baidu.netdisk.file.download.component.caller.PushCommonApiCompManager"
    const val FILE_DOWNLOAD_PUSH_CONSTANT_API_COMP_MANAGER =
        "com.baidu.netdisk.file.download.component.caller.PushConstantApiCompManager"
    const val FILE_DOWNLOAD_PUSH_COMMON_API_GEN =
        "com.baidu.netdisk.file.download.component.caller.PushCommonApiGen"
    const val FILE_DOWNLOAD_PUSH_CONSTANT_API_GEN =
        "com.baidu.netdisk.file.download.component.caller.PushConstantApiGen"
    const val FILE_DOWNLOAD_API_PREFIX =
        "com.baidu.netdisk.file.download.component.apis."
    const val FD_DOWNLOAD_MANAGER_API_SIMPLE_NAME =
        "FDDownloadManagerApi"
    const val DOWNLOAD_TOAST_POSTER_RECEIVER =
        "com.baidu.netdisk.transfer.DownloadToastPoster\$receiver\$1"
    const val NETDISK_SERVICE =
        "com.baidu.netdisk.service.NetdiskService"
    const val TASK_STATE_CALLBACK =
        "com.baidu.netdisk.transfer.transmitter.statuscallback.ITaskStateCallback"
    const val WIFI_ONLY_TRANSFER_ACTION =
        "com.baidu.netdisk.plugins.ACTION_WIFI_DOWNLOAD_ONLY"
    const val WIFI_ONLY_TRANSFER_EXTRA =
        "com.baidu.netdisk.plugins.EXTRA_DOWNLOAD_WIFI_ONLY_STATE"
    const val TRANSFER_ACTION_RESTART_SCHEDULERS =
        "com.baidu.netdisk.ACTION_RESTART_SCHEDULERS"
    const val TRANSFER_ACTION_RESET_SCHEDULERS =
        "com.baidu.netdisk.ACTION_RESET_SCHEDULERS"
    const val USE_TRAFFIC_BUTTON_ID_NAME = "bt_use_traffic"

    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD =
        "showNonWiFiAlertDownloadDialog"
    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_BOTTOM_DIALOG_METHOD =
        "showNonWiFiAlertDownloadBottomDialog"
    const val SHOW_SINGKIL_DIALOG_METHOD =
        "showSingkilDialog"
    const val SHOW_WIFI_ONLY_DIALOG_BY_ADD_TASK_ON_2G3G_METHOD =
        "showWiFiOnlyDialogByAddTaskOn2G3G"
    const val SHOW_WIFI_ONLY_DIALOG_BY_ADD_TASK_ON_2G3G_WITH_3_PARAM_METHOD =
        "showWiFiOnlyDialogByAddTaskOn2G3GWith3Param"
    const val SHOW_WIFI_ONLY_DIALOG_BY_ADD_TASK_ON_2G3G_WITH_ACTIVITY_METHOD =
        "showWiFiOnlyDialogByAddTaskOn2G3GWithActivity"
    const val SHOW_PERSIST_SETTING_BOTTOM_DIALOG_METHOD =
        "showPersistSettingBottomDialog"
    const val SHOW_SCENE_PUSH_GUIDE_METHOD =
        "showScenePushGuide"
    const val GET_DOWNLOAD_PUSH_GUIDE_SCENE_METHOD =
        "getDownloadPushGuideScene"
    const val DIALOG_CTR_LISTENER_ON_OK_METHOD = "onOkBtnClick"
    const val TASK_STATE_CALLBACK_ON_START_METHOD = "onStart"
    const val FLOW_ALERT_SET_TYPE_METHOD = "setType"
    const val FLOW_ALERT_SET_ON_CLICK_USE_TRAFFIC_METHOD = "setOnClickUseTraffic"
    const val FUNCTION0_INVOKE_METHOD = "invoke"
    const val RESTART_SCHEDULERS_METHOD = "restartSchedulers"
    const val SHOW_METHOD = "show"
}
