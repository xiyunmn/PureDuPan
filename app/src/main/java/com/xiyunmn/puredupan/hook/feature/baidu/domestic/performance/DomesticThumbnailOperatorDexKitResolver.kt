package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticThumbnailOperatorDexKitResolver {
    const val CLIENT_COMPUTE_INIT_CACHE_ID = "domestic_client_compute_manager_init"

    private const val TAG = "DomesticThumbnailOperatorDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val usingStrings: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        val init = resolveClientComputeInit(cl) != null
        val addJob = resolveThumbnailAddJob(cl) != null
        return init || addJob
    }

    fun resolveClientComputeInit(cl: ClassLoader): Method? {
        resolveFallbackClientComputeInit(cl)?.let { return it }
        when (val cached = DexKitCompat.getCachedMethod(TAG, CLIENT_COMPUTE_INIT_CACHE_ID) { ref ->
            validateClientComputeInitRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallbackClientComputeInit(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(
            TAG,
            cl,
            resolverId = CLIENT_COMPUTE_INIT_CACHE_ID,
        ) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(Boolean::class.javaPrimitiveType!!)
                            .paramTypes(Context::class.java),
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
        } ?: return resolveFallbackClientComputeInit(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isClientComputeInitShape()) return@mapNotNull null
            val method = validateClientComputeCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic("clientComputeInit", candidates, matches, rejected)
            DexKitCompat.markTargetScanMiss(TAG, CLIENT_COMPUTE_INIT_CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CLIENT_COMPUTE_INIT_CACHE_ID, null)
            return resolveFallbackClientComputeInit(cl)
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved client compute init: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            CLIENT_COMPUTE_INIT_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    fun resolveThumbnailAddJob(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL, cl)
            ?: return null
        return clazz.declaredMethods.singleOrNull { method ->
            val params = method.parameterTypes
            Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                params.size == 4 && params[0] == Context::class.java &&
                params[2].name == BaiduDomesticDexKitHookPoints.CONFIG_COMPRESS_IMAGE &&
                params[3] == String::class.java
        }?.apply { isAccessible = true }
    }

    private fun resolveFallbackClientComputeInit(cl: ClassLoader): Method? {
        return resolveStableClientComputeInit(cl)?.also { method ->
            cacheResolved(CLIENT_COMPUTE_INIT_CACHE_ID, method)
            DexKitCompat.markTargetSuccess(
                TAG,
                CLIENT_COMPUTE_INIT_CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
            XposedCompat.logD(
                "[$TAG] resolved stable client compute init fallback: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveStableClientComputeInit(cl: ClassLoader): Method? =
        validateClientComputeInitRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_STABLE_CLASS,
                BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD,
            ),
        )

    private fun cacheResolved(resolverId: String, method: Method) {
        DexKitCompat.putCachedMethod(
            TAG,
            resolverId,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
    }

    private fun validateClientComputeCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateClientComputeInitRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateClientComputeInitRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isClientComputeManagerClass(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0])
        }?.apply { isAccessible = true }
    }

    private fun isClientComputeManagerClass(clazz: Class<*>): Boolean {
        val tokens = KotlinMetadataUtils.metadataTokens(clazz)
        return BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_METADATA_CLASS in tokens &&
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD in tokens &&
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_SERVER_CONFIG_METHOD in tokens
    }

    private fun DexMethodCandidate.isClientComputeInitShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "boolean" &&
            paramTypeNames == listOf(Context::class.java.name)

    private fun buildDiagnostic(
        label: String,
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n")
            .ifBlank { "-" }
        return buildString {
            append("target=").append(label).append('\n')
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
