package com.xiyunmn.puredupan.hook.settings.runtime

import android.content.Context
import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.settings.registry.SettingsHostState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState

internal data class SettingsRuntimeSession(
    val prefs: SharedPreferences,
    val hostId: String?,
    val isIntlHost: Boolean,
    val primarySplashAdFeatureKey: String?,
    val showDexKitStatus: Boolean,
    val memberCardLayoutMode: MemberCardLayoutMode,
    private val visibleFeatureKeys: Set<String>,
) {
    fun isFeatureVisible(key: String): Boolean {
        return key in visibleFeatureKeys
    }

    companion object {
        fun create(context: Context): SettingsRuntimeSession {
            val prefs = SettingsUserState.getPrefs(context)
            val featureStatusMap = SettingsHostState.featureStatusMapFor(context)
            SettingsUserState.applyFeatureAvailability(
                context = context,
                featureStatusMap = featureStatusMap,
                refreshRuntime = true,
            )
            return SettingsRuntimeSession(
                prefs = prefs,
                hostId = SettingsHostState.hostId(context),
                isIntlHost = SettingsHostState.isIntlHost(context),
                primarySplashAdFeatureKey = SettingsHostState.primarySplashAdFeatureKeyFor(context),
                showDexKitStatus = SettingsHostState.showDexKitStatusInSettings(context),
                memberCardLayoutMode = SettingsHostState.memberCardLayoutMode(context),
                visibleFeatureKeys = featureStatusMap
                    .filterValues { status -> status.isSupported() }
                    .keys,
            )
        }
    }
}
