package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduSearchPageHookPoints {
    const val SEARCH_HINT_VM = "com.mars.feature.search.hint.SearchHintVM"
    const val SEARCH_AI_RECOMMEND_KT = "com.mars.feature.search.hint.SearchAIRecommendKt"
    const val AI_SEARCH_25_VM = "com.mars.feature.search.aisearch.vm.AiSearch25VM"
    const val AI_SEARCH_CARD_KT = "com.mars.feature.search.aisearch.AiSearchCardKt"
    const val MAIN_PRE_SEARCH_TAB_VM = "com.mars.feature.search.main.MainPreSearchTabVM"
    const val PAN_SEARCH_SCREEN_KT = "com.mars.feature.search.main.PanSearchScreenKt"
    const val PRE_SEARCH_TAB = "com.mars.feature.search.main.PreSearchTab"
    const val COMPOSE_SERVICE_PLATFORM_IMPL =
        "com.mars.data.base.commonintf.dependant.AndroidComposeServicePlatformImpl"
    const val KMP_COMPOSE_SERVICE_PLATFORM_IMPL =
        "com.baidu.netdisk.kmp.bridge.impl.AndroidComposeServicePlatformImpl"
    const val COMPOSER = "androidx.compose.runtime.Composer"
    const val TEXT_FIELD_VALUE = "androidx.compose.ui.text.input.TextFieldValue"

    const val QUERY_AI_RECOMMEND_METHOD = "queryAIRecommend"
    const val SHOW_SEARCH_PLACEHOLDER_METHOD = "showText"
    const val ANDROID_AI_SEARCH_CARD_WEB_VIEW_METHOD = "AndroidAiSearchCardWebView"
    const val ANDROID_CREATE_AI_SEARCH_25_PAGE_METHOD = "AndroidCreateAiSearch25Page"
    const val UPDATE_TAB_VISIBILITY_METHOD = "updateTabVisibility"
    const val SHOW_GUIDE_BUBBLE_METHOD = "showGuideBubble"
    const val HIDE_GUIDE_BUBBLE_METHOD = "hideGuideBubble"
    const val AI_POWER_SEARCH_TAB_NAME = "AI_POWER_SEARCH"

    val composeServicePlatformClasses = listOf(
        COMPOSE_SERVICE_PLATFORM_IMPL,
        KMP_COMPOSE_SERVICE_PLATFORM_IMPL,
    )

    val searchDefaultContentHelperClasses = listOf(
        "com.baidu.netdisk.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.guest25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.home25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.newfeedhome.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.util.SearchDefaultContentHelper",
    )
}
