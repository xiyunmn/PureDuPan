package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.membercard

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduMemberCardHeteromoHookPoints
import java.lang.reflect.Method
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * 定位国内版/三星版会员卡 AboutMeTopFragmentHeteromo 类（三渲染入口的宿主）。
 *
 * 用锚点方法 myCardHasOperation 形状 (PopupResponse)->boolean 定位类：命中后取 declaringClass 即为
 * Heteromo 类，setCardText/setCardUi 再在该类内按签名反射取（类内签名唯一）。
 *
 * - 弱/未混淆分支（国内 13.28.9/13.27.8、三星 13.27.8 已核验）：类名与方法名明文，走稳定直连 fallback。
 * - 强混淆分支：类名与私有方法名混淆，但 Kotlin @Metadata d2 保留明文类 token 与方法 token，作为强锚点
 *   （同相册备份栏 9.2 已验证的抗混淆手段）。
 *
 * DexKitCompat 仅缓存方法，故缓存锚点方法 myCardHasOperation 的 MethodRef，declaringClass 即为目标类。
 */
internal object AboutMeTopHeteromoDexKitResolver {
    const val CACHE_ID = "domestic_aboutme_top_heteromo_v1"

    private const val TAG = "AboutMeTopHeteromoDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val HETEROMO_METADATA_TOKENS = listOf(
        BaiduMemberCardHeteromoHookPoints.HETEROMO_METADATA_TOKEN,
        BaiduMemberCardHeteromoHookPoints.SET_CARD_TEXT_METADATA_TOKEN,
        BaiduMemberCardHeteromoHookPoints.SET_CARD_UI_METADATA_TOKEN,
        BaiduMemberCardHeteromoHookPoints.MY_CARD_HAS_OPERATION_METADATA_TOKEN,
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

    /** 返回 AboutMeTopFragmentHeteromo 类，供 hook 反射取三渲染入口方法。 */
    fun resolve(cl: ClassLoader): Class<*>? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value.declaringClass
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create()
                    .matcher(heteromoOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(myCardHasOperationMatcher()),
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
            if (!candidate.isMyCardHasOperationShape()) return@mapNotNull null
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
            XposedCompat.logW("[$TAG] AboutMeTopFragmentHeteromo unresolved: $diagnostic")
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
            "[$TAG] resolved AboutMeTopFragmentHeteromo: ${method.declaringClass.name} " +
                "(anchor ${method.name})",
        )
        return method.declaringClass
    }

    private fun resolveStableFallback(cl: ClassLoader): Class<*>? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduMemberCardHeteromoHookPoints.ABOUT_ME_TOP_FRAGMENT_HETEROMO,
            cl,
        ) ?: return null
        if (!isHeteromoOwner(clazz)) return null
        if (findMyCardHasOperationMethod(clazz) == null) return null
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "fallback:${clazz.name}")
        return clazz
    }

    /** 运营位逻辑门：(PopupResponse)->boolean，类内唯一。 */
    fun findMyCardHasOperationMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduMemberCardHeteromoHookPoints.POPUP_RESPONSE
        }?.apply { isAccessible = true }
    }

    /** setCardText：(CenterConfig)->void，类内唯一（setCardUi$default 为 4 参，不匹配）。 */
    fun findSetCardTextMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduMemberCardHeteromoHookPoints.CENTER_CONFIG
        }?.apply { isAccessible = true }
    }

    /** setCardUi：(CenterConfig, PopupResponse)->void，类内唯一。 */
    fun findSetCardUiMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].name == BaiduMemberCardHeteromoHookPoints.CENTER_CONFIG &&
                method.parameterTypes[1].name == BaiduMemberCardHeteromoHookPoints.POPUP_RESPONSE
        }?.apply { isAccessible = true }
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
        if (!isHeteromoOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduMemberCardHeteromoHookPoints.POPUP_RESPONSE
        }?.apply { isAccessible = true }
    }

    private fun isHeteromoOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduMemberCardHeteromoHookPoints.ABOUT_ME_TOP_FRAGMENT_HETEROMO) return true
        return KotlinMetadataUtils.metadataContainsAll(clazz, HETEROMO_METADATA_TOKENS)
    }

    private fun heteromoOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    HETEROMO_METADATA_TOKENS.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(myCardHasOperationMatcher())
    }

    private fun myCardHasOperationMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Boolean::class.javaPrimitiveType!!)
            .paramTypes(BaiduMemberCardHeteromoHookPoints.POPUP_RESPONSE)
    }

    private fun DexMethodCandidate.isMyCardHasOperationShape(): Boolean =
        !isConstructor &&
            returnTypeName == "boolean" &&
            paramTypeNames.size == 1 &&
            paramTypeNames[0] == BaiduMemberCardHeteromoHookPoints.POPUP_RESPONSE

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
