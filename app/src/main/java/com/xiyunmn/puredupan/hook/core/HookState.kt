package com.xiyunmn.puredupan.hook.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility for managing hook installation state with thread-safe operations.
 *
 * Replaces the pattern of:
 * ```
 * @Volatile private var hooked = false
 * private fun tryMarkHooked(): Boolean = synchronized(this) {
 *     if (hooked) false else { hooked = true; true }
 * }
 * ```
 *
 * With a more efficient and clearer:
 * ```
 * private val hookState = HookState()
 * if (!hookState.markInstalled()) return
 * ```
 */
class HookState {
    private val installed = AtomicBoolean(false)

    /**
     * Attempts to mark this hook as installed.
     * @return true if this call successfully marked it (first time), false if already marked
     */
    fun markInstalled(): Boolean = installed.compareAndSet(false, true)

    /**
     * Resets the installed state (rarely needed, mainly for error recovery)
     */
    fun reset() = installed.set(false)

    /**
     * Checks if already installed without changing state
     */
    fun isInstalled(): Boolean = installed.get()
}
