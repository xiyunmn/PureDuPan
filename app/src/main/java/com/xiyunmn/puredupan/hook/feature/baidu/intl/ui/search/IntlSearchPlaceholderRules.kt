package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchHookPoints

internal object IntlSearchPlaceholderRules {
    fun sanitize(route: Any?, params: Map<*, *>): Map<*, *> {
        if (route != BaiduIntlSearchHookPoints.SEARCH_ROUTE) return params
        // SearchPageHelper passes staticKeyword into the text controller for autoSearch.
        // In that branch it is a real query, so preserve it. Ordinary entry uses it as a hint.
        val preserveQuery = params[BaiduIntlSearchHookPoints.AUTO_SEARCH_PARAM] == true
        return HashMap<Any?, Any?>(params).apply {
            if (!preserveQuery) put(BaiduIntlSearchHookPoints.STATIC_KEYWORD_PARAM, "")
            // HomeSearchPage's speech entry uses recommendWord instead of staticKeyword.
            // Empty strings also prevent Dart's null fallback from restoring a default hint.
            put(BaiduIntlSearchHookPoints.RECOMMEND_WORD_PARAM, "")
        }
    }
}
