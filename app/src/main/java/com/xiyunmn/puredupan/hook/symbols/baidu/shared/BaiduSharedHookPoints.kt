package com.xiyunmn.puredupan.hook.symbols.baidu.shared

/**
 * Stable hook points shared by Baidu Netdisk host flavors.
 */
internal object BaiduSharedHookPoints {
    const val MAIN_ACTIVITY = "com.baidu.netdisk.ui.MainActivity"
    const val HOME_ACTIVITY = "com.baidu.netdisk.homepage.HomeActivity"
    const val NEW_ABOUT_ME_ACTIVITY = "com.baidu.netdisk.ui.aboutme.NewAboutMeActivity"
    const val ABOUT_ME_ACTIVITY = "com.baidu.netdisk.ui.aboutme.AboutMeActivity"

    const val HOME_SEARCHBOX_FRAGMENT = "com.baidu.netdisk.home25ai.fragment.HomeSearchboxFragment"
    const val HOME25_FRAGMENT = "com.baidu.netdisk.home25ai.Home25Fragment"
    const val HOME25_KINGKONG_FRAGMENT = "com.baidu.netdisk.home25ai.fragment.Home25KingKongFragment"
    const val NEW_FH_HOME_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewFHHomeFragment"
    const val INTL_FH_TITLE_BAR_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHTitleBarFragment"
    const val INTL_FH_VAJRA_BAR_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHVajraBarFragment"
    const val HOME25_KINGKONG_CONTENT_LAYOUT_ID = "home25ai_kingkong_content_layout"
    const val INTL_VAJRA_AREA_ID = "cl_vajra_Area"
    const val HOME25AI_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_home25ai.Home25aiContext\$Companion"
    const val HOME25AI_LOAD_HOME_BANNER_METHOD = "loadHomeBanner"
    const val FEED_RECOMMEND_TAB_FRAGMENT =
        "com.baidu.netdisk.home25ai.feedhome.ui.view.fragment.FeedRecommendTabFragment"
    val FEED_FRAGMENT_CLASSES = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.view.fragment.FHFeedFragment",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHFeedFragment",
        "com.baidu.netdisk.feedhome.ui.view.fragment.FHFeedFragment",
        FEED_RECOMMEND_TAB_FRAGMENT,
    )
    const val HOME_STORY_CARD_VIEW = "com.baidu.netdisk.newstory.ui.view.home.HomeStoryCardView"
    val HOME_SAVE_CARD_VIEWS = listOf(
        "com.baidu.netdisk.home25ai.feedhome.ui.view.fragment.NewHomeSaveCardView",
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.NewHomeSaveCardView",
    )
    const val HOME_RECENT_CARD_DATA_USE_CASE =
        "com.baidu.netdisk.newfeedhome.feedhome.domain.GetRecentCardDataUseCase"

    const val MAIN_ACTIVITY_PRESENTER = "com.baidu.netdisk.ui.presenter.MainActivityPresenter"

    const val SPLASH_LIFECYCLE_MANAGER =
        "com.baidu.netdisk.advertise.splash.SplashLifecycleManager"
    const val SPLASH_LIFECYCLE_BACKGROUND_RESUME_AD_START_METHOD = "n"

    const val NEW_HOME_FAB_FRAGMENT =
        "com.baidu.netdisk.homepage.ui.fab.NewHomePageFabFragment"
    const val POPUP_RESPONSE = "com.baidu.netdisk.operation.io.PopupResponse"

    const val SKIN_CONFIG_CLASS = "com.netdisk.themeskin.SkinConfig"
}
