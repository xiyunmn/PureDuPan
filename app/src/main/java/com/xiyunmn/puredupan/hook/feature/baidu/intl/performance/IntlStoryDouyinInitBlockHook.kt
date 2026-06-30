package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlStoryHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlStoryDouyinInitBlockHook {
    const val STORY_INIT_CACHE_ID = "intl_story_douyin_story_init"

    private const val DOUYIN_OPEN_API_FACTORY_CLASS_NAME = BaiduIntlStoryHookPoints.DOUYIN_OPEN_API_FACTORY
    private const val DOUYIN_OPEN_CONFIG_CLASS_NAME = BaiduIntlStoryHookPoints.DOUYIN_OPEN_CONFIG

    private val storySemanticTokens = BaiduIntlStoryHookPoints.STORY_SEMANTIC_TOKENS
    private val storyEntryActivityClassNames = BaiduIntlStoryHookPoints.STORY_ENTRY_ACTIVITIES
    private val douyinEntryActivityClassNames = BaiduIntlStoryHookPoints.DOUYIN_ENTRY_ACTIVITIES

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val invokeDescriptors: Set<String>,
    )

    private val hookState = HookState()
    private val lock = Any()

    @Volatile private var storyInitMethod: Method? = null
    @Volatile private var douyinInitMethod: Method? = null
    @Volatile private var pendingStoryApplication: Application? = null
    @Volatile private var pendingDouyinConfig: Any? = null
    @Volatile private var storySkipped = false
    @Volatile private var douyinSkipped = false
    @Volatile private var storyRestored = false
    @Volatile private var douyinRestored = false
    @Volatile private var storyRestoring = false
    @Volatile private var douyinRestoring = false
    @Volatile private var storySkipCount = 0
    @Volatile private var douyinSkipCount = 0
    @Volatile private var storyRestoreCount = 0
    @Volatile private var douyinRestoreCount = 0

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlStoryDouyinInitBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val resolvedStoryInit = resolveStoryInitMethod(cl)
            val resolvedDouyinInit = resolveDouyinInitMethod(cl)
            if (resolvedStoryInit == null && resolvedDouyinInit == null) {
                hookState.reset()
                XposedCompat.log("[IntlStoryDouyinInitBlockHook] story/douyin init methods NOT FOUND")
                return
            }
            storyInitMethod = resolvedStoryInit
            douyinInitMethod = resolvedDouyinInit

            resolvedStoryInit?.let { method ->
                mod.hook(method).intercept { chain ->
                    val application = chain.args.firstOrNull() as? Application
                    if (application == null || !shouldBlockStoryInit()) {
                        return@intercept chain.proceed()
                    }

                    synchronized(lock) {
                        pendingStoryApplication = application
                        storySkipped = true
                        storySkipCount++
                    }
                    XposedCompat.log(
                        "[IntlStoryDouyinInitBlockHook] blocked startup story init: " +
                            "story_video_preloader, story_ui_service, skipCount=$storySkipCount",
                    )
                    null
                }
            }

            resolvedDouyinInit?.let { method ->
                mod.hook(method).intercept { chain ->
                    val config = chain.args.firstOrNull()
                    if (config == null || !shouldBlockDouyinInit()) {
                        return@intercept chain.proceed()
                    }

                    synchronized(lock) {
                        pendingDouyinConfig = config
                        douyinSkipped = true
                        douyinSkipCount++
                    }
                    XposedCompat.log(
                        "[IntlStoryDouyinInitBlockHook] blocked startup douyin_sdk_init: " +
                            "skipCount=$douyinSkipCount",
                    )
                    false
                }
            }

            val storyEntrySignals = hookStoryEntryRestoreSignals(cl)
            val douyinEntrySignals = hookDouyinEntryRestoreSignals(cl)
            XposedCompat.log(
                "[IntlStoryDouyinInitBlockHook] hooks INSTALLED: " +
                    "storyInit=${resolvedStoryInit?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}, " +
                    "douyinInit=${resolvedDouyinInit?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}, " +
                    "storyEntrySignals=$storyEntrySignals, douyinEntrySignals=$douyinEntrySignals",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlStoryDouyinInitBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolveStoryInitMethod(cl) != null
    }

    private fun resolveStoryInitMethod(cl: ClassLoader): Method? {
        resolveKnownStoryInitMethod(cl)?.let { method ->
            cacheResolvedStoryInit(method, "known")
            return method
        }

        if (!HookSettings.isExperimentalDexKitEnabled) {
            XposedCompat.logD("[IntlStoryDouyinInitBlockHook] story init DexKit resolve skipped: config disabled")
            return null
        }

        when (val cached = DexKitCompat.getCachedMethod(TAG, STORY_INIT_CACHE_ID) { ref ->
            resolveStoryInitRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .modifiers(Modifier.STATIC)
                                .returnType(Void.TYPE)
                                .paramTypes(Application::class.java),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                        invokeDescriptors = runCatching {
                            methodData.invokes.map { it.descriptor }.toSet()
                        }.getOrDefault(emptySet()),
                    )
                }
        } ?: return null
        val result = scanned

        if (result.isEmpty()) {
            XposedCompat.logD("[IntlStoryDouyinInitBlockHook] story init candidate not found")
            DexKitCompat.putCachedMethod(TAG, STORY_INIT_CACHE_ID, null)
            return null
        }

        val candidates = result.mapNotNull { methodData ->
            val method = resolveStoryInitRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
            ) ?: return@mapNotNull null
            if (methodData.isConstructor) return@mapNotNull null
            if (methodData.returnTypeName != "void") return@mapNotNull null
            if (methodData.paramTypeNames != listOf("android.app.Application")) return@mapNotNull null
            if (!Modifier.isStatic(methodData.modifiers)) return@mapNotNull null
            methodData to method
        }

        val rankedCandidates = candidates.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> { storyInitScore(it.first) }
                .thenBy { it.first.className }
                .thenBy { it.first.methodName },
        )
        val best = rankedCandidates.firstOrNull()
        val bestScore = best?.let { storyInitScore(it.first) } ?: 0
        val ambiguousBest = rankedCandidates.drop(1).any { storyInitScore(it.first) == bestScore }

        if (best == null || ambiguousBest) {
            XposedCompat.logW(
                "[IntlStoryDouyinInitBlockHook] ambiguous story init method: " +
                    candidates.joinToString { "${it.second.declaringClass.name}.${it.second.name}" },
            )
            DexKitCompat.putCachedMethod(TAG, STORY_INIT_CACHE_ID, null)
            return null
        }

        val method = best.second
        XposedCompat.log(
            "[IntlStoryDouyinInitBlockHook] resolved story init: " +
                "${method.declaringClass.name}.${method.name}, score=$bestScore",
        )
        cacheResolvedStoryInit(method, "dexkit")
        return method
    }

    private fun resolveKnownStoryInitMethod(cl: ClassLoader): Method? =
        resolveStoryInitRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduIntlStoryHookPoints.STORY_COMPONENT_CLASS,
                BaiduIntlStoryHookPoints.STORY_INIT_13_11_METHOD,
            ),
        )?.also { method ->
            XposedCompat.logD(
                "[IntlStoryDouyinInitBlockHook] resolved known story init: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }

    private fun cacheResolvedStoryInit(method: Method, source: String) {
        DexKitCompat.putCachedMethod(
            TAG,
            STORY_INIT_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            STORY_INIT_CACHE_ID,
            "$source:${method.declaringClass.name}.${method.name}",
        )
    }

    private fun resolveStoryInitRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!metadataContainsAll(clazz, storySemanticTokens)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Application::class.java.isAssignableFrom(method.parameterTypes[0])
        }?.apply { isAccessible = true }
    }

    private fun storyInitScore(candidate: DexMethodCandidate): Int {
        var score = 0
        if (BaiduIntlStoryHookPoints.STORY_VIDEO_PRELOADER_INIT_DESCRIPTOR in candidate.invokeDescriptors) {
            score += 40
        }
        if (BaiduIntlStoryHookPoints.STORY_UI_SERVICE_INIT_DESCRIPTOR in candidate.invokeDescriptors) {
            score += 30
        }
        return score
    }

    private fun resolveDouyinInitMethod(cl: ClassLoader): Method? {
        val factoryClass = XposedCompat.findClassOrNull(DOUYIN_OPEN_API_FACTORY_CLASS_NAME, cl) ?: return null
        val configClass = XposedCompat.findClassOrNull(DOUYIN_OPEN_CONFIG_CLASS_NAME, cl) ?: return null

        val candidates = mutableListOf<Method>()
        var current: Class<*>? = factoryClass
        while (current != null) {
            current.declaredMethods.filterTo(candidates) { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    configClass.isAssignableFrom(method.parameterTypes[0])
            }
            current = current.superclass
        }

        val namedInit = candidates.filter { it.name == "init" }
        if (namedInit.size == 1) {
            return namedInit.single().apply { isAccessible = true }
        }

        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlStoryDouyinInitBlockHook] ambiguous douyin init method: " +
                    candidates.joinToString { it.name },
            )
            return null
        }
        return candidates.single().apply { isAccessible = true }
    }

    private fun metadataContainsAll(clazz: Class<*>, tokens: Collection<String>): Boolean {
        val metadataTokens = metadataTokens(clazz)
        return tokens.all { token ->
            metadataTokens.any { it == token || it.startsWith(token) }
        }
    }

    private fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }

    private fun shouldBlockStoryInit(): Boolean {
        if (!isEnabled()) return false
        if (storyRestoring || storyRestored) return false
        return true
    }

    private fun shouldBlockDouyinInit(): Boolean {
        if (!isEnabled()) return false
        if (douyinRestoring || douyinRestored) return false
        return true
    }

    private fun hookStoryEntryRestoreSignals(cl: ClassLoader): Int {
        var installed = 0
        for (className in storyEntryActivityClassNames) {
            val activityClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (hookActivityOnCreate(activityClass, "story_entry:$className") { activity, reason ->
                    restoreStoryIfPending(reason, activity?.application)
                }
            ) {
                installed++
            }
        }
        return installed
    }

    private fun hookDouyinEntryRestoreSignals(cl: ClassLoader): Int {
        var installed = 0
        for (className in douyinEntryActivityClassNames) {
            val activityClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (hookActivityOnCreate(activityClass, "douyin_entry:$className") { _, reason ->
                    restoreDouyinIfPending(reason)
                }
            ) {
                installed++
            }
            if (hookActivityOnNewIntent(activityClass, "douyin_entry_new_intent:$className") { _, reason ->
                    restoreDouyinIfPending(reason)
                }
            ) {
                installed++
            }
        }
        return installed
    }

    private fun hookActivityOnCreate(
        activityClass: Class<*>,
        reason: String,
        beforeProceed: (Activity?, String) -> Unit,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java) ?: return false
        mod.hook(method).intercept { chain ->
            beforeProceed(chain.thisObject as? Activity, reason)
            chain.proceed()
        }
        return true
    }

    private fun hookActivityOnNewIntent(
        activityClass: Class<*>,
        reason: String,
        beforeProceed: (Activity?, String) -> Unit,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onNewIntent", Intent::class.java) ?: return false
        mod.hook(method).intercept { chain ->
            beforeProceed(chain.thisObject as? Activity, reason)
            chain.proceed()
        }
        return true
    }

    private fun restoreStoryIfPending(reason: String, fallbackApplication: Application?) {
        if (!isEnabled()) return
        val method = storyInitMethod ?: run {
            XposedCompat.logW("[IntlStoryDouyinInitBlockHook] story restore skipped: method missing, reason=$reason")
            return
        }
        val application = synchronized(lock) {
            if (!storySkipped || storyRestored) return
            val pending = pendingStoryApplication ?: fallbackApplication ?: return
            storyRestored = true
            storyRestoreCount++
            pending
        }

        try {
            storyRestoring = true
            method.invoke(null, application)
            XposedCompat.log(
                "[IntlStoryDouyinInitBlockHook] restored story init: " +
                    "reason=$reason, restoreCount=$storyRestoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                storyRestored = false
                storyRestoreCount--
            }
            XposedCompat.logW(
                "[IntlStoryDouyinInitBlockHook] story restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            storyRestoring = false
        }
    }

    private fun restoreDouyinIfPending(reason: String) {
        if (!isEnabled()) return
        val method = douyinInitMethod ?: run {
            XposedCompat.logW("[IntlStoryDouyinInitBlockHook] douyin restore skipped: method missing, reason=$reason")
            return
        }
        val config = synchronized(lock) {
            if (!douyinSkipped || douyinRestored) return
            val pending = pendingDouyinConfig ?: return
            douyinRestored = true
            douyinRestoreCount++
            pending
        }

        try {
            douyinRestoring = true
            val result = method.invoke(null, config)
            XposedCompat.log(
                "[IntlStoryDouyinInitBlockHook] restored douyin_sdk_init: " +
                    "reason=$reason, result=$result, restoreCount=$douyinRestoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                douyinRestored = false
                douyinRestoreCount--
            }
            XposedCompat.logW(
                "[IntlStoryDouyinInitBlockHook] douyin restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            douyinRestoring = false
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlStoryDouyinInitBlocked

    private const val TAG = "IntlStoryDouyinInitBlockHook"
}
