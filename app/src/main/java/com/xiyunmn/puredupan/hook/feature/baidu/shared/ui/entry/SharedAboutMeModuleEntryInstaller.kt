package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import io.github.libxposed.api.XposedModule

internal object SharedAboutMeModuleEntryInstaller {
    private val DEFAULT_SCAN_ICON_ID_NAMES = listOf(
        "self_qrcode_scan_icon",
        "self_qrcode_entrance_icon",
        "self_qrcode_entrance_icon_new_pos",
    )

    private val hookStates = linkedMapOf<String, HookState>()

    fun hook(
        cl: ClassLoader,
        tag: String,
        scanIconIdNames: List<String> = DEFAULT_SCAN_ICON_ID_NAMES,
    ) {
        val mod = XposedCompat.module ?: return
        val hookState = hookStates.getOrPut(tag) { HookState() }
        if (!hookState.markInstalled()) return

        try {
            val activityClassNames = BaiduFeatureRuntime.currentAboutMeActivityClassNames()
                .ifEmpty {
                    BaiduFeatureRuntime.currentAboutMeActivityClassName()
                        ?.let(::listOf)
                        .orEmpty()
                }
            if (activityClassNames.isEmpty()) {
                hookState.reset()
                XposedCompat.log("[$tag] AboutMeActivity host capability missing")
                return
            }

            var installed = 0
            for (activityClassName in activityClassNames) {
                installed += hookActivityOnCreate(
                    mod = mod,
                    cl = cl,
                    tag = tag,
                    activityClassName = activityClassName,
                    scanIconIdNames = scanIconIdNames,
                )
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$tag] AboutMeActivity hooks NOT INSTALLED")
                return
            }

            XposedCompat.log("[$tag] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$tag] install FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookActivityOnCreate(
        mod: XposedModule,
        cl: ClassLoader,
        tag: String,
        activityClassName: String,
        scanIconIdNames: List<String>,
    ): Int {
        val activityClass = XposedCompat.findClassOrNull(
            activityClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[$tag] AboutMeActivity class NOT FOUND: $activityClassName")
            return 0
        }

        var installed = 0
        val onCreateMethod = XposedCompat.findMethodOrNull(
            activityClass,
            "onCreate",
            Bundle::class.java,
        )
        if (onCreateMethod == null) {
            XposedCompat.log("[$tag] $activityClassName.onCreate NOT FOUND")
        } else {
            mod.hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                scheduleBindScanIconLongPress(
                    activity = chain.thisObject as? Activity,
                    tag = tag,
                    scanIconIdNames = scanIconIdNames,
                )
                result
            }
            XposedCompat.log("[$tag] hook INSTALLED: $activityClassName.onCreate")
            installed += 1
        }

        val onResumeMethod = XposedCompat.findMethodOrNull(activityClass, "onResume")
        if (onResumeMethod == null) {
            XposedCompat.logD("[$tag] $activityClassName.onResume NOT FOUND")
        } else {
            mod.hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                scheduleBindScanIconLongPress(
                    activity = chain.thisObject as? Activity,
                    tag = tag,
                    scanIconIdNames = scanIconIdNames,
                )
                result
            }
            XposedCompat.log("[$tag] hook INSTALLED: $activityClassName.onResume")
            installed += 1
        }

        return installed
    }

    private fun scheduleBindScanIconLongPress(
        activity: Activity?,
        tag: String,
        scanIconIdNames: List<String>,
    ) {
        if (activity == null) return
        bindScanIconLongPress(
            activity = activity,
            tag = tag,
            scanIconIdNames = scanIconIdNames,
            verboseMiss = false,
        )
        activity.window?.decorView?.post {
            bindScanIconLongPress(
                activity = activity,
                tag = tag,
                scanIconIdNames = scanIconIdNames,
                verboseMiss = true,
            )
        }
    }

    private fun bindScanIconLongPress(
        activity: Activity?,
        tag: String,
        scanIconIdNames: List<String>,
        verboseMiss: Boolean,
    ) {
        if (activity == null) return

        for (entryName in scanIconIdNames) {
            val scanIconView = findViewByEntryName(activity, entryName) ?: continue
            ModuleEntryBindingSupport.bindLongPressToSettings(
                view = scanIconView,
                classLoader = activity.classLoader,
                tag = tag,
                entryName = entryName,
            )
            return
        }

        if (verboseMiss) {
            XposedCompat.logD("[$tag] scan icon view not found: ${scanIconIdNames.joinToString()}")
        }
    }

    private fun findViewByEntryName(activity: Activity, entryName: String): View? {
        val resId = activity.resources.getIdentifier(
            entryName,
            "id",
            activity.packageName,
        )
        if (resId == 0) return null

        return activity.findViewById(resId)
    }
}
