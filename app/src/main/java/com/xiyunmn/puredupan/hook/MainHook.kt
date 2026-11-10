package com.xiyunmn.puredupan.hook

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookFileLogger
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCacheWarmUp
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.StartupSplashDrawable
import com.xiyunmn.puredupan.hook.feature.baidu.shared.storage.StorageRedirectHook
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.plan.HookInstaller
import com.xiyunmn.puredupan.hook.runtime.HostLoadRuntime
import com.xiyunmn.puredupan.hook.runtime.HostLoadSession
import com.xiyunmn.puredupan.hook.runtime.HostResourceCleanup
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import com.xiyunmn.puredupan.hook.runtime.ThemeResourceCleanup
import com.xiyunmn.puredupan.hook.ui.settings.SettingsReloadCleanup
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

class MainHook : XposedModule() {
    private val attachHookInstalled = AtomicBoolean(false)
    @Volatile private var application: Application? = null
    @Volatile private var hostClassLoader: ClassLoader? = null
    @Volatile private var initialized = false
    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        initializeEntry(param.processName)
        if (!HostLoadRuntime.handlesProcess(processName)) detach()
    }

    private fun initializeEntry(name: String) {
        XposedCompat.module = this
        processName = name
        XposedCompat.setProcessName(name)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (HostLoadRuntime.resolve(param.packageName) != null) {
            XposedCompat.setCurrentPackageName(param.packageName)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (HostLoadRuntime.installSystemUiIfTarget(
                param.packageName, processName, param.classLoader,
                onApplicationReady = { app ->
                    application = app
                    hostClassLoader = param.classLoader
                    ModuleLifetime.hostClassLoader = param.classLoader
                    initialized = true
                },
            )
        ) return
        val session = HostLoadRuntime.resolve(param.packageName) ?: return
        if (!session.shouldHandleProcess(processName)) return
        XposedCompat.setCurrentPackageName(param.packageName)
        if (!session.shouldInstallAttachHook(processName) ||
            !attachHookInstalled.compareAndSet(false, true)
        ) return
        try {
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
            hook(attach).setId("MainHook:application-attach").intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (!initialized && !ModuleLifetime.isRetiring && app?.packageName == session.packageName) {
                    initializeAttached(app, session, param.classLoader)
                    startWarmUp(session, param.classLoader, immediate = false)
                }
                result
            }
        } catch (error: Exception) {
            attachHookInstalled.set(false)
            XposedCompat.logW("[MainHook] Application.attach install failed: ${error.message}")
        }
    }

    private fun initializeAttached(app: Application, session: HostLoadSession, cl: ClassLoader) {
        application = app
        hostClassLoader = cl
        ModuleLifetime.hostClassLoader = cl
        XposedCompat.setCurrentPackageName(session.packageName)
        HookSettings.initialize(app)
        installPostAttachPlan(session, cl)
        initialized = true
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val app = application ?: return false
        val cl = hostClassLoader ?: return false
        if (!initialized) return false
        return onMain {
            val reason = ModuleLifetime.beginRetirement()
            if (reason != null) {
                reloadLog("deferred: $reason")
                return@onMain false
            }
            val systemUi = app.packageName == HostPackages.SYSTEM_UI
            val views = if (systemUi) arrayListOf<Array<Any?>>() else {
                SettingsReloadCleanup.closeDialogs()
                SettingsReloadCleanup.detachHostViews().also { targets ->
                    targets.addAll(HostResourceCleanup.exportState())
                    HostResourceCleanup.disposeForReload()
                    DexKitCacheWarmUp.disposeForReload()
                }
            }
            val splash = StartupSplashDrawable.exportState()
            val theme = if (systemUi) arrayListOf<Array<Any?>>() else ThemeResourceCleanup.exportState()
            val pathState = if (systemUi) null else StorageRedirectHook.exportPathState()
            ThemeResourceCleanup.disposeForReload(systemUi)
            // 容器及元素均由系统或宿主定义，不把旧代实现交给新代。
            param.setSavedInstanceState(arrayOf(app, cl, app.packageName, views, splash, theme, pathState))
            reloadLog("ready: external references released")
            true
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        initializeEntry(param.processName)
        ModuleLifetime.hotReloaded = true
        val oldHandles = param.oldHookHandles
        onMain {
            try {
                val state = param.savedInstanceState as Array<*>
                val app = state[0] as Application
                val cl = state[1] as ClassLoader
                val packageName = state[2] as String
                check(app.packageName == packageName && HostLoadRuntime.handlesProcess(processName))
                application = app
                hostClassLoader = cl
                ModuleLifetime.hostClassLoader = cl
                XposedCompat.setCurrentPackageName(packageName)
                @Suppress("UNCHECKED_CAST")
                val views = state[3] as List<Array<Any?>>
                @Suppress("UNCHECKED_CAST")
                val splash = state[4] as List<Array<Any?>>
                if (packageName == HostPackages.SYSTEM_UI) {
                    HostLoadRuntime.installSystemUiIfTarget(packageName, processName, cl, app)
                    initialized = true
                } else {
                    DexKitCompat.hotReloaded = true
                    StorageRedirectHook.restorePathState(state[6] as Array<*>)
                    val session = checkNotNull(HostLoadRuntime.resolve(packageName))
                    check(session.shouldHandleProcess(processName))
                    initializeAttached(app, session, cl)
                    SettingsReloadCleanup.restoreHostViews(views, cl)
                    HostResourceCleanup.restoreState(views, cl)
                    @Suppress("UNCHECKED_CAST")
                    ThemeResourceCleanup.restoreState(state[5] as List<Array<Any?>>)
                }
                StartupSplashDrawable.restoreState(splash)
            } finally {
                // 同 ID 替换后的旧句柄已失效，unhook 仅移除本轮剩余旧 Hook。
                oldHandles.forEach { it.unhook() }
            }
            reloadLog("installed: oldHooks=${oldHandles.size}, process=$processName")
            if (application?.packageName != HostPackages.SYSTEM_UI) {
                val session = checkNotNull(HostLoadRuntime.resolve(checkNotNull(application).packageName))
                startWarmUp(session, checkNotNull(hostClassLoader), immediate = true)
            }
        }
    }

    private fun startWarmUp(session: HostLoadSession, cl: ClassLoader, immediate: Boolean) {
        session.startDexKitWarmUp(
            processName = processName,
            settings = HookSettings.settingsSnapshot(),
            classLoader = cl,
            onWarmUpFinished = { installPostAttachPlan(session, cl) },
            immediate = immediate,
        )
    }

    private fun installPostAttachPlan(session: HostLoadSession, cl: ClassLoader) {
        if (ModuleLifetime.isRetiring) return
        HookInstaller.install(session.postAttachPlan(processName, HookSettings.settingsSnapshot()), cl)
    }

    private fun reloadLog(message: String) {
        val context = application ?: return
        HookFileLogger.initialize(context)
        HookFileLogger.write(Log.INFO, "WangPanHook", "[HotReload] $message")
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        Handler(Looper.getMainLooper()).post(task)
        return task.get()
    }
}
