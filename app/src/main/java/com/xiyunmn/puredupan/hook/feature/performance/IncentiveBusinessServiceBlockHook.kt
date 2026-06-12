package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks the incentive business service before it starts schedulers and ad reward jobs.
 */
object IncentiveBusinessServiceBlockHook {
    private val hookState = HookState()

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IncentiveBusinessServiceBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val serviceClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.INCENTIVE_BUSINESS_SERVICE,
                cl,
            ) ?: run {
                XposedCompat.log("[IncentiveBusinessServiceBlockHook] BusinessService NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0

            XposedCompat.findMethodOrNull(
                serviceClass,
                StableBaiduPanHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_CREATE_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[IncentiveBusinessServiceBlockHook] onCreate blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[IncentiveBusinessServiceBlockHook] onCreate NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                StableBaiduPanHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_START_COMMAND_METHOD,
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
                            "[IncentiveBusinessServiceBlockHook] onStartCommand blocked: " +
                                "action=${intent?.action}, categories=${intent?.categories}",
                        )
                        START_NOT_STICKY
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[IncentiveBusinessServiceBlockHook] onStartCommand NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                StableBaiduPanHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_BIND_METHOD,
                Intent::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[IncentiveBusinessServiceBlockHook] onBind blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[IncentiveBusinessServiceBlockHook] onBind NOT FOUND")

            if (installedCount == 0) {
                XposedCompat.log("[IncentiveBusinessServiceBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[IncentiveBusinessServiceBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[IncentiveBusinessServiceBlockHook] FAILED: ${e.message}")
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
            XposedCompat.logD("[IncentiveBusinessServiceBlockHook] stopSelf ignored: ${e.message}")
        }
    }



    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isIncentiveBusinessServiceDisabled
}
