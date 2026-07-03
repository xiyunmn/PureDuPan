package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticThumbnailOperatorDexKitResolver
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints

/**
 * Blocks the client-compute thumbnail operator before it starts the operator process.
 */
object DomesticThumbnailOperatorServiceBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            val dexKitClientComputeInstalled = hookDexKitClientComputeInit(cl)
            installedCount += if (dexKitClientComputeInstalled > 0) {
                dexKitClientComputeInstalled
            } else {
                hookClientComputeInit(cl)
            }

            val dexKitAddJobInstalled = hookDexKitAddJob(cl)
            installedCount += if (dexKitAddJobInstalled > 0) dexKitAddJobInstalled else hookAddJob(cl)

            if (installedCount == 0) {
                XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] no hooks installed")
                hookState.reset()
                return
            }

            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookClientComputeInit(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val managerClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.CLIENT_COMPUTE_MANAGER,
            cl,
        )
        if (managerClass == null) {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] ClientComputeManager NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            managerClass,
            BaiduDomesticHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD,
            Context::class.java,
        ) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] ClientComputeManager.init NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD(
                    "[DomesticThumbnailOperatorServiceBlockHook] ClientComputeManager.init blocked",
                )
                false
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookAddJob(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val utilClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.THUMBNAIL_OPERATOR_UTIL,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil NOT FOUND")
            return 0
        }
        val compressBeanClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.TERMINALCALC_COMPRESS_BEAN,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] CompressBean NOT FOUND")
            return 0
        }
        val configCompressImageClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.CONFIG_COMPRESS_IMAGE,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] ConfigCompressImage NOT FOUND")
            return 0
        }

        val method = XposedCompat.findMethodOrNull(
            utilClass,
            BaiduDomesticHookPoints.THUMBNAIL_OPERATOR_UTIL_ADD_JOB_METHOD,
            Context::class.java,
            compressBeanClass,
            configCompressImageClass,
            String::class.java,
        )
        if (method == null) {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil.addJob NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[DomesticThumbnailOperatorServiceBlockHook] ThumbnailOperatorUtil.addJob blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookDexKitClientComputeInit(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = DomesticThumbnailOperatorDexKitResolver.resolveClientComputeInit(cl) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] DexKit ClientComputeManager.init NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD(
                    "[DomesticThumbnailOperatorServiceBlockHook] DexKit ClientComputeManager.init blocked",
                )
                false
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookDexKitAddJob(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = DomesticThumbnailOperatorDexKitResolver.resolveThumbnailAddJob(cl) ?: run {
            XposedCompat.log("[DomesticThumbnailOperatorServiceBlockHook] DexKit ThumbnailOperatorUtil.addJob NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[DomesticThumbnailOperatorServiceBlockHook] DexKit ThumbnailOperatorUtil.addJob blocked")
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
