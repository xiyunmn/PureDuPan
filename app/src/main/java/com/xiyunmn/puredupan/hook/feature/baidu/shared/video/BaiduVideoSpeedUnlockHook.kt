package com.xiyunmn.puredupan.hook.feature.baidu.shared.video

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduVideoSpeedHookPoints
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Unlocks online video playback speed for non-VIP users.
 *
 * Primary gate: MemberPrivilegeContext.privilegeMediaSpeedEnable (stable across hosts).
 * Insurance gates: VideoSpeedUpPresent / VideoPrivilege boolean methods, resolved via
 * DexKit first with stable-name fallback.
 */
object BaiduVideoSpeedUnlockHook {
    private const val TAG = "BaiduVideoSpeedUnlockHook"
    private val hookState = HookState()

    fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        var installed = 0
        try {
            installed += hookPrivilegeMediaSpeedEnable(mod, cl)
            installed += hookPresentBooleanGates(mod, cl)
            installed += hookVideoPrivilegeGates(mod, cl)

            if (installed == 0) {
                XposedCompat.logW("[$TAG] no speed unlock targets found")
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

    private fun hookPrivilegeMediaSpeedEnable(mod: XposedModule, cl: ClassLoader): Int {
        var count = 0
        val methods = linkedSetOf<Method>()

        listOf(
            BaiduVideoSpeedHookPoints.MEMBER_PRIVILEGE_CONTEXT,
            BaiduVideoSpeedHookPoints.MEMBER_PRIVILEGE_CONTEXT_COMPANION,
        ).forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            clazz.declaredMethods
                .filter { method ->
                    method.name == BaiduVideoSpeedHookPoints.PRIVILEGE_MEDIA_SPEED_ENABLE_METHOD &&
                        method.parameterTypes.isEmpty() &&
                        isBooleanLikeReturn(method.returnType)
                }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
        }

        methods.forEach { method ->
            if (installBooleanForceTrue(mod, method, "privilegeMediaSpeedEnable")) {
                count++
            }
        }
        if (count == 0) {
            XposedCompat.logW("[$TAG] privilegeMediaSpeedEnable NOT FOUND")
        }
        return count
    }

    private fun hookPresentBooleanGates(mod: XposedModule, cl: ClassLoader): Int {
        var count = 0
        val online = BaiduVideoSpeedUnlockDexKitResolver.resolveIsSpeedUpOnlineEnable(cl)
        if (online != null && installBooleanForceTrue(mod, online, "isSpeedUpOnlineEnable")) {
            count++
        } else {
            XposedCompat.logD(
                "[$TAG] isSpeedUpOnlineEnable unresolved " +
                    "(privilegeMediaSpeedEnable may still cover personal path)",
            )
        }

        val privilege = BaiduVideoSpeedUnlockDexKitResolver.resolveHasSpeedPrivilege(cl)
        if (privilege != null && installBooleanForceTrue(mod, privilege, "hasSpeedPrivilege")) {
            count++
        } else {
            XposedCompat.logD(
                "[$TAG] hasSpeedPrivilege unresolved " +
                    "(privilegeMediaSpeedEnable may still cover personal path)",
            )
        }
        return count
    }

    private fun hookVideoPrivilegeGates(mod: XposedModule, cl: ClassLoader): Int {
        var count = 0
        BaiduVideoSpeedUnlockDexKitResolver.resolveVideoPrivilegeOnlineSpeedEnable(cl)?.let { method ->
            if (installBooleanForceTrue(mod, method, "onLineSpeedEnable")) {
                count++
            }
        }
        BaiduVideoSpeedUnlockDexKitResolver.resolveVideoPrivilegeSpeedEnable(cl)?.let { method ->
            if (installBooleanForceTrue(mod, method, "speedEnable")) {
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

    private fun isEnabled(): Boolean = HookSettings.isVideoSpeedUnlockEnabled
}
