package com.xiyunmn.puredupan.hook.runtime

import android.app.Activity
import android.os.Handler
import java.util.WeakHashMap

/** 协调本代任务与热重载，正在执行的工作完成后再交接。 */
internal object ModuleLifetime {
    private val lock = Any()
    private var tasks = 0
    private val workers = mutableSetOf<Thread>()
    private val pickers = WeakHashMap<Activity, MutableSet<Int>>()

    @Volatile var isRetiring = false
        private set
    @Volatile var hostClassLoader: ClassLoader? = null
    @Volatile var hotReloaded = false

    fun beginTask(): Boolean = synchronized(lock) {
        if (isRetiring) false else {
            tasks++
            true
        }
    }

    fun finishTask() = synchronized(lock) { tasks-- }

    fun launchWorker(name: String, block: () -> Unit): Boolean = synchronized(lock) {
        if (isRetiring) return false
        val worker = Thread(block, name).apply {
            isDaemon = true
            contextClassLoader = hostClassLoader
        }
        workers.removeAll { !it.isAlive }
        workers += worker
        worker.start()
        true
    }

    /** 结果交付也算任务，避免工作线程退出后遗漏主线程回调。 */
    fun postResult(handler: Handler, block: () -> Unit): Boolean {
        if (!beginTask()) return false
        val posted = handler.post {
            try {
                block()
            } finally {
                finishTask()
            }
        }
        if (!posted) finishTask()
        return posted
    }

    fun beginPicker(activity: Activity, requestCode: Int): Boolean = synchronized(lock) {
        if (isRetiring) return false
        pickers.getOrPut(activity) { mutableSetOf() }.add(requestCode)
        true
    }

    fun finishPicker(activity: Activity?, requestCode: Int) = synchronized(lock) {
        val pending = pickers[activity] ?: return@synchronized
        pending.remove(requestCode)
        if (pending.isEmpty()) pickers.remove(activity)
    }

    /** 返回拒绝原因；空值表示已经关闭本代的新任务入口。 */
    fun beginRetirement(): String? = synchronized(lock) {
        pickers.entries.removeAll { (activity, _) -> activity.isFinishing || activity.isDestroyed }
        workers.removeAll { !it.isAlive }
        when {
            isRetiring -> "already retiring"
            pickers.isNotEmpty() -> "waiting for document picker"
            tasks != 0 || workers.isNotEmpty() -> "module work is running"
            else -> {
                isRetiring = true
                null
            }
        }
    }
}
