package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlHomeLeftScreenSwipeDisableHook {
    private const val TAG = "IntlHomeLeftScreenSwipeDisableHook"
    private const val OPEN_LEFT_DRAWER_METHOD = "openLeftDrawer"
    private const val SWITCH_LEFT_DRAWER_STATE_METHOD = "switchLeftDrawerSate"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val setEnableMethod = IntlHomeLeftScreenDrawerDexKitResolver.resolveSetLeftDrawerEnable(cl)
                ?: run {
                    hookState.reset()
                    XposedCompat.log("[$TAG] FHHomeDrawerLayout.setLeftDrawerEnable equivalent NOT FOUND")
                    return
                }
            val drawerClass = setEnableMethod.declaringClass
            val hookedMethods = mutableListOf<String>()

            mod.hook(setEnableMethod).intercept { chain ->
                if (isEnabled()) {
                    chain.proceed(arrayOf<Any?>(false))
                } else {
                    chain.proceed()
                }
            }
            hookedMethods += setEnableMethod.name

            drawerClass.findBooleanVoidMethod(OPEN_LEFT_DRAWER_METHOD)?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) null else chain.proceed()
                }
                hookedMethods += method.name
            }

            drawerClass.findNoArgVoidMethod(SWITCH_LEFT_DRAWER_STATE_METHOD)?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) null else chain.proceed()
                }
                hookedMethods += method.name
            }

            XposedCompat.log(
                "[$TAG] hooks INSTALLED: ${drawerClass.name} methods=${hookedMethods.joinToString()}",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$TAG] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun Class<*>.findBooleanVoidMethod(name: String): Method? =
        declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(booleanArrayParam)
        }?.apply { isAccessible = true }

    private fun Class<*>.findNoArgVoidMethod(name: String): Method? =
        declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true }

    private fun isEnabled(): Boolean = HookSettings.isIntlHomeLeftScreenSwipeDisabled

    private val booleanArrayParam = arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)
}

internal object IntlHomeLeftScreenDrawerDexKitResolver {
    const val CACHE_ID = "intl_home_left_screen_drawer_enable_v1"

    private const val TAG = "IntlHomeLeftScreenDrawerDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"
    private const val STABLE_CLASS_NAME = BaiduIntlHookPoints.FH_HOME_DRAWER_LAYOUT
    private const val SET_LEFT_DRAWER_ENABLE_METHOD = "setLeftDrawerEnable"

    private val metadataTokens = listOf(
        "FHHomeDrawerLayout",
        "leftDrawerEnable",
        "setLeftDrawerEnable",
        "openLeftDrawer",
        "switchLeftDrawerSate",
        "closeLeftDrawer",
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
        return resolveSetLeftDrawerEnable(cl) != null
    }

    fun resolveSetLeftDrawerEnable(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create()
                    .matcher(drawerClassMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(setLeftDrawerEnableMatcher()),
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
        } ?: return resolveFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isSetLeftDrawerEnableShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == STABLE_CLASS_NAME) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] setLeftDrawerEnable unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveFallback(cl)
        }

        val method = best.second
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        XposedCompat.log("[$TAG] resolved setLeftDrawerEnable: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveFallback(cl: ClassLoader): Method? {
        return validateRef(
            cl,
            DexKitCompat.MethodRef(STABLE_CLASS_NAME, SET_LEFT_DRAWER_ENABLE_METHOD),
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
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!KotlinMetadataUtils.metadataContainsAll(clazz, metadataTokens)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isSetLeftDrawerEnable(method)
        }?.apply { isAccessible = true }
    }

    private fun drawerClassMatcher(): ClassMatcher {
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
            .addMethod(setLeftDrawerEnableMatcher())
    }

    private fun setLeftDrawerEnableMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .name(SET_LEFT_DRAWER_ENABLE_METHOD)
            .returnType(Void.TYPE)
            .paramTypes(Boolean::class.javaPrimitiveType!!)
    }

    private fun DexMethodCandidate.isSetLeftDrawerEnableShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            methodName == SET_LEFT_DRAWER_ENABLE_METHOD &&
            returnTypeName == "void" &&
            paramTypeNames == listOf("boolean")

    private fun isSetLeftDrawerEnable(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!))

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
