package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduSearchPageHookPoints {
    const val SEARCH_HINT_VM = "com.mars.feature.search.hint.SearchHintVM"
    const val SEARCH_AI_RECOMMEND_KT = "com.mars.feature.search.hint.SearchAIRecommendKt"
    const val AI_SEARCH_25_VM = "com.mars.feature.search.aisearch.vm.AiSearch25VM"
    const val AI_SEARCH_CARD_KT = "com.mars.feature.search.aisearch.AiSearchCardKt"
    const val AI_SEARCH_VM = "com.mars.feature.search.aisearch.AiSearchVM"
    const val MAIN_SEARCH_VM = "com.mars.feature.search.main.MainSearchVM"
    const val SEARCH_RESULT_VM = "com.mars.feature.search.result.SearchResultVM"
    const val MAIN_PRE_SEARCH_TAB_VM = "com.mars.feature.search.main.MainPreSearchTabVM"
    const val PRE_SEARCH_TAB = "com.mars.feature.search.main.PreSearchTab"
    const val VOICE_SEARCH_SCREEN_KT = "com.mars.feature.search.main.VoiceSearchScreenKt"
    const val SEARCH_OPERATION_SERVICE_PLATFORM =
        "com.mars.data.base.commonintf.dependant.SearchOperationServicePlatform"
    const val COMPOSE_SERVICE_PLATFORM_IMPL =
        "com.mars.data.base.commonintf.dependant.AndroidComposeServicePlatformImpl"
    const val KMP_COMPOSE_SERVICE_PLATFORM_IMPL =
        "com.baidu.netdisk.kmp.bridge.impl.AndroidComposeServicePlatformImpl"
    const val COMPOSER = "androidx.compose.runtime.Composer"
    const val FUNCTION0 = "kotlin.jvm.functions.Function0"

    const val QUERY_AI_RECOMMEND_METHOD = "queryAIRecommend"
    const val SHOW_SEARCH_PLACEHOLDER_METHOD = "showText"
    const val ANDROID_AI_SEARCH_CARD_WEB_VIEW_METHOD = "AndroidAiSearchCardWebView"
    const val ANDROID_CREATE_AI_SEARCH_25_PAGE_METHOD = "AndroidCreateAiSearch25Page"
    const val UPDATE_TAB_VISIBILITY_METHOD = "updateTabVisibility"
    const val SHOW_GUIDE_BUBBLE_METHOD = "showGuideBubble"
    const val HIDE_GUIDE_BUBBLE_METHOD = "hideGuideBubble"
    const val AI_POWER_SEARCH_TAB_NAME = "AI_POWER_SEARCH"

    val voiceSearchScreenMetadataTokens = listOf(
        "VoiceSearch",
        "VoiceToText",
        "Lcom/mars/feature/search/main/MainSearchVM;",
        "Lcom/mars/data/base/commonintf/dependant/SearchOperationServicePlatform;",
        "isVisiable",
    )

    val composeServicePlatformClasses = listOf(
        COMPOSE_SERVICE_PLATFORM_IMPL,
        KMP_COMPOSE_SERVICE_PLATFORM_IMPL,
    )

    val showSearchPlaceholderMethods = listOf(
        SHOW_SEARCH_PLACEHOLDER_METHOD,
        "g",
        "a",
    )

    val searchDefaultContentHelperClasses = listOf(
        "com.baidu.netdisk.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.guest25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.home25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.newfeedhome.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.util.SearchDefaultContentHelper",
    )
}
