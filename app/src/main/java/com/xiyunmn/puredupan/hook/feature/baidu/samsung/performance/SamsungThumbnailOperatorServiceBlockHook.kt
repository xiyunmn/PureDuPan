package com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

/**
 * Blocks Samsung host client-compute thumbnail jobs before operator startup and job enqueue.
 */
internal object SamsungThumbnailOperatorServiceBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            val managerClass = XposedCompat.findClassOrNull(
                BaiduSamsungHookPoints.CLIENT_COMPUTE_MANAGER,
                cl,
            ) ?: run {
                XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] ClientComputeManager NOT FOUND")
                hookState.reset()
                return
            }

            XposedCompat.findMethodOrNull(
                managerClass,
                BaiduSamsungHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD,
                Context::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        XposedCompat.logD(
                            "[SamsungThumbnailOperatorServiceBlockHook] ClientComputeManager.init blocked",
                        )
                        false
                    } else {
                        chain.proceed()
                    }
                }
                installedCount += 1
            } ?: XposedCompat.log(
                "[SamsungThumbnailOperatorServiceBlockHook] ClientComputeManager.init NOT FOUND",
            )

            installedCount += hookAddJob(cl)

            if (installedCount == 0) {
                XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log(
                "[SamsungThumbnailOperatorServiceBlockHook] hooks INSTALLED: count=$installedCount",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookAddJob(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val utilClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.THUMBNAIL_OPERATOR_UTIL,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil NOT FOUND")
            return 0
        }
        val compressBeanClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.TERMINALCALC_COMPRESS_BEAN,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] CompressBean NOT FOUND")
            return 0
        }
        val configCompressImageClass = XposedCompat.findClassOrNull(
            BaiduSamsungHookPoints.CONFIG_COMPRESS_IMAGE,
            cl,
        ) ?: run {
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] ConfigCompressImage NOT FOUND")
            return 0
        }

        val method = XposedCompat.findMethodOrNull(
            utilClass,
            BaiduSamsungHookPoints.THUMBNAIL_OPERATOR_UTIL_ADD_JOB_METHOD,
            Context::class.java,
            compressBeanClass,
            configCompressImageClass,
            String::class.java,
        )
        if (method == null) {
            XposedCompat.log("[SamsungThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil.addJob NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[SamsungThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil.addJob blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isThumbnailOperatorServiceDisabled
}
