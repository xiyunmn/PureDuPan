package com.xiyunmn.puredupan.hook.symbols.baidu.samsung

/**
 * Stable hook points verified against Baidu Netdisk Samsung host.
 */
internal object BaiduSamsungHookPoints {
    const val NEW_FEED_HOME_TITLE_BAR_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHTitleBarFragment"

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
    const val ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD = "onViewCreated"

    const val SPLASH_MANAGER =
        "com.baidu.netdisk.advertise.splash.SplashManager"
    const val ADVERTISE_HOT_START_MANAGER =
        "com.baidu.netdisk.advertise.AdvertiseHotStartManager"

    const val BUSINESS_OP_DIALOG =
        "com.baidu.netdisk.ui.operation.BusinessOPDialog"
    const val BUSINESS_OP_DIALOG_SHOW_DIALOG_METHOD = "showDialog"
    const val BUSINESS_OP_DIALOG_ON_CREATE_VIEW_METHOD = "onCreateView"

    const val ABOUT_ME_GAME_CENTER_FRAGMENT =
        "com.baidu.netdisk.operation.ui.fragment.game.AboutMeGameCenterFragment"
    const val GAME_CENTER_VIEW_MODEL =
        "com.baidu.netdisk.ui.aboutme.viewmodel.GameCenterViewModel"
    const val GAME_CENTER_AMIS_OPEN_METHOD = "isAmisOpen"
    const val GAME_CENTER_FETCH_CONFIG_METHOD = "fetchGameCenterConfig"
    const val ANDROIDX_FRAGMENT_MANAGER = "androidx.fragment.app.FragmentManager"
    const val ANDROIDX_LIFECYCLE_OWNER = "androidx.lifecycle.LifecycleOwner"
}
