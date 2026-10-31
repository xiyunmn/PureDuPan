package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchHookPoints

internal object IntlSearchPlaceholderRules {
    fun sanitize(route: Any?, params: Map<*, *>): Map<*, *> {
        if (route != BaiduIntlSearchHookPoints.SEARCH_ROUTE) return params
        // autoSearch 会将 staticKeyword 用作实际查询，需保留原值。
        // 普通搜索入口将该值用作提示词。
        val preserveQuery = params[BaiduIntlSearchHookPoints.AUTO_SEARCH_PARAM] == true
        return HashMap<Any?, Any?>(params).apply {
            if (!preserveQuery) put(BaiduIntlSearchHookPoints.STATIC_KEYWORD_PARAM, "")
            // 语音搜索入口使用 recommendWord 作为提示词。
            // 使用空字符串，避免 Dart 遇到 null 时恢复默认提示。
            put(BaiduIntlSearchHookPoints.RECOMMEND_WORD_PARAM, "")
        }
    }
}
