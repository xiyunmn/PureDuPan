package com.xiyunmn.puredupan.hook.feature.performance.intl

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method

internal object IntlOfflinePackageSyncDelayHook {
    private const val DYNAMIC_CONTEXT_CLASS_NAME =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_dynamic.DynamicContext"
    private const val DYNAMIC_PROVIDER_CLASS_NAME = "com.baidu.netdisk.dynamic.main.provider.C_"
    private const val FAST_WEB_VIEW_CLIENT_CLASS_NAME = "com.baidu.netdisk.webview.FastWebViewClient"
    private const val OFFLINE_H5_PACKAGE_ACTIVITY_CLASS_NAME =
        "com.baidu.netdisk.ui.webview.OfflineH5PackageActivity"
    private const val MAIN_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.MainActivity"
    private const val START_SYNC_METHOD_NAME = "startSyncOfflinePackages"
    private const val PROVIDER_START_SYNC_METHOD_NAME = "d"
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private val hookState = HookState()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val lock = Any()

    @Volatile private var startSyncMethod: Method? = null
    @Volatile private var skipped = false
    @Volatile private var restored = false
    @Volatile private var restoring = false
    @Volatile private var skipCount = 0
    @Volatile private var restoreCount = 0
    @Volatile private var homeStableRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlOfflinePackageSyncDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val syncMethod = resolveStartSyncMethod(cl)
            if (syncMethod == null) {
                hookState.reset()
                XposedCompat.log("[IntlOfflinePackageSyncDelayHook] start sync method NOT FOUND")
                return
            }
            startSyncMethod = syncMethod

            mod.hook(syncMethod).intercept { chain ->
                if (!shouldSkipStartupSync()) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    skipped = true
                    skipCount++
                }
                XposedCompat.log(
                    "[IntlOfflinePackageSyncDelayHook] skipped startup H5 offline package sync: " +
                        "${syncMethod.declaringClass.name}.${syncMethod.name}, skipCount=$skipCount",
                )
                null
            }

            val h5HookCount = hookH5RestoreSignals(cl)
            val homeHooked = hookHomeStableRestoreSignal(cl)
            XposedCompat.log(
                "[IntlOfflinePackageSyncDelayHook] hooks INSTALLED: sync=${syncMethod.declaringClass.name}.${syncMethod.name}, " +
                    "h5Signals=$h5HookCount, homeStable=$homeHooked",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlOfflinePackageSyncDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveStartSyncMethod(cl: ClassLoader): Method? {
        val contextClass = XposedCompat.findClassOrNull(DYNAMIC_CONTEXT_CLASS_NAME, cl)
        val contextMethod = contextClass?.let {
            XposedCompat.findMethodOrNull(it, START_SYNC_METHOD_NAME)
        }
        if (contextMethod != null) return contextMethod

        val providerClass = XposedCompat.findClassOrNull(DYNAMIC_PROVIDER_CLASS_NAME, cl)
        return providerClass?.let {
            XposedCompat.findMethodOrNull(it, PROVIDER_START_SYNC_METHOD_NAME)
        }
    }

    private fun shouldSkipStartupSync(): Boolean {
        if (!isEnabled()) return false
        if (restoring || restored) return false
        return true
    }

    private fun hookH5RestoreSignals(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0

        XposedCompat.findClassOrNull(FAST_WEB_VIEW_CLIENT_CLASS_NAME, cl)?.let { fastClientClass ->
            for (constructor in fastClientClass.declaredConstructors) {
                if (constructor.parameterTypes.size < 3) continue
                constructor.isAccessible = true
                mod.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    restoreIfPending("fast_webview_client:${constructor.parameterTypes.joinToString { it.simpleName }}")
                    result
                }
                installed++
            }
        } ?: XposedCompat.log("[IntlOfflinePackageSyncDelayHook] FastWebViewClient class NOT FOUND")

        XposedCompat.findClassOrNull(OFFLINE_H5_PACKAGE_ACTIVITY_CLASS_NAME, cl)?.let { activityClass ->
            listOf("initFragment", "onResume").forEach { methodName ->
                XposedCompat.findMethodOrNull(activityClass, methodName)?.let { method ->
                    mod.hook(method).intercept { chain ->
                        restoreIfPending("offline_h5_activity:$methodName")
                        chain.proceed()
                    }
                    installed++
                }
            }
        } ?: XposedCompat.log("[IntlOfflinePackageSyncDelayHook] OfflineH5PackageActivity class NOT FOUND")

        return installed
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean {
        val mod = XposedCompat.module ?: return false
        val mainActivityClass = XposedCompat.findClassOrNull(MAIN_ACTIVITY_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlOfflinePackageSyncDelayHook] MainActivity class NOT FOUND")
            return false
        }
        val focusMethod = XposedCompat.findMethodOrNull(
            mainActivityClass,
            "onWindowFocusChanged",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[IntlOfflinePackageSyncDelayHook] MainActivity.onWindowFocusChanged NOT FOUND")
            return false
        }

        mod.hook(focusMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
            if (hasFocus && activity?.javaClass?.name == MAIN_ACTIVITY_CLASS_NAME) {
                scheduleHomeStableRestore()
            }
            result
        }
        return true
    }

    private fun scheduleHomeStableRestore() {
        if (!isEnabled() || restored || homeStableRestoreScheduled) return
        synchronized(lock) {
            if (restored || homeStableRestoreScheduled) return
            homeStableRestoreScheduled = true
        }
        mainHandler.postDelayed({
            homeStableRestoreScheduled = false
            restoreIfPending("home_stable")
        }, HOME_STABLE_RESTORE_DELAY_MS)
        XposedCompat.logD("[IntlOfflinePackageSyncDelayHook] home stable restore scheduled")
    }

    private fun restoreIfPending(reason: String) {
        if (!isEnabled()) return
        val method = startSyncMethod ?: run {
            XposedCompat.logW("[IntlOfflinePackageSyncDelayHook] restore skipped: startSyncMethod missing, reason=$reason")
            return
        }

        synchronized(lock) {
            if (!skipped || restored) return
            restored = true
            restoreCount++
        }

        try {
            restoring = true
            method.invoke(null)
            XposedCompat.log(
                "[IntlOfflinePackageSyncDelayHook] restored H5 offline package sync: " +
                    "reason=$reason, restoreCount=$restoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                restored = false
                restoreCount--
            }
            XposedCompat.logW(
                "[IntlOfflinePackageSyncDelayHook] restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            restoring = false
        }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIntlOfflinePackageSyncDelayed
}
