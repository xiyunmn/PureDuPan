package com.xiyunmn.puredupan.hook.ui.settings

import android.os.Handler
import android.os.Looper

/** 合并连续调整，交互结束时提交最终值。 */
internal class DeferredSettingsSave(private val save: () -> Unit) {
    companion object {
        private val instances = java.util.WeakHashMap<DeferredSettingsSave, Boolean>()
        fun flushAll() { instances.keys.toList().forEach { it.flush() } }
    }
    init { instances[this] = true }
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val task = Runnable { flush() }

    fun schedule() {
        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return
        pending = true
        handler.removeCallbacks(task)
        handler.postDelayed(task, 180L)
    }

    fun flush() {
        handler.removeCallbacks(task)
        if (!pending) return
        pending = false
        save()
    }
}
