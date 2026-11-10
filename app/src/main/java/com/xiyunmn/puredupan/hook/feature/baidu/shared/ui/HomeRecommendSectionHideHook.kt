package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 隐藏首页推荐板块，保留宿主初始化和推荐偏好。 */
internal object HomeRecommendSectionHideHook {
    private val installedMethods = mutableSetOf<Method>()

    @Synchronized
    fun hook(cl: ClassLoader): Int {
        if (!isEnabled()) return 0
        val mod = XposedCompat.module ?: return 0
        var installed = hookExpiredFeedRefresh(cl)
        BaiduFeatureRuntime.currentHomeCustomizeHookPoints().feedFragmentClassNames.forEach feedClass@ { name ->
            val clazz = XposedCompat.findClassOrNull(name, cl) ?: return@feedClass
            // 通过两个视图字段和状态处理方法确认所属首页 Fragment。
            val fields = listOf("feedSettingTipViewHeader", "erroViewHeader").map { fieldName ->
                runCatching { clazz.getDeclaredField(fieldName) }.getOrNull()
                    ?.takeIf { View::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true } ?: return@feedClass
            }
            val getView = runCatching { clazz.getMethod("getView") }.getOrNull() ?: return@feedClass
            clazz.declaredMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && when (method.name) {
                    "initView" -> method.parameterCount == 0
                    BaiduHomeCardHookPoints.HIDE_FEED_LIST_METHOD -> method.parameterCount == 2 &&
                        method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                        method.parameterTypes[1].isEnum
                    "handlePageStatus" -> method.parameterCount == 1 && method.parameterTypes[0].isEnum
                    else -> false
                }
            }.forEach methodLoop@ { method ->
                if (method in installedMethods) return@methodLoop
                runCatching {
                    method.isAccessible = true
                    mod.hook(method).setId("HomeRecommendSectionHideHook:hook:method").intercept { chain ->
                        val result = chain.proceed()
                        if (isEnabled()) {
                            runCatching {
                                val root = getView.invoke(chain.thisObject) as? View
                                if (root != null) {
                                    listOf("stickyNavView", "stickyContentView").forEach { idName ->
                                        val id = root.resources.getIdentifier(idName, "id", root.context.packageName)
                                        if (id != 0) root.findViewById<View>(id)?.visibility = View.GONE
                                    }
                                    fields.forEach { (it.get(chain.thisObject) as? View)?.visibility = View.GONE }
                                }
                            }.onFailure { XposedCompat.logW("[HomeRecommendSectionHideHook] render failed: ${it.message}") }
                        }
                        result
                    }
                    installedMethods += method
                    installed++
                }.onFailure { XposedCompat.logW("[HomeRecommendSectionHideHook] install failed: ${it.message}") }
            }
        }
        return installed
    }

    private fun hookExpiredFeedRefresh(cl: ClassLoader): Int {
        if (BaiduFeatureRuntime.isCurrentIntlHost()) return 0
        val mod = XposedCompat.module ?: return 0
        return runCatching {
            val clazz = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.HOME25_FEED_VIEW_MODEL, cl)
                ?: return 0
            val method = clazz.getDeclaredMethod("onResume").apply { isAccessible = true }
            if (method.returnType != Void.TYPE || Modifier.isStatic(method.modifiers)) return 0
            if (method in installedMethods) return 0
            mod.hook(method).setId("HomeRecommendSectionHideHook:hookExpiredFeedRefresh:method").intercept { chain ->
                // 推荐隐藏时跳过三小时过期刷新，保留手动下拉入口。
                if (isEnabled()) null else chain.proceed()
            }
            installedMethods += method
            XposedCompat.log("[HomeRecommendSectionHideHook] expired feed refresh hook installed")
            1
        }.getOrElse {
            XposedCompat.logW("[HomeRecommendSectionHideHook] expired feed refresh hook failed: ${it.message}")
            0
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeRecommendSectionHidden
}
