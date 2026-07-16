package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher

internal object DownloadPagePromotionAdDexKitResolver {
    const val CACHE_ID = "shared_download_page_promotion_ad_render_v3"

    private const val TAG = "DownloadPagePromotionAdDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val youaGuideMetadataTokens = listOf(
        "Lcom/baidu/netdisk/ui/youaguide/YouaGuide;",
        "Lcom/baidu/netdisk/ui/transfer/ITransferYoua;",
        "setGuideViewUI",
        "Lcom/baidu/netdisk/cloudimage/ui/state/AlbumGuideResult\$Success;",
    )
    private val albumGuideSuccessMetadataTokens = listOf(
        "Lcom/baidu/netdisk/cloudimage/ui/state/AlbumGuideResult\$Success;",
    )

    private data class DexClassCandidate(
        val className: String,
        val modifiers: Int,
    ) {
        fun memberName(): String = className
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
            ).map { classData ->
                DexClassCandidate(
                    className = classData.name,
                    modifiers = classData.modifiers,
                )
            }
        } ?: return resolveStableFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexClassCandidate, Method>> {
                if (it.first.className == BaiduTransferHookPoints.YOUA_GUIDE) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.second.name },
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
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            CACHE_ID,
            "fallback:${method.declaringClass.name}.${method.name}",
        )
        return method
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexClassCandidate,
        rejected: MutableList<String>,
    ): Method? {
        if (Modifier.isInterface(candidate.modifiers) || Modifier.isAbstract(candidate.modifiers)) {
            rejected += "${candidate.memberName()} rejected: not concrete class"
            return null
        }
        val clazz = XposedCompat.findClassOrNull(candidate.className, cl)
        if (clazz == null) {
            rejected += "${candidate.memberName()} rejected: class load failed"
            return null
        }
        if (!isYouaGuideOwner(clazz)) {
            rejected += "${candidate.memberName()} rejected: metadata/interface mismatch"
            return null
        }
        val method = findGuideRenderMethod(clazz)
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: render signature mismatch"
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
    }

    private fun isGuideRenderMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.size == 1 &&
            isAlbumGuideSuccessType(params[0])
    }

    private fun isAlbumGuideSuccessType(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduTransferHookPoints.ALBUM_GUIDE_RESULT_SUCCESS) return true
        return KotlinMetadataUtils.metadataContainsAll(clazz, albumGuideSuccessMetadataTokens)
    }

    private fun buildDiagnostic(
        candidates: List<DexClassCandidate>,
        matches: List<Pair<DexClassCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(5)
            .joinToString("\n") { candidate ->
                candidate.memberName()
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
