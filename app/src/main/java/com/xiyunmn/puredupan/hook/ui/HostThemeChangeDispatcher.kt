package com.xiyunmn.puredupan.hook.ui

import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.util.concurrent.CopyOnWriteArraySet

internal object HostThemeChangeDispatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    internal fun register(listener: (String) -> Unit): () -> Unit {
        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return {}
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    internal fun disposeForReload() {
        mainHandler.removeCallbacksAndMessages(null)
        listeners.clear()
    }

    internal fun notifyChanged(reason: String) {
        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return
        UiStyle.invalidateHostNightCache()
        if (listeners.isEmpty()) return

        mainHandler.post {
            if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@post
            for (listener in listeners) {
                try {
                    listener(reason)
                } catch (t: Throwable) {
                    XposedCompat.logD("[HostThemeChangeDispatcher] listener failed: ${t.message}")
                }
            }
        }
    }
}
