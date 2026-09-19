package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

/** A warm-up finishing during launch must not change the mode halfway through routing. */
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
