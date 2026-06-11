package com.xiyunmn.puredupan.hook.ui

import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.util.concurrent.CopyOnWriteArraySet

internal object HostThemeChangeDispatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    internal fun register(listener: (String) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    internal fun notifyChanged(reason: String) {
        UiStyle.invalidateHostNightCache()
        if (listeners.isEmpty()) return

        mainHandler.post {
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
