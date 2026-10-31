package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

/** 记录一次冷启动窗口交接，避免持有 Activity 或 Intent。 */
internal class ColdStartWindowPolicy {
    private var entryObserved = false
    private var launcherTaskId: Int? = null

    @Synchronized
    fun recordNavigate(taskId: Int, launcher: Boolean, restored: Boolean, nativeNoAd: Boolean) {
        if (entryObserved) return
        entryObserved = true
        if (taskId >= 0 && launcher && !restored && nativeNoAd) launcherTaskId = taskId
    }

    @Synchronized
    fun consumeMain(taskId: Int, restored: Boolean, nativeNoAd: Boolean): Boolean {
        entryObserved = true
        val expectedTask = launcherTaskId
        launcherTaskId = null
        return expectedTask != null && expectedTask == taskId && !restored && nativeNoAd
    }
}
