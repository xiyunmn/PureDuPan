package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Map
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object FilePageSafetyFooterUseCaseDexKitResolver {
    const val CACHE_ID = "shared_file_page_safety_footer_use_case_v1"

    private const val TAG = "FilePageSafetyFooterUseCaseDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"

    private val stableClassCandidates = listOf(
        BaiduFilePageHookPoints.SHOW_SAFETY_FOOTER_USE_CASE,
        BaiduFilePageHookPoints.SHOW_SAFETY_FOOTER_USE_CASE_STRONG_SAMPLE,
    )

    private val metadataTokens = listOf(
        "ShowSafetyFooterUseCase",
        "BaseUseCase",
        "IFileListViewModel",
        "realExecute",
        "viewModel",
        "params",
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
        return resolveClass(cl) != null
    }

    fun resolveClass(cl: ClassLoader): Class<*>? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref -> validateRef(cl, ref) }) {
            is DexKitCompat.CachedResult.Found -> return cached.value.declaringClass
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create()
                    .matcher(useCaseOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(realExecuteMatcher()),
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
            if (!candidate.isRealExecuteShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == BaiduFilePageHookPoints.SHOW_SAFETY_FOOTER_USE_CASE) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] ShowSafetyFooterUseCase unresolved: $diagnostic")
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
        XposedCompat.log("[$TAG] resolved ShowSafetyFooterUseCase: ${method.declaringClass.name}")
        return method.declaringClass
    }

    private fun resolveStableFallback(cl: ClassLoader): Class<*>? {
        for (className in stableClassCandidates) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (!isUseCaseOwner(clazz)) continue
            val method = findRealExecuteMethods(clazz).firstOrNull() ?: continue
            DexKitCompat.putCachedMethod(
                TAG,
                CACHE_ID,
                DexKitCompat.MethodRef(method.declaringClass.name, method.name),
            )
            DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "fallback:${method.declaringClass.name}.${method.name}")
            return clazz
        }
        return null
    }

    fun findRealExecuteMethods(clazz: Class<*>): List<Method> {
        if (!isUseCaseOwner(clazz)) return emptyList()
        return clazz.declaredMethods
            .filter(::isRealExecuteMethod)
            .onEach { it.isAccessible = true }
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
        if (!isUseCaseOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isRealExecuteMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isUseCaseOwner(clazz: Class<*>): Boolean {
        return KotlinMetadataUtils.metadataContainsAll(clazz, metadataTokens)
    }

    private fun isRealExecuteMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Boolean::class.javaPrimitiveType) return false
        val params = method.parameterTypes
        if (params.size != 2) return false
        if (!Map::class.java.isAssignableFrom(params[1])) return false
        return params[0].name == BaiduFilePageHookPoints.FILE_LIST_VIEW_MODEL_INTERFACE ||
            params[0].interfaces.any { it.name == BaiduFilePageHookPoints.FILE_LIST_VIEW_MODEL_INTERFACE }
    }

    private fun useCaseOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    metadataTokens.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(realExecuteMatcher())
    }

    private fun realExecuteMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Boolean::class.javaPrimitiveType!!)
            .paramTypes(BaiduFilePageHookPoints.FILE_LIST_VIEW_MODEL_INTERFACE, Map::class.java.name)
    }

    private fun DexMethodCandidate.isRealExecuteShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "boolean" &&
            paramTypeNames.size == 2 &&
            paramTypeNames[0] == BaiduFilePageHookPoints.FILE_LIST_VIEW_MODEL_INTERFACE &&
            paramTypeNames[1] == Map::class.java.name

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
