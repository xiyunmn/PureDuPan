package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DownloadPagePromotionAdDexKitResolver {
    const val CACHE_ID = "domestic_download_page_promotion_ad_render_v1"

    private const val TAG = "DownloadPagePromotionAdDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val youaGuideMetadataTokens = listOf(
        "Lcom/baidu/netdisk/ui/youaguide/YouaGuide;",
        "Lcom/baidu/netdisk/ui/transfer/ITransferYoua;",
        "setGuideViewUI",
        "Lcom/baidu/netdisk/cloudimage/ui/state/AlbumGuideResult\$Success;",
        "showYouaGuideView",
        "cannelYouaGuideView",
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
                    .matcher(youaGuideOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(guideRenderMatcher()),
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
            if (!candidate.isGuideRenderShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == BaiduTransferHookPoints.YOUA_GUIDE) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] YouaGuide render method unresolved: $diagnostic")
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
        XposedCompat.log("[$TAG] resolved YouaGuide render method: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduTransferHookPoints.YOUA_GUIDE, cl)
            ?: return null
        if (!isYouaGuideOwner(clazz)) return null
        val method = findGuideRenderMethod(clazz) ?: return null
        DexKitCompat.markTargetSuccess(
            TAG,
            CACHE_ID,
            "fallback:${method.declaringClass.name}.${method.name}",
        )
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

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isYouaGuideOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isGuideRenderMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun findGuideRenderMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull(::isGuideRenderMethod)
            ?.apply { isAccessible = true }
    }

    private fun isYouaGuideOwner(clazz: Class<*>): Boolean {
        if (!clazz.interfaces.any { it.name == BaiduTransferHookPoints.I_TRANSFER_YOUA }) {
            return false
        }
        if (clazz.name == BaiduTransferHookPoints.YOUA_GUIDE) return true
        return KotlinMetadataUtils.metadataContainsAll(clazz, youaGuideMetadataTokens)
    }

    private fun youaGuideOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    youaGuideMetadataTokens.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(guideRenderMatcher())
    }

    private fun guideRenderMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Void.TYPE)
            .paramTypes(BaiduTransferHookPoints.ALBUM_GUIDE_RESULT_SUCCESS)
    }

    private fun DexMethodCandidate.isGuideRenderShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames.size == 1 &&
            paramTypeNames[0] == BaiduTransferHookPoints.ALBUM_GUIDE_RESULT_SUCCESS

    private fun isGuideRenderMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.size == 1 &&
            params[0].name == BaiduTransferHookPoints.ALBUM_GUIDE_RESULT_SUCCESS
    }

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
