package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import android.content.Intent
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints

internal object DomesticOemPushServiceBlockHook {
    private val hookState = HookState()
    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[DomesticOemPushServiceBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            installed += hookServiceLifecycle(cl)
            installed += hookReceivers(cl)
            installed += hookHeytapDataMessages(cl)
            installed += hookHmsMessages(cl)
            installed += hookHonorMessages(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[DomesticOemPushServiceBlockHook] no hooks installed")
            } else {
                XposedCompat.log("[DomesticOemPushServiceBlockHook] hooks INSTALLED: count=$installed")
            }
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticOemPushServiceBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookServiceLifecycle(cl: ClassLoader): Int {
        data class ServiceHookConfig(
            val classes: List<String>,
            val methodName: String,
            val params: Array<out Class<*>>,
            val returnValue: Any?,
            val getStartId: ((Array<Any?>) -> Int?)? = null,
        )

        val configs = listOf(
            ServiceHookConfig(
                classes = BaiduDomesticHookPoints.OEM_PUSH_ON_START_COMMAND_SERVICE_CLASSES,
                methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_START_COMMAND_METHOD,
                params = arrayOf(Intent::class.java, Integer.TYPE, Integer.TYPE),
                returnValue = START_NOT_STICKY,
                getStartId = { args -> args.getOrNull(2) as? Int },
            ),
            ServiceHookConfig(
                classes = BaiduDomesticHookPoints.OEM_PUSH_ON_CREATE_SERVICE_CLASSES,
                methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_CREATE_METHOD,
                params = emptyArray(),
                returnValue = null,
            ),
            ServiceHookConfig(
                classes = BaiduDomesticHookPoints.OEM_PUSH_ON_START_SERVICE_CLASSES,
                methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_START_METHOD,
                params = arrayOf(Intent::class.java, Integer.TYPE),
                returnValue = null,
                getStartId = { args -> args.getOrNull(1) as? Int },
            ),
            ServiceHookConfig(
                classes = BaiduDomesticHookPoints.OEM_PUSH_ON_BIND_SERVICE_CLASSES,
                methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_BIND_METHOD,
                params = arrayOf(Intent::class.java),
                returnValue = null,
            ),
            ServiceHookConfig(
                classes = BaiduDomesticHookPoints.OEM_PUSH_ON_HANDLE_INTENT_SERVICE_CLASSES,
                methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_HANDLE_INTENT_METHOD,
                params = arrayOf(Intent::class.java),
                returnValue = null,
            ),
        )

        return configs.sumOf { config ->
            hookMultipleClasses(
                cl = cl,
                classNames = config.classes,
                methodName = config.methodName,
                params = config.params,
                returnValue = config.returnValue,
                getStartId = config.getStartId,
            )
        }
    }

    private fun hookReceivers(cl: ClassLoader): Int {
        return hookMultipleClasses(
            cl = cl,
            classNames = BaiduDomesticHookPoints.OEM_PUSH_RECEIVER_CLASSES,
            methodName = BaiduDomesticHookPoints.OEM_PUSH_ON_RECEIVE_METHOD,
            params = arrayOf(Context::class.java, Intent::class.java),
            returnValue = null,
        )
    }

    private fun hookHeytapDataMessages(cl: ClassLoader): Int {
        val dataMessageClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.OEM_PUSH_HEYTAP_DATA_MESSAGE,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticOemPushServiceBlockHook] Heytap DataMessage class NOT FOUND")
            return 0
        }

        return hookMultipleClasses(
            cl = cl,
            classNames = BaiduDomesticHookPoints.OEM_PUSH_HEYTAP_DATA_MESSAGE_SERVICE_CLASSES,
            methodName = BaiduDomesticHookPoints.OEM_PUSH_PROCESS_MESSAGE_METHOD,
            params = arrayOf(Context::class.java, dataMessageClass),
            returnValue = null,
        )
    }

    private fun hookMultipleClasses(
        cl: ClassLoader,
        classNames: List<String>,
        methodName: String,
        params: Array<out Class<*>>,
        returnValue: Any?,
        getStartId: ((Array<Any?>) -> Int?)? = null,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0

        for (className in classNames) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[DomesticOemPushServiceBlockHook] $className NOT FOUND")
                continue
            }
            val method = XposedCompat.findMethodOrNull(clazz, methodName, *params) ?: run {
                XposedCompat.log("[DomesticOemPushServiceBlockHook] $className.$methodName NOT FOUND")
                continue
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    val startId = getStartId?.invoke(chain.args.toTypedArray())
                    stopSelfQuietly(chain.thisObject, startId)
                    XposedCompat.logD(
                        "[DomesticOemPushServiceBlockHook] " +
                            "${chain.thisObject.javaClass.simpleName}.$methodName blocked",
                    )
                    returnValue
                } else {
                    chain.proceed()
                }
            }
            count += 1
        }

        return count
    }

    private fun hookHmsMessages(cl: ClassLoader): Int {
        val remoteMessageClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.OEM_PUSH_HMS_REMOTE_MESSAGE,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticOemPushServiceBlockHook] RemoteMessage class NOT FOUND")
            return 0
        }

        val mod = XposedCompat.module ?: return 0
        var count = 0

        for (className in BaiduDomesticHookPoints.OEM_PUSH_HUAWEI_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[DomesticOemPushServiceBlockHook] $className NOT FOUND")
                continue
            }

            XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                remoteMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[DomesticOemPushServiceBlockHook] " +
                                "${chain.thisObject.javaClass.simpleName}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[DomesticOemPushServiceBlockHook] " +
                                "${chain.thisObject.javaClass.simpleName}.onNewToken blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.OEM_PUSH_ON_TOKEN_ERROR_METHOD,
                Exception::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[DomesticOemPushServiceBlockHook] " +
                                "${chain.thisObject.javaClass.simpleName}.onTokenError blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }
        }

        return count
    }

    private fun hookHonorMessages(cl: ClassLoader): Int {
        val honorMessageClass = XposedCompat.findClassOrNull(
            BaiduDomesticHookPoints.OEM_PUSH_HONOR_MESSAGE,
            cl,
        ) ?: run {
            XposedCompat.log("[DomesticOemPushServiceBlockHook] HonorPushDataMsg class NOT FOUND")
            return 0
        }

        val mod = XposedCompat.module ?: return 0
        var count = 0

        for (className in BaiduDomesticHookPoints.OEM_PUSH_HONOR_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[DomesticOemPushServiceBlockHook] $className NOT FOUND")
                continue
            }

            XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                honorMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[DomesticOemPushServiceBlockHook] " +
                                "${chain.thisObject.javaClass.simpleName}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            XposedCompat.findMethodOrNull(
                clazz,
                BaiduDomesticHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[DomesticOemPushServiceBlockHook] " +
                                "${chain.thisObject.javaClass.simpleName}.onNewToken blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }
        }

        return count
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
            XposedCompat.logD("[DomesticOemPushServiceBlockHook] stopSelf ignored: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isOemPushServiceDisabled
}
