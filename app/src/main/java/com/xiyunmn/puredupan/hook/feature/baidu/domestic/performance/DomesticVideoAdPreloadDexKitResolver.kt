package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticVideoAdPreloadDexKitResolver {
    const val CACHE_ID = "domestic_video_front_ad_download_v2"

    private const val TAG = "DomesticVideoAdPreloadDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val invokeDescriptors: Set<String>,
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
            DexKitCompat.CachedResult.NotFound -> return resolveFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        if (DexKitCompat.shouldSkipScan(TAG, CACHE_ID)) return resolveFallback(cl)

        val scan = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)

            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(BaiduDomesticDexKitHookPoints.MARS_ADVERTISE_SDK),
                    ),
            ).filter { methodData ->
                methodData.invokes.any { invoke ->
                    invoke.descriptor.contains(
                        BaiduDomesticDexKitHookPoints.DOWNLOAD_VIDEO_FRONT_AD_JOB_DESCRIPTOR,
                    )
                }
            }.map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        } ?: return resolveFallback(cl)

        val candidates = scan
        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isDownloadVideoFrontAdShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] downloadVideoFrontAd unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveFallback(cl)
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved downloadVideoFrontAd: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun resolveFallback(cl: ClassLoader): Method? {
        return resolveHistoricalMethod(cl)?.also { method ->
            DexKitCompat.markTargetSuccess(
                TAG,
                CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveHistoricalMethod(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.ADVERTISE_SDK,
            cl,
        ) ?: return null
        return XposedCompat.findMethodOrNull(
            clazz,
            BaiduDomesticDexKitHookPoints.ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD,
            Context::class.java,
        )?.takeIf { method ->
            method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers)
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
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (
            clazz.name != BaiduDomesticDexKitHookPoints.ADVERTISE_SDK &&
            clazz.name != BaiduDomesticDexKitHookPoints.MARS_ADVERTISE_SDK
        ) {
            return null
        }
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                isDownloadVideoFrontAdMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun DexMethodCandidate.isDownloadVideoFrontAdShape(): Boolean =
        !isConstructor &&
            className == BaiduDomesticDexKitHookPoints.MARS_ADVERTISE_SDK

    private fun isDownloadVideoFrontAdMethod(method: Method): Boolean {
        if (method.declaringClass.name == BaiduDomesticDexKitHookPoints.ADVERTISE_SDK) {
            return !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        return method.declaringClass.name == BaiduDomesticDexKitHookPoints.MARS_ADVERTISE_SDK &&
            method.parameterTypes.size == 1 &&
            Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
            (
                method.returnType == Void.TYPE ||
                    method.returnType.name == "kotlin.Unit"
                )
    }

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
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
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }

}
