package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSearchPageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SearchPageCustomizeHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SearchPageCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            if (HookSettings.isSearchPageRecommendHidden) {
                installed += hookSearchRecommend(cl)
            }
            if (HookSettings.isSearchPagePlaceholderHidden) {
                installed += hookSearchPlaceholder(cl)
            }
            if (HookSettings.isSearchPageAiEntryHidden) {
                installed += hookAiEntry(cl)
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[SearchPageCustomizeHook] no hooks installed")
                return
            }

            XposedCompat.log("[SearchPageCustomizeHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SearchPageCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookSearchRecommend(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = BaiduSearchPageHookPoints.queryAiRecommendMethodNames
            .firstNotNullOfOrNull { methodName ->
                XposedCompat.findMethodOrNull(
                    BaiduSearchPageHookPoints.SEARCH_HINT_VM,
                    cl,
                    methodName,
                )
            } ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] SearchHintVM.queryAIRecommend() NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageRecommendHidden) {
                XposedCompat.logD("[SearchPageCustomizeHook] queryAIRecommend blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookSearchPlaceholder(cl: ClassLoader): Int {
        var installed = 0
        val mod = XposedCompat.module ?: return 0
        for (className in BaiduSearchPageHookPoints.searchDefaultContentHelperClasses) {
            val method = BaiduSearchPageHookPoints.showSearchPlaceholderMethodNames
                .firstNotNullOfOrNull { methodName ->
                    XposedCompat.findMethodOrNull(className, cl, methodName)
                }
            if (method == null) {
                XposedCompat.logD("[SearchPageCustomizeHook] $className placeholder display method not found")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPagePlaceholderHidden) {
                    XposedCompat.logD("[SearchPageCustomizeHook] $className placeholder display blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        }
        if (installed == 0) {
            XposedCompat.log("[SearchPageCustomizeHook] no SearchDefaultContentHelper.showText hooks installed")
        }
        return installed
    }

    private fun hookAiEntry(cl: ClassLoader): Int {
        var installed = 0
        installed += hookAiSearch25InitData(cl)
        installed += hookAiSearchResultCard(cl)
        return installed
    }

    private fun hookAiSearch25InitData(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            BaiduSearchPageHookPoints.AI_SEARCH_25_VM,
            cl,
            "initData",
            String::class.java,
        ) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] AiSearch25VM.initData(String) NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                XposedCompat.logD("[SearchPageCustomizeHook] AiSearch25VM.initData blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookAiSearchResultCard(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(BaiduSearchPageHookPoints.AI_SEARCH_CARD_KT, cl) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] AiSearchCardKt class NOT FOUND")
            return 0
        }
        val method = findAiSearchCardMethod(clazz) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] AiSearchCardKt.______(...) NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                XposedCompat.logD("[SearchPageCustomizeHook] AiSearchCardKt.______ blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun findAiSearchCardMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == "______" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 10 &&
                method.parameterTypes.firstOrNull() == Boolean::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isSearchPageCustomizeEnabled &&
            (
                HookSettings.isSearchPageAiEntryHidden ||
                    HookSettings.isSearchPagePlaceholderHidden ||
                    HookSettings.isSearchPageRecommendHidden
                )
    }
}
