package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.host.features.baidu.BaiduFeatureSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarSettingsItemsBuilderTest {
    @Test
    fun internationalCannotHideEveryExistingTabUsingAbsentTabsAsFallback() {
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        val values = BottomBarSettingsItemsBuilder.bottomBarCustomizeSaveValues(
            isFeatureVisible = available::contains,
            isChecked = { it != FeatureKeys.KEY_HIDE_TAB_VIP && it != FeatureKeys.KEY_HIDE_TAB_AIGC },
        )
        assertFalse(values.hasVisibleTab)
        assertTrue(values.tabValues.isEmpty())
    }

    @Test
    fun internationalSaveIgnoresStaleUnsupportedOptions() {
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        val values = BottomBarSettingsItemsBuilder.bottomBarCustomizeSaveValues(
            isFeatureVisible = available::contains,
            isChecked = { it != FeatureKeys.KEY_HIDE_TAB_HOME },
        )
        assertTrue(values.hasVisibleTab)
        assertEquals(setOf(FeatureKeys.KEY_HIDE_TAB_HOME, FeatureKeys.KEY_HIDE_TAB_FILE,
            FeatureKeys.KEY_HIDE_TAB_SHARE, FeatureKeys.KEY_HIDE_TAB_MINE), values.tabValues.keys)
        assertFalse(values.directValues.containsKey(FeatureKeys.KEY_REPLACE_BOTTOM_AI))
    }
}
