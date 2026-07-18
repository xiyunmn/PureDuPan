package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduHomeCardHookPoints {
    const val ACCOUNT_UTILS = "com.baidu.netdisk.account.AccountUtils"
    const val EVIDENCE = "com.baidu.netdisk.account.Evidence"
    const val BASE_APPLICATION = "com.baidu.netdisk.kernel.BaseApplication"
    const val TRANSFER_SAVED_SERVICE_KT = "com.baidu.netdisk.logic.ITransferSavedServiceKt"
    const val ANDROIDX_OBSERVER = "androidx.lifecycle.Observer"
    const val GSON = "com.google.gson.Gson"
    const val HOME_FRAGMENT = "com.baidu.netdisk.feedhome.ui.view.fragment.FHHomeFragment"
    const val HOME_TOP_SIZE_METHOD = "onTopSizeChanged"
    const val HOME_TOP_SIZE_CALLBACK_PREFIX = "initView\$lambda\$"
    const val HIDE_FEED_LIST_METHOD = "hideFeedList"

    val DOMESTIC_SAVE_CARD_VIEWS = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.view.fragment.NewHomeSaveCardView",
        "com.baidu.netdisk.guest25ai.feedhome.ui.view.fragment.NewHomeSaveCardView",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeSaveCardView",
    )
    val INTL_SAVE_CARD_VIEWS = listOf(
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeSaveCardView",
    )
    val DOMESTIC_RECENT_CARD_VIEW_MODELS = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
        "com.baidu.netdisk.guest25ai.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
    )
    val INTL_RECENT_CARD_VIEW_MODELS = listOf(
        "com.baidu.netdisk.newfeedhome.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
    )
}
