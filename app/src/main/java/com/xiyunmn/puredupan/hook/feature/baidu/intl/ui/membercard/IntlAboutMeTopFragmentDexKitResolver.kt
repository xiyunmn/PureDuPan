package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlMemberCardHookPoints
import java.lang.reflect.Method
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * 定位国际版会员卡 AboutMeTopFragment.setCardUi(CenterConfig)（benefit-slot 渲染入口）。
 *
 * setCardUi 内部先设 ivVipImage + 主题，最后调 setCardText 写权益/续费/svip 文案，
 * proceed() 后所有 binding 就绪，是唯一渲染入口。
 *
 * setCardUi 与 setCardText 同签名 (CenterConfig)->void，用「setCardUi 内部 invoke 另一个
 * (CenterConfig)->void（即 setCardText）」判别，抗方法名混淆；类用 Kotlin @Metadata d2 明文锚点定位，
 * 抗类名混淆。弱/未混淆分支走明文类名 + 明文方法名 fallback。
 */
internal object IntlAboutMeTopFragmentDexKitResolver {
    const val CACHE_ID = "intl_aboutme_top_fragment_set_card_ui_v1"

    private const val TAG = "IntlAboutMeTopFragmentDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val FRAGMENT_METADATA_TOKENS = listOf(
        BaiduIntlMemberCardHookPoints.ABOUT_ME_TOP_FRAGMENT_METADATA_TOKEN,
        BaiduIntlMemberCardHookPoints.SET_CARD_TEXT_METADATA_TOKEN,
        BaiduIntlMemberCardHookPoints.SET_CARD_UI_METADATA_TOKEN,
    )

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

    /** 返回 AboutMeTopFragment.setCardUi(CenterConfig) 方法，供 hook 挂渲染入口。 */
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
                    .matcher(fragmentOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(setCardUiMatcher()),
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
                )
            }
        } ?: return resolveStableFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isSetCardUiShape()) return@mapNotNull null
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
            XposedCompat.logW("[$TAG] AboutMeTopFragment.setCardUi unresolved: $diagnostic")
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
        XposedCompat.log(
            "[$TAG] resolved AboutMeTopFragment.setCardUi: " +
                "${method.declaringClass.name}.${method.name}",
        )
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduIntlMemberCardHookPoints.ABOUT_ME_TOP_FRAGMENT,
            cl,
        ) ?: return null
        if (!isFragmentOwner(clazz)) return null
        val method = clazz.declaredMethods.firstOrNull { m ->
            m.name == BaiduIntlMemberCardHookPoints.SET_CARD_UI_METHOD && isSetCardUiMethod(m)
        }?.apply { isAccessible = true } ?: return null
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "fallback:${method.declaringClass.name}.${method.name}")
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
        if (!isFragmentOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isSetCardUiMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isSetCardUiMethod(method: Method): Boolean {
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        if (params.size != 1) return false
        return params[0].name == BaiduIntlMemberCardHookPoints.CENTER_CONFIG
    }

    private fun isFragmentOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduIntlMemberCardHookPoints.ABOUT_ME_TOP_FRAGMENT) return true
        return KotlinMetadataUtils.metadataContainsAllOrAbsent(clazz, FRAGMENT_METADATA_TOKENS)
    }

    private fun fragmentOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    FRAGMENT_METADATA_TOKENS.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(setCardUiMatcher())
    }

    /**
     * setCardUi = (CenterConfig)->void 且内部 invoke 另一个 (CenterConfig)->void（setCardText）。
     * 该 invoke 判别把 setCardUi 与同签名的 setCardText 区分开，抗方法名混淆。
     */
    private fun setCardUiMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Void.TYPE)
            .paramTypes(BaiduIntlMemberCardHookPoints.CENTER_CONFIG)
            .addInvoke(
                MethodMatcher.create()
                    .returnType(Void.TYPE)
                    .paramTypes(BaiduIntlMemberCardHookPoints.CENTER_CONFIG),
            )
    }

    private fun DexMethodCandidate.isSetCardUiShape(): Boolean =
        !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames.size == 1 &&
            paramTypeNames[0] == BaiduIntlMemberCardHookPoints.CENTER_CONFIG &&
            invokeDescriptors.any { it.contains("(L") && it.endsWith(";)V") && it.contains(CENTER_CONFIG_DESCRIPTOR) }

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

    private val CENTER_CONFIG_DESCRIPTOR =
        "L" + BaiduIntlMemberCardHookPoints.CENTER_CONFIG.replace('.', '/') + ";"
}
