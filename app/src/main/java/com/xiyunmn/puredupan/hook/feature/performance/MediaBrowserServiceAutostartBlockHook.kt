package com.xiyunmn.puredupan.hook.feature.performance

import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks automatic media-browser starts while allowing user-initiated audio binding.
 */
object MediaBrowserServiceAutostartBlockHook {
    @Volatile private var hooked = false

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val mediaServiceClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.NETDISK_MEDIA_BROWSER_SERVICE,
                cl,
            ) ?: run {
                XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] NetdiskMediaBrowserService NOT FOUND")
                resetHooked()
                return
            }
            val audioPlayServiceClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.AUDIO_PLAY_SERVICE,
                cl,
            ) ?: run {
                XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] AudioPlayService NOT FOUND")
                resetHooked()
                return
            }

            var installedCount = 0
            installedCount += hookFloatViewStartupTask(cl)
            installedCount += hookAudioCircleViewHelperInit(cl)
            installedCount += hookAudioCircleViewManagerBind(cl)
            installedCount += hookOnStartCommand(mediaServiceClass)
            installedCount += hookOnGetRoot(mediaServiceClass)
            installedCount += hookOnBind(audioPlayServiceClass)

            if (installedCount == 0) {
                XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] no hooks installed")
                resetHooked()
                return
            }

            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookFloatViewStartupTask(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.FLOAT_VIEW_STARTUP_TASK,
            cl,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] FloatViewStartupTask NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] initAudioCircleView NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD(
                    "[MediaBrowserServiceAutostartBlockHook] FloatViewStartupTask.initAudioCircleView blocked",
                )
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookAudioCircleViewHelperInit(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.AUDIO_CIRCLE_VIEW_HELPER,
            cl,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] AudioCircleViewHelper NOT FOUND")
            return 0
        }
        var count = 0

        XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.AUDIO_CIRCLE_VIEW_HELPER_INIT_METHOD,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[MediaBrowserServiceAutostartBlockHook] AudioCircleViewHelper.init blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        } ?: XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] AudioCircleViewHelper.init NOT FOUND")

        XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.AUDIO_CIRCLE_VIEW_HELPER_INIT_METHOD,
            Application::class.java,
        )?.let { method ->
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[MediaBrowserServiceAutostartBlockHook] AudioCircleViewHelper.init(Application) blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        } ?: XposedCompat.log(
            "[MediaBrowserServiceAutostartBlockHook] AudioCircleViewHelper.init(Application) NOT FOUND",
        )

        return count
    }

    private fun hookAudioCircleViewManagerBind(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(
            StableBaiduPanHookPoints.AUDIO_CIRCLE_VIEW_MANAGER,
            cl,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] AudioCircleViewManager NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            StableBaiduPanHookPoints.AUDIO_CIRCLE_VIEW_MANAGER_BIND_PLAYER_SERVICE_METHOD,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] bindPlayerService NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD(
                    "[MediaBrowserServiceAutostartBlockHook] AudioCircleViewManager.bindPlayerService blocked",
                )
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookOnStartCommand(mediaServiceClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            mediaServiceClass,
            StableBaiduPanHookPoints.NETDISK_MEDIA_BROWSER_SERVICE_ON_START_COMMAND_METHOD,
            Intent::class.java,
            Integer.TYPE,
            Integer.TYPE,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] onStartCommand NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (!isEnabled()) {
                chain.proceed()
            } else {
                val intent = chain.args.getOrNull(0) as? Intent
                val action = intent?.action
                if (isManualAudioAction(action)) {
                    chain.proceed()
                } else {
                    val startId = chain.args.getOrNull(2) as? Int
                    stopSelfQuietly(chain.thisObject, startId)
                    XposedCompat.logD(
                        "[MediaBrowserServiceAutostartBlockHook] onStartCommand blocked: action=$action",
                    )
                    START_NOT_STICKY
                }
            }
        }
        return 1
    }

    private fun hookOnGetRoot(mediaServiceClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            mediaServiceClass,
            StableBaiduPanHookPoints.NETDISK_MEDIA_BROWSER_SERVICE_ON_GET_ROOT_METHOD,
            String::class.java,
            Integer.TYPE,
            Bundle::class.java,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] onGetRoot NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                val clientPackage = chain.args.getOrNull(0) as? String
                stopSelfQuietly(chain.thisObject)
                XposedCompat.logD(
                    "[MediaBrowserServiceAutostartBlockHook] onGetRoot blocked: client=$clientPackage",
                )
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookOnBind(audioPlayServiceClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            audioPlayServiceClass,
            StableBaiduPanHookPoints.AUDIO_PLAY_SERVICE_ON_BIND_METHOD,
            Intent::class.java,
        ) ?: run {
            XposedCompat.log("[MediaBrowserServiceAutostartBlockHook] AudioPlayService.onBind NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (!isEnabled()) {
                chain.proceed()
            } else {
                val intent = chain.args.getOrNull(0) as? Intent
                val action = intent?.action
                if (isManualAudioAction(action) || !isNetdiskMediaBrowserService(chain.thisObject)) {
                    chain.proceed()
                } else {
                    stopSelfQuietly(chain.thisObject)
                    XposedCompat.logD(
                        "[MediaBrowserServiceAutostartBlockHook] onBind blocked: action=$action",
                    )
                    null
                }
            }
        }
        return 1
    }

    private fun isManualAudioAction(action: String?): Boolean {
        return action == StableBaiduPanHookPoints.AUDIO_PLAY_SERVICE_BIND_ACTION
    }

    private fun isNetdiskMediaBrowserService(service: Any?): Boolean {
        return service?.javaClass?.name == StableBaiduPanHookPoints.NETDISK_MEDIA_BROWSER_SERVICE
    }

    private fun stopSelfQuietly(service: Any?, startId: Int? = null) {
        if (service == null) return
        try {
            if (startId != null) {
                XposedCompat.callMethod(service, "stopSelf", startId)
            } else {
                XposedCompat.callMethod(service, "stopSelf")
            }
        } catch (t: Throwable) {
            XposedCompat.logD("[MediaBrowserServiceAutostartBlockHook] stopSelf ignored: ${t.message}")
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isMediaBrowserServiceAutostartDisabled
}
