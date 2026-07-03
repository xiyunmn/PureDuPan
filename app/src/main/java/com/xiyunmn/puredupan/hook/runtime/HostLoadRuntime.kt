package com.xiyunmn.puredupan.hook.runtime

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.dexkit.DexKitCacheWarmUp
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.host.HostDexKitRuntimeInfo
import com.xiyunmn.puredupan.hook.host.HostProfile
import com.xiyunmn.puredupan.hook.host.HostRuntimeState
import com.xiyunmn.puredupan.hook.plan.HookInstallPlan
import com.xiyunmn.puredupan.hook.plan.HookInstallPlanner

internal class HostLoadSession(
    private val host: HostProfile,
) {
    val hostId: String get() = host.id
    val packageName: String get() = host.packageName

    fun shouldHandleProcess(processName: String): Boolean =
        HookInstallPlanner.shouldHandleProcess(host, processName)

    fun shouldInstallAttachHook(processName: String): Boolean =
        HookInstallPlanner.shouldInstallAttachHook(host, processName)

    fun postAttachPlan(processName: String, settings: SettingsSnapshot): HookInstallPlan =
        HookInstallPlanner.postAttachPlan(
            host = host,
            processName = processName,
            settings = settings,
        )

    fun startDexKitWarmUp(
        processName: String,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
        onWarmUpFinished: (() -> Unit)? = null,
    ) {
        DexKitCacheWarmUp.startIfNeeded(
            host = DexKitHostContext.from(HostDexKitRuntimeInfo.from(host)),
            settings = settings,
            processName = processName,
            classLoader = classLoader,
            onWarmUpFinished = onWarmUpFinished,
        )
    }
}

internal object HostLoadRuntime {
    fun resolve(packageName: String): HostLoadSession? {
        return HostRuntimeState.profileForPackage(packageName)?.let(::HostLoadSession)
    }
}
