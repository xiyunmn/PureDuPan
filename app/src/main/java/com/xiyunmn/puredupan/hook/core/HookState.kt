package com.xiyunmn.puredupan.hook.core

import java.util.concurrent.atomic.AtomicBoolean

/** 通过原子状态防止重复安装 Hook。 */
class HookState {
    private val installed = AtomicBoolean(false)

    /** 首次标记安装时返回 true，已标记时返回 false。 */
    fun markInstalled(): Boolean = installed.compareAndSet(false, true)

    /** 重置安装状态，允许重试。 */
    fun reset() = installed.set(false)

    /** 读取当前安装状态。 */
    fun isInstalled(): Boolean = installed.get()
}
