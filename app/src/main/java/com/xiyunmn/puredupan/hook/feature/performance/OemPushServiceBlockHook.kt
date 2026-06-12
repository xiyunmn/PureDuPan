package com.xiyunmn.puredupan.hook.feature.performance

import android.content.Context
import android.content.Intent
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 阻止厂商推送服务自动启动
 *
 * ## 覆盖的厂商
 * - 华为 (Huawei HMS)
 * - 荣耀 (Honor)
 * - 小米 (Xiaomi MiPush)
 * - OPPO (HeytapPush)
 * - VIVO (VivoPush)
 * - 魅族 (Meizu FlymeOS)
 *
 * ## Hook策略
 *
 * 由于OEM推送是第三方SDK，由系统和厂商服务器直接触发，没有宿主启动任务包装，
 * 因此必须在Service/Receiver层全面拦截。
 *
 * ### Service生命周期
 * - onCreate - 服务创建
 * - onStartCommand - 服务启动
 * - onStart - 旧版启动回调
 * - onBind - 服务绑定
 * - onHandleIntent - IntentService处理
 *
 * ### BroadcastReceiver
 * - onReceive - 接收推送广播
 *
 * ### 厂商特定回调
 * - 华为/荣耀: onMessageReceived/onNewToken/onTokenError
 *
 * ## 设计原则
 *
 * - 通用方法：提取重复的Hook逻辑到 hookMultipleClasses
 * - 配置驱动：使用 ServiceHookConfig 描述Hook配置
 * - 易于扩展：新增厂商只需添加类名到列表
 */
object OemPushServiceBlockHook {
    private val hookState = HookState()
    private const val START_NOT_STICKY = 2

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[OemPushServiceBlock] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0

            // Service生命周期Hook
            installed += hookServiceLifecycle(cl)

            // BroadcastReceiver Hook
            installed += hookReceivers(cl)

            // 华为/荣耀特殊消息Hook
            installed += hookHmsMessages(cl)
            installed += hookHonorMessages(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[OemPushServiceBlock] No hooks installed")
            } else {
                XposedCompat.log("[OemPushServiceBlock] hooks INSTALLED: count=$installed")
            }
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[OemPushServiceBlock] Installation error: ${e.message}")
            XposedCompat.log(e)
        }
    }

    /**
     * Hook Service生命周期方法
     *
     * 统一处理 onCreate/onStartCommand/onStart/onBind/onHandleIntent
     */
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
                classes = StableBaiduPanHookPoints.OEM_PUSH_ON_START_COMMAND_SERVICE_CLASSES,
                methodName = "onStartCommand",
                params = arrayOf(Intent::class.java, Integer.TYPE, Integer.TYPE),
                returnValue = START_NOT_STICKY,
                getStartId = { args -> args.getOrNull(2) as? Int },
            ),
            ServiceHookConfig(
                classes = StableBaiduPanHookPoints.OEM_PUSH_ON_CREATE_SERVICE_CLASSES,
                methodName = "onCreate",
                params = emptyArray(),
                returnValue = null,
            ),
            ServiceHookConfig(
                classes = StableBaiduPanHookPoints.OEM_PUSH_ON_START_SERVICE_CLASSES,
                methodName = "onStart",
                params = arrayOf(Intent::class.java, Integer.TYPE),
                returnValue = null,
                getStartId = { args -> args.getOrNull(1) as? Int },
            ),
            ServiceHookConfig(
                classes = StableBaiduPanHookPoints.OEM_PUSH_ON_BIND_SERVICE_CLASSES,
                methodName = "onBind",
                params = arrayOf(Intent::class.java),
                returnValue = null,
            ),
            ServiceHookConfig(
                classes = StableBaiduPanHookPoints.OEM_PUSH_ON_HANDLE_INTENT_SERVICE_CLASSES,
                methodName = "onHandleIntent",
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

    /**
     * Hook BroadcastReceiver.onReceive
     */
    private fun hookReceivers(cl: ClassLoader): Int {
        return hookMultipleClasses(
            cl = cl,
            classNames = StableBaiduPanHookPoints.OEM_PUSH_RECEIVER_CLASSES,
            methodName = "onReceive",
            params = arrayOf(Context::class.java, Intent::class.java),
            returnValue = null,
        )
    }

    /**
     * 通用多类Hook方法
     *
     * 对指定的类列表批量安装同一个方法的Hook。
     *
     * @param classNames 要Hook的类名列表
     * @param methodName 方法名
     * @param params 方法参数类型
     * @param returnValue Hook时返回的值（null表示返回void）
     * @param getStartId 从方法参数中提取startId的函数（用于stopSelf）
     * @return 成功安装的Hook数量
     */
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
                XposedCompat.log("[OemPushServiceBlock] $className NOT FOUND")
                continue
            }

            val method = XposedCompat.findMethodOrNull(clazz, methodName, *params) ?: run {
                XposedCompat.log("[OemPushServiceBlock] $className.$methodName NOT FOUND")
                continue
            }

            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    val startId = getStartId?.invoke(chain.args.toTypedArray())
                    stopSelfQuietly(chain.thisObject, startId)
                    XposedCompat.logD(
                        "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.$methodName blocked",
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

    /**
     * Hook 华为HMS推送消息回调
     *
     * 华为使用 RemoteMessage 作为消息参数类型，需要动态查找。
     */
    private fun hookHmsMessages(cl: ClassLoader): Int {
        val remoteMessageClass = XposedCompat.findClassOrNull(
            "com.huawei.hms.push.RemoteMessage",
            cl,
        ) ?: run {
            XposedCompat.log("[OemPushServiceBlock] RemoteMessage class NOT FOUND")
            return 0
        }

        val mod = XposedCompat.module ?: return 0
        var count = 0

        for (className in StableBaiduPanHookPoints.OEM_PUSH_HUAWEI_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlock] $className NOT FOUND")
                continue
            }

            // onMessageReceived(RemoteMessage)
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                remoteMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            // onNewToken(String)
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.onNewToken blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            // onTokenError(Exception)
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_TOKEN_ERROR_METHOD,
                Exception::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.onTokenError blocked",
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

    /**
     * Hook 荣耀推送消息回调
     *
     * 荣耀使用 HonorPushDataMsg 作为消息参数类型，需要动态查找。
     */
    private fun hookHonorMessages(cl: ClassLoader): Int {
        val honorMessageClass = XposedCompat.findClassOrNull(
            "com.hihonor.push.sdk.HonorPushDataMsg",
            cl,
        ) ?: run {
            XposedCompat.log("[OemPushServiceBlock] HonorPushDataMsg class NOT FOUND")
            return 0
        }

        val mod = XposedCompat.module ?: return 0
        var count = 0

        for (className in StableBaiduPanHookPoints.OEM_PUSH_HONOR_MESSAGE_SERVICE_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.log("[OemPushServiceBlock] $className NOT FOUND")
                continue
            }

            // onMessageReceived(HonorPushDataMsg)
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_MESSAGE_RECEIVED_METHOD,
                honorMessageClass,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.onMessageReceived blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }

            // onNewToken(String)
            XposedCompat.findMethodOrNull(
                clazz,
                StableBaiduPanHookPoints.OEM_PUSH_ON_NEW_TOKEN_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    if (isEnabled()) {
                        stopSelfQuietly(chain.thisObject)
                        XposedCompat.logD(
                            "[OemPushServiceBlock] ${chain.thisObject.javaClass.simpleName}.onNewToken blocked",
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

    /**
     * 静默停止Service
     *
     * 调用 Service.stopSelf() 或 stopSelf(startId)。
     * 如果调用失败（不是Service对象），静默忽略。
     */
    private fun stopSelfQuietly(service: Any?, startId: Int? = null) {
        if (service == null) return
        try {
            if (startId != null) {
                XposedCompat.callMethod(service, "stopSelf", startId)
            } else {
                XposedCompat.callMethod(service, "stopSelf")
            }
        } catch (e: Exception) {
            XposedCompat.logD("[OemPushServiceBlock] stopSelf ignored: ${e.message}")
        }
    }

    private fun isEnabled(): Boolean =
        ConfigManager.isPerformanceOptimizeEnabled && ConfigManager.isOemPushServiceDisabled
}
