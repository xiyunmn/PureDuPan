package com.xiyunmn.puredupan.hook

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.Constants
import com.xiyunmn.puredupan.hook.core.XposedCompat
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.xiyunmn.puredupan.hook.BuildConfig

class MainHook : XposedModule() {

    @Volatile private var sAppContext: Context? = null
    @Volatile private var sAttachHookInstalled = false
    @Volatile private var sStaticHooksInstalled = false
    @Volatile private var sPostAttachStaticHooksInstalled = false
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

        if (param.packageName != Constants.TARGET_PACKAGE) return
        if (processName != Constants.TARGET_PACKAGE) return
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        XposedCompat.log("[MainHook] onPackageReady: pkg=${param.packageName}, process=$processName")

        if (param.packageName != Constants.TARGET_PACKAGE) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target package (${param.packageName})")
            return
        }
        if (!HookInstallPlanner.shouldHandleProcess(processName)) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target process ($processName)")
            return
        }
        val cl = param.classLoader
        XposedCompat.log("[MainHook] onPackageReady: using app classloader=$cl")

        handleLoadPackage(param.packageName, cl)
    }

    private fun handleLoadPackage(packageName: String, cl: ClassLoader) {
        XposedCompat.log("[MainHook] handleLoadPackage: pkg=$packageName, cl=$cl")
        val staticPlan = HookInstallPlanner.staticPlan(processName)
        if (staticPlan.isEmpty()) {
            XposedCompat.logD("[MainHook] static hook plan empty for process=$processName, skip")
        } else if (markStaticHooksInstalled()) {
            try {
                XposedCompat.log("[MainHook] initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
                HookInstaller.install(staticPlan, cl)
                XposedCompat.log("[MainHook] All static hooks dispatched.")
            } catch (t: Throwable) {
                synchronized(this) { sStaticHooksInstalled = false }
                XposedCompat.log("[MainHook] static hook install FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        } else {
            XposedCompat.log("[MainHook] static hooks already installed, skip duplicate install")
        }

        if (!HookInstallPlanner.shouldInstallAttachHook(processName)) {
            XposedCompat.logD("[MainHook] Application.attach hook skipped for process=$processName")
            return
        }

        if (!markAttachHookInstalled()) {
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

                if (sAppContext == null) {
                    val app = chain.thisObject as? android.app.Application
                    if (app != null) {
                        sAppContext = app
                        ConfigManager.init(app)
                        XposedCompat.log("[MainHook] > ConfigManager initialized, app=${app.packageName}")
                    }
                }

                if (markPostAttachStaticHooksInstalled()) {
                    HookInstaller.install(
                        HookInstallPlanner.postAttachPlan(
                            processName = processName,
                            settings = ConfigManager.snapshot(),
                        ),
                        cl,
                    )
                }

                result
            }
            XposedCompat.log("[MainHook] Application.attach hook INSTALLED")
        } catch (t: Throwable) {
            synchronized(this) { sAttachHookInstalled = false }
            XposedCompat.log("[MainHook] FAILED to hook Application.attach: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun markAttachHookInstalled(): Boolean {
        synchronized(this) {
            if (sAttachHookInstalled) return false
            sAttachHookInstalled = true
            return true
        }
    }

    private fun markStaticHooksInstalled(): Boolean {
        synchronized(this) {
            if (sStaticHooksInstalled) return false
            sStaticHooksInstalled = true
            return true
        }
    }

    private fun markPostAttachStaticHooksInstalled(): Boolean {
        synchronized(this) {
            if (sPostAttachStaticHooksInstalled) return false
            sPostAttachStaticHooksInstalled = true
            return true
        }
    }

}
