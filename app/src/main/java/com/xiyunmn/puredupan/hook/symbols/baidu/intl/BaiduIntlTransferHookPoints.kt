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
    const val NET_CONFIG_UTIL =
        "com.baidu.netdisk.base.utils.NetConfigUtil"
    const val MAIN_CREATE_OBJECT_API =
        "com.baidu.netdisk.main.provider.MCreateObjectApi"
    const val WIFI_ONLY_TRANSFER_ACTION =
        "com.baidu.netdisk.plugins.ACTION_WIFI_DOWNLOAD_ONLY"
    const val WIFI_ONLY_TRANSFER_EXTRA =
        "com.baidu.netdisk.plugins.EXTRA_DOWNLOAD_WIFI_ONLY_STATE"

    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD =
        "showNonWiFiAlertDownloadDialog"
    const val SHOW_NON_WIFI_ALERT_DOWNLOAD_BOTTOM_DIALOG_METHOD =
        "showNonWiFiAlertDownloadBottomDialog"
    const val DIALOG_CTR_LISTENER_ON_OK_METHOD = "onOkBtnClick"
    const val FLOW_ALERT_SET_TYPE_METHOD = "setType"
    const val FLOW_ALERT_SET_ON_CLICK_USE_TRAFFIC_METHOD = "setOnClickUseTraffic"
    const val FUNCTION0_INVOKE_METHOD = "invoke"
    const val SET_WIFI_ONLY_CHECKED_CONFIG_METHOD = "setWiFiOnlyCheckedConfig"
    const val RESTART_SCHEDULERS_METHOD = "restartSchedulers"
    const val SHOW_METHOD = "show"
}
