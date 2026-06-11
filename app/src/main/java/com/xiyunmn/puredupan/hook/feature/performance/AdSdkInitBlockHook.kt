package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks ad SDK download services before they initialize large runtime delegates.
 */
object AdSdkInitBlockHook {
    @Volatile private var hooked = false

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[AdSdkInitBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            var installedCount = 0
            for (className in StableBaiduPanHookPoints.AD_SDK_DOWNLOAD_SERVICE_CLASSES) {
                val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                    XposedCompat.log("[AdSdkInitBlockHook] $className NOT FOUND")
                    continue
                }

                XposedCompat.findMethodOrNull(
                    clazz,
                    StableBaiduPanHookPoints.AD_SDK_SERVICE_ON_CREATE_METHOD,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[AdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onCreate blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[AdSdkInitBlockHook] $className.onCreate NOT FOUND")

                XposedCompat.findMethodOrNull(
                    clazz,
                    StableBaiduPanHookPoints.AD_SDK_SERVICE_ON_START_COMMAND_METHOD,
                    Intent::class.java,
                    Integer.TYPE,
                    Integer.TYPE,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            val startId = chain.args.getOrNull(2) as? Int
                            stopSelfQuietly(chain.thisObject, startId)
                            XposedCompat.logD(
                                "[AdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onStartCommand blocked",
                            )
                            START_NOT_STICKY
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[AdSdkInitBlockHook] $className.onStartCommand NOT FOUND")

                XposedCompat.findMethodOrNull(
                    clazz,
                    StableBaiduPanHookPoints.AD_SDK_SERVICE_ON_BIND_METHOD,
                    Intent::class.java,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[AdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onBind blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[AdSdkInitBlockHook] $className.onBind NOT FOUND")
            }

            if (installedCount == 0) {
                XposedCompat.log("[AdSdkInitBlockHook] no hooks installed")
                resetHooked()
                return
            }

            XposedCompat.log("[AdSdkInitBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[AdSdkInitBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
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
            XposedCompat.logD("[AdSdkInitBlockHook] stopSelf ignored: ${t.message}")
        }
    }

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isAdSdkInitDisabled
}
