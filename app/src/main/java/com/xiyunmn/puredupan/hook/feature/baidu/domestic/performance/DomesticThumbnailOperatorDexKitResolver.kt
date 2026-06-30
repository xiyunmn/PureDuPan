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
    const val THUMBNAIL_ADD_JOB_CACHE_ID = "domestic_thumbnail_operator_add_job"

    private const val TAG = "DomesticThumbnailOperatorDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val descriptor: String,
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
        when (val cached = DexKitCompat.getCachedMethod(TAG, CLIENT_COMPUTE_INIT_CACHE_ID) { ref ->
            validateClientComputeInitRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallbackClientComputeInit(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl) { bridge ->
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
                    descriptor = methodData.descriptor,
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
            XposedCompat.logW("[$TAG] client compute init unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CLIENT_COMPUTE_INIT_CACHE_ID, diagnostic)
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
        when (val cached = DexKitCompat.getCachedMethod(TAG, THUMBNAIL_ADD_JOB_CACHE_ID) { ref ->
            validateThumbnailAddJobRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallbackThumbnailAddJob(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL)
                            .modifiers(Modifier.STATIC)
                            .returnType(Void.TYPE)
                            .paramTypes(
                                listOf(
                                    Context::class.java.name,
                                    null,
                                    BaiduDomesticDexKitHookPoints.CONFIG_COMPRESS_IMAGE,
                                    String::class.java.name,
                                ),
                            ),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    descriptor = methodData.descriptor,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    usingStrings = methodData.usingStrings.toSet(),
                )
            }
        } ?: return resolveFallbackThumbnailAddJob(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isThumbnailAddJobShape()) return@mapNotNull null
            val method = validateThumbnailAddJobCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }

        val rankedMatches = matches.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> { thumbnailAddJobScore(it.first) }
                .thenBy { it.first.className }
                .thenBy { it.first.methodName },
        )
        val best = rankedMatches.firstOrNull()
        val bestScore = best?.let { thumbnailAddJobScore(it.first) } ?: 0
        val ambiguousBest = rankedMatches.drop(1).any { thumbnailAddJobScore(it.first) == bestScore }

        if (best == null || ambiguousBest) {
            val diagnostic = buildDiagnostic("thumbnailAddJob", candidates, matches, rejected)
            XposedCompat.logW("[$TAG] thumbnail addJob unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, THUMBNAIL_ADD_JOB_CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, THUMBNAIL_ADD_JOB_CACHE_ID, null)
            return resolveFallbackThumbnailAddJob(cl)
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved thumbnail addJob: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            THUMBNAIL_ADD_JOB_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun resolveFallbackClientComputeInit(cl: ClassLoader): Method? {
        return resolveKnown13_27ClientComputeInit(cl)?.also { method ->
            cacheResolved(CLIENT_COMPUTE_INIT_CACHE_ID, method)
            DexKitCompat.markTargetSuccess(
                TAG,
                CLIENT_COMPUTE_INIT_CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
            XposedCompat.logD(
                "[$TAG] resolved known 13.27 client compute init fallback: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveFallbackThumbnailAddJob(cl: ClassLoader): Method? {
        return resolveKnown13_27ThumbnailAddJob(cl)?.also { method ->
            cacheResolved(THUMBNAIL_ADD_JOB_CACHE_ID, method)
            DexKitCompat.markTargetSuccess(
                TAG,
                THUMBNAIL_ADD_JOB_CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
            XposedCompat.logD(
                "[$TAG] resolved known 13.27 thumbnail addJob fallback: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveKnown13_27ClientComputeInit(cl: ClassLoader): Method? {
        for (className in known13_27ClientComputeManagerClassNames()) {
            val method = validateClientComputeInitRef(
                cl,
                DexKitCompat.MethodRef(
                    className,
                    BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_INIT_13_27_METHOD,
                ),
            )
            if (method != null) return method
        }
        return null
    }

    private fun known13_27ClientComputeManagerClassNames(): List<String> =
        listOf(
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_13_27_CLASS,
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_13_27_JADX_CLASS,
        )

    private fun resolveKnown13_27ThumbnailAddJob(cl: ClassLoader): Method? =
        validateThumbnailAddJobRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL,
                BaiduDomesticDexKitHookPoints.THUMBNAIL_ADD_JOB_13_27_METHOD,
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

    private fun validateThumbnailAddJobCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateThumbnailAddJobRef(
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

    private fun validateThumbnailAddJobRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (clazz.name != BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL) return null
        if (!isThumbnailOperatorUtilClass(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[2].name == BaiduDomesticDexKitHookPoints.CONFIG_COMPRESS_IMAGE &&
                method.parameterTypes[3] == String::class.java
        }?.apply { isAccessible = true }
    }

    private fun isClientComputeManagerClass(clazz: Class<*>): Boolean {
        val tokens = KotlinMetadataUtils.metadataTokens(clazz)
        return BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_METADATA_CLASS in tokens &&
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD in tokens &&
            BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_SERVER_CONFIG_METHOD in tokens
    }

    private fun isThumbnailOperatorUtilClass(clazz: Class<*>): Boolean {
        val tokens = KotlinMetadataUtils.metadataTokens(clazz)
        return BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL_JVM_NAME in tokens ||
            listOf(
                BaiduDomesticDexKitHookPoints.THUMBNAIL_ADD_JOB_PARAM_CONTEXT,
                BaiduDomesticDexKitHookPoints.THUMBNAIL_ADD_JOB_PARAM_COMPRESS_BEAN,
                BaiduDomesticDexKitHookPoints.THUMBNAIL_ADD_JOB_PARAM_CONFIG,
                BaiduDomesticDexKitHookPoints.THUMBNAIL_ADD_JOB_PARAM_UID,
            ).all { token -> token in tokens }
    }

    private fun DexMethodCandidate.isClientComputeInitShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "boolean" &&
            paramTypeNames == listOf(Context::class.java.name)

    private fun DexMethodCandidate.isThumbnailAddJobShape(): Boolean =
        !isConstructor &&
            Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            className == BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL &&
            paramTypeNames.size == 4 &&
            paramTypeNames[0] == Context::class.java.name &&
            paramTypeNames[2] == BaiduDomesticDexKitHookPoints.CONFIG_COMPRESS_IMAGE &&
            paramTypeNames[3] == String::class.java.name

    private fun thumbnailAddJobScore(candidate: DexMethodCandidate): Int {
        var score = 0
        if (candidate.usingStrings.any { it.contains(BaiduDomesticDexKitHookPoints.THUMBNAIL_JOB_NAME_PREFIX) }) {
            score += 20
        }
        if (candidate.methodName == "addJob") score += 10
        return score
    }

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
