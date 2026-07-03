package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticFloatViewStartupDexKitResolver {
    const val CACHE_ID = "domestic_float_view_startup_audio_circle"

    private const val TAG = "DomesticFloatViewStartupDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val invokeDescriptors: Set<String>,
        val usingStrings: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    private data class CandidateScore(
        val score: Int,
        val match: Pair<DexMethodCandidate, Method>,
    )

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolveInitAudioCircleView(cl) != null
    }

    fun resolveInitAudioCircleView(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val scan = DexKitCompat.withBridge(TAG, cl) { bridge ->
            bridge.setThreadNum(1)
            val audioShowBridgeDescriptors = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(Void.TYPE)
                            .paramTypes()
                            .addInvoke(
                                MethodMatcher.create()
                                    .name(BaiduDomesticDexKitHookPoints.AUDIO_API_INIT_AUDIO_CIRCLE_METHOD),
                            ),
                    ),
            ).mapTo(linkedSetOf()) { methodData -> methodData.descriptor }

            val startupTaskClassNames = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(String::class.java)
                            .paramTypes()
                            .usingStrings(BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_NAME),
                    ),
            ).mapTo(linkedSetOf()) { methodData -> methodData.className }

            val startupTaskCandidates = startupTaskClassNames.flatMap { className ->
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .declaredClass(className)
                                .returnType(Void.TYPE)
                                .paramTypes(),
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
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                    usingStrings = methodData.usingStrings.toSet(),
                )
            }

            val candidates = if (startupTaskCandidates.isEmpty()) {
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .returnType(Void.TYPE)
                                .paramTypes()
                                .addInvoke(
                                    MethodMatcher.create()
                                        .name(BaiduDomesticDexKitHookPoints.AUDIO_API_INIT_AUDIO_CIRCLE_METHOD),
                                ),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                        invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                        usingStrings = methodData.usingStrings.toSet(),
                    )
                }
            } else {
                startupTaskCandidates
            }
            audioShowBridgeDescriptors to candidates
        } ?: return resolveFallback(cl)

        val (audioShowBridgeDescriptors, candidates) = scan
        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isStartupTaskVoidMethodShape()) return@mapNotNull null
            if (candidate.invokeDescriptors.intersect(audioShowBridgeDescriptors).isEmpty()) {
                rejected += "${candidate.memberName()} rejected: no audio init bridge invoke"
                return@mapNotNull null
            }
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }

        val best = selectBestMatch(matches)
        if (best == null) {
            val diagnostic = buildDiagnostic(audioShowBridgeDescriptors, candidates, matches, rejected)
            XposedCompat.logW("[$TAG] initAudioCircleView unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveFallback(cl)
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved initAudioCircleView: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun resolveFallback(cl: ClassLoader): Method? {
        return resolveStableMethod(cl)?.also { method ->
            DexKitCompat.markTargetSuccess(
                TAG,
                CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveStableMethod(cl: ClassLoader): Method? {
        val method = validateRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_STABLE_CLASS,
                BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_STABLE_METHOD,
            ),
        )
        if (method != null) {
            XposedCompat.logD("[$TAG] resolved stable initAudioCircleView: ${method.declaringClass.name}.${method.name}")
        }
        return method
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true }
    }

    private fun DexMethodCandidate.isStartupTaskVoidMethodShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames.isEmpty()

    private fun selectBestMatch(
        matches: List<Pair<DexMethodCandidate, Method>>,
    ): Pair<DexMethodCandidate, Method>? {
        if (matches.isEmpty()) return null
        val scored = matches.map { match ->
            CandidateScore(score = scoreCandidate(match.first, match.second), match = match)
        }.sortedByDescending { it.score }
        return scored.firstOrNull()?.match
    }

    private fun scoreCandidate(candidate: DexMethodCandidate, method: Method): Int {
        var score = 0
        when (method.name) {
            BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_STABLE_METHOD -> {
                score += 200
            }
            "run" -> {
                score -= 50
            }
        }
        if (method.name.startsWith("init", ignoreCase = true)) {
            score += 40
        }
        if (method.name.contains("AudioCircle", ignoreCase = true)) {
            score += 30
        }
        if (candidate.className == BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_STABLE_CLASS) {
            score += 20
        }
        if (candidate.invokeDescriptors.size <= 1) {
            score += 10
        }
        return score
    }

    private fun buildDiagnostic(
        audioShowBridgeDescriptors: Set<String>,
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates
            .filter { candidate ->
                candidate.usingStrings.contains(BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_NAME) ||
                    candidate.invokeDescriptors.intersect(audioShowBridgeDescriptors).isNotEmpty()
            }
            .take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} invokes=${candidate.invokeDescriptors.size}"
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
            append("audioShowBridgeCount=").append(audioShowBridgeDescriptors.size).append('\n')
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
