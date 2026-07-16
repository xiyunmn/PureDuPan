package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.app.Activity
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlBottomAiTabHideHook {
    private const val INIT_VIEW_METHOD = "initView"
    private const val INIT_TABS_SKIN_METHOD = "initTabsSkin"
    private const val REFRESH_TAB_SKIN_METHOD = "refreshTabSkin"
    private const val DO_AI_CLOUD_GREET_ANIM_METHOD = "doAiCloudGreetAnim"
    private const val DO_AI_CLOUD_WINK_ANIM_METHOD = "doAiCloudWinkAnim"
    private const val ON_CLICK_METHOD = "onClick"

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlBottomAiTabHideHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val className = BaiduFeatureRuntime.currentMainActivityClassName()
            val clazz = className?.let { XposedCompat.findClassOrNull(it, cl) }
            val method = clazz?.declaredMethods?.firstOrNull {
                it.name == INIT_VIEW_METHOD && it.parameterTypes.isEmpty()
            }

            if (method == null) {
                hookState.reset()
                XposedCompat.log("[IntlBottomAiTabHideHook] MainActivity.initView NOT FOUND")
                return
            }

            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null && isEnabled()) {
                    applyAigcTabHidden(activity)
                }
                result
            }
            hookRefreshMethod(clazz, INIT_TABS_SKIN_METHOD)
            hookRefreshMethod(clazz, REFRESH_TAB_SKIN_METHOD)
            hookAiCloudAnimation(clazz, DO_AI_CLOUD_GREET_ANIM_METHOD)
            hookAiCloudAnimation(clazz, DO_AI_CLOUD_WINK_ANIM_METHOD, Int::class.javaPrimitiveType!!)
            hookAigcClick(clazz)
            XposedCompat.log("[IntlBottomAiTabHideHook] hook INSTALLED: ${clazz.name}.$INIT_VIEW_METHOD")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlBottomAiTabHideHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookRefreshMethod(clazz: Class<*>, methodName: String) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && isEnabled()) {
                applyAigcTabHidden(activity)
            }
            result
        }
    }

    private fun hookAiCloudAnimation(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterTypes.contentEquals(paramTypes)
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && isEnabled()) {
                applyAigcTabHidden(activity)
                return@intercept null
            }
            chain.proceed()
        }
    }

    private fun hookAigcClick(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == ON_CLICK_METHOD &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == View::class.java
        } ?: return
        method.isAccessible = true
        XposedCompat.module?.hook(method)?.intercept { chain ->
            val activity = chain.thisObject as? Activity
            val view = chain.args.firstOrNull() as? View
            if (activity != null && view != null && isEnabled() && isAigcTabView(activity, view)) {
                applyAigcTabHidden(activity)
                return@intercept null
            }
            chain.proceed()
        }
    }

    private fun applyAigcTabHidden(activity: Activity) {
        collapseViewById(activity, "aigc_cloud")
        collapseViewById(activity, "aigc_hi_lottie")
    }

    private fun collapseViewById(activity: Activity, idName: String) {
        val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
        if (id == 0) return
        activity.findViewById<View>(id)?.let(::collapseView)
    }

    private fun collapseView(view: View) {
        view.visibility = View.GONE
        view.isEnabled = false
        view.isClickable = false
        view.setOnClickListener(null)
    }

    private fun isAigcTabView(activity: Activity, view: View): Boolean =
        view.id == activity.resources.getIdentifier("aigc_cloud", "id", activity.packageName)

    private fun isEnabled(): Boolean =
        HookSettings.isBottomBarCustomEnabled && HookSettings.isBottomBarTabAigcHidden
}

internal object IntlBottomAiTabReplaceHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlBottomAiTabReplaceHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = IntlBottomAiTabModeDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log("[IntlBottomAiTabReplaceHook] getAiCloudTabMode equivalent NOT FOUND")
                hookState.reset()
                return
            }
            mod.hook(method).intercept {
                if (isEnabled()) 0L else it.proceed()
            }
            XposedCompat.log(
                "[IntlBottomAiTabReplaceHook] hook INSTALLED: ${method.declaringClass.name}.${method.name}",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlBottomAiTabReplaceHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isBottomBarCustomEnabled && HookSettings.isBottomAiReplaced
}

internal object IntlBottomAiTabModeDexKitResolver {
    const val CACHE_ID = "intl_bottom_ai_tab_mode_v1"

    private const val TAG = "IntlBottomAiTabModeDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"
    private const val STABLE_CLASS_NAME = "pz._"
    private const val STABLE_METHOD_NAME = "_"

    private val metadataTokens = listOf(
        "AI_CLOUD_TAB_NODE",
        "AMIS_AI_CLOUD_MODE_DEFAULT",
        "AMIS_AI_CLOUD_MODE",
        "AMIS_VIP_MODE",
        "AMIS_YIKE_MODE",
        "aiCloudTabMode",
        "getAiCloudTabMode",
        "()J",
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
                    .matcher(aiCloudTabOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(tabModeGetterMatcher()),
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
            if (!candidate.isTabModeGetterShape()) return@mapNotNull null
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
            XposedCompat.logW("[$TAG] getAiCloudTabMode unresolved: $diagnostic")
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
        XposedCompat.log("[$TAG] resolved getAiCloudTabMode: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        return validateRef(
            cl,
            DexKitCompat.MethodRef(STABLE_CLASS_NAME, STABLE_METHOD_NAME),
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
            method.name == ref.methodName && isTabModeGetter(method)
        }?.apply { isAccessible = true }
    }

    private fun aiCloudTabOwnerMatcher(): ClassMatcher {
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
            .addMethod(tabModeGetterMatcher())
    }

    private fun tabModeGetterMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .modifiers(Modifier.STATIC)
            .returnType(Long::class.javaPrimitiveType!!)
            .paramTypes()
    }

    private fun DexMethodCandidate.isTabModeGetterShape(): Boolean =
        !isConstructor &&
            Modifier.isStatic(modifiers) &&
            returnTypeName == "long" &&
            paramTypeNames.isEmpty()

    private fun isTabModeGetter(method: Method): Boolean =
        Modifier.isStatic(method.modifiers) &&
            method.returnType == Long::class.javaPrimitiveType &&
            method.parameterTypes.isEmpty()

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
