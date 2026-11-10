package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.runtime.AutoDailySignInRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAutomationHookPoints
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object DomesticAutoDailySignInHook {
    private const val TAG = "DomesticAutoDailySignInHook"
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

        runMembershipSignIn(
            context = context,
            account = account,
            cookieAccess = DomesticCookieAccess.resolve(cl),
            force = force,
        )
    }

    private fun runMembershipSignIn(
        context: Context,
        account: AccountState,
        cookieAccess: DomesticCookieAccess?,
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
                val resolvedCookie = cookieAccess?.cookieFor(bduss).takeUnless { it.isNullOrBlank() }
                val cookie = if (resolvedCookie.isNullOrBlank()) {
                    XposedCompat.logD("[$TAG] cookie source selected: BDUSS fallback")
                    "BDUSS=$bduss"
                } else {
                    XposedCompat.logD("[$TAG] cookie source selected: ${cookieAccess?.source ?: "unknown"}")
                    resolvedCookie
                }
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
                        confirmMembershipFailure(
                            context = context,
                            uid = uid,
                            cookie = cookie,
                            failure = result,
                        )
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
                return
            }
            MembershipSignInStatusResult.NotSignedIn -> Unit
            is MembershipSignInStatusResult.Unknown -> {
                XposedCompat.logD("[$TAG] membership status unavailable after failure: ${status.detail}")
            }
        }
        ModuleLifetime.postResult(resultHandler) { AutoDailySignInStateStore.markRetryableFailed(context, uid, TAG, failure.detail) }
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
            resolveExternal(cl)?.let { return it }
            return resolveInternal(cl)
        }

        private fun resolveExternal(cl: ClassLoader): AccountState? {
            return runCatching {
                val clazz = XposedCompat.findClassOrNull(
                    BaiduAutomationHookPoints.EXTERNAL_ACCOUNT_UTILS,
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

        private fun resolveInternal(cl: ClassLoader): AccountState? {
            return runCatching {
                val clazz = XposedCompat.findClassOrNull(BaiduAutomationHookPoints.ACCOUNT_UTILS, cl) ?: return null
                val getInstance = findFirstMethod(clazz, "getInstance", "k") ?: return null
                val instance = getInstance.invoke(null) ?: return null
                val isLogin = findFirstMethod(clazz, "isLogin", "Q")
                    ?.invoke(instance) as? Boolean ?: return null
                val uid = findFirstMethod(clazz, "getUid", "x")
                    ?.invoke(instance) as? String
                val bduss = findFirstMethod(clazz, "getBduss", "d")
                    ?.invoke(instance) as? String
                AccountState(isLogin = isLogin, uid = uid, bduss = bduss)
            }.getOrElse { t ->
                XposedCompat.logD("[$TAG] account state resolve failed: ${t.message}")
                null
            }
        }

        private fun findFirstMethod(clazz: Class<*>, vararg names: String): Method? {
            return names.firstNotNullOfOrNull { name ->
                XposedCompat.findMethodOrNull(clazz, name)
            }
        }
    }

    private class DomesticCookieAccess(
        val source: String,
        private val getCookieByBduss: (String) -> String,
    ) {
        fun cookieFor(bduss: String): String {
            return runCatching { getCookieByBduss(bduss) }
                .getOrElse { t ->
                    XposedCompat.logD("[$TAG] getCookieByBduss failed: source=$source, ${t.message}")
                    null
                }
                .orEmpty()
        }

        companion object {
            private const val PROBE_BDUSS = "__puredupan_cookie_probe__"

            fun resolve(cl: ClassLoader): DomesticCookieAccess? {
                return resolveByDexKitCache(cl)
                    ?: resolveByMethodName(
                    cl,
                    BaiduAutomationHookPoints.COOKIE_UTILS,
                    "getCookieByBduss",
                )
            }

            private fun resolveByDexKitCache(cl: ClassLoader): DomesticCookieAccess? {
                val probe = DomesticCookieByBdussResolver.cookieFor(cl, PROBE_BDUSS)
                if (!probe.looksLikeBdussCookie(PROBE_BDUSS)) return null
                XposedCompat.logD("[$TAG] cookie helper resolved: DexKit cache")
                return DomesticCookieAccess("DexKit") { bduss ->
                    DomesticCookieByBdussResolver.cookieFor(cl, bduss)
                }
            }

            private fun resolveByMethodName(
                cl: ClassLoader,
                className: String,
                methodName: String,
            ): DomesticCookieAccess? {
                return runCatching {
                    val clazz = XposedCompat.findClassOrNull(className, cl) ?: return null
                    val method = XposedCompat.findMethodOrNull(clazz, methodName, String::class.java)
                        ?: return null
                    val probe = method.invoke(null, PROBE_BDUSS) as? String
                    if (!probe.looksLikeBdussCookie(PROBE_BDUSS)) return null
                    XposedCompat.logD("[$TAG] cookie helper resolved: $className.$methodName")
                    DomesticCookieAccess("$className.$methodName") { bduss ->
                        method.invoke(null, bduss) as? String ?: ""
                    }
                }.getOrElse { t ->
                    XposedCompat.logD("[$TAG] cookie helper rejected: $className.$methodName, ${t.message}")
                    null
                }
            }

            private fun String?.looksLikeBdussCookie(bduss: String): Boolean {
                return this != null &&
                    contains(bduss) &&
                    contains("BDUSS", ignoreCase = true)
            }
        }
    }

}
