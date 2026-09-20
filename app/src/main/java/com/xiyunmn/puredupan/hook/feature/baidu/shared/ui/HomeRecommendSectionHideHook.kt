package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Preserve native initialization and preferences; only hide the Home25 recommendation section. */
internal object HomeRecommendSectionHideHook {
    private val installedMethods = mutableSetOf<Method>()

    @Synchronized
    fun hook(cl: ClassLoader): Int {
        if (!isEnabled()) return 0
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        BaiduFeatureRuntime.currentHomeCustomizeHookPoints().feedFragmentClassNames.forEach feedClass@ { name ->
            val clazz = XposedCompat.findClassOrNull(name, cl) ?: return@feedClass
            // These two fields and the native state handlers identify the owning feed fragment.
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
                    mod.hook(method).intercept { chain ->
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

    private fun isEnabled(): Boolean =
        HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeRecommendSectionHidden
}
