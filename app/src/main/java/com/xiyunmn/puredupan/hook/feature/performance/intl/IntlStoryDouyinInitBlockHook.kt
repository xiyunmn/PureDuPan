package com.xiyunmn.puredupan.hook.feature.performance.intl

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object IntlStoryDouyinInitBlockHook {
    private const val NEW_STORY_COMPONENT_CLASS_NAME = "com.baidu.netdisk.newstory.component.c"
    private const val DOUYIN_OPEN_API_FACTORY_CLASS_NAME = "com.bytedance.sdk.open.douyin.a"
    private const val DOUYIN_OPEN_CONFIG_CLASS_NAME = "com.bytedance.sdk.open.douyin.DouYinOpenConfig"

    private val storyEntryActivityClassNames = listOf(
        "com.baidu.netdisk.newstory.calendar.ui.view.StorySetActivity",
        "com.baidu.netdisk.newstory.feedhome.ui.view.HomeStoryActivity",
        "com.baidu.netdisk.newstory.feedhome.ui.view.FeedHomeToolsMakeSameActivity",
        "com.baidu.netdisk.newstory.ui.view.AllStoryListActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateStyleVideoStyleLoadingActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoLoadingActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoPreviewActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoStyleSelectionActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStoryDetailActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStoryMediaListActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStorySingleMediaActivity",
        "com.baidu.netdisk.newstory.ui.view.share.MemoryStoryMediaShareActivity",
        "com.baidu.netdisk.newstory.campus.preview.ui.GraduationSeasonActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonGenerateLoadingActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonGenerateVideoShareActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonShareActivity",
    )

    private val douyinEntryActivityClassNames = listOf(
        "com.baidu.netdisk.newstory.ui.view.DouYinEntryActivity",
        "com.bytedance.sdk.open.douyin.ui.DouYinWebAuthorizeActivity",
        "com.baidu.sapi2.activity.social.DouyinSSOLoginActivity",
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

    private fun resolveStoryInitMethod(cl: ClassLoader): Method? {
        val componentClass = XposedCompat.findClassOrNull(NEW_STORY_COMPONENT_CLASS_NAME, cl) ?: return null
        if (!metadataContains(componentClass, "initStory")) {
            XposedCompat.logW("[IntlStoryDouyinInitBlockHook] story component metadata mismatch")
            return null
        }
        val candidates = componentClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Application::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlStoryDouyinInitBlockHook] ambiguous story init method: " +
                    candidates.joinToString { it.name },
            )
            return null
        }
        return candidates.single().apply { isAccessible = true }
    }

    private fun resolveDouyinInitMethod(cl: ClassLoader): Method? {
        val factoryClass = XposedCompat.findClassOrNull(DOUYIN_OPEN_API_FACTORY_CLASS_NAME, cl) ?: return null
        val configClass = XposedCompat.findClassOrNull(DOUYIN_OPEN_CONFIG_CLASS_NAME, cl) ?: return null
        val candidates = factoryClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                configClass.isAssignableFrom(method.parameterTypes[0])
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

    private fun metadataContains(clazz: Class<*>, token: String): Boolean {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return false
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return false
        return d2.any { it == token }
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
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIntlStoryDouyinInitBlocked
}
