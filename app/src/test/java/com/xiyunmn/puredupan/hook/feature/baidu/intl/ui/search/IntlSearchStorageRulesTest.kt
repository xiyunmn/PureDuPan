package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IntlSearchStorageRulesTest {
    private val history = "flutter.search_history_123"
    private val recommendation = "flutter.search_history_recommend_item_123"
    private val source = linkedMapOf<Any?, Any?>(
        "flutter.search_history" to listOf("legacy query"),
        history to listOf("query"),
        recommendation to "recommendation",
        "flutter.last_person_recommend" to "personal recommendation",
        "flutter.search_historyEnabled" to true,
        "other.search_history_123" to "unrelated",
        "flutter.theme" to "dark",
        null to null,
    )

    @Test
    fun hidesAccountAndUnscopedHistoryWithoutChangingRecommendationOrSource() {
        val original = source.toMap()
        val result = IntlSearchStorageRules.sanitize(source, hideHistory = true, hideRecommend = false)
        assertFalse(result.containsKey(history))
        assertFalse(result.containsKey("flutter.search_history"))
        assertEquals(original - history - "flutter.search_history", result)
        assertEquals(original, source)
    }

    @Test
    fun recommendationSettingDoesNotHideHistoryDespiteSharedPrefix() {
        val result = IntlSearchStorageRules.sanitize(source, hideHistory = false, hideRecommend = true)
        assertTrue(result.containsKey(history))
        assertEquals(source - recommendation - "flutter.last_person_recommend", result)
    }

    @Test
    fun bothSettingsPreserveOtherFlutterAndNonFlutterKeys() {
        val result = IntlSearchStorageRules.sanitize(source, hideHistory = true, hideRecommend = true)
        assertEquals(setOf("flutter.search_historyEnabled", "other.search_history_123", "flutter.theme", null), result.keys)
    }

    @Test
    fun disabledOrUnrelatedReadPreservesIdentity() {
        assertSame(source, IntlSearchStorageRules.sanitize(source, hideHistory = false, hideRecommend = false))
        val unrelated = mapOf("flutter.theme" to "dark")
        assertSame(unrelated, IntlSearchStorageRules.sanitize(unrelated, hideHistory = true, hideRecommend = true))
    }
}
