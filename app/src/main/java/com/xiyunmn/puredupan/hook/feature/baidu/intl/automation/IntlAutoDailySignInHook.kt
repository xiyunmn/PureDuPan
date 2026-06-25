package com.xiyunmn.puredupan.hook.feature.baidu.intl.automation

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.AutoDailySignInScheduler
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.AutoDailySignInStateStore
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAutomationHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object IntlAutoDailySignInHook {
    private const val TAG = "IntlAutoDailySignInHook"
    private const val TASK_TYPE_SIGN_IN = "132"
    private const val TASK_FROM = "taskCenter"
    private const val TASK_STATUS_NOT_COMPLETE = 0
    private const val TASK_STATUS_COMPLETE = 1
    private const val REFRESH_RETRY_DELAY_MS = 8_000L
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun hook(cl: ClassLoader) {
        AutoDailySignInScheduler.install(cl, TAG) { activity ->
            run(activity, cl)
        }
    }

    private fun run(activity: Activity, cl: ClassLoader) {
        if (!HookSettings.isAutoDailySignInEnabled) return

        val context = activity.applicationContext ?: activity
        val account = AccountAccess.resolve(cl)
        if (account == null) {
            if (AutoDailySignInStateStore.beginAttempt(context, null, TAG)) {
                AutoDailySignInStateStore.markFailed(context, null, TAG, "account state unavailable")
            }
            return
        }
        if (!account.isLogin) {
            XposedCompat.logD("[$TAG] auto sign-in skipped: account not logged in")
            return
        }
        if (!AutoDailySignInStateStore.beginAttempt(context, account.uid, TAG)) return

        val access = TaskScoreAccess.resolve(cl)
        if (access == null) {
            AutoDailySignInStateStore.markFailed(context, account.uid, TAG, "task manager unavailable")
            return
        }

        dispatchOrRefresh(
            activity = activity,
            context = context,
            accountIdentity = account.uid,
            access = access,
            allowRefresh = true,
        )
    }

    private fun dispatchOrRefresh(
        activity: Activity,
        context: Context,
        accountIdentity: String?,
        access: TaskScoreAccess,
        allowRefresh: Boolean,
    ) {
        val manager = access.manager() ?: run {
            AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "task manager instance unavailable")
            return
        }
        val task = access.findSignInTask(manager)
        if (task == null) {
            if (!allowRefresh) {
                AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "sign-in task unavailable")
                return
            }
            access.syncTaskList(manager, context)
            scheduleRetry(activity, context, accountIdentity, access)
            XposedCompat.log("[$TAG] sign-in task missing, task list refresh requested")
            return
        }

        val status = access.taskStatus(task)
        when (status) {
            TASK_STATUS_COMPLETE -> {
                AutoDailySignInStateStore.markSuccess(context, accountIdentity, TAG, "already complete")
                return
            }
            TASK_STATUS_NOT_COMPLETE -> Unit
            else -> {
                AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "unexpected task status $status")
                return
            }
        }

        val taskId = access.taskId(task)
        val taskClassId = access.taskClassId(task)
        if (taskId <= 0L || taskClassId <= 0L) {
            AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "invalid task identifiers")
            return
        }

        val dispatched = access.doTask(manager, activity, taskId, taskClassId)
        if (dispatched) {
            AutoDailySignInStateStore.markSuccess(context, accountIdentity, TAG, "task dispatched")
        } else {
            AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "task dispatch rejected")
        }
    }

    private fun scheduleRetry(
        activity: Activity,
        context: Context,
        accountIdentity: String?,
        access: TaskScoreAccess,
    ) {
        val activityRef = WeakReference(activity)
        mainHandler.postDelayed({
            val current = activityRef.get() ?: run {
                AutoDailySignInStateStore.markFailed(context, accountIdentity, TAG, "activity released after refresh")
                return@postDelayed
            }
            if (!HookSettings.isAutoDailySignInEnabled) return@postDelayed
            if (current.isFinishing) return@postDelayed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && current.isDestroyed) {
                return@postDelayed
            }
            dispatchOrRefresh(
                activity = current,
                context = context,
                accountIdentity = accountIdentity,
                access = access,
                allowRefresh = false,
            )
        }, REFRESH_RETRY_DELAY_MS)
    }

    private data class AccountState(
        val isLogin: Boolean,
        val uid: String?,
    )

    private object AccountAccess {
        fun resolve(cl: ClassLoader): AccountState? {
            return resolveExternal(cl) ?: resolveCore(cl)
        }

        private fun resolveExternal(cl: ClassLoader): AccountState? {
            return runCatching {
                val clazz = XposedCompat.findClassOrNull(
                    BaiduIntlHookPoints.EXTERNAL_ACCOUNT_UTILS,
                    cl,
                ) ?: return null
                val constructor = clazz.getDeclaredConstructor().apply { isAccessible = true }
                val instance = constructor.newInstance()
                val isLogin = XposedCompat.findMethodOrNull(clazz, "isLogin")
                    ?.invoke(instance) as? Boolean ?: return null
                val uid = XposedCompat.findMethodOrNull(clazz, "getUid")
                    ?.invoke(instance) as? String
                AccountState(isLogin = isLogin, uid = uid)
            }.getOrElse { t ->
                XposedCompat.logD("[$TAG] external account state resolve failed: ${t.message}")
                null
            }
        }

        private fun resolveCore(cl: ClassLoader): AccountState? {
            return runCatching {
                val clazz = XposedCompat.findClassOrNull(BaiduAutomationHookPoints.ACCOUNT_UTILS, cl) ?: return null
                val getInstance = findNamed(clazz, "getInstance")
                    ?: clazz.declaredMethods.firstOrNull { method ->
                        Modifier.isStatic(method.modifiers) &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == clazz
                    }?.apply { isAccessible = true }
                    ?: return null
                val instance = getInstance.invoke(null) ?: return null
                val isLoginMethod = findNamed(clazz, "isLogin") ?: return null
                val uidMethod = findNamed(clazz, "getUid")
                val isLogin = isLoginMethod.invoke(instance) as? Boolean ?: return null
                val uid = uidMethod?.invoke(instance) as? String
                AccountState(isLogin = isLogin, uid = uid)
            }.getOrElse { t ->
                XposedCompat.logD("[$TAG] core account state resolve failed: ${t.message}")
                null
            }
        }

        private fun findNamed(clazz: Class<*>, vararg names: String): Method? {
            for (name in names) {
                val method = XposedCompat.findMethodOrNull(clazz, name)
                if (method != null) return method
            }
            return null
        }
    }

    private class TaskScoreAccess(
        private val getInstance: Method,
        private val findTaskScoreByType: Method,
        private val syncTaskList: Method,
        private val doTask: Method,
        private val getTaskId: Method,
        private val getTaskClassId: Method,
        private val getTaskStatus: Method,
    ) {
        fun manager(): Any? = runCatching { getInstance.invoke(null) }.getOrNull()

        fun findSignInTask(manager: Any): Any? =
            runCatching { findTaskScoreByType.invoke(manager, TASK_TYPE_SIGN_IN, null) }.getOrNull()

        fun syncTaskList(manager: Any, context: Context) {
            runCatching { syncTaskList.invoke(manager, context, true) }
                .onFailure { t -> XposedCompat.logD("[$TAG] syncTaskList failed: ${t.message}") }
        }

        fun taskId(task: Any): Long =
            (runCatching { getTaskId.invoke(task) }.getOrNull() as? Number)?.toLong() ?: 0L

        fun taskClassId(task: Any): Long =
            (runCatching { getTaskClassId.invoke(task) }.getOrNull() as? Number)?.toLong() ?: 0L

        fun taskStatus(task: Any): Int =
            (runCatching { getTaskStatus.invoke(task) }.getOrNull() as? Number)?.toInt() ?: Int.MIN_VALUE

        fun doTask(manager: Any, activity: Activity, taskId: Long, taskClassId: Long): Boolean {
            return runCatching {
                doTask.invoke(manager, activity, taskId, taskClassId, TASK_FROM, null, null) as? Boolean
            }.getOrElse { t ->
                XposedCompat.logD("[$TAG] doTask failed: ${t.message}")
                null
            } == true
        }

        companion object {
            fun resolve(cl: ClassLoader): TaskScoreAccess? {
                return runCatching {
                    val managerClass = XposedCompat.findClassOrNull(
                        BaiduAutomationHookPoints.TASK_SCORE_MANAGER,
                        cl,
                    ) ?: return null
                    val taskScoreClass = XposedCompat.findClassOrNull(BaiduAutomationHookPoints.TASK_SCORE, cl)
                        ?: return null
                    val function1Class = XposedCompat.findClassOrNull(BaiduAutomationHookPoints.KOTLIN_FUNCTION1, cl)
                        ?: return null

                    val getInstance = findSingletonMethod(managerClass) ?: return null
                    val findTaskScoreByType = findByNameOrSignature(
                        managerClass,
                        names = listOf("findTaskScoreByType"),
                    ) { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == taskScoreClass &&
                            method.parameterTypes.contentEquals(arrayOf(String::class.java, function1Class))
                    } ?: return null
                    val syncTaskList = findByNameOrSignature(
                        managerClass,
                        names = listOf("syncTaskList"),
                    ) { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Void.TYPE &&
                            method.parameterTypes.contentEquals(
                                arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!),
                            )
                    } ?: return null
                    val doTask = findByNameOrSignature(
                        managerClass,
                        names = listOf("doTask"),
                    ) { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Boolean::class.javaPrimitiveType!! &&
                            method.parameterTypes.contentEquals(
                                arrayOf(
                                    Activity::class.java,
                                    Long::class.javaPrimitiveType!!,
                                    Long::class.javaPrimitiveType!!,
                                    String::class.java,
                                    String::class.java,
                                    String::class.java,
                                ),
                            )
                    } ?: return null
                    val getTaskId = XposedCompat.findMethodOrNull(taskScoreClass, "getTaskId") ?: return null
                    val getTaskClassId = XposedCompat.findMethodOrNull(
                        taskScoreClass,
                        "getTaskClassId",
                    ) ?: return null
                    val getTaskStatus = XposedCompat.findMethodOrNull(taskScoreClass, "getTaskStatus") ?: return null

                    TaskScoreAccess(
                        getInstance = getInstance,
                        findTaskScoreByType = findTaskScoreByType,
                        syncTaskList = syncTaskList,
                        doTask = doTask,
                        getTaskId = getTaskId,
                        getTaskClassId = getTaskClassId,
                        getTaskStatus = getTaskStatus,
                    )
                }.getOrElse { t ->
                    XposedCompat.logD("[$TAG] task score access resolve failed: ${t.message}")
                    null
                }
            }

            private fun findSingletonMethod(clazz: Class<*>): Method? {
                return findByNameOrSignature(clazz, names = listOf("getInstance")) { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == clazz
                }
            }

            private fun findByNameOrSignature(
                clazz: Class<*>,
                names: List<String>,
                predicate: (Method) -> Boolean,
            ): Method? {
                for (name in names) {
                    val method = clazz.declaredMethods.firstOrNull { it.name == name && predicate(it) }
                    if (method != null) return method.apply { isAccessible = true }
                }
                return clazz.declaredMethods.firstOrNull(predicate)?.apply { isAccessible = true }
            }
        }
    }
}
