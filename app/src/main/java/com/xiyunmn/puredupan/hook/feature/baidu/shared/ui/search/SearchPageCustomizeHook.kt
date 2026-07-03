package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSearchPageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SearchPageCustomizeHook {
    private val staticHookState = HookState()
    private val voiceSearchHookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SearchPageCustomizeHook] skipped: config disabled")
            return
        }

        try {
            var installed = 0
            if (isStaticSearchCustomizeEnabled() && staticHookState.markInstalled()) {
                var staticInstalled = 0
                if (HookSettings.isSearchPageRecommendHidden) {
                    staticInstalled += hookSearchRecommend(cl)
                }
                if (HookSettings.isSearchPagePlaceholderHidden) {
                    staticInstalled += hookSearchPlaceholder(cl)
                }
                if (HookSettings.isSearchPageAiEntryHidden) {
                    staticInstalled += hookAiEntry(cl)
                }
                if (staticInstalled == 0) {
                    staticHookState.reset()
                }
                installed += staticInstalled
            }
            if (HookSettings.isSearchPageVoiceSearchHidden) {
                installed += hookVoiceSearch(cl)
            }
            if (installed == 0) {
                XposedCompat.logD("[SearchPageCustomizeHook] no new hooks installed")
                return
            }

            XposedCompat.log("[SearchPageCustomizeHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            staticHookState.reset()
            voiceSearchHookState.reset()
            XposedCompat.log("[SearchPageCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookSearchRecommend(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        val method = XposedCompat.findMethodOrNull(
            BaiduSearchPageHookPoints.SEARCH_HINT_VM,
            cl,
            BaiduSearchPageHookPoints.QUERY_AI_RECOMMEND_METHOD,
        ) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] SearchHintVM.queryAIRecommend() NOT FOUND")
            null
        }

        if (method != null) {
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageRecommendHidden) {
                    XposedCompat.logD("[SearchPageCustomizeHook] queryAIRecommend blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        }

        installed += hookSearchRecommendCard(cl)
        return installed
    }

    private fun hookSearchPlaceholder(cl: ClassLoader): Int {
        var installed = 0
        val mod = XposedCompat.module ?: return 0
        for (className in BaiduSearchPageHookPoints.searchDefaultContentHelperClasses) {
            val clazz = XposedCompat.findClassOrNull(className, cl)
            if (clazz == null) {
                XposedCompat.logD("[SearchPageCustomizeHook] $className placeholder helper class not found")
                continue
            }
            val methods = findSearchPlaceholderDisplayMethods(clazz)
            if (methods.isEmpty()) {
                XposedCompat.logD("[SearchPageCustomizeHook] $className placeholder display method not found")
                continue
            }
            methods.forEach { method ->
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPagePlaceholderHidden) {
                        XposedCompat.logD(
                            "[SearchPageCustomizeHook] $className.${method.name} placeholder display blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }
        }
        if (installed == 0) {
            XposedCompat.log("[SearchPageCustomizeHook] no SearchDefaultContentHelper placeholder display hooks installed")
        }
        return installed
    }

    private fun findSearchPlaceholderDisplayMethods(clazz: Class<*>): List<Method> {
        return BaiduSearchPageHookPoints.showSearchPlaceholderMethods.mapNotNull { methodName ->
            clazz.declaredMethods.firstOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty() &&
                    !Modifier.isStatic(method.modifiers)
            }?.apply { isAccessible = true }
        }.distinct()
    }

    private fun hookVoiceSearch(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        if (!voiceSearchHookState.markInstalled()) return 0
        val clazz = resolveVoiceSearchScreenClass(cl) ?: run {
            voiceSearchHookState.reset()
            XposedCompat.log("[SearchPageCustomizeHook] VoiceSearchScreenKt metadata class NOT FOUND")
            return 0
        }
        val methods = listOfNotNull(
            findVoiceSearchMethod(clazz),
            findVoiceToTextMethod(clazz),
        ).distinct()
        if (methods.isEmpty()) {
            voiceSearchHookState.reset()
            XposedCompat.log("[SearchPageCustomizeHook] VoiceSearchScreenKt voice compose methods NOT FOUND")
            return 0
        }

        methods.forEach { method ->
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageVoiceSearchHidden) {
                    XposedCompat.logD(
                        "[SearchPageCustomizeHook] ${method.declaringClass.name}.${method.name} voice search blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
        }
        return methods.size
    }

    private fun resolveVoiceSearchScreenClass(cl: ClassLoader): Class<*>? {
        return SearchPageVoiceSearchDexKitResolver.resolve(cl)
    }

    private fun findVoiceSearchMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            isStaticVoidMethod(method) &&
                method.parameterTypes.size == 5 &&
                method.parameterTypes[0].name == BaiduSearchPageHookPoints.MAIN_SEARCH_VM &&
                method.parameterTypes[1].name == BaiduSearchPageHookPoints.SEARCH_OPERATION_SERVICE_PLATFORM &&
                method.parameterTypes[2].name == BaiduSearchPageHookPoints.COMPOSER &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                method.parameterTypes[4] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun findVoiceToTextMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            isStaticVoidMethod(method) &&
                method.parameterTypes.size == 5 &&
                method.parameterTypes[0].name == BaiduSearchPageHookPoints.MAIN_SEARCH_VM &&
                method.parameterTypes[1].name == BaiduSearchPageHookPoints.FUNCTION0 &&
                method.parameterTypes[2].name == BaiduSearchPageHookPoints.COMPOSER &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                method.parameterTypes[4] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun isStaticVoidMethod(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
    }

    private fun hookAiEntry(cl: ClassLoader): Int {
        var installed = 0
        installed += hookAiSearch25InitData(cl)
        installed += hookAiSearchResultCard(cl)
        installed += hookComposeServiceMethod(
            cl = cl,
            methodName = BaiduSearchPageHookPoints.ANDROID_CREATE_AI_SEARCH_25_PAGE_METHOD,
            logName = "AndroidCreateAiSearch25Page",
        )
        installed += hookComposeServiceMethod(
            cl = cl,
            methodName = BaiduSearchPageHookPoints.ANDROID_AI_SEARCH_CARD_WEB_VIEW_METHOD,
            logName = "AndroidAiSearchCardWebView",
        )
        installed += hookMainPreSearchAiPowerTab(cl)
        return installed
    }

    private fun hookMainPreSearchAiPowerTab(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val vmClass = XposedCompat.findClassOrNull(BaiduSearchPageHookPoints.MAIN_PRE_SEARCH_TAB_VM, cl) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] MainPreSearchTabVM class NOT FOUND")
            return 0
        }
        if (!metadataContainsAll(
                vmClass,
                listOf(
                    "MainPreSearchTabVM",
                    BaiduSearchPageHookPoints.UPDATE_TAB_VISIBILITY_METHOD,
                    BaiduSearchPageHookPoints.SHOW_GUIDE_BUBBLE_METHOD,
                    BaiduSearchPageHookPoints.HIDE_GUIDE_BUBBLE_METHOD,
                ),
            )
        ) {
            XposedCompat.logW("[SearchPageCustomizeHook] MainPreSearchTabVM metadata signature mismatch")
            return 0
        }
        val tabClass = XposedCompat.findClassOrNull(BaiduSearchPageHookPoints.PRE_SEARCH_TAB, cl) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] PreSearchTab class NOT FOUND")
            return 0
        }
        val aiPowerSearchTab = runCatching {
            tabClass.getDeclaredField(BaiduSearchPageHookPoints.AI_POWER_SEARCH_TAB_NAME).apply {
                isAccessible = true
            }.get(null)
        }.getOrNull() ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] PreSearchTab.AI_POWER_SEARCH NOT FOUND")
            return 0
        }

        var installed = 0
        for (constructor in vmClass.declaredConstructors) {
            constructor.isAccessible = true
            mod.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                    suppressAiPowerSearchTab(chain.thisObject, aiPowerSearchTab)
                    XposedCompat.logD("[SearchPageCustomizeHook] MainPreSearchTabVM initial AI tab hidden")
                }
                result
            }
            installed++
        }

        XposedCompat.findMethodOrNull(
            vmClass,
            BaiduSearchPageHookPoints.UPDATE_TAB_VISIBILITY_METHOD,
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                    val args = chain.args.toTypedArray()
                    if (args.firstOrNull() == true) {
                        args[0] = false
                        XposedCompat.logD("[SearchPageCustomizeHook] MainPreSearchTabVM.updateTabVisibility forced false")
                    }
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }
            installed++
        } ?: XposedCompat.log("[SearchPageCustomizeHook] MainPreSearchTabVM.updateTabVisibility(Boolean) NOT FOUND")

        XposedCompat.findMethodOrNull(
            vmClass,
            BaiduSearchPageHookPoints.SHOW_GUIDE_BUBBLE_METHOD,
            tabClass,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                if (
                    HookSettings.isSearchPageCustomizeEnabled &&
                    HookSettings.isSearchPageAiEntryHidden &&
                    chain.args.firstOrNull() == aiPowerSearchTab
                ) {
                    XposedCompat.logD("[SearchPageCustomizeHook] MainPreSearchTabVM.showGuideBubble AI tab blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        } ?: XposedCompat.log("[SearchPageCustomizeHook] MainPreSearchTabVM.showGuideBubble(PreSearchTab) NOT FOUND")

        return installed
    }

    private fun hookSearchRecommendCard(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(BaiduSearchPageHookPoints.SEARCH_AI_RECOMMEND_KT, cl) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] SearchAIRecommendKt class NOT FOUND")
            return 0
        }
        val method = findSearchAiRecommendCardMethod(clazz) ?: run {
            XposedCompat.log("[SearchPageCustomizeHook] SearchAIRecommendKt.SearchAIRecommendCard(...) NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageRecommendHidden) {
                XposedCompat.logD("[SearchPageCustomizeHook] SearchAIRecommendCard blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
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
            XposedCompat.log("[SearchPageCustomizeHook] AiSearchCardKt.AiSearchCard(...) NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                XposedCompat.logD("[SearchPageCustomizeHook] AiSearchCardKt.AiSearchCard blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun findAiSearchCardMethod(clazz: Class<*>): Method? {
        if (!metadataContainsAll(clazz, listOf("AiSearchCard", "MAIN_SEARCH_AI_SEARCH_GLOBAL"))) {
            XposedCompat.logW("[SearchPageCustomizeHook] AiSearchCardKt metadata signature mismatch")
            return null
        }
        return clazz.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 10 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.parameterTypes[3].name == BaiduSearchPageHookPoints.MAIN_SEARCH_VM &&
                method.parameterTypes[4].name == BaiduSearchPageHookPoints.SEARCH_RESULT_VM &&
                method.parameterTypes[5].name == BaiduSearchPageHookPoints.AI_SEARCH_VM &&
                method.parameterTypes[7].name == BaiduSearchPageHookPoints.COMPOSER &&
                method.parameterTypes[8] == Int::class.javaPrimitiveType &&
                method.parameterTypes[9] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun hookComposeServiceMethod(
        cl: ClassLoader,
        methodName: String,
        logName: String,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        for (className in BaiduSearchPageHookPoints.composeServicePlatformClasses) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: continue
            val methods = clazz.declaredMethods.filter { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    !Modifier.isStatic(method.modifiers)
            }
            if (methods.isEmpty()) {
                XposedCompat.logD("[SearchPageCustomizeHook] $className.$logName NOT FOUND")
                continue
            }
            methods.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageAiEntryHidden) {
                        XposedCompat.logD("[SearchPageCustomizeHook] $className.$logName blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }
        }
        return installed
    }

    private fun findSearchAiRecommendCardMethod(clazz: Class<*>): Method? {
        if (!metadataContainsAll(clazz, listOf("SearchAIRecommendCard", "AIRecommendResult"))) {
            XposedCompat.logW("[SearchPageCustomizeHook] SearchAIRecommendKt metadata signature mismatch")
            return null
        }
        return clazz.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 9 &&
                method.parameterTypes[0].name == BaiduSearchPageHookPoints.SEARCH_HINT_VM &&
                method.parameterTypes[6].name == BaiduSearchPageHookPoints.COMPOSER &&
                method.parameterTypes[7] == Int::class.javaPrimitiveType &&
                method.parameterTypes[8] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun suppressAiPowerSearchTab(vm: Any?, aiPowerSearchTab: Any) {
        if (vm == null) return
        runCatching {
            XposedCompat.callMethod(vm, BaiduSearchPageHookPoints.UPDATE_TAB_VISIBILITY_METHOD, false)
        }.onFailure {
            XposedCompat.logW("[SearchPageCustomizeHook] updateTabVisibility(false) failed: ${it.message}")
        }
        runCatching {
            XposedCompat.callMethod(vm, BaiduSearchPageHookPoints.HIDE_GUIDE_BUBBLE_METHOD, aiPowerSearchTab)
        }.onFailure {
            XposedCompat.logW("[SearchPageCustomizeHook] hideGuideBubble(AI_POWER_SEARCH) failed: ${it.message}")
        }
    }

    private fun metadataContainsAll(clazz: Class<*>, tokens: Collection<String>): Boolean {
        val metadataTokens = metadataTokens(clazz)
        return tokens.all { token ->
            metadataTokens.any { it == token || it.contains(token) }
        }
    }

    private fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isSearchPageCustomizeEnabled &&
            (
                isStaticSearchCustomizeEnabled() ||
                    HookSettings.isSearchPageVoiceSearchHidden
                )
    }

    private fun isStaticSearchCustomizeEnabled(): Boolean {
        return HookSettings.isSearchPageAiEntryHidden ||
            HookSettings.isSearchPagePlaceholderHidden ||
            HookSettings.isSearchPageRecommendHidden
    }
}
