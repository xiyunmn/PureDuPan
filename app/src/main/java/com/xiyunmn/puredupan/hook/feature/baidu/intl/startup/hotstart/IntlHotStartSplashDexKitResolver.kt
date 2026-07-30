package com.xiyunmn.puredupan.hook.feature.baidu.intl.startup.hotstart

import android.app.Activity
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlHotStartSplashDexKitResolver {
    const val CACHE_ID = "intl_hot_start_splash"

    data class ResolveResult(
        val className: String,
        val methodName: String,
    )

    private val methodBodyHints = listOf(
        "onResume startActivity",
        "onResume disable show activity",
        "returnToYun.isAdFiltered()",
        "SplashManager().limitCountAdShow()",
        "onPauseByScreenLock",
        "hot_start_filter_ad",
        BaiduIntlHookPoints.SPLASH_AD_ACTIVITY,
    )

    private val classMetadataTokens = listOf(
        "AdvertiseHotStartManager",
        "onResume",
        "onPause",
        "onPauseByScreenLock",
        "isSplashAdActivity",
        "BaiduNetDiskModules_Business_Advertise_netdiskRelease",
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val usingStrings: Set<String>,
    )

    fun resolve(cl: ClassLoader): ResolveResult? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateCachedResult(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val methods = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
                bridge.setThreadNum(1)

                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .modifiers(Modifier.STATIC)
                                .returnType(Boolean::class.javaPrimitiveType!!)
                                .paramTypes(Activity::class.java),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                        usingStrings = methodData.usingStrings.toSet(),
                    )
                }
        }

        val rankedCandidates = methods.orEmpty().mapNotNull { methodData ->
                if (!methodData.isHotStartShape()) return@mapNotNull null
                val result = validateCachedResult(
                    cl,
                    DexKitCompat.MethodRef(methodData.className, methodData.methodName),
                ) ?: return@mapNotNull null
                methodData to result
            }
            .sortedWith(
                compareByDescending<Pair<DexMethodCandidate, ResolveResult>> { score(it.first) }
                    .thenBy { it.first.className }
                    .thenBy { it.first.methodName },
            )
        val best = rankedCandidates.firstOrNull()
        val bestScore = best?.let { score(it.first) } ?: 0
        val ambiguousBest = rankedCandidates.drop(1).any { score(it.first) == bestScore }

        if (best != null && bestScore > 0 && !ambiguousBest) {
            XposedCompat.log(
                "[$TAG] resolved ${best.second.className}.${best.second.methodName} " +
                    "score=$bestScore",
            )
            val result = best.second
            cacheResolved(result, "dexkit")
            return result
        }

        DexKitCompat.markTargetScanMiss(
            TAG,
            CACHE_ID,
            "validatedCandidateCount=${rankedCandidates.size}, " +
                "ambiguousBest=$ambiguousBest, bestScore=$bestScore",
        )
        resolveStableFallback(cl)?.let { return it }

        XposedCompat.log("[IntlHotStartSplashDexKitResolver] no candidate matched")
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
        return null
    }

    private fun resolveStableFallback(cl: ClassLoader): ResolveResult? {
        val clazz = XposedCompat.findClassOrNull(BaiduIntlHookPoints.HOT_START_MANAGER_CLASS, cl)
            ?: return null
        if (!metadataContainsAllOrAbsent(clazz, classMetadataTokens)) return null
        val method = clazz.declaredMethods.singleOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.returnType == Boolean::class.javaPrimitiveType &&
                candidate.parameterTypes.contentEquals(arrayOf(Activity::class.java))
        } ?: return null
        val result = ResolveResult(clazz.name, method.name)
        cacheResolved(result, "fallback")
        XposedCompat.logD(
            "[$TAG] resolved stable hot-start entry fallback: ${result.className}.${result.methodName}",
        )
        return result
    }

    private fun cacheResolved(result: ResolveResult, source: String) {
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(result.className, result.methodName),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            CACHE_ID,
            "$source:${result.className}.${result.methodName}",
        )
    }

    private fun validateCachedResult(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): ResolveResult? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!metadataContainsAllOrAbsent(clazz, classMetadataTokens)) return null
        val method = XposedCompat.findMethodOrNull(
            clazz,
            ref.methodName,
            Activity::class.java,
        ) ?: return null
        if (!Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != Boolean::class.javaPrimitiveType) return null
        return ResolveResult(ref.className, ref.methodName)
    }

    private fun DexMethodCandidate.isHotStartShape(): Boolean =
        !isConstructor &&
            returnTypeName == "boolean" &&
            paramTypeNames == listOf("android.app.Activity") &&
            Modifier.isStatic(modifiers)

    private fun score(method: DexMethodCandidate): Int {
        var score = 0
        methodBodyHints.forEachIndexed { index, hint ->
            if (hint in method.usingStrings) {
                score += 100 - index
            }
        }
        return score
    }

    private fun metadataContainsAll(clazz: Class<*>, tokens: Collection<String>): Boolean {
        val metadataTokens = metadataTokens(clazz)
        return tokens.all { token ->
            metadataTokens.any { it == token || it.contains(token) }
        }
    }

    /**
     * Softened metadata gate: strict when @Metadata is present (cn / samsung),
     * but degrades to "pass" when R8 has stripped @Metadata (intl 13.11.9) so
     * the static-boolean(Activity) shape + usingStrings body-hint scoring takes
     * over as the trust anchor.
     */
    private fun metadataContainsAllOrAbsent(clazz: Class<*>, tokens: Collection<String>): Boolean {
        if (metadataTokens(clazz).isEmpty()) return true
        return metadataContainsAll(clazz, tokens)
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

    private const val TAG = "IntlHotStartSplashDexKitResolver"
}
