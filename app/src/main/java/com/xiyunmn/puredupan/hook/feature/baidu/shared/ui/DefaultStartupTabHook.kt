package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xiyunmn.puredupan.hook.config.model.StartupTab
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupTabHookPoints
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object DefaultStartupTabHook {
    private const val TAG = "DefaultStartupTabHook"
    private val installed = mutableSetOf<Method>()
    private val freshActivities = WeakHashMap<Activity, Boolean>()

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val main = BaiduFeatureRuntime.currentMainActivityClassName()
            ?.let { XposedCompat.findClassOrNull(it, cl) } ?: return
        try {
            val onCreate = main.getDeclaredMethod("onCreate", Bundle::class.java)
            val initTabs = main.getDeclaredMethod("initTabs", Intent::class.java).apply { isAccessible = true }
            val getTabIndex = main.getDeclaredMethod("getTabIndex", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
            val getCheckedId = main.getDeclaredMethod("getCheckedRadioButtonId").apply { isAccessible = true }
            val tabClass = Class.forName(BaiduStartupTabHookPoints.LOTTIE_RADIO_BUTTON, false, cl)
            val labelField = tabClass.getDeclaredField("textView").apply { isAccessible = true }

            if (onCreate !in installed) {
                mod.hook(onCreate).intercept { chain ->
                    freshActivities[chain.thisObject as Activity] = chain.args[0] == null
                    chain.proceed()
                }
                installed += onCreate
            }
            if (initTabs in installed) return
            mod.hook(initTabs).intercept { chain ->
                val activity = chain.thisObject as Activity
                val firstStartup = freshActivities.remove(activity) == true
                val result = chain.proceed()
                runCatching {
                    val containerId = activity.resources.getIdentifier("rg_tabs", "id", activity.packageName)
                    val container = activity.findViewById<ViewGroup>(containerId) ?: return@runCatching
                    // 启动页候选限定为原生页面标签，排除操作按钮。
                    val tabs = (0 until container.childCount).mapNotNull { index ->
                        val view = container.getChildAt(index)
                        if (!tabClass.isInstance(view) || view.visibility != View.VISIBLE || !view.isEnabled) {
                            return@mapNotNull null
                        }
                        val label = (labelField.get(view) as? TextView)?.text?.toString()
                            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        StartupTab(activity.resources.getResourceEntryName(view.id), label) to view
                    }
                    HookSettings.cacheStartupTabs(activity, tabs.map { it.first })
                    val settings = HookSettings.settingsSnapshot()
                    val intent = chain.args[0] as? Intent
                    if (firstStartup && settings.isDefaultStartupTabEnabled &&
                        intent?.hasExtra(BaiduStartupTabHookPoints.TAB_INDEX_KEY) != true &&
                        intent?.data == null && (intent?.action == null || intent.action == Intent.ACTION_MAIN)
                    ) {
                        val target = tabs.singleOrNull { it.first.id == settings.defaultStartupTab }?.second
                        if (target != null && getCheckedId.invoke(activity) != target.id) {
                            val tabIndex = getTabIndex.invoke(activity, target.id) as Int
                            initTabs.invoke(activity, Intent(intent ?: Intent()).putExtra(
                                BaiduStartupTabHookPoints.TAB_INDEX_KEY, tabIndex,
                            ))
                            XposedCompat.logD("[$TAG] startup tab: ${settings.defaultStartupTab}")
                        }
                    }
                }.onFailure { XposedCompat.logW("[$TAG] tab discovery/selection failed: ${it.message}") }
                result
            }
            installed += initTabs
            XposedCompat.log("[$TAG] hooks INSTALLED")
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW("[$TAG] install failed: ${e.message}")
        }
    }
}
