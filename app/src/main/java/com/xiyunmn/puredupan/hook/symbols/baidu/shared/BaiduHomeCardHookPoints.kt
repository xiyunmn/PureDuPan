package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduHomeCardHookPoints {
    const val ACCOUNT_UTILS = "com.baidu.netdisk.account.AccountUtils"
    const val EVIDENCE = "com.baidu.netdisk.account.Evidence"
    const val TRANSFER_SAVED_SERVICE_KT = "com.baidu.netdisk.logic.ITransferSavedServiceKt"
    const val TRANSFER_SAVED_MANAGER = "com.baidu.netdisk.logic.TransferSavedManager"
    const val HANDLABLE_MANAGER = "com.baidu.netdisk.service.HandlableManager"
    const val ANDROIDX_OBSERVER = "androidx.lifecycle.Observer"
    const val GSON = "com.google.gson.Gson"
    const val HOME_FRAGMENT = "com.baidu.netdisk.feedhome.ui.view.fragment.FHHomeFragment"
    const val HOME_TOP_SIZE_METHOD = "onTopSizeChanged"
    const val HOME_TOP_SIZE_CALLBACK_PREFIX = "initView\$lambda\$"
    const val HIDE_FEED_LIST_METHOD = "hideFeedList"
    const val STICKY_NESTED_LAYOUT = "com.baidu.netdisk.ui.widget.sticknestlayout.StickyNestedLayout"

    val SAVE_STATE_COLLECTOR_SUFFIXES = listOf("\$initObserver\$4\$1", "\$initObserver\$4\$_")

    val SAVE_CARD_VIEWS = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.view.fragment.NewHomeSaveCardView",
        "com.baidu.netdisk.guest25ai.feedhome.ui.view.fragment.NewHomeSaveCardView",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeSaveCardView",
    )
    val RECENT_CARD_VIEW_MODELS = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
        "com.baidu.netdisk.guest25ai.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.viewmodels.NewHomeRecentCardViewModel",
    )
}
