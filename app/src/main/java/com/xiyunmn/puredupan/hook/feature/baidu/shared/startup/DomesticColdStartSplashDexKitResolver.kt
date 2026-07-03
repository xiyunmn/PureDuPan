package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticColdStartSplashDexKitResolver {
    const val CACHE_ID = "domestic_cold_start_splash"

    data class ResolveResult(
        val className: String,
        val methodName: String,
    )

    private val knownFallbacks = listOf(
        DexKitCompat.MethodRef(
            BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_STABLE,
            BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_IS_SHOW_SPLASH_STABLE_METHOD,
        ),
    )

    private val methodBodyHints = listOf(
        "filterad",
        "push_type",
        "space_type",
        "AD_LIMIT_COUNT",
        "AD_COLD_DURATION_LIMIT",
        "AD_PIP_MODE",
        "RETURN_TO_YUN",
        "ColdDuration",
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val usingStrings: Set<String>,
        val invokeDescriptors: Set<String>,
    )

    fun warmUpDexKitCache(cl: ClassLoader): Boolean =
        resolve(cl) != null

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
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        }

        val best = methods.orEmpty()
            .asSequence()
            .filter { methodData -> methodData.isColdSplashShape() }
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
        if (Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != Boolean::class.javaPrimitiveType) return null
        return ResolveResult(ref.className, ref.methodName)
    }

    private fun DexMethodCandidate.isColdSplashShape(): Boolean =
        !isConstructor &&
            returnTypeName == "boolean" &&
            paramTypeNames == listOf("android.app.Activity") &&
            !Modifier.isStatic(modifiers)

    private fun score(method: DexMethodCandidate): Int {
        if (!method.invokesSameClassBooleanActivityMethod()) return 0
        var score = 1000
        methodBodyHints.forEachIndexed { index, hint ->
            if (method.usingStrings.any { it.contains(hint) }) {
                score += 100 - index
            }
        }
        return score
    }

    private fun DexMethodCandidate.invokesSameClassBooleanActivityMethod(): Boolean {
        val ownerDescriptor = "L${className.replace('.', '/')};->"
        val selfDescriptor = "$ownerDescriptor$methodName(Landroid/app/Activity;)Z"
        return invokeDescriptors.any { descriptor ->
            descriptor != selfDescriptor &&
                descriptor.startsWith(ownerDescriptor) &&
                descriptor.endsWith("(Landroid/app/Activity;)Z")
        }
    }

    private const val TAG = "DomesticColdStartSplashDexKitResolver"
}
