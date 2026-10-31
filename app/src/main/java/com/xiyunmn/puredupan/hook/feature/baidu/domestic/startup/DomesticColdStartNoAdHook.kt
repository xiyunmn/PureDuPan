package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method

/** 使用宿主无广告启动分支，保留路由、引导和延后初始化。 */
internal object DomesticColdStartNoAdHook {
    private const val TAG = "DomesticColdStartNoAdHook"
    private val policy = ColdStartAdPolicy()
    private val windowPolicy = ColdStartWindowPolicy()
    private val installed = mutableSetOf<Method>()
    private val coldDecision = ThreadLocal<Activity?>()

    @Synchronized
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val mainClass = BaiduFeatureRuntime.currentMainActivityClassName() ?: return
        val entries = listOf(BaiduDomesticHookPoints.NAVIGATE_ACTIVITY, mainClass).map { name ->
            XposedCompat.findMethodOrNull(name, cl, "onCreate", Bundle::class.java) ?: return
        }
        // 首次进入时固定进程模式，方法缺失时沿用原生模式。
        entries.forEach { method ->
            if (method !in installed) {
                mod.hook(method).intercept { chain ->
                    val nativeNoAd = useNativeNoAdMode()
                    (chain.thisObject as? Activity)?.let { activity ->
                        prepareColdStartWindow(activity, chain.args.firstOrNull() != null, nativeNoAd, mainClass)
                    }
                    chain.proceed()
                }
                installed += method
            }
        }

        val mode = DomesticColdStartModeResolver.resolve(cl) ?: return
        val gate = DomesticColdStartSplashResolver.resolve(cl) ?: return
        val entry = XposedCompat.findMethodOrNull(
            BaiduDomesticHookPoints.NAVIGATE_ACTIVITY, cl, BaiduDomesticHookPoints.NAVIGATE_SHOW_FLASH_SCREEN,
        ) ?: return

        if (entry !in installed) {
            mod.hook(entry).intercept { chain ->
                val previous = coldDecision.get()
                coldDecision.set(chain.thisObject as? Activity)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) coldDecision.remove() else coldDecision.set(previous)
                }
            }
            installed += entry
        }
        if (gate !in installed) {
            mod.hook(gate).intercept { chain ->
                val activity = coldDecision.get()
                if (activity != null && chain.args.firstOrNull() === activity && useNativeNoAdMode()) {
                    false
                } else {
                    chain.proceed()
                }
            }
            installed += gate
        }
        if (mode !in installed) {
            mod.hook(mode).intercept { chain ->
                if (useNativeNoAdMode()) false else chain.proceed()
            }
            installed += mode
        }
        // 两个决策入口及其作用域安装完成后标记就绪。
        policy.markReady()
        XposedCompat.log("[$TAG] hooks INSTALLED: native non-parallel mode + scoped cold-ad gate")
    }

    @Suppress("DEPRECATION")
    private fun prepareColdStartWindow(
        activity: Activity,
        restored: Boolean,
        nativeNoAd: Boolean,
        mainClass: String,
    ) {
        runCatching {
            if (activity.javaClass.name == BaiduDomesticHookPoints.NAVIGATE_ACTIVITY) {
                val intent = activity.intent
                val launcher = intent?.action == Intent.ACTION_MAIN &&
                    intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.data == null &&
                    !intent.hasCategory(Intent.CATEGORY_BROWSABLE) &&
                    intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
                windowPolicy.recordNavigate(activity.taskId, launcher, restored, nativeNoAd)
            } else if (activity.javaClass.name == mainClass &&
                windowPolicy.consumeMain(activity.taskId, restored, nativeNoAd)
            ) {
                // 在窗口内容创建前将状态栏设为透明，避免首帧闪黑。
                activity.window.statusBarColor = Color.TRANSPARENT
            }
        }.onFailure { error ->
            XposedCompat.logW("[$TAG] initial status bar setup failed: ${error.message}")
        }
    }

    private fun useNativeNoAdMode(): Boolean = policy.select(HookSettings.isSplashInterstitialBlockEnabled)
}
