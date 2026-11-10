package com.xiyunmn.puredupan.hook.runtime

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat

internal object AutoDailySignInRuntime {
    @Volatile private var manualTrigger: ((Context) -> Boolean)? = null

    fun disposeForReload() { manualTrigger = null }

    fun registerManualTrigger(trigger: (Context) -> Boolean) {
        manualTrigger = trigger
    }

    fun triggerNow(context: Context): Boolean {
        if (ModuleLifetime.isRetiring) return false
        return runCatching { manualTrigger?.invoke(context) == true }
            .getOrElse { t ->
                XposedCompat.logW("[AutoDailySignInRuntime] manual trigger failed: ${t.message}")
                false
            }
    }
}
