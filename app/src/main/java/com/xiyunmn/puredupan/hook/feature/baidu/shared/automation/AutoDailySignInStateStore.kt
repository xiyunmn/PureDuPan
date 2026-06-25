package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.UiText
import java.security.MessageDigest
import java.util.Calendar

internal object AutoDailySignInStateStore {
    private const val PREFIX_LAST_ATTEMPT_DAY = "auto_daily_sign_in_last_attempt_day_"
    private const val PREFIX_LAST_SUCCESS_DAY = "auto_daily_sign_in_last_success_day_"
    private const val PREFIX_LAST_STATUS = "auto_daily_sign_in_last_status_"
    private const val PREFIX_LAST_UPDATED_AT = "auto_daily_sign_in_last_updated_at_"
    private const val STATUS_STARTED = "started"
    private const val STATUS_SUCCESS = "success"
    private const val STATUS_SKIPPED = "skipped"
    private const val STATUS_FAILED = "failed"
    private const val FALLBACK_ACCOUNT_IDENTITY = "default"
    private const val HEX = "0123456789abcdef"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun beginAttempt(context: Context, accountIdentity: String?, tag: String): Boolean {
        if (!HookSettings.isAutoDailySignInEnabled) return false
        val prefs = HookSettings.getModuleStatePrefs(context)
        val suffix = accountSuffix(accountIdentity)
        val today = todayKey()

        synchronized(this) {
            if (prefs.getInt(PREFIX_LAST_SUCCESS_DAY + suffix, 0) == today) {
                XposedCompat.logD("[$tag] auto sign-in skipped: already successful today")
                return false
            }
            if (prefs.getInt(PREFIX_LAST_ATTEMPT_DAY + suffix, 0) == today) {
                XposedCompat.logD("[$tag] auto sign-in skipped: already attempted today")
                return false
            }
            prefs.edit()
                .putInt(PREFIX_LAST_ATTEMPT_DAY + suffix, today)
                .putString(PREFIX_LAST_STATUS + suffix, STATUS_STARTED)
                .putLong(PREFIX_LAST_UPDATED_AT + suffix, System.currentTimeMillis())
                .apply()
        }

        XposedCompat.log("[$tag] auto sign-in attempt started")
        return true
    }

    fun markSuccess(context: Context, accountIdentity: String?, tag: String, detail: String) {
        markFinished(context, accountIdentity, STATUS_SUCCESS, detail)
        showToast(context, UiText.Settings.AUTO_DAILY_SIGN_IN_SUCCESS_TOAST)
        XposedCompat.log("[$tag] auto sign-in finished: $detail")
    }

    fun markSkipped(context: Context, accountIdentity: String?, tag: String, detail: String) {
        markFinished(context, accountIdentity, STATUS_SKIPPED, detail)
        XposedCompat.log("[$tag] auto sign-in skipped: $detail")
    }

    fun markFailed(context: Context, accountIdentity: String?, tag: String, detail: String) {
        markFinished(context, accountIdentity, STATUS_FAILED, detail)
        showToast(context, UiText.Settings.AUTO_DAILY_SIGN_IN_FAILED_TOAST)
        XposedCompat.log("[$tag] auto sign-in stopped: $detail")
    }

    private fun markFinished(
        context: Context,
        accountIdentity: String?,
        status: String,
        detail: String,
    ) {
        val suffix = accountSuffix(accountIdentity)
        val today = todayKey()
        HookSettings.getModuleStatePrefs(context)
            .edit()
            .putInt(PREFIX_LAST_SUCCESS_DAY + suffix, if (status == STATUS_SUCCESS) today else 0)
            .putString(PREFIX_LAST_STATUS + suffix, "$status:$detail")
            .putLong(PREFIX_LAST_UPDATED_AT + suffix, System.currentTimeMillis())
            .apply()
    }

    private fun todayKey(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun showToast(context: Context, text: String) {
        val appContext = context.applicationContext ?: context
        val action = {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun accountSuffix(accountIdentity: String?): String {
        val normalized = accountIdentity?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_ACCOUNT_IDENTITY
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(16)
        for (i in 0 until 8.coerceAtMost(digest.size)) {
            val value = digest[i].toInt() and 0xff
            builder.append(HEX[value ushr 4])
            builder.append(HEX[value and 0x0f])
        }
        return builder.toString()
    }
}
