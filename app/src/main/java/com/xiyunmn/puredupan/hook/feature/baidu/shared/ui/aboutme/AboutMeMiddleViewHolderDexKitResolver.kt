package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * Locates BaseMiddleViewHolder.bind(MiddleNode, boolean), the render entry for my-page middle rows.
 */
internal object AboutMeMiddleViewHolderDexKitResolver {
    const val CACHE_ID = "shared_aboutme_middle_view_holder_bind_v1"

    private const val TAG = "AboutMeMiddleViewHolderDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val viewHolderMetadataTokens = listOf(
        BaiduAboutMeHookPoints.BASE_MIDDLE_VIEW_HOLDER_METADATA_TOKEN,
        "bind",
        "setHint",
        "setTitle",
        "MiddleNode",
    )

    private val middleNodeMetadataTokens = listOf(
        BaiduAboutMeHookPoints.MIDDLE_NODE_METADATA_TOKEN,
        "nodeKey",
        "nodeName",
        "nodeHint",
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
                    .matcher(viewHolderOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(bindMatcher()),
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
            if (!candidate.isBindShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == BaiduAboutMeHookPoints.BASE_MIDDLE_VIEW_HOLDER) 1 else 0
            }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] BaseMiddleViewHolder.bind unresolved: $diagnostic")
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
        XposedCompat.log("[$TAG] resolved BaseMiddleViewHolder.bind: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.BASE_MIDDLE_VIEW_HOLDER,
            cl,
        ) ?: return null
        if (!isViewHolderOwner(clazz)) return null
        val method = findBindMethod(clazz) ?: return null
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
        if (!isViewHolderOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isBindMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun findBindMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull(::isBindMethod)
            ?.apply { isAccessible = true }
    }

    private fun isBindMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        if (params.size != 2) return false
        if (params[1] != Boolean::class.javaPrimitiveType) return false
        return isMiddleNodeClass(params[0])
    }

    private fun isViewHolderOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduAboutMeHookPoints.BASE_MIDDLE_VIEW_HOLDER) return true
        return KotlinMetadataUtils.metadataContainsAllOrAbsent(clazz, viewHolderMetadataTokens)
    }

    private fun isMiddleNodeClass(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduAboutMeHookPoints.MIDDLE_NODE) return true
        return KotlinMetadataUtils.metadataContainsAllOrAbsent(clazz, middleNodeMetadataTokens)
    }

    private fun viewHolderOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    viewHolderMetadataTokens.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(bindMatcher())
    }

    private fun bindMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Void.TYPE)
    }

    private fun DexMethodCandidate.isBindShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames.size == 2 &&
            paramTypeNames[1] == "boolean"

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
