package com.xiyunmn.puredupan.hook.runtime

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.StartupSplashSettingsSync
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.StartupSplashDrawable
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.ComposeSearchThemeCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.FileFilterThemeCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.FlutterSearchThemeRefresh
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher

/** 在导出原生启动图后解除本代主题资源的所有权。 */
internal object ThemeResourceCleanup {
    fun exportState(): ArrayList<Array<Any?>> = ArrayList<Array<Any?>>().apply {
        addAll(ComposeSearchThemeCompat.exportState())
        addAll(FileFilterThemeCompat.exportState())
        addAll(BaiduSystemNightModeSyncHook.exportState())
    }

    fun restoreState(state: List<Array<Any?>>) {
        if (ModuleLifetime.isRetiring) return
        for (target in state) {
            fun valueAt(index: Int): Any? = (target[index] as? java.lang.ref.WeakReference<*>)?.get()
            when (target[0] as? String) {
                "compose" -> {
                    val activity = valueAt(1) as? android.app.Activity ?: continue
                    ComposeSearchThemeCompat.restoreTarget(activity, valueAt(2) ?: continue)
                }
                "file-filter" -> FileFilterThemeCompat.restoreTarget(valueAt(1) ?: continue)
                "night-owner" -> BaiduSystemNightModeSyncHook.restoreOwner(target)
            }
        }
    }

    fun disposeForReload(failures: HotReloadFailures, systemUi: Boolean = false) {
        failures.run("StartupSplashSettingsSync") { StartupSplashSettingsSync.disposeForReload() }
        failures.run("StartupSplashDrawable") { StartupSplashDrawable.disposeForReload() }
        if (systemUi) return
        failures.run("ConfigManager") { ConfigManager.disposeForReload() }
        failures.run("BaiduSystemNightModeSyncHook") { BaiduSystemNightModeSyncHook.disposeAllForReload() }
        failures.run("ComposeSearchThemeCompat") { ComposeSearchThemeCompat.disposeForReload() }
        failures.run("FileFilterThemeCompat") { FileFilterThemeCompat.disposeForReload() }
        failures.run("FlutterSearchThemeRefresh") { FlutterSearchThemeRefresh.disposeForReload() }
        failures.run("HostThemeChangeDispatcher") { HostThemeChangeDispatcher.disposeForReload() }
    }
}
