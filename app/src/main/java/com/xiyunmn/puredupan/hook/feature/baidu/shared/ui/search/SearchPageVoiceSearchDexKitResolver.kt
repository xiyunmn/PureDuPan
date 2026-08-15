package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSearchPageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object SearchPageVoiceSearchDexKitResolver {
    const val CACHE_ID = "domestic_search_page_voice_search"

    private const val TAG = "SearchPageVoiceSearchDexKitResolver"

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    )

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    fun resolve(cl: ClassLoader): Class<*>? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)?.also {
                cacheResolved(it, "fallback")
            }
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            // VoiceSearch gained a SearchResultVM parameter in newer releases. Keep
            // both narrow signatures so the resolver remains compatible with old
            // and new domestic/Samsung hosts without falling back to broad scans.
            BaiduSearchPageHookPoints.voiceSearchMethodParamTypeNames.flatMap { parameterTypes ->
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .modifiers(Modifier.STATIC)
                                .returnType("void")
                                .paramTypes(*parameterTypes.toTypedArray()),
                        ),
                )
            }.map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                )
            }
        } ?: return resolveStableFallback(cl)?.also { cacheResolved(it, "fallback") }

        val best = candidates
            .asSequence()
            .filter { it.isVoiceSearchShape() }
            .mapNotNull { candidate ->
                val clazz = validateRef(
                    cl,
                    DexKitCompat.MethodRef(candidate.className, candidate.methodName),
                ) ?: return@mapNotNull null
                candidate to clazz
            }
            .sortedWith(
                compareBy<Pair<DexMethodCandidate, Class<*>>> { it.first.className }
                    .thenBy { it.first.methodName },
            )
            .firstOrNull()

        if (best != null) {
            val clazz = best.second
            cacheResolved(clazz, "dexkit")
            XposedCompat.log("[$TAG] resolved voice search screen: ${clazz.name}.${best.first.methodName}")
            return clazz
        }

        resolveStableFallback(cl)?.let { clazz ->
            cacheResolved(clazz, "fallback")
            XposedCompat.logD("[$TAG] resolved stable fallback: ${clazz.name}")
            return clazz
        }

        XposedCompat.log("[$TAG] no candidate matched")
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
        return null
    }

    private fun resolveStableFallback(cl: ClassLoader): Class<*>? {
        val clazz = XposedCompat.findClassOrNull(BaiduSearchPageHookPoints.VOICE_SEARCH_SCREEN_KT, cl)
            ?: return null
        return clazz.takeIf { validateVoiceSearchScreenClass(it) }
    }

    private fun cacheResolved(clazz: Class<*>, source: String) {
        val method = findVoiceSearchMethod(clazz) ?: return
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(clazz.name, method.name),
        )
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "$source:${clazz.name}.${method.name}")
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Class<*>? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val method = findVoiceSearchMethod(clazz) ?: return null
        if (method.name != ref.methodName) return null
        return clazz.takeIf { validateVoiceSearchScreenClass(it) }
    }

    private fun validateVoiceSearchScreenClass(clazz: Class<*>): Boolean {
        val tokens = metadataTokens(clazz)
        return tokens.containsAll(BaiduSearchPageHookPoints.voiceSearchScreenMetadataTokens) &&
            findVoiceSearchMethod(clazz) != null &&
            findVoiceToTextMethod(clazz) != null
    }

    private fun DexMethodCandidate.isVoiceSearchShape(): Boolean {
        return !isConstructor &&
            Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames in BaiduSearchPageHookPoints.voiceSearchMethodParamTypeNames
    }

    private fun findVoiceSearchMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            isStaticVoidMethod(method) &&
                method.parameterTypes.toList().matchesVoiceSearchParams()
        }?.apply { isAccessible = true }
    }

    private fun List<Class<*>>.matchesVoiceSearchParams(): Boolean =
        map { type -> if (type == Int::class.javaPrimitiveType) "int" else type.name } in
            BaiduSearchPageHookPoints.voiceSearchMethodParamTypeNames

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

    private fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }
}
