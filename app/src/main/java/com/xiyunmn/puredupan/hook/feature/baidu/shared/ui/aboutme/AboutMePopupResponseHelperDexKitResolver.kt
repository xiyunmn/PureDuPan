package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.lang.reflect.Method
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * Locates PopupResponseHelper.refresh(PopupResponse) across weak/strong obfuscation.
 */
internal object AboutMePopupResponseHelperDexKitResolver {
    const val CACHE_ID = "shared_aboutme_popup_response_helper_refresh_v1"

    private const val TAG = "AboutMePopupResponseHelperDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val METADATA_TOKENS = listOf(
        BaiduAboutMeHookPoints.POPUP_RESPONSE_HELPER_METADATA_TOKEN,
        BaiduAboutMeHookPoints.POPUP_RESPONSE_HELPER_REFRESH_METADATA_TOKEN,
        BaiduAboutMeHookPoints.POPUP_RESPONSE_HELPER_LAST_RESPONSE_METADATA_TOKEN,
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

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create()
                    .matcher(helperOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(refreshMatcher()),
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
            if (!candidate.isRefreshShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.isBridgeOrSynthetic()) 0 else 1
            }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] PopupResponseHelper.refresh unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveStableFallback(cl)
        }

        val method = best.second
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        XposedCompat.log("[$TAG] resolved PopupResponseHelper.refresh: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.POPUP_RESPONSE_HELPER,
            cl,
        ) ?: return null
        if (!isHelperOwner(clazz)) return null
        val method = findRefreshMethod(clazz) ?: return null
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "fallback:${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateRef(cl, DexKitCompat.MethodRef(candidate.className, candidate.methodName))
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isHelperOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isRefreshMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun findRefreshMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            isRefreshMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isRefreshMethod(method: Method): Boolean {
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        if (params.size != 1) return false
        val popupResponseClassName = BaiduFeatureRuntime.currentPopupResponseClassName()
            ?: BaiduAboutMeHookPoints.POPUP_RESPONSE
        return params[0].name == popupResponseClassName || params[0].name == BaiduAboutMeHookPoints.POPUP_RESPONSE
    }

    private fun isHelperOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduAboutMeHookPoints.POPUP_RESPONSE_HELPER) return true
        return KotlinMetadataUtils.metadataContainsAll(clazz, METADATA_TOKENS)
    }

    private fun helperOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    METADATA_TOKENS.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(refreshMatcher())
    }

    private fun refreshMatcher(): MethodMatcher {
        val popupResponseClassName = BaiduFeatureRuntime.currentPopupResponseClassName()
            ?: BaiduAboutMeHookPoints.POPUP_RESPONSE
        return MethodMatcher.create()
            .returnType(Void.TYPE)
            .paramTypes(popupResponseClassName)
    }

    private fun DexMethodCandidate.isRefreshShape(): Boolean =
        !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames.size == 1 &&
            (paramTypeNames[0] == BaiduAboutMeHookPoints.POPUP_RESPONSE ||
                paramTypeNames[0] == BaiduFeatureRuntime.currentPopupResponseClassName())

    private fun DexMethodCandidate.isBridgeOrSynthetic(): Boolean =
        (modifiers and 0x40) != 0 || (modifiers and 0x1000) != 0

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(5)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(5)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(5).joinToString("\n").ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
