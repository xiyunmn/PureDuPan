package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSocketHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.HashMap
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlNonCoreDiffSocketDelayHook {
    const val SOCKET_REGISTER_CACHE_ID = "intl_non_core_diff_socket_register"

    private const val KOTLIN_FUNCTION1_CLASS_NAME = "kotlin.jvm.functions.Function1"
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private const val CLOUD_FILE_DIFF_ACTION = BaiduIntlSocketHookPoints.CLOUD_FILE_DIFF_ACTION
    private const val CLOUD_FILE_DIFF_ACTION_2 = BaiduIntlSocketHookPoints.CLOUD_FILE_DIFF_ACTION_COMPAT
    private const val CLOUD_FILE_DIFF_CHECK_ACTION = BaiduIntlSocketHookPoints.CLOUD_FILE_DIFF_CHECK_ACTION
    private const val CLOUD_IMAGE_DIFF_ACTION = BaiduIntlSocketHookPoints.CLOUD_IMAGE_DIFF_ACTION
    private const val CLOUD_VIDEO_DIFF_ACTION = BaiduIntlSocketHookPoints.CLOUD_VIDEO_DIFF_ACTION
    private const val SEARCH_DIFF_ACTION = BaiduIntlSocketHookPoints.SEARCH_DIFF_ACTION

    private val imageEntryActivityClassNames = BaiduIntlSocketHookPoints.IMAGE_ENTRY_ACTIVITIES
    private val videoEntryActivityClassNames = BaiduIntlSocketHookPoints.VIDEO_ENTRY_ACTIVITIES
    private val searchEntryActivityClassNames = BaiduIntlSocketHookPoints.SEARCH_ENTRY_ACTIVITIES

    private data class PendingRegistration(
        val receiver: Any,
        val callback: Any,
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    )

    private data class ActionState(
        val action: String,
        var pending: PendingRegistration? = null,
        var skipped: Boolean = false,
        var restored: Boolean = false,
        var restoring: Boolean = false,
        var skipCount: Int = 0,
        var restoreCount: Int = 0,
    )

    private val hookState = HookState()
    private val lock = Any()
    private val actionStates = linkedMapOf(
        CLOUD_IMAGE_DIFF_ACTION to ActionState(CLOUD_IMAGE_DIFF_ACTION),
        CLOUD_VIDEO_DIFF_ACTION to ActionState(CLOUD_VIDEO_DIFF_ACTION),
        SEARCH_DIFF_ACTION to ActionState(SEARCH_DIFF_ACTION),
    )

    @Volatile private var registerMethod: Method? = null
    @Volatile private var homeStableRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = resolveSocketRegisterMethod(cl)
            if (method == null) {
                hookState.reset()
                XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] socket register method NOT FOUND")
                return
            }
            registerMethod = method

            mod.hook(method).intercept { chain ->
                val action = chain.args.getOrNull(0) as? String
                val callback = chain.args.getOrNull(1)
                val receiver = chain.thisObject
                if (action == null || callback == null || receiver == null) {
                    return@intercept chain.proceed()
                }

                if (isCoreAction(action)) {
                    XposedCompat.logD("[IntlNonCoreDiffSocketDelayHook] allow core diff socket: action=$action")
                    return@intercept chain.proceed()
                }

                val state = synchronized(lock) { actionStates[action] }
                if (state == null || !shouldDelay(state)) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    state.pending = PendingRegistration(receiver, callback)
                    state.skipped = true
                    state.skipCount++
                }
                XposedCompat.log(
                    "[IntlNonCoreDiffSocketDelayHook] delayed non-core diff socket: " +
                        "action=$action, skipCount=${state.skipCount}",
                )
                null
            }

            val homeHooked = hookHomeStableRestoreSignal(cl)
            val imageSignals = hookEntryRestoreSignals(
                cl,
                imageEntryActivityClassNames,
                CLOUD_IMAGE_DIFF_ACTION,
                "image_entry",
            )
            val videoSignals = hookEntryRestoreSignals(
                cl,
                videoEntryActivityClassNames,
                CLOUD_VIDEO_DIFF_ACTION,
                "video_entry",
            )
            val searchSignals = hookEntryRestoreSignals(
                cl,
                searchEntryActivityClassNames,
                SEARCH_DIFF_ACTION,
                "search_entry",
            )

            XposedCompat.log(
                "[IntlNonCoreDiffSocketDelayHook] hooks INSTALLED: " +
                    "register=${method.declaringClass.name}.${method.name}, " +
                    "homeStable=$homeHooked, imageSignals=$imageSignals, " +
                    "videoSignals=$videoSignals, searchSignals=$searchSignals",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolveSocketRegisterMethod(cl) != null
    }

    private fun resolveSocketRegisterMethod(cl: ClassLoader): Method? {
        return resolveSocketRegisterMethodWithDexKit(cl)
    }

    private fun resolveSocketRegisterMethodWithDexKit(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, SOCKET_REGISTER_CACHE_ID) { ref ->
            resolveSocketRegisterRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallbackSocketRegisterMethod(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val function1Class = XposedCompat.findClassOrNull(KOTLIN_FUNCTION1_CLASS_NAME, cl)

        val scanned = if (function1Class == null) {
            XposedCompat.logW("[IntlNonCoreDiffSocketDelayHook] Function1 class NOT FOUND for DexKit resolve")
            null
        } else {
            DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .returnType(Void.TYPE)
                                .paramTypes(String::class.java, function1Class),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                    )
                }
            }
        }
        val result = scanned.orEmpty()

        if (result.isEmpty()) {
            XposedCompat.logD("[IntlNonCoreDiffSocketDelayHook] socket register candidate not found by DexKit")
            return resolveFallbackSocketRegisterMethod(cl) ?: run {
                DexKitCompat.putCachedMethod(TAG, SOCKET_REGISTER_CACHE_ID, null)
                null
            }
        }

        val candidates = result.mapNotNull { methodData ->
            val method = resolveSocketRegisterRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
            ) ?: return@mapNotNull null
            if (methodData.isConstructor) return@mapNotNull null
            if (methodData.returnTypeName != "void") return@mapNotNull null
            if (methodData.paramTypeNames != listOf("java.lang.String", KOTLIN_FUNCTION1_CLASS_NAME)) {
                return@mapNotNull null
            }
            if (Modifier.isStatic(methodData.modifiers)) return@mapNotNull null
            method
        }

        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] ambiguous DexKit socket register method: " +
                    candidates.joinToString { "${it.declaringClass.name}.${it.name}" },
            )
            DexKitCompat.putCachedMethod(TAG, SOCKET_REGISTER_CACHE_ID, null)
            return resolveFallbackSocketRegisterMethod(cl)
        }

        val method = candidates.single()
        XposedCompat.log(
            "[IntlNonCoreDiffSocketDelayHook] resolved socket register by DexKit: " +
                "${method.declaringClass.name}.${method.name}",
        )
        cacheResolvedSocketRegister(method)
        return method
    }

    private fun resolveFallbackSocketRegisterMethod(cl: ClassLoader): Method? {
        return resolveKnown13_11SocketRegisterMethod(cl)?.also { method ->
            cacheResolvedSocketRegister(method)
            XposedCompat.logD(
                "[IntlNonCoreDiffSocketDelayHook] resolved known socket register fallback: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveKnown13_11SocketRegisterMethod(cl: ClassLoader): Method? =
        resolveSocketRegisterRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduIntlSocketHookPoints.SOCKET_MANAGER_13_11_CLASS,
                BaiduIntlSocketHookPoints.SOCKET_REGISTER_13_11_METHOD,
            ),
        )

    private fun cacheResolvedSocketRegister(method: Method) {
        DexKitCompat.putCachedMethod(
            TAG,
            SOCKET_REGISTER_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
    }

    private fun resolveSocketRegisterRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        if (!ref.className.startsWith(BaiduIntlSocketHookPoints.SOCKET_PACKAGE_PREFIX)) return null
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isExpectedSocketManagerClass(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].name == KOTLIN_FUNCTION1_CLASS_NAME
        }?.apply { isAccessible = true }
    }

    private fun isExpectedSocketManagerClass(clazz: Class<*>): Boolean {
        val requiredActions = requiredSocketActions().toSet()
        val stringConstants = staticStringConstants(clazz)
        if (!stringConstants.containsAll(requiredActions)) {
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] socket manager action constants mismatch: " +
                    "missing=${requiredActions - stringConstants}",
            )
            return false
        }

        if (!hasActionCallbackMapField(clazz)) {
            XposedCompat.logW("[IntlNonCoreDiffSocketDelayHook] socket manager action map field missing")
            return false
        }

        val metadataTokens = metadataTokens(clazz)
        if ("mSocketActionHashMap" !in metadataTokens) {
            XposedCompat.logD("[IntlNonCoreDiffSocketDelayHook] socket manager metadata token missing: mSocketActionHashMap")
        }

        return true
    }

    private fun requiredSocketActions(): List<String> = BaiduIntlSocketHookPoints.REQUIRED_DIFF_ACTIONS

    private fun staticStringConstants(clazz: Class<*>): Set<String> =
        clazz.declaredFields.mapNotNull { field ->
            runCatching {
                if (field.type == String::class.java && Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    field.get(null) as? String
                } else {
                    null
                }
            }.getOrNull()
        }.toSet()

    private fun hasActionCallbackMapField(clazz: Class<*>): Boolean =
        clazz.declaredFields.any { field ->
            HashMap::class.java.isAssignableFrom(field.type) &&
                !Modifier.isStatic(field.modifiers)
        }

    private fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }

    private fun isCoreAction(action: String): Boolean =
        action == CLOUD_FILE_DIFF_ACTION ||
            action == CLOUD_FILE_DIFF_ACTION_2 ||
            action == CLOUD_FILE_DIFF_CHECK_ACTION

    private fun shouldDelay(state: ActionState): Boolean {
        if (!isEnabled()) return false
        if (state.restoring || state.restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean =
        IntlHomeStableRestoreSignal.hook(cl, TAG) {
            scheduleHomeStableRestore()
        }

    private fun hookEntryRestoreSignals(
        cl: ClassLoader,
        classNames: List<String>,
        action: String,
        reasonPrefix: String,
    ): Int {
        var installed = 0
        for (className in classNames.distinct()) {
            val activityClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (hookActivityOnCreate(activityClass, "$reasonPrefix:$className") { reason ->
                    restoreActionIfPending(action, reason)
                }
            ) {
                installed++
            }
        }
        return installed
    }

    private fun hookActivityOnCreate(
        activityClass: Class<*>,
        reason: String,
        beforeProceed: (String) -> Unit,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java) ?: return false
        mod.hook(method).intercept { chain ->
            beforeProceed(reason)
            chain.proceed()
        }
        return true
    }

    private fun scheduleHomeStableRestore() {
        if (!isEnabled()) return
        IntlHomeStableRestoreSignal.scheduleDelayedRestore(
            tag = TAG,
            delayMs = HOME_STABLE_RESTORE_DELAY_MS,
            tryMarkScheduled = {
                synchronized(lock) {
                    val allRestored = actionStates.values.all { it.restored || !it.skipped }
                    if (homeStableRestoreScheduled || allRestored) {
                        false
                    } else {
                        homeStableRestoreScheduled = true
                        true
                    }
                }
            },
            clearScheduled = { homeStableRestoreScheduled = false },
            restore = ::restoreAllPending,
        )
    }

    private fun restoreAllPending(reason: String) {
        actionStates.keys.forEach { action ->
            restoreActionIfPending(action, reason)
        }
    }

    private fun restoreActionIfPending(action: String, reason: String) {
        if (!isEnabled()) return
        val method = registerMethod ?: run {
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] restore skipped: registerMethod missing, " +
                    "action=$action, reason=$reason",
            )
            return
        }
        val restoreState = synchronized(lock) {
            val state = actionStates[action] ?: return
            if (!state.skipped || state.restored) return
            val pending = state.pending ?: return
            state.restored = true
            state.restoring = true
            state.restoreCount++
            state to pending
        }

        val (state, pending) = restoreState
        try {
            method.invoke(pending.receiver, state.action, pending.callback)
            XposedCompat.log(
                "[IntlNonCoreDiffSocketDelayHook] restored non-core diff socket: " +
                    "action=${state.action}, reason=$reason, restoreCount=${state.restoreCount}",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                state.restored = false
                state.restoreCount--
            }
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] restore FAILED: " +
                    "action=${state.action}, reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            synchronized(lock) {
                state.restoring = false
            }
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled &&
            HookSettings.isIntlNonCoreDiffSocketDelayed

    private const val TAG = "IntlNonCoreDiffSocketDelayHook"
}
