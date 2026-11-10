package com.xiyunmn.puredupan.hook.feature.baidu.intl.automation

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.AutoDailySignInScheduler
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.AutoDailySignInStateStore
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.MembershipSignInClient
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.MembershipSignInResult
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.MembershipSignInStatusResult
import com.xiyunmn.puredupan.hook.runtime.AutoDailySignInRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.util.concurrent.atomic.AtomicBoolean

internal object IntlAutoDailySignInHook {
    private const val TAG = "IntlAutoDailySignInHook"
    private val resultHandler = android.os.Handler(android.os.Looper.getMainLooper())
    fun disposeForReload() { resultHandler.removeCallbacksAndMessages(null) }
    private val membershipSignInRunning = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        AutoDailySignInRuntime.registerManualTrigger { context ->
            val activity = findActivity(context)
            if (activity == null) {
                XposedCompat.logW("[$TAG] manual sign-in skipped: activity unavailable")
                false
            } else {
                XposedCompat.log("[$TAG] manual sign-in requested")
                run(activity, cl, force = true)
                true
            }
        }
        AutoDailySignInScheduler.install(cl, TAG) { activity ->
            run(activity, cl)
        }
    }

    private fun run(activity: Activity, cl: ClassLoader, force: Boolean = false) {
        if (ModuleLifetime.isRetiring || (!force && !HookSettings.isAutoDailySignInEnabled)) return

        val context = activity.applicationContext ?: activity
        val account = AccountAccess.resolve(cl)
        if (account == null) {
            if (force && AutoDailySignInStateStore.beginAttempt(context, null, TAG, force = true)) {
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markFailed(context, null, TAG, "account state unavailable") }
            } else {
                XposedCompat.logD("[$TAG] auto sign-in skipped: account state unavailable")
            }
            return
        }
        if (!account.isLogin) {
            if (force && AutoDailySignInStateStore.beginAttempt(context, account.uid, TAG, force = true)) {
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markFailed(context, account.uid, TAG, "account not logged in") }
            } else {
                XposedCompat.logD("[$TAG] auto sign-in skipped: account not logged in")
            }
            return
        }
        if (!AutoDailySignInStateStore.beginAttempt(context, account.uid, TAG, force = force)) return

        runMembershipSignIn(context, account, cl, force)
    }

    private fun runMembershipSignIn(
        context: Context,
        account: AccountState,
        cl: ClassLoader,
        force: Boolean,
    ) {
        val uid = account.uid
        val bduss = account.bduss
        if (bduss.isNullOrBlank()) {
            ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markFailed(context, uid, TAG, "bduss unavailable") }
            return
        }
        if (!membershipSignInRunning.compareAndSet(false, true)) {
            ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markRetryableFailed(context, uid, TAG, "membership sign-in already running") }
            return
        }

        if (!ModuleLifetime.launchWorker("$TAG-MembershipSignIn") {
            try {
                if (!force && !HookSettings.isAutoDailySignInEnabled) {
                    ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markSkipped(
                        context,
                        uid,
                        TAG,
                        "disabled before membership request",
                        clearAttempt = true,
                    ) }
                    return@launchWorker
                }
                val cookie = IntlCookieByBdussDexKitResolver.cookieFor(cl, bduss)
                    .takeUnless { it.isBlank() }
                    ?: "BDUSS=$bduss"
                when (val result = MembershipSignInClient.signIn(cookie, TAG)) {
                    is MembershipSignInResult.Success -> {
                        ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markSuccess(
                            context,
                            uid,
                            TAG,
                            "membership endpoint signed in, points=${result.points ?: "unknown"}",
                        ) }
                    }
                    is MembershipSignInResult.AlreadySignedIn -> {
                        ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markAlreadySignedIn(
                            context,
                            uid,
                            TAG,
                            "membership endpoint reports already signed: ${result.message}",
                        ) }
                    }
                    is MembershipSignInResult.Failed -> {
                        confirmMembershipFailure(context, uid, cookie, result)
                    }
                }
            } finally {
                membershipSignInRunning.set(false)
            }
        }) membershipSignInRunning.set(false)
    }

    private fun confirmMembershipFailure(
        context: Context,
        uid: String?,
        cookie: String,
        failure: MembershipSignInResult.Failed,
    ) {
        when (val status = MembershipSignInClient.queryTaskCenterSignedIn(cookie, TAG)) {
            MembershipSignInStatusResult.SignedIn -> {
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markAlreadySignedIn(
                    context,
                    uid,
                    TAG,
                    "${failure.detail}, task-center status reports already signed after request",
                ) }
                return
            }
            MembershipSignInStatusResult.NotSignedIn -> Unit
            is MembershipSignInStatusResult.Unknown -> {
                XposedCompat.logD("[$TAG] task-center status unavailable after failure: ${status.detail}")
            }
        }
        when (val status = MembershipSignInClient.queryTodaySignedIn(cookie, TAG)) {
            MembershipSignInStatusResult.SignedIn -> {
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markAlreadySignedIn(
                    context,
                    uid,
                    TAG,
                    "${failure.detail}, membership status reports already signed after request",
                ) }
            }
            MembershipSignInStatusResult.NotSignedIn -> {
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markRetryableFailed(context, uid, TAG, failure.detail) }
            }
            is MembershipSignInStatusResult.Unknown -> {
                XposedCompat.logD("[$TAG] membership status unavailable after failure: ${status.detail}")
                ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markRetryableFailed(context, uid, TAG, failure.detail) }
            }
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private data class AccountState(
        val isLogin: Boolean,
        val uid: String?,
        val bduss: String?,
    )

    private object AccountAccess {
        fun resolve(cl: ClassLoader): AccountState? {
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
                val bduss = XposedCompat.findMethodOrNull(clazz, "getBduss")
                    ?.invoke(instance) as? String
                AccountState(isLogin = isLogin, uid = uid, bduss = bduss)
            }.getOrElse { t ->
                XposedCompat.logD("[$TAG] external account state resolve failed: ${t.message}")
                null
            }
        }
    }
}
