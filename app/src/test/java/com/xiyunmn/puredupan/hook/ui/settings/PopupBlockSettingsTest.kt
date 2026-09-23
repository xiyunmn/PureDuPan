package com.xiyunmn.puredupan.hook.ui.settings

import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityState
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.config.runtime.FeatureAvailabilityRuntime
import com.xiyunmn.puredupan.hook.host.features.baidu.BaiduFeatureSets
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupBlockSettingsTest {
    private val children = listOf(
        FeatureKeys.KEY_REMOVE_HOME_FAB,
        FeatureKeys.KEY_BLOCK_IN_APP_DIALOG,
        FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
        FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT,
        FeatureKeys.KEY_BLOCK_UPDATE_DIALOG,
        FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP,
        FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW,
        FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE,
    ).plus(MarketingPopup.entries.map { it.key }).distinct()

    @Test
    fun legacySelectionsStayEnabledAndFreshSettingsStayDisabledForAllHosts() {
        for (available in allHosts()) {
            assertFalse(snapshot(TestPreferences(), available).isPopupBlockEnabled)
            for (key in children.filter(available::contains)) {
                val prefs = TestPreferences(key to true)
                val result = snapshot(prefs, available)
                assertTrue(key, result.isPopupBlockEnabled)
                val expected = mutableSetOf(key)
                if (key == FeatureKeys.KEY_BLOCK_IN_APP_DIALOG &&
                    MarketingPopup.COUPON_GIFT_V3.key in available
                ) expected += MarketingPopup.COUPON_GIFT_V3.key
                assertEquals(expected, childValues(result).filterValues { it }.keys)
                assertFalse(prefs.values.containsKey(FeatureKeys.KEY_POPUP_BLOCK))
            }
        }
    }

    @Test
    fun explicitMasterOffMasksEveryChildAndOnRestoresSavedSelections() {
        for (available in allHosts()) {
            val prefs = TestPreferences(*children.map { it to true }.toTypedArray())
            prefs.values[FeatureKeys.KEY_POPUP_BLOCK] = false
            assertTrue(childValues(snapshot(prefs, available)).values.none { it })
            assertTrue(children.all { prefs.values[it] == true })
            prefs.values[FeatureKeys.KEY_POPUP_BLOCK] = true
            assertEquals(children.filter(available::contains).toSet(),
                childValues(snapshot(prefs, available)).filterValues { it }.keys)
        }
    }

    @Test
    fun unsupportedLegacySelectionsDoNotEnableInternationalPopupBlocking() {
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        val prefs = TestPreferences(*children.filterNot(available::contains).map { it to true }.toTypedArray())
        assertFalse(snapshot(prefs, available).isPopupBlockEnabled)
        assertFalse(PageCustomizeSettingsItemsBuilder.hasEnabledPopupBlockOption(available::contains) {
            prefs.values[it] == true
        })
        assertEquals(setOf(FeatureKeys.KEY_REMOVE_HOME_FAB, FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
            FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP, FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW),
            PopupBlockSettingsRegistry.specs.map { it.key }.filter(available::contains).toSet())
        assertTrue(MarketingPopup.entries.none { it.key in available })
        prefs.values[FeatureKeys.KEY_POPUP_BLOCK] = true
        assertTrue(snapshot(prefs, available).blockedMarketingPopups.isEmpty())
        assertTrue(MarketingPopup.entries.all { it.key in BaiduFeatureSets.baiduCnAvailableKeys &&
            it.key in BaiduFeatureSets.baiduSamsungAvailableKeys })
    }

    @Test
    fun legacyCouponSplitRespectsExplicitChoiceAndDoesNotEnableNewAds() {
        val available = BaiduFeatureSets.baiduCnAvailableKeys
        val prefs = TestPreferences(FeatureKeys.KEY_BLOCK_IN_APP_DIALOG to true)
        assertEquals(setOf(MarketingPopup.OPERATION_IMAGE, MarketingPopup.COUPON_GIFT_V3),
            snapshot(prefs, available).blockedMarketingPopups)
        assertTrue(MarketingPopup.savedValue(MarketingPopup.COUPON_GIFT_V3.key, prefs.values::containsKey) {
            prefs.values[it] == true
        })
        prefs.values[MarketingPopup.COUPON_GIFT_V3.key] = false
        assertEquals(setOf(MarketingPopup.OPERATION_IMAGE), snapshot(prefs, available).blockedMarketingPopups)
        prefs.values[FeatureKeys.KEY_BLOCK_IN_APP_DIALOG] = false
        prefs.values[MarketingPopup.COUPON_GIFT_V3.key] = true
        assertEquals(setOf(MarketingPopup.COUPON_GIFT_V3), snapshot(prefs, available).blockedMarketingPopups)
    }

    @Test
    fun everyMarketingOptionHasOneIndependentVisibleControlAndSnapshotValue() {
        val specs = PopupBlockSettingsRegistry.specs
        assertEquals(specs.size, specs.map { it.key }.toSet().size)
        for (option in MarketingPopup.entries) {
            assertEquals(1, specs.count { it.key == option.key })
            val prefs = TestPreferences(option.key to true, MarketingPopup.COUPON_GIFT_V3.key to false)
            prefs.values[option.key] = true
            assertEquals(setOf(option), snapshot(prefs, BaiduFeatureSets.baiduCnAvailableKeys).blockedMarketingPopups)
            prefs.values[FeatureKeys.KEY_POPUP_BLOCK] = false
            assertTrue(snapshot(prefs, BaiduFeatureSets.baiduCnAvailableKeys).blockedMarketingPopups.isEmpty())
        }
    }

    @Test
    fun downloadIsFirstChildAndEnableAllTracksOnlyVisibleOptions() {
        assertEquals(FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
            PopupBlockSettingsRegistry.specs.first().key)
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        val visible = PopupBlockSettingsRegistry.specs.map { it.key }.filter(available::contains).toSet()
        assertTrue(PageCustomizeSettingsItemsBuilder.areAllPopupBlockOptionsEnabled(
            available::contains, visible::contains))
        assertFalse(PageCustomizeSettingsItemsBuilder.areAllPopupBlockOptionsEnabled(
            available::contains,
        ) { it in visible && it != FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG })
        assertFalse(PageCustomizeSettingsItemsBuilder.areAllPopupBlockOptionsEnabled(
            { false }, { true }))
    }

    @Test
    fun enablingAllTurnsOnMasterAndKeepsUnsupportedSelections() {
        val prefs = TestPreferences(FeatureKeys.KEY_POPUP_BLOCK to false,
            FeatureKeys.KEY_BLOCK_UPDATE_DIALOG to true)
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            prefs = prefs.prefs,
            editor = prefs.prefs.edit(),
            isFeatureVisible = available::contains,
            isChecked = { true },
            enableMaster = true,
        ).apply()
        assertEquals(true, prefs.values[FeatureKeys.KEY_POPUP_BLOCK])
        assertEquals(true, prefs.values[FeatureKeys.KEY_BLOCK_UPDATE_DIALOG])
        assertTrue(PopupBlockSettingsRegistry.specs.map { it.key }
            .filter(available::contains).all { prefs.values[it] == true })
    }

    @Test
    fun savingChildrenPreservesExplicitMasterOffAndUnsupportedSettings() {
        val prefs = TestPreferences(FeatureKeys.KEY_POPUP_BLOCK to false, FeatureKeys.KEY_BLOCK_UPDATE_DIALOG to true)
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            prefs.prefs, prefs.prefs.edit(), BaiduFeatureSets.baiduIntlAvailableKeys::contains,
        ) { it == FeatureKeys.KEY_REMOVE_HOME_FAB }.apply()
        assertEquals(false, prefs.values[FeatureKeys.KEY_POPUP_BLOCK])
        assertEquals(true, prefs.values[FeatureKeys.KEY_REMOVE_HOME_FAB])
        assertEquals(true, prefs.values[FeatureKeys.KEY_BLOCK_UPDATE_DIALOG])
        assertTrue(childValues(snapshot(prefs, BaiduFeatureSets.baiduIntlAvailableKeys)).values.none { it })
    }

    @Test
    fun firstSaveFreezesPreviousInferredMasterStateBeforeChangingChildren() {
        val available = BaiduFeatureSets.baiduIntlAvailableKeys
        val legacy = TestPreferences(FeatureKeys.KEY_REMOVE_HOME_FAB to true)
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            legacy.prefs, legacy.prefs.edit(), available::contains,
        ) { false }.apply()
        assertEquals(true, legacy.values[FeatureKeys.KEY_POPUP_BLOCK])
        val fresh = TestPreferences()
        PageCustomizeSettingsItemsBuilder.putPopupBlockValues(
            fresh.prefs, fresh.prefs.edit(), available::contains,
        ) { true }.apply()
        assertEquals(false, fresh.values[FeatureKeys.KEY_POPUP_BLOCK])
    }

    private fun allHosts() = listOf(BaiduFeatureSets.baiduCnAvailableKeys,
        BaiduFeatureSets.baiduIntlAvailableKeys, BaiduFeatureSets.baiduSamsungAvailableKeys)

    private fun snapshot(prefs: TestPreferences, available: Set<String>): SettingsSnapshot {
        FeatureAvailabilityRuntime.apply(available.associateWith { FeatureAvailabilityStatus(FeatureAvailabilityState.FULL) })
        return try {
            ConfigManager::class.java.getDeclaredMethod("buildSettingsSnapshot", SharedPreferences::class.java)
                .apply { isAccessible = true }.invoke(ConfigManager, prefs.prefs) as SettingsSnapshot
        } finally {
            FeatureAvailabilityRuntime.apply(emptyMap())
        }
    }

    private fun childValues(snapshot: SettingsSnapshot) = mapOf(
        FeatureKeys.KEY_REMOVE_HOME_FAB to snapshot.isHomeFabRemoved,
        FeatureKeys.KEY_BLOCK_IN_APP_DIALOG to snapshot.isInAppDialogBlocked,
        FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG to snapshot.isNonWifiDownloadDialogBlocked,
        FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT to snapshot.isNotificationPromptBlocked,
        FeatureKeys.KEY_BLOCK_UPDATE_DIALOG to snapshot.isUpdateDialogBlocked,
        FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP to snapshot.isFullScreenBackupBlocked,
        FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW to snapshot.isAppStoreReviewBlocked,
        FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE to snapshot.isSharePushGuideBlocked,
    ) + MarketingPopup.entries.associate { it.key to (it in snapshot.blockedMarketingPopups) }

    private class TestPreferences(vararg entries: Pair<String, Any>) {
        val values = mutableMapOf(*entries)
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean", "getInt", "getString" -> values[args[0]] ?: args[1]
                "contains" -> values.containsKey(args[0])
                "edit" -> editor()
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "putBoolean" -> {
                        pending[args[0] as String] = args[1]
                        proxy
                    }
                    "apply" -> { values.putAll(pending); null }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
