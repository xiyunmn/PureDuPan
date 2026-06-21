package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchPageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlSearchPageCustomizeHook {
    private val hookState = HookState()

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val sameClassVoidNoArgInvokeCount: Int = 0,
        val invokesSearchDefaultContentListener: Boolean = false,
    )

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        if (!HookSettings.isExperimentalDexKitEnabled) {
            XposedCompat.log("[$TAG] skipped: DexKit disabled")
            return
        }
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            if (HookSettings.isSearchPageRecommendHidden) {
                installed += hookSearchRecommend(cl)
            }
            if (HookSettings.isSearchPagePlaceholderHidden) {
                installed += hookSearchPlaceholder(cl)
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] no hooks installed")
                return
            }
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$TAG] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        var resolved = false
        if (resolveQueryAiRecommendMethod(cl) != null) resolved = true
        if (resolveSearchAiRecommendCardMethod(cl) != null) resolved = true
        for ((className, cacheId) in BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds) {
            if (resolvePlaceholderDisplayMethod(cl, className, cacheId) != null) resolved = true
        }
        return resolved
    }

    internal fun warmUpQueryAiRecommendCache(cl: ClassLoader): Boolean =
        resolveQueryAiRecommendMethod(cl) != null

    internal fun warmUpSearchAiRecommendCardCache(cl: ClassLoader): Boolean =
        resolveSearchAiRecommendCardMethod(cl) != null

    internal fun warmUpPlaceholderDisplayCache(cl: ClassLoader, cacheId: String): Boolean {
        val className = BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds
            .firstOrNull { (_, id) -> id == cacheId }
            ?.first
            ?: return false
        return resolvePlaceholderDisplayMethod(cl, className, cacheId) != null
    }

    private fun hookSearchRecommend(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0

        resolveQueryAiRecommendMethod(cl)?.let { method ->
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageRecommendHidden) {
                    XposedCompat.logD("[$TAG] queryAIRecommend blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        } ?: XposedCompat.log("[$TAG] SearchHintVM.queryAIRecommend DexKit target NOT FOUND")

        resolveSearchAiRecommendCardMethod(cl)?.let { method ->
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPageRecommendHidden) {
                    XposedCompat.logD("[$TAG] SearchAIRecommendCard blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            installed++
        } ?: XposedCompat.log("[$TAG] SearchAIRecommendCard NOT FOUND")

        return installed
    }

    private fun hookSearchPlaceholder(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0

        for ((className, cacheId) in BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds) {
            resolvePlaceholderDisplayMethod(cl, className, cacheId)?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPagePlaceholderHidden) {
                        XposedCompat.logD("[$TAG] $className placeholder display blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            } ?: XposedCompat.logD("[$TAG] $className placeholder display DexKit target NOT FOUND")

            installed += hookPlaceholderRegistration(cl, className)
        }

        if (installed == 0) {
            XposedCompat.log("[$TAG] no SearchDefaultContentHelper placeholder hooks installed")
        }
        return installed
    }

    private fun hookPlaceholderRegistration(cl: ClassLoader, className: String): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        val methods = clazz.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name.endsWith(BaiduIntlSearchPageHookPoints.SEARCH_DEFAULT_CONTENT_LISTENER_SUFFIX)
        }
        methods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPagePlaceholderHidden) {
                    XposedCompat.logD("[$TAG] $className placeholder registration blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
        }
        return methods.size
    }

    private fun resolveQueryAiRecommendMethod(cl: ClassLoader): Method? {
        if (!HookSettings.isExperimentalDexKitEnabled) return null

        when (val cached = DexKitCompat.getCachedMethod(TAG, BaiduIntlSearchPageHookPoints.QUERY_AI_RECOMMEND_CACHE_ID) { ref ->
            resolveQueryAiRecommendRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM)
                            .returnType(Void.TYPE)
                            .paramTypes(),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    sameClassVoidNoArgInvokeCount = methodData.invokes.count { invoke ->
                        invoke.className == BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM &&
                            invoke.returnTypeName == "void" &&
                            invoke.paramTypeNames.isEmpty()
                    },
                )
            }
        } ?: return null

        val candidates = scanned.mapNotNull { candidate ->
            if (!candidate.isSearchHintVmVoidNoArg()) return@mapNotNull null
            if (candidate.sameClassVoidNoArgInvokeCount < 3) return@mapNotNull null
            resolveQueryAiRecommendRef(
                cl,
                DexKitCompat.MethodRef(candidate.className, candidate.methodName),
            )
        }

        if (candidates.size != 1) {
            XposedCompat.logW(
                "[$TAG] ambiguous queryAIRecommend DexKit target: " +
                    candidates.joinToString { "${it.declaringClass.name}.${it.name}" },
            )
            DexKitCompat.putCachedMethod(TAG, BaiduIntlSearchPageHookPoints.QUERY_AI_RECOMMEND_CACHE_ID, null)
            return null
        }

        return candidates.single().also { method ->
            XposedCompat.log("[$TAG] resolved queryAIRecommend: ${method.declaringClass.name}.${method.name}")
            DexKitCompat.putCachedMethod(
                TAG,
                BaiduIntlSearchPageHookPoints.QUERY_AI_RECOMMEND_CACHE_ID,
                DexKitCompat.MethodRef(method.declaringClass.name, method.name),
            )
        }
    }

    private fun resolveSearchAiRecommendCardMethod(cl: ClassLoader): Method? {
        if (HookSettings.isExperimentalDexKitEnabled) {
            when (val cached = DexKitCompat.getCachedMethod(
                TAG,
                BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_CARD_CACHE_ID,
            ) { ref ->
                resolveSearchAiRecommendCardRef(cl, ref)
            }) {
                is DexKitCompat.CachedResult.Found -> return cached.value
                DexKitCompat.CachedResult.NotFound -> return null
                DexKitCompat.CachedResult.Miss -> Unit
            }

            val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .declaredClass(BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_KT)
                                .modifiers(Modifier.STATIC)
                                .returnType(Void.TYPE),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                    )
                }
            }

            scanned?.mapNotNull { candidate ->
                if (!candidate.isSearchAiRecommendCardShape()) return@mapNotNull null
                resolveSearchAiRecommendCardRef(
                    cl,
                    DexKitCompat.MethodRef(candidate.className, candidate.methodName),
                )
            }?.singleOrNull()?.also { method ->
                XposedCompat.log("[$TAG] resolved SearchAIRecommendCard: ${method.declaringClass.name}.${method.name}")
                DexKitCompat.putCachedMethod(
                    TAG,
                    BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_CARD_CACHE_ID,
                    DexKitCompat.MethodRef(method.declaringClass.name, method.name),
                )
                return method
            }
        }

        return resolveSearchAiRecommendCardByReflection(cl)
    }

    private fun resolvePlaceholderDisplayMethod(
        cl: ClassLoader,
        className: String,
        cacheId: String,
    ): Method? {
        if (!HookSettings.isExperimentalDexKitEnabled) return null

        when (val cached = DexKitCompat.getCachedMethod(TAG, cacheId) { ref ->
            resolvePlaceholderDisplayRef(cl, ref, className)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(className)
                            .returnType(Void.TYPE)
                            .paramTypes(),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    invokesSearchDefaultContentListener = methodData.invokes.any(::isSearchDefaultContentListenerCall),
                )
            }
        } ?: return null

        val candidates = scanned.mapNotNull { candidate ->
            if (!candidate.isPlaceholderDisplayShape(className)) return@mapNotNull null
            if (!candidate.invokesSearchDefaultContentListener) return@mapNotNull null
            resolvePlaceholderDisplayRef(cl, DexKitCompat.MethodRef(candidate.className, candidate.methodName), className)
        }

        if (candidates.size != 1) {
            XposedCompat.logW(
                "[$TAG] ambiguous placeholder display DexKit target for $className: " +
                    candidates.joinToString { "${it.declaringClass.name}.${it.name}" },
            )
            DexKitCompat.putCachedMethod(TAG, cacheId, null)
            return null
        }

        return candidates.single().also { method ->
            XposedCompat.log("[$TAG] resolved placeholder display: ${method.declaringClass.name}.${method.name}")
            DexKitCompat.putCachedMethod(
                TAG,
                cacheId,
                DexKitCompat.MethodRef(method.declaringClass.name, method.name),
            )
        }
    }

    private fun resolveQueryAiRecommendRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        if (ref.className != BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM) return null
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!metadataContainsAll(clazz, BaiduIntlSearchPageHookPoints.searchHintVmMetadataTokens)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty() &&
                !Modifier.isStatic(method.modifiers)
        }?.apply { isAccessible = true }
    }

    private fun resolveSearchAiRecommendCardRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        if (ref.className != BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_KT) return null
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return findSearchAiRecommendCardMethod(clazz, ref.methodName)
    }

    private fun resolvePlaceholderDisplayRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
        expectedClassName: String,
    ): Method? {
        if (ref.className != expectedClassName) return null
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty() &&
                !Modifier.isStatic(method.modifiers)
        }?.apply { isAccessible = true }
    }

    private fun resolveSearchAiRecommendCardByReflection(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_KT, cl) ?: return null
        return findSearchAiRecommendCardMethod(clazz, expectedName = null)
    }

    private fun findSearchAiRecommendCardMethod(clazz: Class<*>, expectedName: String?): Method? {
        if (!metadataContainsAll(clazz, BaiduIntlSearchPageHookPoints.searchAiRecommendCardMetadataTokens)) {
            XposedCompat.logW("[$TAG] SearchAIRecommendKt metadata signature mismatch")
            return null
        }
        return clazz.declaredMethods.firstOrNull { method ->
            (expectedName == null || method.name == expectedName) &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 9 &&
                method.parameterTypes[0].name == BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM &&
                method.parameterTypes[6].name == BaiduIntlSearchPageHookPoints.COMPOSER &&
                method.parameterTypes[7] == Int::class.javaPrimitiveType &&
                method.parameterTypes[8] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun DexMethodCandidate.isSearchHintVmVoidNoArg(): Boolean =
        className == BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM &&
            !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames.isEmpty() &&
            !Modifier.isStatic(modifiers)

    private fun DexMethodCandidate.isSearchAiRecommendCardShape(): Boolean =
        className == BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_KT &&
            !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames.size == 9 &&
            paramTypeNames[0] == BaiduIntlSearchPageHookPoints.SEARCH_HINT_VM &&
            paramTypeNames[6] == BaiduIntlSearchPageHookPoints.COMPOSER &&
            paramTypeNames[7] == "int" &&
            paramTypeNames[8] == "int" &&
            Modifier.isStatic(modifiers)

    private fun DexMethodCandidate.isPlaceholderDisplayShape(expectedClassName: String): Boolean =
        className == expectedClassName &&
            !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames.isEmpty() &&
            !Modifier.isStatic(modifiers)

    private fun isSearchDefaultContentListenerCall(method: org.luckypray.dexkit.result.MethodData): Boolean {
        return method.className.endsWith(BaiduIntlSearchPageHookPoints.SEARCH_DEFAULT_CONTENT_LISTENER_SUFFIX) &&
            method.paramTypeNames == listOf("java.lang.String") &&
            method.returnTypeName == "void"
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

    private fun isEnabled(): Boolean =
        HookSettings.isSearchPageCustomizeEnabled &&
            (HookSettings.isSearchPagePlaceholderHidden || HookSettings.isSearchPageRecommendHidden)

    private const val TAG = "IntlSearchPageCustomizeHook"
}
