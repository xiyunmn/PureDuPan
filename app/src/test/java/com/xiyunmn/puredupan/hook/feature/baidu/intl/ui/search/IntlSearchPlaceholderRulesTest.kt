package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class IntlSearchPlaceholderRulesTest {
    @Test
    fun ordinarySearchClearsHintsWithoutChangingOtherArgumentsOrSourceMap() {
        val params = mapOf(
            "staticKeyword" to "搜索网盘文件",
            "recommendWord" to "推荐提示词",
            "query" to "报告",
            "directory" to "/工作",
            "speech" to true,
        )
        val result = IntlSearchPlaceholderRules.sanitize("/netdisk/search", params)
        assertEquals("", result["staticKeyword"])
        assertEquals("", result["recommendWord"])
        assertEquals(params - setOf("staticKeyword", "recommendWord"), result - setOf("staticKeyword", "recommendWord"))
        assertEquals("搜索网盘文件", params["staticKeyword"])
        assertEquals("推荐提示词", params["recommendWord"])
    }

    @Test
    fun missingHintsBecomeEmptyStringsToPreventDartDefaultFallback() {
        val result = IntlSearchPlaceholderRules.sanitize("/netdisk/search", emptyMap<String, Any?>())
        assertEquals(mapOf("staticKeyword" to "", "recommendWord" to ""), result)
    }

    @Test
    fun autoSearchPreservesInitialQueryAndSearchBehavior() {
        val params = mapOf("staticKeyword" to "年度报告", "autoSearch" to true, "recommendWord" to "推荐")
        val result = IntlSearchPlaceholderRules.sanitize("/netdisk/search", params)
        assertEquals("年度报告", result["staticKeyword"])
        assertEquals(true, result["autoSearch"])
        assertEquals("", result["recommendWord"])
    }

    @Test
    fun otherRoutesAndUnknownRoutesAreUntouched() {
        val params = mapOf("staticKeyword" to "其他页面", "recommendWord" to "其他推荐")
        for (route in listOf(null, "/netdisk/search/other", "/netdisk/other")) {
            assertSame(params, IntlSearchPlaceholderRules.sanitize(route, params))
        }
    }
}
