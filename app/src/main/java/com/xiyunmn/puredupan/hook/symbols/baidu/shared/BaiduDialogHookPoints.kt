package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduDialogHookPoints {
    const val NEW_QUICK_SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.NewQuickSettingsActivity"
    const val REPEATED_NEW_QUICK_SETTINGS_ACTIVITY =
        "com.baidu.netdisk.ui.RepeatedNewQuickSettingsActivity"
    const val NEW_QUICK_SETTINGS_ACTIVITY_ON_CREATE_METHOD = "onCreate"
    const val NEW_QUICK_SETTINGS_CAN_SHOW_METHOD = "canNewQuickSettingsActivityShow"
    const val REPEATED_NEW_QUICK_SETTINGS_CAN_SHOW_METHOD =
        "checkIsShowRepeateNewQuickSettingsActivity"

    const val SVIP_ICON_GUIDE = "com.baidu.netdisk.ui.svipicon.SvipIconGuide"
    const val SVIP_ICON_GUIDE_SHOW_GUIDE_METHOD = "showGuide"

    const val APP_STORE_REVIEW_DIALOG = "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewDialog"
    const val APP_STORE_REVIEW_DIALOG_SHOW_METHOD = "show"
    const val APP_STORE_SCORE_BOTTOM_DIALOG =
        "com.baidu.netdisk.ui.operation.storereview.AppStoreScoreBottomDialog"
    const val APP_STORE_REVIEW_SHOW_STRATEGY =
        "com.baidu.netdisk.ui.operation.storereview.AppStoreReviewShowStrategy"
    const val APP_STORE_REVIEW_SHOW_CENTER_DIALOG_METHOD = "showCenterDialog"

    const val OPERATION_APIS_KT = "com.baidu.netdisk.rubik.OperationApisKt"
    const val NETDISK_CONTEXT_OPERATION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_test_netdisk.NetdiskContext\$Operation"
    const val PERFORM_APP_STORE_REVIEW_STRATEGY_METHOD = "performAppStoreReviewStrategy"
}
