package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks the Samsung host incentive business service before it starts schedulers and ad reward jobs.
 */
internal object SamsungIncentiveBusinessServiceBlockHook {
    private val hookState = HookState()

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val serviceClass = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.INCENTIVE_BUSINESS_SERVICE,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] BusinessService NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduSamsungHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_CREATE_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[SamsungIncentiveBusinessServiceBlockHook] onCreate blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] onCreate NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduSamsungHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_START_COMMAND_METHOD,
                Intent::class.java,
                Integer.TYPE,
                Integer.TYPE,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        val intent = chain.args.getOrNull(0) as? Intent
                        val startId = chain.args.getOrNull(2) as? Int
                        stopSelfQuietly(chain.thisObject, startId)
                        XposedCompat.logD(
                            "[SamsungIncentiveBusinessServiceBlockHook] onStartCommand blocked: " +
                                "action=${intent?.action}, categories=${intent?.categories}",
                        )
                        START_NOT_STICKY
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] onStartCommand NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduSamsungHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_BIND_METHOD,
                Intent::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[SamsungIncentiveBusinessServiceBlockHook] onBind blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] onBind NOT FOUND")

            if (installedCount == 0) {
                XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log(
                "[SamsungIncentiveBusinessServiceBlockHook] hooks INSTALLED: count=$installedCount",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungIncentiveBusinessServiceBlockHook] FAILED: ${e.message}")
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
            XposedCompat.logD("[SamsungIncentiveBusinessServiceBlockHook] stopSelf ignored: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIncentiveBusinessServiceDisabled
}
