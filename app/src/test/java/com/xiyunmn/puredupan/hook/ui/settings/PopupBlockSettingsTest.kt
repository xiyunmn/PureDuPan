package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupBlockSettingsTest {
    @Test
    fun savingChildrenPreservesExplicitMasterOffAndUnsupportedSettings() {
        val visibleKey = PopupBlockSettingsRegistry.specs.first().key
        val hiddenKey = PopupBlockSettingsRegistry.specs.first { it.key != visibleKey }.key
        val prefs = TestPreferences(FeatureKeys.KEY_POPUP_BLOCK to false, hiddenKey to true)
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            prefs.prefs, prefs.prefs.edit(), { it == visibleKey },
        ) { it == visibleKey }.apply()
        assertEquals(false, prefs.values[FeatureKeys.KEY_POPUP_BLOCK])
        assertEquals(true, prefs.values[visibleKey])
        assertEquals(true, prefs.values[hiddenKey])
    }

    @Test
    fun firstSaveFreezesPreviousInferredMasterStateBeforeChangingChildren() {
        val visibleKey = PopupBlockSettingsRegistry.specs.first().key
        val legacy = TestPreferences(visibleKey to true)
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            legacy.prefs, legacy.prefs.edit(), { it == visibleKey },
        ) { false }.apply()
        assertEquals(true, legacy.values[FeatureKeys.KEY_POPUP_BLOCK])
        val fresh = TestPreferences()
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            fresh.prefs, fresh.prefs.edit(), { it == visibleKey },
        ) { true }.apply()
        assertEquals(false, fresh.values[FeatureKeys.KEY_POPUP_BLOCK])
    }

    private class TestPreferences(vararg entries: Pair<String, Boolean>) {
        val values = mutableMapOf(*entries)
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args[0]] ?: args[1]
                "contains" -> values.containsKey(args[0])
                "edit" -> editor()
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Boolean>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "putBoolean" -> {
                        pending[args[0] as String] = args[1] as Boolean
                        proxy
                    }
                    "apply" -> { values.putAll(pending); null }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
