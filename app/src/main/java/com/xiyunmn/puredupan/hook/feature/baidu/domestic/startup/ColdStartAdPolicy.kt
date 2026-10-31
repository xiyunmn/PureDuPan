package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

/** 首次确定进程启动模式后保持不变，避免启动路由中途切换。 */
internal class ColdStartAdPolicy {
    private var ready = false
    private var selected: Boolean? = null

    @Synchronized
    fun markReady() {
        ready = true
    }

    @Synchronized
    fun select(enabled: Boolean): Boolean {
        selected?.let { return it }
        return (enabled && ready).also { selected = it }
    }
}
