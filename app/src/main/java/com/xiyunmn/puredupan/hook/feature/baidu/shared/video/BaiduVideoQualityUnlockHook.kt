package com.xiyunmn.puredupan.hook.feature.baidu.shared.video

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduVideoQualityHookPoints
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Unlocks online video quality selection gates for non-VIP users.
 *
 * Safety scope:
 * - Only video-play quality privileges (HD / FHD / Original)
 * - No global SVIP identity rewrite
 * - No high-speed / ad-skip / export privilege rewrite
 * - Does not forge account cookies or server identity
 *
 * Resolution convention:
 * - DexKit first for canPlayResolution / VideoPrivilege quality methods
 * - Stable class/method names only as verified fallback
 *
 * Note: opening client gates only removes VIP dialogs and allows requesting
 * higher-quality streams. Ordinary 1080 (`M3U8_AUTO_1080`) may still fail if
 * the server rejects the stream; AI path (`M3U8_HQ_1080`) is separate.
 */
object BaiduVideoQualityUnlockHook {
    private const val TAG = "BaiduVideoQualityUnlockHook"
    private val hookState = HookState()

    private val PRIVILEGE_METHODS = listOf(
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_HD_METHOD,
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_FHD_METHOD,
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_ORIGINAL_METHOD,
    )

    fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            installed += hookMemberPrivilegeQualityFlags(mod, cl)
            installed += hookCanPlayResolution(mod, cl)
            installed += hookVideoPrivilegeQualityMethods(mod, cl)

            if (installed == 0) {
                XposedCompat.logW("[$TAG] no quality unlock targets found")
                hookState.reset()
                return
            }
            XposedCompat.log("[$TAG] hook INSTALLED targets=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookMemberPrivilegeQualityFlags(mod: XposedModule, cl: ClassLoader): Int {
        var count = 0
        val methods = linkedSetOf<Method>()

        listOf(
            BaiduVideoQualityHookPoints.MEMBER_PRIVILEGE_CONTEXT,
            BaiduVideoQualityHookPoints.MEMBER_PRIVILEGE_CONTEXT_COMPANION,
        ).forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            clazz.declaredMethods
                .filter { method ->
                    method.name in PRIVILEGE_METHODS &&
                        method.parameterTypes.isEmpty() &&
                        isBooleanLikeReturn(method.returnType)
                }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
        }

        methods.forEach { method ->
            if (installBooleanForceTrue(mod, method, method.name)) {
                count++
            }
        }
        if (count == 0) {
            XposedCompat.logW("[$TAG] MemberPrivilege video quality flags NOT FOUND")
        }
        return count
    }

    private fun hookCanPlayResolution(mod: XposedModule, cl: ClassLoader): Int {
        val method = BaiduVideoQualityUnlockDexKitResolver.resolveCanPlayResolution(cl)
        if (method == null) {
            XposedCompat.logD(
                "[$TAG] canPlayResolution unresolved " +
                    "(MemberPrivilege quality flags may still cover personal path)",
            )
            return 0
        }
        return if (installBooleanForceTrue(mod, method, "canPlayResolution")) 1 else 0
    }

    private fun hookVideoPrivilegeQualityMethods(mod: XposedModule, cl: ClassLoader): Int {
        val methods = BaiduVideoQualityUnlockDexKitResolver.resolveVideoPrivilegeQualityMethods(cl)
        if (methods.isEmpty()) {
            XposedCompat.logW(
                "[$TAG] VideoPrivilege quality methods unresolved " +
                    "(MemberPrivilege quality flags may still cover personal path)",
            )
            return 0
        }
        var count = 0
        methods.forEach { method ->
            val label = when (method.name) {
                BaiduVideoQualityHookPoints.CAN_PLAY_720_METHOD -> "canPlay720"
                BaiduVideoQualityHookPoints.IS_SUPPORT_FHD_METHOD -> "isSupportFHD"
                BaiduVideoQualityHookPoints.PLAY_HD_ENABLED_METHOD -> "playHdEnabled"
                BaiduVideoQualityHookPoints.PLAY_FHD_ENABLED_METHOD -> "playFhdEnabled"
                BaiduVideoQualityHookPoints.PLAY_ORIGINAL_ENABLED_METHOD -> "playOriginalEnabled"
                else -> "qualityGate:${method.name}"
            }
            if (installBooleanForceTrue(mod, method, label)) {
                count++
            }
        }
        return count
    }

    private fun installBooleanForceTrue(
        mod: XposedModule,
        method: Method,
        label: String,
    ): Boolean {
        return try {
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD("[$TAG] $label forced true")
                    booleanReturnValue(method, true)
                } else {
                    chain.proceed()
                }
            }
            XposedCompat.logD("[$TAG] hooked $label: ${method.declaringClass.name}.${method.name}")
            true
        } catch (e: Exception) {
            XposedCompat.logW("[$TAG] failed to hook $label: ${e.message}")
            false
        }
    }

    private fun booleanReturnValue(method: Method, value: Boolean): Any {
        return if (method.returnType == Boolean::class.javaPrimitiveType) {
            value
        } else {
            java.lang.Boolean.valueOf(value)
        }
    }

    private fun isBooleanLikeReturn(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType ||
            type == Boolean::class.javaObjectType ||
            type.name == "java.lang.Boolean"
    }

    private fun isEnabled(): Boolean = HookSettings.isVideoQualityUnlockEnabled
}
