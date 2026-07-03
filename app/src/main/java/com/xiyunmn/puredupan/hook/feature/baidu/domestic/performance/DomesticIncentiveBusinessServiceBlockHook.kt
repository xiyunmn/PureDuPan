package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Blocks the incentive business service before it starts schedulers and ad reward jobs.
 */
object DomesticIncentiveBusinessServiceBlockHook {
    private val hookState = HookState()

    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val serviceClass = XposedCompat.findClassOrNull(
                BaiduDomesticHookPoints.INCENTIVE_BUSINESS_SERVICE,
                cl,
            ) ?: run {
                XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] BusinessService NOT FOUND")
                hookState.reset()
                return
            }

            var installedCount = 0

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduDomesticHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_CREATE_METHOD,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[DomesticIncentiveBusinessServiceBlockHook] onCreate blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] onCreate NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduDomesticHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_START_COMMAND_METHOD,
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
                            "[DomesticIncentiveBusinessServiceBlockHook] onStartCommand blocked: " +
                                "action=${intent?.action}, categories=${intent?.categories}",
                        )
                        START_NOT_STICKY
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] onStartCommand NOT FOUND")

            XposedCompat.findMethodOrNull(
                serviceClass,
                BaiduDomesticHookPoints.INCENTIVE_BUSINESS_SERVICE_ON_BIND_METHOD,
                Intent::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD("[DomesticIncentiveBusinessServiceBlockHook] onBind blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] onBind NOT FOUND")

            if (installedCount == 0) {
                XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticIncentiveBusinessServiceBlockHook] FAILED: ${e.message}")
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
            XposedCompat.logD("[DomesticIncentiveBusinessServiceBlockHook] stopSelf ignored: ${e.message}")
        }
    }



    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIncentiveBusinessServiceDisabled
}
