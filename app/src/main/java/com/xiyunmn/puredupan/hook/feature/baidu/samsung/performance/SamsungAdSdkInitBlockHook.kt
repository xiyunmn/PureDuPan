package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks Samsung host ad SDK download services before they initialize runtime delegates.
 */
internal object SamsungAdSdkInitBlockHook {
    private val hookState = HookState()

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungAdSdkInitBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            for (className in BaiduSamsungHookPoints.AD_SDK_DOWNLOAD_SERVICE_CLASSES) {
                val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                    XposedCompat.log("[SamsungAdSdkInitBlockHook] $className NOT FOUND")
                    continue
                }

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduSamsungHookPoints.AD_SDK_SERVICE_ON_CREATE_METHOD,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[SamsungAdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onCreate blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[SamsungAdSdkInitBlockHook] $className.onCreate NOT FOUND")

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduSamsungHookPoints.AD_SDK_SERVICE_ON_START_COMMAND_METHOD,
                    Intent::class.java,
                    Integer.TYPE,
                    Integer.TYPE,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            val startId = chain.args.getOrNull(2) as? Int
                            stopSelfQuietly(chain.thisObject, startId)
                            XposedCompat.logD(
                                "[SamsungAdSdkInitBlockHook] " +
                                    "${chain.thisObject.javaClass.name}.onStartCommand blocked",
                            )
                            START_NOT_STICKY
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log(
                    "[SamsungAdSdkInitBlockHook] $className.onStartCommand NOT FOUND",
                )

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduSamsungHookPoints.AD_SDK_SERVICE_ON_BIND_METHOD,
                    Intent::class.java,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[SamsungAdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onBind blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[SamsungAdSdkInitBlockHook] $className.onBind NOT FOUND")
            }

            if (installedCount == 0) {
                XposedCompat.log("[SamsungAdSdkInitBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[SamsungAdSdkInitBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log(
                "[SamsungAdSdkInitBlockHook] FAILED (reflection): " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungAdSdkInitBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
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
        } catch (e: Exception) {
            XposedCompat.logD("[SamsungAdSdkInitBlockHook] stopSelf ignored: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isAdSdkInitDisabled
}
