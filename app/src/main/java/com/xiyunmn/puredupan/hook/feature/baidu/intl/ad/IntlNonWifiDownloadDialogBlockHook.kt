package com.xiyunmn.puredupan.hook.feature.baidu.intl.ad

import android.content.Context
import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlTransferHookPoints
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object IntlNonWifiDownloadDialogBlockHook {
    private const val FLOW_ALERT_DOWNLOAD_TYPE = 0
    private const val LOCAL_BROADCAST_MANAGER_CLASS =
        "androidx.localbroadcastmanager.content.LocalBroadcastManager"

    private val hookState = HookState()
    private val flowAlertDialogTypes = Collections.synchronizedMap(WeakHashMap<Any, Int>())
    private val flowAlertUseTrafficListeners = Collections.synchronizedMap(WeakHashMap<Any, Any>())

    private data class RestartSchedulersInvoker(
        val target: Any,
        val method: Method,
    )

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isNonWifiDownloadDialogBlocked) {
            XposedCompat.log("[IntlNonWifiDownloadDialogBlockHook] skipped: config disabled")
            return
        }
        if (!hookState.markInstalled()) return

        try {
            val listenerClass = XposedCompat.findClassOrNull(
                BaiduIntlTransferHookPoints.DIALOG_CTR_LISTENER,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[IntlNonWifiDownloadDialogBlockHook] DialogCtrListener class NOT FOUND")
                return
            }
            val wifiOnlyConfigMethod = findSetWifiOnlyConfigMethod(cl)
            val restartSchedulersInvoker = findRestartSchedulersInvoker(cl)
            val flowAlertDialogPathInstalled = hookFlowAlertTransferFileDialog(
                cl = cl,
                wifiOnlyConfigMethod = wifiOnlyConfigMethod,
                restartSchedulersInvoker = restartSchedulersInvoker,
            )

            var installed = 0
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                wifiOnlyConfigMethod = wifiOnlyConfigMethod,
                restartSchedulersInvoker = restartSchedulersInvoker,
                flowAlertDialogPathInstalled = flowAlertDialogPathInstalled,
                className = BaiduIntlTransferHookPoints.TRANSFER_CONTEXT_COMPANION,
                tagPrefix = "TransferContext.Companion",
            )
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                wifiOnlyConfigMethod = wifiOnlyConfigMethod,
                restartSchedulersInvoker = restartSchedulersInvoker,
                flowAlertDialogPathInstalled = flowAlertDialogPathInstalled,
                className = BaiduIntlTransferHookPoints.TRANSFER_APIS,
                tagPrefix = "TransferApis",
            )
            installed += hookDialogMethods(
                cl = cl,
                listenerClass = listenerClass,
                wifiOnlyConfigMethod = wifiOnlyConfigMethod,
                restartSchedulersInvoker = restartSchedulersInvoker,
                flowAlertDialogPathInstalled = flowAlertDialogPathInstalled,
                className = BaiduIntlTransferHookPoints.FLOW_ALERT_DIALOG_MANAGER,
                tagPrefix = "FlowAlertDialogManager",
            )

            if (installed == 0 && !flowAlertDialogPathInstalled) {
                hookState.reset()
                XposedCompat.log("[IntlNonWifiDownloadDialogBlockHook] target methods NOT FOUND")
                return
            }

            XposedCompat.log(
                "[IntlNonWifiDownloadDialogBlockHook] hooks INSTALLED: count=$installed, " +
                    "flowAlert=$flowAlertDialogPathInstalled",
            )
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log(
                "[IntlNonWifiDownloadDialogBlockHook] FAILED (reflection): " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[IntlNonWifiDownloadDialogBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookDialogMethods(
        cl: ClassLoader,
        listenerClass: Class<*>,
        wifiOnlyConfigMethod: Method?,
        restartSchedulersInvoker: RestartSchedulersInvoker?,
        flowAlertDialogPathInstalled: Boolean,
        className: String,
        tagPrefix: String,
    ): Int {
        var installed = 0
        installed += hookDialogMethod(
            cl = cl,
            listenerClass = listenerClass,
            wifiOnlyConfigMethod = wifiOnlyConfigMethod,
            restartSchedulersInvoker = restartSchedulersInvoker,
            flowAlertDialogPathInstalled = flowAlertDialogPathInstalled,
            className = className,
            methodName = BaiduIntlTransferHookPoints.SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD,
            tag = "$tagPrefix.showNonWiFiAlertDownloadDialog",
        )
        installed += hookDialogMethod(
            cl = cl,
            listenerClass = listenerClass,
            wifiOnlyConfigMethod = wifiOnlyConfigMethod,
            restartSchedulersInvoker = restartSchedulersInvoker,
            flowAlertDialogPathInstalled = flowAlertDialogPathInstalled,
            className = className,
            methodName = BaiduIntlTransferHookPoints.SHOW_NON_WIFI_ALERT_DOWNLOAD_BOTTOM_DIALOG_METHOD,
            tag = "$tagPrefix.showNonWiFiAlertDownloadBottomDialog",
        )
        return installed
    }

    private fun hookDialogMethod(
        cl: ClassLoader,
        listenerClass: Class<*>,
        wifiOnlyConfigMethod: Method?,
        restartSchedulersInvoker: RestartSchedulersInvoker?,
        flowAlertDialogPathInstalled: Boolean,
        className: String,
        methodName: String,
        tag: String,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] class not found: $className")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, methodName, listenerClass) ?: run {
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] method not found: $tag")
            return 0
        }
        val mod = XposedCompat.module ?: return 0
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            if (!HookSettings.isNonWifiDownloadDialogBlocked) {
                return@intercept chain.proceed()
            }
            if (
                flowAlertDialogPathInstalled &&
                methodName == BaiduIntlTransferHookPoints.SHOW_NON_WIFI_ALERT_DOWNLOAD_DIALOG_METHOD
            ) {
                XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag delegated to FlowAlert dialog path")
                return@intercept chain.proceed()
            }

            val listener = chain.args.firstOrNull()
            if (confirmDownload(listener, wifiOnlyConfigMethod, restartSchedulersInvoker, chain.thisObject, tag)) {
                HookUtils.getDefaultReturnValue(method.returnType)
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookFlowAlertTransferFileDialog(
        cl: ClassLoader,
        wifiOnlyConfigMethod: Method?,
        restartSchedulersInvoker: RestartSchedulersInvoker?,
    ): Boolean {
        val mod = XposedCompat.module ?: return false
        val dialogClass = XposedCompat.findClassOrNull(
            BaiduIntlTransferHookPoints.FLOW_ALERT_TRANSFER_FILE_DIALOG,
            cl,
        ) ?: run {
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog class not found")
            return false
        }
        val function0Class = XposedCompat.findClassOrNull(
            BaiduIntlTransferHookPoints.KOTLIN_FUNCTION0,
            cl,
        ) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] kotlin Function0 class not found")
            return false
        }
        val setTypeMethod = XposedCompat.findMethodOrNull(
            dialogClass,
            BaiduIntlTransferHookPoints.FLOW_ALERT_SET_TYPE_METHOD,
            Int::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog.setType not found")
            return false
        }
        val setUseTrafficMethod = XposedCompat.findMethodOrNull(
            dialogClass,
            BaiduIntlTransferHookPoints.FLOW_ALERT_SET_ON_CLICK_USE_TRAFFIC_METHOD,
            function0Class,
        ) ?: run {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog.setOnClickUseTraffic not found",
            )
            return false
        }
        val showMethod = XposedCompat.findMethodOrNull(
            dialogClass,
            BaiduIntlTransferHookPoints.SHOW_METHOD,
        ) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog.show not found")
            return false
        }

        mod.hook(setTypeMethod).intercept { chain ->
            val result = chain.proceed()
            val dialog = chain.thisObject
            val type = chain.args.firstOrNull() as? Int
            if (dialog != null && type != null) {
                flowAlertDialogTypes[dialog] = type
            }
            result
        }
        mod.hook(setUseTrafficMethod).intercept { chain ->
            val result = chain.proceed()
            val dialog = chain.thisObject
            val listener = chain.args.firstOrNull()
            if (dialog != null && listener != null) {
                flowAlertUseTrafficListeners[dialog] = listener
            }
            result
        }
        mod.hook(showMethod).intercept { chain ->
            if (!HookSettings.isNonWifiDownloadDialogBlocked) {
                return@intercept chain.proceed()
            }
            val dialog = chain.thisObject ?: return@intercept chain.proceed()
            val type = flowAlertDialogType(dialog)
            if (type != FLOW_ALERT_DOWNLOAD_TYPE) {
                return@intercept chain.proceed()
            }

            val listener = removeFlowAlertUseTrafficListener(dialog)
            removeFlowAlertDialogType(dialog)
            if (listener == null) {
                XposedCompat.logW(
                    "[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog.show " +
                        "download listener unavailable",
                )
                return@intercept chain.proceed()
            }

            if (
                confirmFlowAlertUseTraffic(
                    listener = listener,
                    wifiOnlyConfigMethod = wifiOnlyConfigMethod,
                    restartSchedulersInvoker = restartSchedulersInvoker,
                    dialog = dialog,
                )
            ) {
                HookUtils.getDefaultReturnValue(showMethod.returnType)
            } else {
                chain.proceed()
            }
        }

        XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] FlowAlertTransferFileDialog hooks installed")
        return true
    }

    private fun confirmFlowAlertUseTraffic(
        listener: Any,
        wifiOnlyConfigMethod: Method?,
        restartSchedulersInvoker: RestartSchedulersInvoker?,
        dialog: Any,
    ): Boolean {
        val invokeMethod = findNoArgMethodInHierarchy(
            listener.javaClass,
            BaiduIntlTransferHookPoints.FUNCTION0_INVOKE_METHOD,
        ) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] Function0.invoke not found")
            return false
        }
        val tag = "FlowAlertTransferFileDialog.show"

        return try {
            allowMobileDataDownload(wifiOnlyConfigMethod, tag)
            invokeMethod.invoke(listener)
            restartTransferSchedulers(restartSchedulersInvoker, dialog, listener, tag)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag confirmed by use-traffic listener")
            true
        } catch (e: InvocationTargetException) {
            XposedCompat.logE(
                "[IntlNonWifiDownloadDialogBlockHook] use-traffic listener threw: " +
                    "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
            )
            XposedCompat.log(e.targetException ?: e)
            false
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] use-traffic listener invoke failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    private fun flowAlertDialogType(dialog: Any): Int? {
        return flowAlertDialogTypes[dialog]
    }

    private fun removeFlowAlertDialogType(dialog: Any) {
        flowAlertDialogTypes.remove(dialog)
    }

    private fun removeFlowAlertUseTrafficListener(dialog: Any): Any? {
        return flowAlertUseTrafficListeners.remove(dialog)
    }

    private fun confirmDownload(
        listener: Any?,
        wifiOnlyConfigMethod: Method?,
        restartSchedulersInvoker: RestartSchedulersInvoker?,
        managerObject: Any?,
        tag: String,
    ): Boolean {
        if (listener == null) {
            allowMobileDataDownload(wifiOnlyConfigMethod, tag)
            restartTransferSchedulers(restartSchedulersInvoker, managerObject, null, tag)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag skipped with null listener")
            return true
        }

        val onOkMethod = findNoArgMethodInHierarchy(
            listener.javaClass,
            BaiduIntlTransferHookPoints.DIALOG_CTR_LISTENER_ON_OK_METHOD,
        ) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] onOkBtnClick not found for $tag")
            return false
        }

        return try {
            allowMobileDataDownload(wifiOnlyConfigMethod, tag)
            onOkMethod.invoke(listener)
            restartTransferSchedulers(restartSchedulersInvoker, managerObject, listener, tag)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag confirmed without dialog")
            true
        } catch (e: InvocationTargetException) {
            restartTransferSchedulers(restartSchedulersInvoker, managerObject, listener, tag)
            XposedCompat.logE(
                "[IntlNonWifiDownloadDialogBlockHook] onOkBtnClick threw in $tag: " +
                    "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
            )
            XposedCompat.log(e.targetException ?: e)
            true
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] onOkBtnClick invoke failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    private fun findSetWifiOnlyConfigMethod(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduIntlTransferHookPoints.NET_CONFIG_UTIL, cl)
            ?: run {
                XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] NetConfigUtil class not found")
                return null
            }
        return XposedCompat.findMethodOrNull(
            clazz,
            BaiduIntlTransferHookPoints.SET_WIFI_ONLY_CHECKED_CONFIG_METHOD,
            Boolean::class.javaPrimitiveType!!,
        )?.apply { isAccessible = true } ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] setWiFiOnlyCheckedConfig not found")
            null
        }
    }

    private fun findRestartSchedulersInvoker(cl: ClassLoader): RestartSchedulersInvoker? {
        val clazz = XposedCompat.findClassOrNull(BaiduIntlTransferHookPoints.MAIN_CREATE_OBJECT_API, cl)
            ?: run {
                XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] MCreateObjectApi class not found")
                return null
            }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            BaiduIntlTransferHookPoints.RESTART_SCHEDULERS_METHOD,
            Context::class.java,
        )?.apply { isAccessible = true } ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] restartSchedulers(Context) not found")
            return null
        }

        return try {
            val constructor = clazz.getDeclaredConstructor().apply { isAccessible = true }
            RestartSchedulersInvoker(constructor.newInstance(), method)
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] create MCreateObjectApi failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }

    private fun allowMobileDataDownload(method: Method?, tag: String) {
        if (method != null) {
            try {
                method.invoke(null, false)
                XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag disabled wifi-only before confirm")
            } catch (e: InvocationTargetException) {
                XposedCompat.logW(
                    "[IntlNonWifiDownloadDialogBlockHook] setWiFiOnlyCheckedConfig threw in $tag: " +
                        "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
                )
            } catch (e: ReflectiveOperationException) {
                XposedCompat.logW(
                    "[IntlNonWifiDownloadDialogBlockHook] setWiFiOnlyCheckedConfig failed in $tag: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
        broadcastWifiOnlyState(enabled = false, tag = tag)
    }

    private fun broadcastWifiOnlyState(enabled: Boolean, tag: String) {
        val context = currentApplicationContext() ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] host context unavailable for $tag wifi-only broadcast")
            return
        }
        try {
            val intent = Intent(BaiduIntlTransferHookPoints.WIFI_ONLY_TRANSFER_ACTION)
                .putExtra(BaiduIntlTransferHookPoints.WIFI_ONLY_TRANSFER_EXTRA, enabled)
            val managerClass = Class.forName(LOCAL_BROADCAST_MANAGER_CLASS, false, context.classLoader)
            val getInstanceMethod = managerClass.getDeclaredMethod("getInstance", Context::class.java)
                .apply { isAccessible = true }
            val sendBroadcastMethod = managerClass.getDeclaredMethod("sendBroadcast", Intent::class.java)
                .apply { isAccessible = true }
            val manager = getInstanceMethod.invoke(null, context)
            sendBroadcastMethod.invoke(manager, intent)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag broadcast wifi-only=$enabled")
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] wifi-only broadcast reflection failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        } catch (e: RuntimeException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] wifi-only broadcast failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private fun restartTransferSchedulers(
        invoker: RestartSchedulersInvoker?,
        managerObject: Any?,
        listener: Any?,
        tag: String,
    ): Boolean {
        if (restartSchedulersByProvider(invoker, managerObject, listener, tag)) return true
        if (restartSchedulersByManager(managerObject, tag)) return true

        XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] restart schedulers unavailable for $tag")
        return false
    }

    private fun restartSchedulersByProvider(
        invoker: RestartSchedulersInvoker?,
        managerObject: Any?,
        listener: Any?,
        tag: String,
    ): Boolean {
        if (invoker == null) return false
        val context = resolveHostContext(managerObject, listener) ?: run {
            XposedCompat.logW("[IntlNonWifiDownloadDialogBlockHook] host context unavailable for $tag")
            return false
        }
        return try {
            invoker.method.invoke(invoker.target, context)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag restarted transfer schedulers")
            true
        } catch (e: InvocationTargetException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] restartSchedulers threw in $tag: " +
                    "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
            )
            false
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] restartSchedulers failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    private fun restartSchedulersByManager(managerObject: Any?, tag: String): Boolean {
        if (managerObject == null) return false
        val method = findNoArgMethodInHierarchy(
            managerObject.javaClass,
            BaiduIntlTransferHookPoints.RESTART_SCHEDULERS_METHOD,
        ) ?: return false

        return try {
            method.invoke(managerObject)
            XposedCompat.logD("[IntlNonWifiDownloadDialogBlockHook] $tag restarted schedulers via manager")
            true
        } catch (e: InvocationTargetException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] manager restart threw in $tag: " +
                    "${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}",
            )
            false
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW(
                "[IntlNonWifiDownloadDialogBlockHook] manager restart failed in $tag: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    private fun resolveHostContext(managerObject: Any?, listener: Any?): Context? {
        currentApplicationContext()?.let { return it }
        findContextField(managerObject)?.let { return it }
        return findContextField(listener)
    }

    private fun currentApplicationContext(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
            val context = method.invoke(null) as? Context
            context?.applicationContext ?: context
        }.getOrNull()
    }

    private fun findContextField(instance: Any?): Context? {
        if (instance == null) return null

        var current: Class<*>? = instance.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                if (!Context::class.java.isAssignableFrom(field.type)) continue
                val context = runCatching {
                    field.isAccessible = true
                    field.get(instance) as? Context
                }.getOrNull()
                if (context != null) {
                    return context.applicationContext ?: context
                }
            }
            current = current.superclass
        }
        return null
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
