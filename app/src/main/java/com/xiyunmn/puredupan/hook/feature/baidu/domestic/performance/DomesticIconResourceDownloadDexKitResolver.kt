package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticIconResourceDownloadDexKitResolver {
    const val CACHE_ID = "domestic_icon_resource_download_start"

    private const val TAG = "DomesticIconResourceDownloadDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5
    private val ownerStringAnchors = setOf(
        "file_icon_data",
        "icon_aggregation",
        "bgicon_aggregation",
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        if (DexKitCompat.shouldSkipScan(TAG, CACHE_ID)) return resolveStableFallback(cl)

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val ownerClassNames = ownerStringAnchors.flatMapTo(linkedSetOf()) { anchor ->
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(MethodMatcher.create().usingStrings(anchor)),
                ).map { methodData -> methodData.className }
            }

            ownerClassNames.flatMap { className ->
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .declaredClass(className)
                                .returnType(Void.TYPE)
                                .paramTypes(
                                    listOf(
                                        BaiduDomesticHookPoints.KOTLIN_FUNCTION2,
                                        BaiduDomesticHookPoints.KOTLIN_FUNCTION2,
                                    ),
                                ),
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
        } ?: return resolveStableFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isStartDownloadShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.first.methodName },
        )

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] startDownload unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveStableFallback(cl)
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved startDownload: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        return validateRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER,
                BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER_START_DOWNLOAD_METHOD,
            ),
        )?.also { method ->
            DexKitCompat.markTargetSuccess(
                TAG,
                CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
        }
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
            rejected += "${candidate.memberName()} rejected: owner/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isIconDownloadManagerOwner(clazz)) return null
        val function2Class = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.KOTLIN_FUNCTION2,
            cl,
        ) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isStartDownloadMethod(method, function2Class)
        }?.apply { isAccessible = true }
    }

    private fun isIconDownloadManagerOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER) return true
        val staticStringValues = clazz.declaredFields.mapNotNullTo(mutableSetOf()) { field ->
            if (!Modifier.isStatic(field.modifiers) || field.type != String::class.java) {
                return@mapNotNullTo null
            }
            runCatching {
                field.isAccessible = true
                field.get(null) as? String
            }.getOrNull()
        }
        return ownerStringAnchors.all { anchor -> anchor in staticStringValues }
    }

    private fun DexMethodCandidate.isStartDownloadShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames == listOf(
                BaiduDomesticHookPoints.KOTLIN_FUNCTION2,
                BaiduDomesticHookPoints.KOTLIN_FUNCTION2,
            )

    private fun isStartDownloadMethod(method: Method, function2Class: Class<*>): Boolean =
        !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == function2Class &&
            method.parameterTypes[1] == function2Class

    private fun buildDiagnostic(
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
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
