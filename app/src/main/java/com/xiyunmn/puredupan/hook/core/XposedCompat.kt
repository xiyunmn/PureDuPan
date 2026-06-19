package com.xiyunmn.puredupan.hook.core

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the API 101 module lifecycle reference and common hook utilities.
 *
 * Feature hooks install through [module], for example:
 * ```
 * XposedCompat.module?.hook(method)?.intercept { chain ->
 *     // chain.thisObject, chain.args, chain.proceed(args)
 * }
 * ```
 *
 * This object provides module reference management, structured logging, and
 * reflection helpers for classes, methods, constructors, and fields.
 */
object XposedCompat {
    private const val LOG_TAG = "WangPanHook"

    @Volatile
    var module: XposedModule? = null

    @Volatile
    private var currentPackageName: String? = null

    private val installInfoOnce = ConcurrentHashMap.newKeySet<String>()

    internal fun setProcessName(name: String) {
        HookFileLogger.setProcessName(name)
    }

    internal fun setCurrentPackageName(name: String) {
        currentPackageName = name
    }

    fun currentPackageName(): String? = currentPackageName

    internal fun initializeFileLogging(context: Context) {
        if (ConfigManager.shouldOutputDetailedLogs()) {
            HookFileLogger.initialize(context)
        }
    }

    internal fun clearLogFiles(context: Context?): HookFileLogger.ClearResult {
        return HookFileLogger.clear(context)
    }

    internal fun logDirectoryPath(context: Context?): String {
        return HookFileLogger.logDirectoryPath(context)
    }

    // Logging
    fun log(msg: String) {
        // Deduplication for hook install logs
        if (isSuccessfulHookInstallLog(msg)) {
            if (!installInfoOnce.add(msg)) return
        }

        // Choose priority based on detailed logging setting
        val priority = if (ConfigManager.shouldOutputDetailedLogs()) {
            android.util.Log.DEBUG
        } else {
            android.util.Log.INFO
        }

        android.util.Log.println(priority, LOG_TAG, msg)
        module?.log(priority, LOG_TAG, msg)
        writeFileLogIfEnabled(priority, msg)
    }

    fun logD(msg: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        android.util.Log.d(LOG_TAG, msg)
        module?.log(android.util.Log.DEBUG, LOG_TAG, msg)
        writeFileLogIfEnabled(android.util.Log.DEBUG, msg)
    }

    inline fun logD(msg: () -> String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        logD(msg())
    }

    fun logW(msg: String) {
        android.util.Log.w(LOG_TAG, msg)
        module?.log(android.util.Log.WARN, LOG_TAG, msg)
        writeFileLogIfEnabled(android.util.Log.WARN, msg)
    }

    fun logE(msg: String) {
        android.util.Log.e(LOG_TAG, msg)
        module?.log(android.util.Log.ERROR, LOG_TAG, msg)
        writeFileLogIfEnabled(android.util.Log.ERROR, msg)
    }

    fun log(t: Throwable) {
        val summary = "${t.javaClass.simpleName}: ${t.message.orEmpty()}"
        val stackTrace = android.util.Log.getStackTraceString(t)

        // Always output summary
        android.util.Log.e(LOG_TAG, summary)
        module?.log(android.util.Log.ERROR, LOG_TAG, summary)
        writeFileLogIfEnabled(android.util.Log.ERROR, summary)

        // Always output stack trace (truncated in release mode)
        val truncated = if (ConfigManager.shouldOutputDetailedLogs()) {
            stackTrace  // Full stack trace
        } else {
            stackTrace.lines().take(10).joinToString("\n")  // First 10 lines
        }

        android.util.Log.e(LOG_TAG, truncated)
        module?.log(android.util.Log.ERROR, LOG_TAG, truncated)
        writeFileLogIfEnabled(android.util.Log.ERROR, truncated)
    }

    private fun writeFileLogIfEnabled(priority: Int, msg: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        HookFileLogger.write(priority, LOG_TAG, msg)
    }

    private fun isSuccessfulHookInstallLog(msg: String): Boolean {
        if (msg.contains("FAILED", ignoreCase = true) || msg.contains("no hooks installed", ignoreCase = true)) {
            return false
        }
        return msg.contains("hook INSTALLED", ignoreCase = true) ||
            msg.contains("hooks INSTALLED", ignoreCase = true)
    }

    // Class lookup
    fun findClassOrNull(name: String, cl: ClassLoader): Class<*>? =
        try { Class.forName(name, false, cl) } catch (_: ClassNotFoundException) { null }

    // Reflection helpers
    fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldError(fieldName)
    }

    fun getObjectField(obj: Any, fieldName: String): Any? =
        findField(obj.javaClass, fieldName).get(obj)

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        var current: Class<*>? = obj.javaClass
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name == methodName && method.parameterTypes.size == args.size) {
                    method.isAccessible = true
                    return method.invoke(obj, *args)
                }
            }
            current = current.superclass
        }
        throw NoSuchMethodError(methodName)
    }

    // Method and constructor lookup
    fun findMethodOrNull(
        className: String, cl: ClassLoader,
        methodName: String, vararg paramTypes: Class<*>,
    ): java.lang.reflect.Method? {
        val clazz = findClassOrNull(className, cl) ?: return null
        return findMethodOrNull(clazz, methodName, *paramTypes)
    }

    fun findMethodOrNull(
        clazz: Class<*>,
        methodName: String, vararg paramTypes: Class<*>,
    ): java.lang.reflect.Method? {
        return try {
            clazz.getDeclaredMethod(methodName, *paramTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) { null }
    }

    fun findConstructorOrNull(
        className: String, cl: ClassLoader,
        vararg paramTypes: Class<*>,
    ): java.lang.reflect.Constructor<*>? {
        val clazz = findClassOrNull(className, cl) ?: return null
        return try {
            clazz.getDeclaredConstructor(*paramTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) { null }
    }
}
