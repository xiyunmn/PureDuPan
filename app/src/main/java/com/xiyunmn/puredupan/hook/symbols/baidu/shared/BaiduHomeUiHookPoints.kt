package com.xiyunmn.puredupan.hook.symbols.baidu.shared

import com.xiyunmn.puredupan.hook.host.HomeCustomizeHookPoints

/** Home25 contracts verified in mainland, Samsung and international 13.11.13. */
internal object BaiduHomeUiHookPoints {
    val home25 = HomeCustomizeHookPoints(
        searchboxFragmentClassName = BaiduSharedHookPoints.HOME_SEARCHBOX_FRAGMENT,
        searchTextFragmentClassNames = listOf(BaiduSharedHookPoints.HOME_SEARCHBOX_FRAGMENT),
        homeRootFragmentClassNames = listOf(BaiduSharedHookPoints.HOME25_FRAGMENT),
        feedFragmentClassNames = BaiduSharedHookPoints.FEED_FRAGMENT_CLASSES,
        toolbarFragmentClassNames = listOf(BaiduSharedHookPoints.HOME25_KINGKONG_FRAGMENT),
        toolbarViewIdNames = listOf(BaiduSharedHookPoints.HOME25_KINGKONG_CONTENT_LAYOUT_ID),
        storyCardRenderContextClassName = BaiduSharedHookPoints.STORY_CONTEXT,
        storyCardRenderMethodName =
            BaiduSharedHookPoints.STORY_CONTEXT_GET_NEW_HOME_STORY_CARD_VIEW_METHOD,
        feedRecentCardRenderMethodName = BaiduSharedHookPoints.HOME_FEED_INIT_RECENT_CARD_VIEW_METHOD,
        feedSaveCardRenderMethodName = BaiduSharedHookPoints.HOME_FEED_INIT_SAVE_CARD_VIEW_METHOD,
        feedStoryCardRenderMethodName = BaiduSharedHookPoints.HOME_FEED_INIT_STORY_CARD_VIEW_METHOD,
        saveCardViewModelClassNames = BaiduSharedHookPoints.HOME_SAVE_CARD_VIEW_MODELS,
        saveCardViewClassNames = BaiduHomeCardHookPoints.SAVE_CARD_VIEWS,
        saveCardNoArgBlockedMethodNames =
            BaiduSharedHookPoints.HOME_SAVE_CARD_NO_ARG_BLOCKED_METHODS,
        saveCardRedPotMethodNames = BaiduSharedHookPoints.HOME_SAVE_CARD_RED_POT_METHODS,
        recentCardDataUseCaseClassNames = BaiduSharedHookPoints.HOME_RECENT_CARD_DATA_USE_CASES,
        recentCardViewModelClassNames =
            BaiduHomeCardHookPoints.RECENT_CARD_VIEW_MODELS,
        supportsRecentScrollRangeAdjustment = true,
        home25aiContextCompanionClassName = BaiduSharedHookPoints.HOME25AI_CONTEXT_COMPANION,
        loadHomeBannerMethodName = BaiduSharedHookPoints.HOME25AI_LOAD_HOME_BANNER_METHOD,
    )
}
