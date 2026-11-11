package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.app.Application
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object IntlFloatViewStartupDelayHook {
    fun disposeForReload() {
        try {
            if (chainStates.values.any { it.pending != null }) restoreAllPending("reload")
        } finally {
            chainStates.values.forEach { it.pending = null }
            registerLifecycleMethod = null
            homeStableRestoreScheduled = false
        }
    }

    private const val TAG = "IntlFloatViewStartupDelayHook"
    private const val TASK_QUERY_API_CLASS_NAME = BaiduIntlHookPoints.TASK_QUERY_API
    private const val AUDIO_API_CLASS_NAME = BaiduIntlHookPoints.AUDIO_API
    private const val RETURN_THIRD_APP_VIEW_CLASS_NAME = BaiduIntlHookPoints.RETURN_THIRD_APP_VIEW
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private enum class Chain(
        val logName: String,
    ) {
        TASK_QUERY("task_query_tip"),
        AUDIO_CIRCLE("audio_circle_container"),
        RETURN_THIRD_APP("return_third_app"),
    }

    private data class PendingLifecycle(
        val application: Application,
        val callback: Application.ActivityLifecycleCallbacks,
    )

    private data class ChainState(
        val chain: Chain,
        var pending: PendingLifecycle? = null,
        var skipped: Boolean = false,
        var restored: Boolean = false,
        var restoring: Boolean = false,
        var skipCount: Int = 0,
        var restoreCount: Int = 0,
    )

    private val hookState = HookState()
    private val lock = Any()
    private val chainStates = linkedMapOf(
        Chain.TASK_QUERY to ChainState(Chain.TASK_QUERY),
        Chain.AUDIO_CIRCLE to ChainState(Chain.AUDIO_CIRCLE),
        Chain.RETURN_THIRD_APP to ChainState(Chain.RETURN_THIRD_APP),
    )

    @Volatile private var registerLifecycleMethod: Method? = null
    @Volatile private var homeStableReached = false
    @Volatile private var homeStableRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        // 旧代已交还待恢复调用，热重载不重新进入冷启动抑制阶段。
        if (ModuleLifetime.hotReloaded) return
        if (!isEnabled()) {
            XposedCompat.log("[IntlFloatViewStartupDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = Application::class.java.getDeclaredMethod(
                "registerActivityLifecycleCallbacks",
                Application.ActivityLifecycleCallbacks::class.java,
            ).apply { isAccessible = true }
            registerLifecycleMethod = method

            mod.hook(method).setId("IntlFloatViewStartupDelayHook:hook:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                val application = chain.thisObject as? Application
                val callback = chain.args.firstOrNull() as? Application.ActivityLifecycleCallbacks
                val targetChain = callback?.let { classifyLifecycleCallback(it.javaClass) }
                if (application == null || callback == null || targetChain == null) {
                    return@intercept chain.proceed()
                }

                val state = synchronized(lock) { chainStates[targetChain] } ?: return@intercept chain.proceed()
                if (!shouldDelay(state)) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    state.pending = PendingLifecycle(application, callback)
                    state.skipped = true
                    state.skipCount++
                }
                XposedCompat.log(
                    "[IntlFloatViewStartupDelayHook] delayed lifecycle registration: " +
                        "chain=${state.chain.logName}, callback=${callback.javaClass.name}, " +
                        "skipCount=${state.skipCount}",
                )
                null
            }

            val homeHooked = hookHomeStableRestoreSignal(cl)
            val taskSignals = hookTaskQueryRestoreSignals(cl)
            val audioSignals = hookAudioRestoreSignals(cl)
            val returnThirdSignals = hookReturnThirdRestoreSignals(cl)
            XposedCompat.log(
                "[IntlFloatViewStartupDelayHook] hooks INSTALLED: " +
                    "register=${method.declaringClass.name}.${method.name}, " +
                    "homeStable=$homeHooked, taskSignals=$taskSignals, " +
                    "audioSignals=$audioSignals, returnThirdSignals=$returnThirdSignals",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlFloatViewStartupDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun classifyLifecycleCallback(clazz: Class<*>): Chain? {
        val callbackTokens = metadataTokens(clazz)
        if (callbackTokens.contains("TaskQueryTipActivityLifecycle")) {
            return Chain.TASK_QUERY
        }

        val ownerClasses = ownerClasses(clazz)
        if (ownerClasses.any { owner ->
                metadataTokens(owner).contains("TaskQueryTip") || hasTaskQueryTipFields(owner)
            }
        ) {
            return Chain.TASK_QUERY
        }
        if (ownerClasses.any { staticStringConstants(it).contains("AudioCircleViewHelper") }) {
            return Chain.AUDIO_CIRCLE
        }
        if (ownerClasses.any { staticStringConstants(it).contains("ReturnThirdAppViewHelper") }) {
            return Chain.RETURN_THIRD_APP
        }
        return null
    }

    /** 新版移除了 Metadata，保留任务提示视图及广播模型的字段组合。 */
    private fun hasTaskQueryTipFields(owner: Class<*>): Boolean {
        val types = owner.declaredFields.map { it.type.name }.toSet()
        return "com.baidu.netdisk.ui.widget.taskquerytip.LoadingToastView" in types &&
            "com.baidu.netdisk.cloudfile.io.model.FileManagerBroadcastBean" in types
    }

    private fun ownerClasses(clazz: Class<*>): List<Class<*>> {
        val result = linkedSetOf<Class<*>>()
        var current: Class<*>? = clazz
        while (current != null) {
            result += current
            current.declaringClass?.let { result += it }
            current.enclosingClass?.let { result += it }
            current = current.declaringClass ?: current.enclosingClass
        }
        return result.toList()
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

    private fun staticStringConstants(clazz: Class<*>): Set<String> {
        return clazz.declaredFields.mapNotNull { field ->
            runCatching {
                if (field.type == String::class.java && Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    field.get(null) as? String
                } else {
                    null
                }
            }.getOrNull()
        }.toSet()
    }

    private fun shouldDelay(state: ChainState): Boolean {
        if (ModuleLifetime.isRetiring || !isEnabled()) return false
        if (homeStableReached) return false
        if (state.restoring || state.restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean =
        IntlHomeStableRestoreSignal.hook(cl, TAG) {
            scheduleHomeStableRestore()
        }

    private fun hookTaskQueryRestoreSignals(cl: ClassLoader): Int {
        val apiClass = XposedCompat.findClassOrNull(TASK_QUERY_API_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlFloatViewStartupDelayHook] TaskQueryApi class NOT FOUND")
            return 0
        }
        return hookNamedMethodsBeforeProceed(
            clazz = apiClass,
            methodNames = setOf("attachView", "showTaskQueryTip"),
            reasonPrefix = "task_query_api",
        ) {
            restoreChainIfPending(Chain.TASK_QUERY, it)
        }
    }

    private fun hookAudioRestoreSignals(cl: ClassLoader): Int {
        val apiClass = XposedCompat.findClassOrNull(AUDIO_API_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlFloatViewStartupDelayHook] MAudioApi class NOT FOUND")
            return 0
        }
        return hookNamedMethodsBeforeProceed(
            clazz = apiClass,
            methodNames = setOf("showAudioCircleViewManagerAudio", "startAudioPlayerActivity"),
            reasonPrefix = "audio_api",
        ) {
            restoreChainIfPending(Chain.AUDIO_CIRCLE, it)
        }
    }

    private fun hookReturnThirdRestoreSignals(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val viewClass = XposedCompat.findClassOrNull(RETURN_THIRD_APP_VIEW_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlFloatViewStartupDelayHook] ReturnThirdAppView class NOT FOUND")
            return 0
        }
        var installed = 0
        for (constructor in viewClass.declaredConstructors) {
            constructor.isAccessible = true
            mod.hook(constructor).setId("IntlFloatViewStartupDelayHook:hookReturnThirdRestoreSignals:constructor").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                restoreChainIfPending(Chain.RETURN_THIRD_APP, "return_third_view:${constructor.parameterTypes.size}")
                chain.proceed()
            }
            installed++
        }
        return installed
    }

    private fun hookNamedMethodsBeforeProceed(
        clazz: Class<*>,
        methodNames: Set<String>,
        reasonPrefix: String,
        beforeProceed: (String) -> Unit,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        for (method in clazz.declaredMethods) {
            if (method.name !in methodNames) continue
            method.isAccessible = true
            mod.hook(method).setId("IntlFloatViewStartupDelayHook:hookNamedMethodsBeforeProceed:method").intercept { chain ->
                if (ModuleLifetime.isRetiring) return@intercept chain.proceed()
                beforeProceed("$reasonPrefix:${method.name}/${method.parameterTypes.size}")
                chain.proceed()
            }
            installed++
        }
        return installed
    }

    private fun scheduleHomeStableRestore() {
        homeStableReached = true
        if (ModuleLifetime.isRetiring || !isEnabled()) return
        IntlHomeStableRestoreSignal.scheduleDelayedRestore(
            tag = TAG,
            delayMs = HOME_STABLE_RESTORE_DELAY_MS,
            tryMarkScheduled = {
                synchronized(lock) {
                    val allRestored = chainStates.values.all { it.restored || !it.skipped }
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
        Chain.values().forEach { chain ->
            restoreChainIfPending(chain, reason)
        }
    }

    private fun restoreChainIfPending(chain: Chain, reason: String) {
        if (reason != "reload" && (ModuleLifetime.isRetiring || !isEnabled())) return
        val method = registerLifecycleMethod ?: run {
            XposedCompat.logW(
                "[IntlFloatViewStartupDelayHook] restore skipped: register method missing, " +
                    "chain=${chain.logName}, reason=$reason",
            )
            return
        }
        val restoreState = synchronized(lock) {
            val state = chainStates[chain] ?: return
            if (!state.skipped || state.restored) return
            val pending = state.pending ?: return
            state.restored = true
            state.restoring = true
            state.restoreCount++
            state to pending
        }

        val (state, pending) = restoreState
        try {
            method.invoke(pending.application, pending.callback)
            XposedCompat.log(
                "[IntlFloatViewStartupDelayHook] restored lifecycle registration: " +
                    "chain=${state.chain.logName}, reason=$reason, " +
                    "restoreCount=${state.restoreCount}",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                state.restored = false
                state.restoreCount--
            }
            XposedCompat.logW(
                "[IntlFloatViewStartupDelayHook] restore FAILED: " +
                    "chain=${state.chain.logName}, reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            synchronized(lock) {
                state.restoring = false
            }
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlFloatViewStartupDelayed
}
