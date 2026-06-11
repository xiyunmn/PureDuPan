package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import android.content.Intent
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks OEM push SDK components before they initialize vendor push services.
 */
object OemPushServiceBlockHook {
    @Volatile private var hooked = false

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[OemPushServiceBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!tryMarkHooked()) return

        try {
            var installedCount = 0
            installedCount += installOnStartCommandHooks(cl)
            installedCount += installOnCreateHooks(cl)
            installedCount += installOnStartHooks(cl)
            installedCount += installOnBindHooks(cl)
            installedCount += installOnHandleIntentHooks(cl)
            installedCount += installOnReceiveHooks(cl)
            installedCount += installHuaweiMessageHooks(cl)
            installedCount += installHonorMessageHooks(cl)

            if (installedCount == 0) {
                XposedCompat.log("[OemPushServiceBlockHook] no hooks installed")
                resetHooked()
                return
            }

            XposedCompat.log("[OemPushServiceBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[OemPushServiceBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installOnStartCommandHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_ON_START_COMMAND_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_SERVICE_ON_START_COMMAND_METHOD,
                Intent::class.java,
                Integer.TYPE,
                Integer.TYPE,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onStartCommand NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    val startId = chain.args.getOrNull(2) as? Int
                    stopSelfQuietly(chain.thisObject, startId)
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onStartCommand blocked",
                    )
                    START_NOT_STICKY
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installOnCreateHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_ON_CREATE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_SERVICE_ON_CREATE_METHOD,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onCreate NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    stopSelfQuietly(chain.thisObject)
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onCreate blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installOnStartHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_ON_START_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_SERVICE_ON_START_METHOD,
                Intent::class.java,
                Integer.TYPE,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onStart NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    val startId = chain.args.getOrNull(1) as? Int
                    stopSelfQuietly(chain.thisObject, startId)
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onStart blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installOnBindHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_ON_BIND_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_SERVICE_ON_BIND_METHOD,
                Intent::class.java,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onBind NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    stopSelfQuietly(chain.thisObject)
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onBind blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installOnHandleIntentHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_ON_HANDLE_INTENT_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_SERVICE_ON_HANDLE_INTENT_METHOD,
                Intent::class.java,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onHandleIntent NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    stopSelfQuietly(chain.thisObject)
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onHandleIntent blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installOnReceiveHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_RECEIVER_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_RECEIVER_ON_RECEIVE_METHOD,
                Context::class.java,
                Intent::class.java,
            ) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className.onReceive NOT FOUND")
                continue
            }
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onReceive blocked",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }
        return count
    }

    private fun installHuaweiMessageHooks(cl: ClassLoader): Int {
        val remoteMessageClass = XposedCompat.findClassOrNull("com.huawei.hms.push.RemoteMessage", cl) ?: run {
            XposedCompat.log("[OemPushServiceBlockHook] RemoteMessage class NOT FOUND")
            return 0
        }
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_HUAWEI_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                remoteMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } ?: XposedCompat.log("[OemPushServiceBlockHook] $className.onMessageReceived NOT FOUND")

            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onNewToken blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } ?: XposedCompat.log("[OemPushServiceBlockHook] $className.onNewToken NOT FOUND")

            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_TOKEN_ERROR_METHOD,
                Exception::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onTokenError blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } ?: XposedCompat.log("[OemPushServiceBlockHook] $className.onTokenError NOT FOUND")
        }
        return count
    }

    private fun installHonorMessageHooks(cl: ClassLoader): Int {
        val honorMessageClass = XposedCompat.findClassOrNull("com.hihonor.push.sdk.HonorPushDataMsg", cl) ?: run {
            XposedCompat.log("[OemPushServiceBlockHook] HonorPushDataMsg class NOT FOUND")
            return 0
        }
        val mod = XposedCompat.module ?: return 0
        var count = 0
        for (className in StableBaiduPanHookPoints.OEM_PUSH_HONOR_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                honorMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } ?: XposedCompat.log("[OemPushServiceBlockHook] $className.onMessageReceived NOT FOUND")

            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlockHook] ${chain.thisObject.javaClass.name}.onNewToken blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } ?: XposedCompat.log("[OemPushServiceBlockHook] $className.onNewToken NOT FOUND")
        }
        return count
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
            XposedCompat.logD("[OemPushServiceBlockHook] stopSelf ignored: ${t.message}")
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isOemPushServiceDisabled
}
