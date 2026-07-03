package com.xiyunmn.puredupan.hook

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.plan.HookInstaller
import com.xiyunmn.puredupan.hook.runtime.HostLoadRuntime
import com.xiyunmn.puredupan.hook.runtime.HostLoadSession
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MainHook : XposedModule() {

    private val sAppContext = AtomicReference<Context?>(null)
    private val sAttachHookInstalled = AtomicBoolean(false)
    private val sPostAttachStaticHooksInstalled = AtomicBoolean(false)
    private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedCompat.module = this
        processName = param.processName
        XposedCompat.setProcessName(param.processName)
        XposedCompat.log("[MainHook] onModuleLoaded: process=${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        XposedCompat.log("[MainHook] onPackageLoaded: pkg=${param.packageName}")

        HostLoadRuntime.resolve(param.packageName) ?: return
        XposedCompat.setCurrentPackageName(param.packageName)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        XposedCompat.log("[MainHook] onPackageReady: pkg=${param.packageName}, process=$processName")

        val hostSession = HostLoadRuntime.resolve(param.packageName)
        if (hostSession == null) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target package (${param.packageName})")
            return
        }
        XposedCompat.setCurrentPackageName(param.packageName)
        if (!hostSession.shouldHandleProcess(processName)) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target process ($processName)")
            return
        }
        val cl = param.classLoader
        XposedCompat.log("[MainHook] onPackageReady: using app classloader=$cl")

        handleLoadPackage(hostSession, cl)
    }

    private fun handleLoadPackage(hostSession: HostLoadSession, cl: ClassLoader) {
        XposedCompat.log(
            "[MainHook] handleLoadPackage: pkg=${hostSession.packageName}, cl=$cl, host=${hostSession.hostId}",
        )
        if (!hostSession.shouldInstallAttachHook(processName)) {
            XposedCompat.logD("[MainHook] Application.attach hook skipped for process=$processName")
            return
        }

        if (!sAttachHookInstalled.compareAndSet(false, true)) {
            XposedCompat.log("[MainHook] Application.attach hook already installed, skip")
            return
        }

        try {
            val attachMethod = android.app.Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
            attachMethod.isAccessible = true
            XposedCompat.log("[MainHook] Application.attach method found, installing hook...")

            hook(attachMethod).intercept { chain ->
                XposedCompat.log("[MainHook] > Application.attach INTERCEPTED, thisObj=${chain.thisObject?.javaClass?.name}")
                val result = chain.proceed()
                XposedCompat.log("[MainHook] > Application.attach proceed() returned")

                if (sAppContext.get() == null) {
                    val app = chain.thisObject as? android.app.Application
                    if (app != null) {
                        sAppContext.set(app)
                        HookSettings.initialize(app)
                        XposedCompat.log("[MainHook] > settings initialized, app=${app.packageName}")
                    }
                }

                if (sPostAttachStaticHooksInstalled.compareAndSet(false, true)) {
                    installPostAttachPlan(hostSession, cl, "initial")
                    hostSession.startDexKitWarmUp(
                        processName = processName,
                        settings = HookSettings.settingsSnapshot(),
                        classLoader = cl,
                        onWarmUpFinished = {
                            installPostAttachPlan(hostSession, cl, "dexkit-warm-up")
                        },
                    )
                }

                result
            }
            XposedCompat.log("[MainHook] Application.attach hook INSTALLED")
        } catch (e: Exception) {
            sAttachHookInstalled.set(false)
            XposedCompat.log("[MainHook] FAILED to hook Application.attach: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Error) {
            sAttachHookInstalled.set(false)
            XposedCompat.logE("[MainHook] FATAL ERROR in Application.attach hook: ${e.message}")
            throw e
        }
    }

    private fun installPostAttachPlan(hostSession: HostLoadSession, cl: ClassLoader, reason: String) {
        val settings = HookSettings.settingsSnapshot()
        XposedCompat.logD("[MainHook] installing postAttach plan: reason=$reason, process=$processName")
        HookInstaller.install(
            hostSession.postAttachPlan(processName, settings),
            cl,
        )
    }

}
