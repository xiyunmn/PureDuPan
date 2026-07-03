package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticSwanPreloadResolver {
    const val PREFETCH_EVENT_CACHE_ID = "domestic_swan_prefetch_event"

    private const val TAG = "DomesticSwanPreloadResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

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

    fun warmUpPrefetchEventCache(cl: ClassLoader): Boolean {
        return resolvePrefetchEventMethod(cl) != null
    }

    fun resolvePrefetchEventMethod(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, PREFETCH_EVENT_CACHE_ID) { ref ->
            validatePrefetchEventRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStablePrefetchEventMethod(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return resolveStablePrefetchEventMethod(cl)

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = PREFETCH_EVENT_CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(Void.TYPE)
                            .paramTypes(listOf(BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT)),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                )
            }
        } ?: return resolveStablePrefetchEventMethod(cl)

        val rejected = mutableListOf<String>()
        val candidateMatches = candidates.mapNotNull { candidate ->
            if (!candidate.isPrefetchEventShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(compareBy { it.first.className })

        val matches = candidateMatches
            .groupBy { (_, method) -> method.declaringClass }
            .mapNotNull { (clazz, entries) ->
                val selected = selectPrefetchEventEntryMethod(
                    clazz = clazz,
                    cl = cl,
                    matches = entries.map { it.second },
                ) ?: return@mapNotNull null
                val candidate = entries.firstOrNull { (_, method) -> methodKey(method) == methodKey(selected) }?.first
                    ?: return@mapNotNull null
                candidate to selected
            }
            .distinctBy { (_, method) -> methodKey(method) }
            .sortedWith(compareBy({ it.first.className }, { it.first.methodName }))

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            DexKitCompat.markTargetError(
                TAG,
                PREFETCH_EVENT_CACHE_ID,
                diagnostic,
            )
            DexKitCompat.putCachedMethod(TAG, PREFETCH_EVENT_CACHE_ID, null)
            return resolveStablePrefetchEventMethod(cl)
        }

        val method = best.second
        method.isAccessible = true
        DexKitCompat.putCachedMethod(
            TAG,
            PREFETCH_EVENT_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun validatePrefetchEventRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val prefetchEventClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return null
        if (!isPrefetchManagerClass(clazz, cl)) return null
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == prefetchEventClass &&
                implementsInterfaceMethodBySignature(managerInterface, method)
        }?.apply { isAccessible = true }
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validatePrefetchEventRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: manager/signature mismatch"
        }
        return method
    }

    private fun selectPrefetchEventEntryMethod(
        clazz: Class<*>,
        cl: ClassLoader,
        matches: List<Method>,
    ): Method? {
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return null
        return matches.firstOrNull { method -> implementsInterfaceMethod(managerInterface, method) }
            ?: matches.firstOrNull { method ->
                method.name.startsWith("mo") && implementsInterfaceMethodBySignature(managerInterface, method)
            }
            ?: clazz.declaredMethods.firstOrNull { method ->
                method.name == "_" &&
                    matches.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }
            }
    }

    private fun methodKey(method: Method): String =
        method.declaringClass.name + "#" + method.name + "(" +
            method.parameterTypes.joinToString(",") { it.name } + ")"

    private fun implementsInterfaceMethod(managerInterface: Class<*>, method: Method): Boolean {
        return managerInterface.declaredMethods.any { interfaceMethod ->
            interfaceMethod.name == method.name &&
                interfaceMethod.returnType == method.returnType &&
                interfaceMethod.parameterTypes.contentEquals(method.parameterTypes)
        }
    }

    private fun implementsInterfaceMethodBySignature(managerInterface: Class<*>, method: Method): Boolean {
        return managerInterface.declaredMethods.any { interfaceMethod ->
            interfaceMethod.returnType == method.returnType &&
                interfaceMethod.parameterTypes.contentEquals(method.parameterTypes)
        }
    }

    private fun resolveStablePrefetchEventMethod(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_STABLE_CLASS,
            cl,
        ) ?: return null
        if (!isPrefetchManagerClass(clazz, cl)) return null
        val prefetchEventClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return null
        val matches = clazz.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == prefetchEventClass
        }
        return selectPrefetchEventEntryMethod(clazz, cl, matches)?.also { method ->
            method.isAccessible = true
            DexKitCompat.markTargetSuccess(
                TAG,
                PREFETCH_EVENT_CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun isPrefetchManagerClass(clazz: Class<*>, cl: ClassLoader): Boolean {
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return false
        if (!managerInterface.isAssignableFrom(clazz)) return false
        val hasEnvControllerField = clazz.declaredFields.any { field ->
            field.type.name == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_ENV_CONTROLLER
        }
        if (!hasEnvControllerField) return false
        return hasManagerTagField(clazz)
    }

    private fun hasManagerTagField(clazz: Class<*>): Boolean {
        return clazz.declaredFields.any { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java &&
                runCatching {
                    field.isAccessible = true
                    field.get(null) == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_TAG
                }.getOrDefault(false)
        }
    }

    private fun DexMethodCandidate.isPrefetchEventShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames == listOf(BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT)

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
