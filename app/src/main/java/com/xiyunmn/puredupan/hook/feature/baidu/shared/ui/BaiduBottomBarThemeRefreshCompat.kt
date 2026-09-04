package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduBottomBarHookPoints
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Restores the host's selected bottom-tab visual after a completed skin change. */
internal class BaiduBottomBarThemeRefreshCompat(
    private val logTag: String,
    private val homeFoldedFieldNames: List<String>,
    private val themeRefreshMethodNames: List<String>,
    private val themeRefreshCompletionMethodName: String?,
    private val gateColdStartBottomBar: Boolean,
) {
    private data class Handles(
        val mainActivityClass: Class<*>,
        val refreshTabFolder: Method,
        val getLottieRootFolder: Method,
        val showHomeTab: Method?,
        val showHomeTopTab: Method?,
        val showFileTab: Method?,
        val showShareTab: Method?,
        val showFindTab: Method?,
        val showAboutMeTab: Method?,
        val childIndex: Field,
        val tabContainer: Field,
        val skinData: Field,
        val homeFolded: Field?,
        val getTabImageView: Method?,
        val tabImageView: Field?,
        val pauseImageAnimation: Method,
        val setImageProgress: Method,
    )

    private val hookState = HookState()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var handles: Handles? = null
    @Volatile private var mainActivityRef: WeakReference<Activity>? = null
    private var initializedActivityRef: WeakReference<Activity>? = null
    private var expectedHostThemeChange = false
    private var pendingCompletion: PendingCompletion? = null

    private data class PendingCompletion(
        val activity: Activity,
        val reason: String,
        val isExpectedThemeChange: Boolean,
    )

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val mainActivityClassName = BaiduFeatureRuntime.currentMainActivityClassName()
                ?: error("MainActivity host capability missing")
            val mainActivityClass = XposedCompat.findClassOrNull(mainActivityClassName, cl)
                ?: error("MainActivity class not found: $mainActivityClassName")
            val resolved = resolveHandles(mainActivityClass)
            val refreshEntries = themeRefreshMethodNames.map { methodName ->
                findNoArgMethod(mainActivityClass, methodName)
                    ?: error("MainActivity.$methodName not found")
            }

            handles = resolved
            refreshEntries.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { activity ->
                        if (themeRefreshCompletionMethodName == null) {
                            refreshAfterThemeEntry(activity, method.name)
                        } else {
                            awaitThemeRefreshCompletion(activity, method.name)
                        }
                    }
                    result
                }
            }
            themeRefreshCompletionMethodName?.let { methodName ->
                hookThemeRefreshCompletion(mainActivityClass, methodName)
            }
            if (gateColdStartBottomBar) {
                hookColdStartBottomBarGate(mainActivityClass, resolved)
            }
            log(
                "hook INSTALLED: ${mainActivityClass.name}." +
                    refreshEntries.joinToString { it.name },
            )
        } catch (t: Throwable) {
            handles = null
            hookState.reset()
            log("install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun expectHostThemeChange() {
        if (themeRefreshCompletionMethodName == null) return
        expectedHostThemeChange = true
        logD("expecting host theme change")
    }

    internal fun cancelExpectedHostThemeChange() {
        expectedHostThemeChange = false
    }

    private fun refreshAfterThemeEntry(activity: Activity, reason: String) {
        if (!HookSettings.isFollowSystemNightModeEnabled) return
        val resolved = handles ?: return
        if (resolved.childIndex.getInt(activity) < 0) {
            logD("refresh skipped: tabs not selected ($reason)")
            return
        }
        mainActivityRef = WeakReference(activity)
        refreshNow(activity, reason)
    }

    private fun awaitThemeRefreshCompletion(activity: Activity, reason: String) {
        if (!HookSettings.isFollowSystemNightModeEnabled) return
        val isExpectedThemeChange = expectedHostThemeChange ||
            pendingCompletion?.let { pending ->
                pending.activity === activity && pending.isExpectedThemeChange
            } == true
        val pending = PendingCompletion(activity, reason, isExpectedThemeChange)
        pendingCompletion = pending
        mainHandler.post {
            if (pendingCompletion === pending) {
                pendingCompletion = null
                logD("refresh completion expired ($reason)")
            }
        }
    }

    private fun hookThemeRefreshCompletion(mainActivityClass: Class<*>, methodName: String) {
        val mod = XposedCompat.module ?: return
        val completion = findMethod(mainActivityClass) { method ->
            method.name == methodName && method.parameterTypes.size == 1
        } ?: error("MainActivity.$methodName(selectedTab) not found")
        completion.isAccessible = true
        mod.hook(completion).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val selectedTab = chain.args.firstOrNull() as? View
            val pending = pendingCompletion
            val completesPending = activity != null && selectedTab != null && pending?.activity === activity
            val isInitialized = initializedActivityRef?.get() === activity

            if (completesPending && isInitialized && !pending.isExpectedThemeChange) {
                pendingCompletion = null
                logD("redundant tab visual refresh skipped (${pending.reason} -> $methodName)")
                return@intercept null
            }

            val result = chain.proceed()
            if (completesPending) {
                pendingCompletion = null
                if (pending.isExpectedThemeChange) expectedHostThemeChange = false
                if (!isInitialized) {
                    initializedActivityRef = WeakReference(activity)
                    logD("initial tab visual refresh completed")
                } else if (pending.isExpectedThemeChange) {
                    refreshSelectedNow(activity, selectedTab, "${pending.reason} -> $methodName")
                }
            }
            result
        }
        log("hook INSTALLED: ${mainActivityClass.name}.$methodName completion")
    }

    private fun hookColdStartBottomBarGate(mainActivityClass: Class<*>, resolved: Handles) {
        val mod = XposedCompat.module ?: return
        val initTabs = findMethod(mainActivityClass) { method ->
            method.name == BaiduBottomBarHookPoints.INIT_TABS_METHOD &&
                method.parameterTypes.contentEquals(arrayOf(Intent::class.java))
        } ?: error("MainActivity.initTabs(Intent) not found")
        val tabRootField = requireField(mainActivityClass, BaiduBottomBarHookPoints.TAB_ROOT_FIELD)
        val contentViewField = requireField(mainActivityClass, BaiduBottomBarHookPoints.CONTENT_VIEW_FIELD)

        initTabs.isAccessible = true
        mod.hook(initTabs).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val isColdSelection = activity != null && resolved.childIndex.getInt(activity) < 0
            val tabRoot = if (isColdSelection) tabRootField.get(activity) as? View else null
            val originalVisibility = tabRoot?.visibility
            if (originalVisibility == View.VISIBLE) {
                tabRoot.visibility = View.INVISIBLE
            }

            val result = chain.proceed()
            if (activity != null && tabRoot != null && originalVisibility != null) {
                finishSelectedAnimationByIndex(activity, resolved, "cold-start initTabs")
                val contentView = contentViewField.get(activity) as? View
                val reveal = Runnable {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        finishSelectedAnimationByIndex(activity, resolved, "cold-start reveal")
                        tabRoot.visibility = originalVisibility
                        logD("cold-start bottom bar revealed with content")
                    }
                }
                if (contentView != null) contentView.post(reveal) else mainHandler.post(reveal)
            }
            result
        }
        log("hook INSTALLED: ${mainActivityClass.name}.initTabs cold-start gate")
    }

    private fun refreshNow(activity: Activity?, reason: String) {
        val resolved = handles ?: return
        if (activity == null || !resolved.mainActivityClass.isInstance(activity)) return
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            resolved.refreshTabFolder.invoke(activity)

            val tabContainer = resolved.tabContainer.get(activity) as? ViewGroup
                ?: error("mTab is not a ViewGroup")
            val childIndex = resolved.childIndex.getInt(activity)
            if (childIndex !in 0 until tabContainer.childCount) {
                logD("refresh skipped: invalid child index=$childIndex ($reason)")
                return
            }
            val selectedTab = tabContainer.getChildAt(childIndex)
            replaySelectedVisual(activity, selectedTab, resolved)
            finishSelectedAnimation(selectedTab, resolved)
            logD("selected tab refreshed: ${resourceEntryName(activity, selectedTab)} ($reason)")
        } catch (t: Throwable) {
            log("selected tab refresh FAILED ($reason): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun refreshSelectedNow(activity: Activity, selectedTab: View, reason: String) {
        val resolved = handles ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            replaySelectedVisual(activity, selectedTab, resolved)
            finishSelectedAnimation(selectedTab, resolved)
            logD("selected tab refreshed: ${resourceEntryName(activity, selectedTab)} ($reason)")
        } catch (t: Throwable) {
            log("selected tab refresh FAILED ($reason): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun finishSelectedAnimationByIndex(
        activity: Activity,
        resolved: Handles,
        reason: String,
    ) {
        runCatching {
            val tabContainer = resolved.tabContainer.get(activity) as? ViewGroup ?: return
            val childIndex = resolved.childIndex.getInt(activity)
            if (childIndex !in 0 until tabContainer.childCount) return
            finishSelectedAnimation(tabContainer.getChildAt(childIndex), resolved)
            logD("selected animation finished ($reason)")
        }.onFailure { error ->
            log("selected animation finish FAILED ($reason): ${error.message}")
            XposedCompat.log(error)
        }
    }

    private fun replaySelectedVisual(activity: Activity, selectedTab: View, resolved: Handles) {
        val tabName = resourceEntryName(activity, selectedTab)
        val skinData = resolved.skinData.get(activity)
        val lottieRootFolder = resolved.getLottieRootFolder.invoke(activity) as? String
            ?: error("getLottieRootFolder returned null")

        when (tabName) {
            BaiduBottomBarHookPoints.HOME_TAB_ID_NAME -> {
                val folded = resolved.homeFolded?.getBoolean(activity) == true
                val method = if (folded) resolved.showHomeTopTab else resolved.showHomeTab
                invokeTabVisual(method, activity, lottieRootFolder, skinData, tabName)
            }
            BaiduBottomBarHookPoints.FILE_TAB_ID_NAME -> invokeTabVisual(
                resolved.showFileTab,
                activity,
                lottieRootFolder,
                skinData,
                tabName,
            )
            BaiduBottomBarHookPoints.SHARE_TAB_ID_NAME -> invokeTabVisual(
                resolved.showShareTab,
                activity,
                lottieRootFolder,
                skinData,
                tabName,
            )
            BaiduBottomBarHookPoints.FIND_TAB_ID_NAME -> invokeTabVisual(
                resolved.showFindTab,
                activity,
                lottieRootFolder,
                skinData,
                tabName,
            )
            BaiduBottomBarHookPoints.ABOUT_ME_TAB_ID_NAME -> invokeAboutMeVisual(
                resolved.showAboutMeTab,
                activity,
                skinData,
            )
            else -> logD("selected visual replay skipped: unknown tab=$tabName")
        }
    }

    private fun invokeTabVisual(
        method: Method?,
        activity: Activity,
        lottieRootFolder: String,
        skinData: Any?,
        tabName: String,
    ) {
        if (method == null) error("selected visual method missing for $tabName")
        method.invoke(activity, lottieRootFolder, skinData)
    }

    private fun invokeAboutMeVisual(method: Method?, activity: Activity, skinData: Any?) {
        if (method == null) error("showAboutMeAnim missing")
        method.invoke(activity, skinData)
    }

    private fun finishSelectedAnimation(selectedTab: View, resolved: Handles) {
        val imageView = resolved.getTabImageView?.invoke(selectedTab)
            ?: resolved.tabImageView?.get(selectedTab)
            ?: return
        resolved.pauseImageAnimation.invoke(imageView)
        resolved.setImageProgress.invoke(imageView, 1f)
    }

    private fun resolveHandles(clazz: Class<*>): Handles {
        val refreshTabFolder = requireNoArgMethod(
            clazz,
            BaiduBottomBarHookPoints.REFRESH_TAB_FOLDER_METHOD,
        )
        val getLottieRootFolder = requireNoArgMethod(
            clazz,
            BaiduBottomBarHookPoints.GET_LOTTIE_ROOT_FOLDER_METHOD,
        )
        val lottieRadioButtonClass = XposedCompat.findClassOrNull(
            BaiduBottomBarHookPoints.LOTTIE_RADIO_BUTTON,
            clazz.classLoader ?: error("MainActivity class loader unavailable"),
        ) ?: error("LottieRadioButton class not found")
        val getTabImageView = findNoArgMethod(lottieRadioButtonClass, "getImageView")
        val tabImageView = if (getTabImageView == null) {
            requireUniqueFieldByTypeName(
                lottieRadioButtonClass,
                "com.airbnb.lottie.LottieAnimationView",
                "LottieRadioButton image view",
            )
        } else {
            null
        }
        val imageViewClass = getTabImageView?.returnType ?: requireNotNull(tabImageView).type

        return Handles(
            mainActivityClass = clazz,
            refreshTabFolder = refreshTabFolder,
            getLottieRootFolder = getLottieRootFolder,
            showHomeTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_HOME_TAB_METHOD, 2),
            showHomeTopTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_HOME_TOP_TAB_METHOD, 2),
            showFileTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_FILE_TAB_METHOD, 2),
            showShareTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_SHARE_TAB_METHOD, 2),
            showFindTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_FIND_TAB_METHOD, 2),
            showAboutMeTab = findVisualMethod(clazz, BaiduBottomBarHookPoints.SHOW_ABOUT_ME_TAB_METHOD, 1),
            childIndex = requireField(clazz, BaiduBottomBarHookPoints.CHILD_INDEX_FIELD),
            tabContainer = requireField(clazz, BaiduBottomBarHookPoints.TAB_CONTAINER_FIELD),
            skinData = requireField(clazz, BaiduBottomBarHookPoints.SKIN_DATA_FIELD),
            homeFolded = homeFoldedFieldNames.firstNotNullOfOrNull { findField(clazz, it) },
            getTabImageView = getTabImageView,
            tabImageView = tabImageView,
            pauseImageAnimation = requireNoArgMethod(imageViewClass, "pauseAnimation"),
            setImageProgress = findMethod(imageViewClass) { method ->
                method.name == "setProgress" &&
                    method.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
            } ?: error("LottieAnimationView.setProgress(float) not found"),
        )
    }

    private fun findVisualMethod(clazz: Class<*>, name: String, parameterCount: Int): Method? =
        findMethod(clazz) { method ->
            method.name == name && method.parameterTypes.size == parameterCount
        }

    private fun requireNoArgMethod(clazz: Class<*>, name: String): Method =
        findNoArgMethod(clazz, name) ?: error("$name() not found")

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? =
        findMethod(clazz) { method -> method.name == name && method.parameterTypes.isEmpty() }

    private fun findMethod(clazz: Class<*>, accept: (Method) -> Boolean): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull(accept)?.let { method ->
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }

    private fun requireField(clazz: Class<*>, name: String): Field =
        findField(clazz, name) ?: error("field $name not found")

    private fun requireUniqueFieldByTypeName(
        clazz: Class<*>,
        typeName: String,
        label: String,
    ): Field {
        val candidates = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredFields
                .filterTo(candidates) { field -> field.type.name == typeName }
            current = current.superclass
        }
        if (candidates.size != 1) {
            error("$label field count=${candidates.size}, expected=1")
        }
        return candidates.single().apply { isAccessible = true }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun resourceEntryName(activity: Activity, view: View): String =
        runCatching { activity.resources.getResourceEntryName(view.id) }
            .getOrDefault("0x${view.id.toString(16)}")

    private fun log(message: String) {
        XposedCompat.log("[$logTag.BottomBarThemeRefresh] $message")
    }

    private fun logD(message: String) {
        XposedCompat.logD("[$logTag.BottomBarThemeRefresh] $message")
    }
}
