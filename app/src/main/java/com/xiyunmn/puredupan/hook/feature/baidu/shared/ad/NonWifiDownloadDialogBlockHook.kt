package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal object NonWifiDownloadDialogBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isNonWifiDownloadDialogBlocked) {
            XposedCompat.log("[NonWifiDownloadDialogBlockHook] skipped: config disabled")
            return
        }
        if (!hookState.markInstalled()) return

        try {
            val listenerClass = XposedCompat.findClassOrNull(
                BaiduTransferHookPoints.DIALOG_CTR_LISTENER,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[NonWifiDownloadDialogBlockHook] DialogCtrListener class NOT FOUND")
                return
            }

            var installed = 0
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                className = BaiduTransferHookPoints.TRANSFER_CONTEXT_COMPANION,
                tagPrefix = "TransferContext.Companion",
            )
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                className = BaiduTransferHookPoints.TRANSFER_APIS,
                tagPrefix = "TransferApis",
            )
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                className = BaiduTransferHookPoints.FLOW_ALERT_DIALOG_MANAGER,
                tagPrefix = "FlowAlertDialogManager",
            )

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[NonWifiDownloadDialogBlockHook] target methods NOT FOUND")
                return
            }

            XposedCompat.log("[NonWifiDownloadDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log(
                "[NonWifiDownloadDialogBlockHook] FAILED (reflection): " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[NonWifiDownloadDialogBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookDialogMethods(
        cl: ClassLoader,
        listenerClass: Class<*>,
        className: String,
        tagPrefix: String,
    ): Int {
        var installed = 0
        installed += hookDialogMethod(
            cl = cl,
            listenerClass = listenerClass,
            className = className,
            methodName = BaiduTransferHookPoints.SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD,
            tag = "$tagPrefix.showNonWiFiAlertDownloadDialog",
        )
        installed += hookDialogMethod(
            cl = cl,
            listenerClass = listenerClass,
            className = className,
            methodName = BaiduTransferHookPoints.SHOW_NON_WIFI_ALERT_DOWNLOAD_BOTTOM_DIALOG_METHOD,
            tag = "$tagPrefix.showNonWiFiAlertDownloadBottomDialog",
        )
        return installed
    }

    private fun hookDialogMethod(
        cl: ClassLoader,
        listenerClass: Class<*>,
        className: String,
        methodName: String,
        tag: String,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[NonWifiDownloadDialogBlockHook] class not found: $className")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, methodName, listenerClass) ?: run {
            XposedCompat.logD("[NonWifiDownloadDialogBlockHook] method not found: $tag")
            return 0
        }
        val mod = XposedCompat.module ?: return 0
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            if (!HookSettings.isNonWifiDownloadDialogBlocked) {
                return@intercept chain.proceed()
            }

            val listener = chain.args.firstOrNull()
            if (confirmDownload(listener, tag)) {
                HookUtils.getDefaultReturnValue(method.returnType)
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun confirmDownload(listener: Any?, tag: String): Boolean {
        if (listener == null) {
            XposedCompat.logD("[NonWifiDownloadDialogBlockHook] $tag skipped with null listener")
            return true
        }

        val onOkMethod = findNoArgMethodInHierarchy(
            listener.javaClass,
            BaiduTransferHookPoints.DIALOG_CTR_LISTENER_ON_OK_METHOD,
        ) ?: run {
            XposedCompat.logW("[NonWifiDownloadDialogBlockHook] onOkBtnClick not found for $tag")
            return false
        }

        return try {
            onOkMethod.invoke(listener)
            XposedCompat.logD("[NonWifiDownloadDialogBlockHook] $tag confirmed without dialog")
            true
        } catch (e: InvocationTargetException) {
            XposedCompat.logE(
                "[NonWifiDownloadDialogBlockHook] onOkBtnClick threw in $tag: " +
                    "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
            )
            XposedCompat.log(e.targetException ?: e)
            true
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[NonWifiDownloadDialogBlockHook] onOkBtnClick invoke failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    private fun findNoArgMethodInHierarchy(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
