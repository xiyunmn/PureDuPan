package com.xiyunmn.puredupan.hook.core

/**
 * Hook utilities for common operations.
 */
object HookUtils {
    /**
     * Returns the default value for a given primitive type or null for reference types.
     * Used when a hooked method needs to return early with a safe default value.
     */
    @JvmStatic
    fun getDefaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }
}
