package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticHotStartSplashDexKitResolver {
    const val CACHE_ID = "domestic_hot_start_splash"

    data class ResolveResult(
        val className: String,
        val methodName: String,
    )

    private val knownFallbacks = listOf(
        DexKitCompat.MethodRef(
            BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_STABLE,
            BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_ON_RESUME_STABLE_METHOD,
        ),
    )

    private val methodBodyHints = listOf(
        "onResume for:",
        "onResume disable show activity",
        "onResume limitCountAdShow",
        "is from xiaomiAnyWhereDor",
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
            DexKitCompat.CachedResult.NotFound -> return resolveKnownFallback(cl)?.also(::cacheResolved)
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

        val best = methods.orEmpty()
            .asSequence()
            .filter { methodData -> methodData.isHotStartShape() }
            .filter { methodData -> score(methodData) > 0 }
            .mapNotNull { methodData ->
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
            .firstOrNull()

        if (best != null) {
            val result = best.second
            cacheResolved(result)
            XposedCompat.log(
                "[$TAG] resolved ${result.className}.${result.methodName} score=${score(best.first)}",
            )
            return result
        }

        resolveKnownFallback(cl)?.let { result ->
            cacheResolved(result)
            XposedCompat.logD("[$TAG] resolved known fallback: ${result.className}.${result.methodName}")
            return result
        }

        if (methods != null) {
            XposedCompat.log("[$TAG] no candidate matched")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
        }
        return null
    }

    private fun resolveKnownFallback(cl: ClassLoader): ResolveResult? {
        for (ref in knownFallbacks) {
            val result = validateCachedResult(cl, ref)
            if (result != null) return result
        }
        return null
    }

    private fun cacheResolved(result: ResolveResult) {
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(result.className, result.methodName),
        )
    }

    private fun validateCachedResult(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): ResolveResult? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
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
            if (method.usingStrings.any { it.contains(hint) }) {
                score += 100 - index
            }
        }
        return score
    }

    private const val TAG = "DomesticHotStartSplashDexKitResolver"
}
