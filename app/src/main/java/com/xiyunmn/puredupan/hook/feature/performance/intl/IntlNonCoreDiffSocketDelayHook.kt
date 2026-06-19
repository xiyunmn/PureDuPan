package com.xiyunmn.puredupan.hook.feature.performance.intl

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.jvm.functions.Function1

internal object IntlNonCoreDiffSocketDelayHook {
    private const val SOCKET_MANAGER_CLASS_NAME = "com.baidu.netdisk.socket.____"
    private const val MAIN_ACTIVITY_CLASS_NAME = "com.baidu.netdisk.ui.MainActivity"
    private const val HOME_STABLE_RESTORE_DELAY_MS = 2500L

    private const val CLOUD_FILE_DIFF_ACTION = "cloudfile_diff_action"
    private const val CLOUD_FILE_DIFF_ACTION_2 = "cloud_file_diff_acton"
    private const val CLOUD_FILE_DIFF_CHECK_ACTION = "cloud_file_diff_check_action"
    private const val CLOUD_IMAGE_DIFF_ACTION = "cloud_image_diff_acton"
    private const val CLOUD_VIDEO_DIFF_ACTION = "cloud_video_diff_acton"
    private const val SEARCH_DIFF_ACTION = "search_diff_acton"

    private val imageEntryActivityClassNames = listOf(
        "com.baidu.netdisk.cloudimage.ui.view.AlbumActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumServiceActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumTimelineActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumTimelineFromShortcutActivity",
        "com.baidu.netdisk.cloudimage.ui.view.FoundAlbumListActivity",
        "com.baidu.netdisk.cloudimage.ui.view.FoundAlbumDetailActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.ImageSearchActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchPhotoActivity",
    )

    private val videoEntryActivityClassNames = listOf(
        "com.baidu.netdisk.servicepage.video.ui.VideoServiceActivity",
        "com.baidu.netdisk.servicepage.video.ui.VideoTimeRecentActivity",
        "com.baidu.netdisk.servicepage.video.ui.VideoCompilationsActivity",
        "com.baidu.netdisk.video.VideoPlayerActivity",
        "com.baidu.netdisk.video.VerticalVideoPlayerActivity",
        "com.baidu.netdisk.ui.VideoLauncherActivity",
    )

    private val searchEntryActivityClassNames = listOf(
        "com.baidu.netdisk.ui.cloudfile.HomeSearchActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchCategoryActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchDirectoryActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.ImageSearchActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchPhotoActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchTimeLineActivity",
    )

    private data class PendingRegistration(
        val receiver: Any,
        val callback: Any,
    )

    private data class ActionState(
        val action: String,
        var pending: PendingRegistration? = null,
        var skipped: Boolean = false,
        var restored: Boolean = false,
        var restoring: Boolean = false,
        var skipCount: Int = 0,
        var restoreCount: Int = 0,
    )

    private val hookState = HookState()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val lock = Any()
    private val actionStates = linkedMapOf(
        CLOUD_IMAGE_DIFF_ACTION to ActionState(CLOUD_IMAGE_DIFF_ACTION),
        CLOUD_VIDEO_DIFF_ACTION to ActionState(CLOUD_VIDEO_DIFF_ACTION),
        SEARCH_DIFF_ACTION to ActionState(SEARCH_DIFF_ACTION),
    )

    @Volatile private var registerMethod: Method? = null
    @Volatile private var homeStableRestoreScheduled = false

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = resolveSocketRegisterMethod(cl)
            if (method == null) {
                hookState.reset()
                XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] socket register method NOT FOUND")
                return
            }
            registerMethod = method

            mod.hook(method).intercept { chain ->
                val action = chain.args.getOrNull(0) as? String
                val callback = chain.args.getOrNull(1)
                val receiver = chain.thisObject
                if (action == null || callback == null || receiver == null) {
                    return@intercept chain.proceed()
                }

                if (isCoreAction(action)) {
                    XposedCompat.logD("[IntlNonCoreDiffSocketDelayHook] allow core diff socket: action=$action")
                    return@intercept chain.proceed()
                }

                val state = synchronized(lock) { actionStates[action] }
                if (state == null || !shouldDelay(state)) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    state.pending = PendingRegistration(receiver, callback)
                    state.skipped = true
                    state.skipCount++
                }
                XposedCompat.log(
                    "[IntlNonCoreDiffSocketDelayHook] delayed non-core diff socket: " +
                        "action=$action, skipCount=${state.skipCount}",
                )
                null
            }

            val homeHooked = hookHomeStableRestoreSignal(cl)
            val imageSignals = hookEntryRestoreSignals(
                cl,
                imageEntryActivityClassNames,
                CLOUD_IMAGE_DIFF_ACTION,
                "image_entry",
            )
            val videoSignals = hookEntryRestoreSignals(
                cl,
                videoEntryActivityClassNames,
                CLOUD_VIDEO_DIFF_ACTION,
                "video_entry",
            )
            val searchSignals = hookEntryRestoreSignals(
                cl,
                searchEntryActivityClassNames,
                SEARCH_DIFF_ACTION,
                "search_entry",
            )

            XposedCompat.log(
                "[IntlNonCoreDiffSocketDelayHook] hooks INSTALLED: " +
                    "register=${method.declaringClass.name}.${method.name}, " +
                    "homeStable=$homeHooked, imageSignals=$imageSignals, " +
                    "videoSignals=$videoSignals, searchSignals=$searchSignals",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveSocketRegisterMethod(cl: ClassLoader): Method? {
        val socketManagerClass = XposedCompat.findClassOrNull(SOCKET_MANAGER_CLASS_NAME, cl) ?: return null
        if (!isExpectedSocketManagerClass(socketManagerClass)) {
            XposedCompat.logW("[IntlNonCoreDiffSocketDelayHook] socket manager signature mismatch")
            return null
        }

        val candidates = socketManagerClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Function1::class.java.isAssignableFrom(method.parameterTypes[1])
        }
        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] ambiguous socket register method: " +
                    candidates.joinToString { it.name },
            )
            return null
        }
        return candidates.single().apply { isAccessible = true }
    }

    private fun isExpectedSocketManagerClass(clazz: Class<*>): Boolean {
        val requiredActions = setOf(
            CLOUD_FILE_DIFF_ACTION,
            CLOUD_FILE_DIFF_ACTION_2,
            CLOUD_FILE_DIFF_CHECK_ACTION,
            CLOUD_IMAGE_DIFF_ACTION,
            CLOUD_VIDEO_DIFF_ACTION,
            SEARCH_DIFF_ACTION,
        )
        val stringConstants = clazz.declaredFields.mapNotNull { field ->
            runCatching {
                if (field.type == String::class.java && Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    field.get(null) as? String
                } else {
                    null
                }
            }.getOrNull()
        }.toSet()
        if (!stringConstants.containsAll(requiredActions)) return false

        val metadataTokens = metadataTokens(clazz)
        return metadataTokens.contains("mSocketActionHashMap") &&
            metadataTokens.contains("CLOUD_IMAGE_DIFF_ACTION") &&
            metadataTokens.contains("CLOUD_VIDEO_DIFF_ACTION") &&
            metadataTokens.contains("SEARCH_DIFF_ACTION")
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

    private fun isCoreAction(action: String): Boolean =
        action == CLOUD_FILE_DIFF_ACTION ||
            action == CLOUD_FILE_DIFF_ACTION_2 ||
            action == CLOUD_FILE_DIFF_CHECK_ACTION

    private fun shouldDelay(state: ActionState): Boolean {
        if (!isEnabled()) return false
        if (state.restoring || state.restored) return false
        return true
    }

    private fun hookHomeStableRestoreSignal(cl: ClassLoader): Boolean {
        val mod = XposedCompat.module ?: return false
        val mainActivityClass = XposedCompat.findClassOrNull(MAIN_ACTIVITY_CLASS_NAME, cl) ?: run {
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] MainActivity class NOT FOUND")
            return false
        }
        val focusMethod = XposedCompat.findMethodOrNull(
            mainActivityClass,
            "onWindowFocusChanged",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[IntlNonCoreDiffSocketDelayHook] MainActivity.onWindowFocusChanged NOT FOUND")
            return false
        }

        mod.hook(focusMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            val hasFocus = chain.args.firstOrNull() as? Boolean ?: false
            if (hasFocus && activity?.javaClass?.name == MAIN_ACTIVITY_CLASS_NAME) {
                scheduleHomeStableRestore()
            }
            result
        }
        return true
    }

    private fun hookEntryRestoreSignals(
        cl: ClassLoader,
        classNames: List<String>,
        action: String,
        reasonPrefix: String,
    ): Int {
        var installed = 0
        for (className in classNames.distinct()) {
            val activityClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (hookActivityOnCreate(activityClass, "$reasonPrefix:$className") { reason ->
                    restoreActionIfPending(action, reason)
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
        beforeProceed: (String) -> Unit,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java) ?: return false
        mod.hook(method).intercept { chain ->
            beforeProceed(reason)
            chain.proceed()
        }
        return true
    }

    private fun scheduleHomeStableRestore() {
        if (!isEnabled() || homeStableRestoreScheduled || allActionsRestored()) return
        synchronized(lock) {
            if (homeStableRestoreScheduled || allActionsRestored()) return
            homeStableRestoreScheduled = true
        }
        mainHandler.postDelayed({
            homeStableRestoreScheduled = false
            restoreAllPending("home_stable")
        }, HOME_STABLE_RESTORE_DELAY_MS)
        XposedCompat.logD("[IntlNonCoreDiffSocketDelayHook] home stable restore scheduled")
    }

    private fun allActionsRestored(): Boolean = synchronized(lock) {
        actionStates.values.all { it.restored || !it.skipped }
    }

    private fun restoreAllPending(reason: String) {
        actionStates.keys.forEach { action ->
            restoreActionIfPending(action, reason)
        }
    }

    private fun restoreActionIfPending(action: String, reason: String) {
        if (!isEnabled()) return
        val method = registerMethod ?: run {
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] restore skipped: registerMethod missing, " +
                    "action=$action, reason=$reason",
            )
            return
        }
        val restoreState = synchronized(lock) {
            val state = actionStates[action] ?: return
            if (!state.skipped || state.restored) return
            val pending = state.pending ?: return
            state.restored = true
            state.restoring = true
            state.restoreCount++
            state to pending
        }

        val (state, pending) = restoreState
        try {
            method.invoke(pending.receiver, state.action, pending.callback)
            XposedCompat.log(
                "[IntlNonCoreDiffSocketDelayHook] restored non-core diff socket: " +
                    "action=${state.action}, reason=$reason, restoreCount=${state.restoreCount}",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                state.restored = false
                state.restoreCount--
            }
            XposedCompat.logW(
                "[IntlNonCoreDiffSocketDelayHook] restore FAILED: " +
                    "action=${state.action}, reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            synchronized(lock) {
                state.restoring = false
            }
        }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIntlNonCoreDiffSocketDelayed
}
