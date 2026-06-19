package com.xiyunmn.puredupan.hook

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.DexKitCacheWarmUp
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostProfile
import com.xiyunmn.puredupan.hook.host.HostRegistry
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.xiyunmn.puredupan.hook.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MainHook : XposedModule() {

    private val sAppContext = AtomicReference<Context?>(null)
    private val sAttachHookInstalled = AtomicBoolean(false)
    private val sStaticHooksInstalled = AtomicBoolean(false)
    private val sPostAttachStaticHooksInstalled = AtomicBoolean(false)
    private val activeHost = AtomicReference<HostProfile?>(null)
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
        XposedCompat.log("[MainHook] onPackageLoaded: pkg=${param.packageName}, cl=${param.defaultClassLoader}")

        val host = HostRegistry.resolveByPackageName(param.packageName) ?: return
        XposedCompat.setCurrentPackageName(param.packageName)
        if (!host.isMainProcess(processName)) return
        activeHost.compareAndSet(null, host)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        XposedCompat.log("[MainHook] onPackageReady: pkg=${param.packageName}, process=$processName")

        val host = HostRegistry.resolveByPackageName(param.packageName)
        if (host == null) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target package (${param.packageName})")
            return
        }
        XposedCompat.setCurrentPackageName(param.packageName)
        activeHost.set(host)
        if (!HookInstallPlanner.shouldHandleProcess(host, processName)) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target process ($processName)")
            return
        }
        val cl = param.classLoader
        XposedCompat.log("[MainHook] onPackageReady: using app classloader=$cl")

        handleLoadPackage(host, cl)
    }

    private fun handleLoadPackage(host: HostProfile, cl: ClassLoader) {
        XposedCompat.log("[MainHook] handleLoadPackage: pkg=${host.packageName}, cl=$cl, host=${host.flavor}")
        val staticPlan = HookInstallPlanner.staticPlan(host, processName)
        if (staticPlan.isEmpty()) {
            XposedCompat.logD("[MainHook] static hook plan empty for process=$processName, skip")
        } else if (sStaticHooksInstalled.compareAndSet(false, true)) {
            try {
                XposedCompat.log("[MainHook] initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
                HookInstaller.install(staticPlan, cl)
                XposedCompat.log("[MainHook] All static hooks dispatched.")
            } catch (e: Exception) {
                sStaticHooksInstalled.set(false)
                XposedCompat.log("[MainHook] static hook install FAILED: ${e.message}")
                XposedCompat.log(e)
            } catch (e: Error) {
                sStaticHooksInstalled.set(false)
                XposedCompat.logE("[MainHook] static hook install FATAL ERROR: ${e.message}")
                throw e
            }
        } else {
            XposedCompat.log("[MainHook] static hooks already installed, skip duplicate install")
        }

        if (!HookInstallPlanner.shouldInstallAttachHook(host, processName)) {
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
                        ConfigManager.init(app)
                        XposedCompat.log("[MainHook] > ConfigManager initialized, app=${app.packageName}")
                    }
                }

                if (sPostAttachStaticHooksInstalled.compareAndSet(false, true)) {
                    val settings = ConfigManager.snapshot()
                    HookInstaller.install(
                        HookInstallPlanner.postAttachPlan(
                            host = host,
                            processName = processName,
                            settings = settings,
                        ),
                        cl,
                    )
                    DexKitCacheWarmUp.startIfNeeded(
                        host = host,
                        processName = processName,
                        settings = settings,
                        classLoader = cl,
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

    // Removed: markAttachHookInstalled, markStaticHooksInstalled, markPostAttachStaticHooksInstalled
    // These are now replaced by AtomicBoolean.compareAndSet() inline

}
