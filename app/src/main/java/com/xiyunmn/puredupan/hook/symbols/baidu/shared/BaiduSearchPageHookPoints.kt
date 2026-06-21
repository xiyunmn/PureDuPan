package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduSearchPageHookPoints {
    const val SEARCH_HINT_VM = "com.mars.feature.search.hint.SearchHintVM"
    const val AI_SEARCH_25_VM = "com.mars.feature.search.aisearch.vm.AiSearch25VM"
    const val AI_SEARCH_CARD_KT = "com.mars.feature.search.aisearch.AiSearchCardKt"

    val queryAiRecommendMethodNames = listOf(
        "queryAIRecommend",
        "L0",
    )

    val showSearchPlaceholderMethodNames = listOf(
        "showText",
        "g",
    )

    val searchDefaultContentHelperClasses = listOf(
        "com.baidu.netdisk.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.guest25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.home25ai.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.newfeedhome.feedhome.logic.util.SearchDefaultContentHelper",
        "com.baidu.netdisk.util.SearchDefaultContentHelper",
    )
}
