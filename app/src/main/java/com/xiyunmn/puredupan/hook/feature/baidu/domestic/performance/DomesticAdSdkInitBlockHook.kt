package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * Blocks ad SDK download services before they initialize large runtime delegates.
 */
object DomesticAdSdkInitBlockHook {
    private val hookState = HookState()

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticAdSdkInitBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            for (className in BaiduDomesticHookPoints.AD_SDK_DOWNLOAD_SERVICE_CLASSES) {
                val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                    XposedCompat.log("[DomesticAdSdkInitBlockHook] $className NOT FOUND")
                    continue
                }

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduDomesticHookPoints.AD_SDK_SERVICE_ON_CREATE_METHOD,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[DomesticAdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onCreate blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[DomesticAdSdkInitBlockHook] $className.onCreate NOT FOUND")

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduDomesticHookPoints.AD_SDK_SERVICE_ON_START_COMMAND_METHOD,
                    Intent::class.java,
                    Integer.TYPE,
                    Integer.TYPE,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            val startId = chain.args.getOrNull(2) as? Int
                            stopSelfQuietly(chain.thisObject, startId)
                            XposedCompat.logD(
                                "[DomesticAdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onStartCommand blocked",
                            )
                            START_NOT_STICKY
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[DomesticAdSdkInitBlockHook] $className.onStartCommand NOT FOUND")

                XposedCompat.findMethodOrNull(
                    clazz,
                    BaiduDomesticHookPoints.AD_SDK_SERVICE_ON_BIND_METHOD,
                    Intent::class.java,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            stopSelfQuietly(chain.thisObject)
                            XposedCompat.logD(
                                "[DomesticAdSdkInitBlockHook] ${chain.thisObject.javaClass.name}.onBind blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } ?: XposedCompat.log("[DomesticAdSdkInitBlockHook] $className.onBind NOT FOUND")
            }

            if (installedCount == 0) {
                XposedCompat.log("[DomesticAdSdkInitBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[DomesticAdSdkInitBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[DomesticAdSdkInitBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticAdSdkInitBlockHook] FAILED: ${e.message}")
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
            XposedCompat.logD("[DomesticAdSdkInitBlockHook] stopSelf ignored: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isAdSdkInitDisabled
}
