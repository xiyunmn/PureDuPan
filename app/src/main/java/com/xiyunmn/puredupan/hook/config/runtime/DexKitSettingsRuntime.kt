package com.xiyunmn.puredupan.hook.config.runtime

import android.content.Context
import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat

internal object DexKitSettingsRuntime {
    fun bindRuntimeProvider(
        appContextProvider: () -> Context?,
        moduleStatePrefsProvider: (Context) -> SharedPreferences,
    ) {
        DexKitCompat.setRuntimeProvider(
            appContextProvider = appContextProvider,
            moduleStatePrefsProvider = moduleStatePrefsProvider,
        )
    }

    fun markFullScanPendingFromConfigListener() {
        DexKitCompat.markFullScanPending("dexkit automatic scan requested")
    }

    fun markFullScanPendingFromSettings() {
        DexKitCompat.markFullScanPending("dexkit manual scan requested from settings")
    }
}
