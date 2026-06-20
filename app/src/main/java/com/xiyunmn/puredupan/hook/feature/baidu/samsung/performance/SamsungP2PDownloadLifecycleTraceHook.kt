package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Diagnostic-only hook for the Samsung download guard -> p2p process lifecycle.
 */
internal object SamsungP2PDownloadLifecycleTraceHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isPerformanceOptimizeEnabled) {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            installed += hookMainActivity(cl)
            installed += hookGuardService(cl)
            installed += hookProxy(cl)
            installed += hookDownloadService(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] no hooks installed")
            } else {
                XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] hooks INSTALLED: count=$installed")
            }
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookMainActivity(cl: ClassLoader): Int {
        val mainActivityClassName = BaiduFeatureRuntime.currentMainActivityClassName() ?: return 0
        val clazz = XposedCompat.findClassOrNull(mainActivityClassName, cl) ?: run {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] MainActivity class NOT FOUND")
            return 0
        }
        return hookDeclared(
            clazz = clazz,
            methodName = "bindService",
            label = "MainActivity.bindService",
        ) + hookDeclared(
            clazz = clazz,
            methodName = "unBindService",
            label = "MainActivity.unBindService",
            includeStackBefore = true,
        )
    }

    private fun hookGuardService(cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(BaiduSamsungHookPoints.P2P_DOWNLOAD_GUARD_SERVICE, cl) ?: run {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] NetdiskDownloadGuardService class NOT FOUND")
            return 0
        }
        return hookDeclared(clazz, "onCreate", "GuardService.onCreate") +
            hookDeclared(clazz, "onDestroy", "GuardService.onDestroy", includeStackBefore = true) +
            hookDeclared(clazz, "onBind", "GuardService.onBind", Intent::class.java) +
            hookDeclared(
                clazz,
                "onServiceConnected",
                "GuardService.onServiceConnected",
                ComponentName::class.java,
                IBinder::class.java,
            ) +
            hookDeclared(
                clazz,
                "onServiceDisconnected",
                "GuardService.onServiceDisconnected",
                ComponentName::class.java,
                includeStackBefore = true,
            )
    }

    private fun hookProxy(cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(BaiduSamsungHookPoints.P2P_SERVICE_PROXY, cl) ?: run {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] P2PServiceProxy class NOT FOUND")
            return 0
        }
        return hookDeclared(clazz, "startProcess", "P2PServiceProxy.startProcess") +
            hookDeclared(clazz, "startProcessInner", "P2PServiceProxy.startProcessInner") +
            hookDeclared(clazz, "stopProcess", "P2PServiceProxy.stopProcess", includeStackBefore = true) +
            hookDeclared(
                clazz,
                "onServiceConnected",
                "P2PServiceProxy.onServiceConnected",
                ComponentName::class.java,
                IBinder::class.java,
            ) +
            hookDeclared(
                clazz,
                "onServiceDisconnected",
                "P2PServiceProxy.onServiceDisconnected",
                ComponentName::class.java,
                includeStackBefore = true,
            )
    }

    private fun hookDownloadService(cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(BaiduSamsungHookPoints.P2P_DOWNLOAD_SERVICE, cl) ?: run {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] NetdiskDownloadService class NOT FOUND")
            return 0
        }
        return hookDeclared(clazz, "onCreate", "DownloadService.onCreate") +
            hookDeclared(clazz, "onDestroy", "DownloadService.onDestroy", includeStackBefore = true) +
            hookDeclared(clazz, "onBind", "DownloadService.onBind", Intent::class.java)
    }

    private fun hookDeclared(
        clazz: Class<*>,
        methodName: String,
        label: String,
        vararg params: Class<*>,
        includeStackBefore: Boolean = false,
    ): Int {
        val method = XposedCompat.findMethodOrNull(clazz, methodName, *params) ?: run {
            XposedCompat.log("[SamsungP2PDownloadLifecycleTraceHook] ${clazz.name}.$methodName NOT FOUND")
            return 0
        }
        val mod = XposedCompat.module ?: return 0
        mod.hook(method).intercept { chain ->
            logEvent("$label before", includeStackBefore)
            val result = chain.proceed()
            logEvent("$label after")
            result
        }
        return 1
    }

    private fun logEvent(event: String, includeStack: Boolean = false) {
        XposedCompat.logD {
            buildString {
                append("[SamsungP2PDownloadLifecycleTraceHook] ")
                append(event)
                if (includeStack) {
                    append('\n')
                    append(compactStackTrace())
                }
            }
        }
    }

    private fun compactStackTrace(): String {
        return Throwable().stackTrace
            .asSequence()
            .drop(2)
            .filterNot { frame ->
                frame.className.startsWith("com.xiyunmn.puredupan.") ||
                    frame.className.startsWith("io.github.libxposed.") ||
                    frame.className == "android.util.Log"
            }
            .take(14)
            .joinToString(separator = "\n") { frame ->
                "    at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
            }
    }
}
