package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchHookPoints

/** Filters the Pigeon read response without mutating the plugin map or persisted preferences. */
internal object IntlSearchStorageRules {
    fun sanitize(value: Map<*, *>, hideHistory: Boolean, hideRecommend: Boolean): Map<*, *> {
        fun shouldHide(key: Any?): Boolean {
            if (key !is String) return false
            val recommendation = key.startsWith(BaiduIntlSearchHookPoints.SEARCH_HISTORY_RECOMMEND_ITEM_STORAGE_KEY_PREFIX)
            val history = !recommendation && (
                key == BaiduIntlSearchHookPoints.SEARCH_HISTORY_STORAGE_KEY ||
                    key.startsWith(BaiduIntlSearchHookPoints.SEARCH_HISTORY_STORAGE_KEY_PREFIX)
                )
            return hideHistory && history || hideRecommend && (
                recommendation || key == BaiduIntlSearchHookPoints.LAST_PERSON_RECOMMEND_STORAGE_KEY
                )
        }
        if (value.keys.none(::shouldHide)) return value
        return value.filterKeys { !shouldHide(it) }
    }
}
