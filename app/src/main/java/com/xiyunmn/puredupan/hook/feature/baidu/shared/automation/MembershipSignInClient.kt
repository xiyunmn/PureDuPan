package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

internal sealed class MembershipSignInResult {
    data class Success(val points: Int?) : MembershipSignInResult()
    data class AlreadySignedIn(val message: String) : MembershipSignInResult()
    data class Failed(
        val detail: String,
        val errorCode: Int? = null,
        val errno: Int? = null,
        val message: String = "",
    ) : MembershipSignInResult()
}

internal sealed class MembershipSignInStatusResult {
    data object SignedIn : MembershipSignInStatusResult()
    data object NotSignedIn : MembershipSignInStatusResult()
    data class Unknown(val detail: String) : MembershipSignInStatusResult()
}

internal object MembershipSignInClient {
    private const val SIGN_IN_URL =
        "https://pan.baidu.com/rest/2.0/membership/level?app_id=250528&web=5&method=signin"
    private const val SIGN_IN_LIST_URL = "https://pan.baidu.com/pmall/points/signinlist"
    private const val TASK_CENTER_SIGN_IN_LIST_URL = "https://pan.baidu.com/coins/taskcenter/signinlist"
    private const val REFERER = "https://pan.baidu.com/wap/svip/growth/task"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/90.0.4430.91 Mobile Safari/537.36"
    private const val HTTP_TIMEOUT_MS = 12_000
    private const val SIGN_IN_STATUS_LOOKBACK_MS = 604_800_000L

    fun signIn(cookie: String, tag: String): MembershipSignInResult {
        if (cookie.isBlank()) {
            return MembershipSignInResult.Failed("membership request cookie unavailable")
        }

        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(SIGN_IN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", REFERER)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                setRequestProperty("Cookie", cookie)
            }
            val code = connection?.responseCode ?: -1
            val body = readResponseBody(connection, code)
            XposedCompat.logD("[$tag] membership sign-in response: code=$code, body=${safePreview(body)}")
            parseResponse(code, body)
        }.getOrElse { t ->
            MembershipSignInResult.Failed(
                "membership request failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}",
            )
        }.also {
            connection?.disconnect()
        }
    }

    fun queryTodaySignedIn(cookie: String, tag: String): MembershipSignInStatusResult {
        if (cookie.isBlank()) {
            return MembershipSignInStatusResult.Unknown("membership status cookie unavailable")
        }

        var connection: HttpURLConnection? = null
        return runCatching {
            val (startTime, endTime) = signInDateRange()
            val body = formBody(
                "ptype" to "1",
                "upgrade" to "v2",
                "starttime" to startTime,
                "endtime" to endTime,
            )
            connection = (URL(SIGN_IN_LIST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                useCaches = false
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", REFERER)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Cookie", cookie)
                outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection?.responseCode ?: -1
            val responseBody = readResponseBody(connection, code)
            XposedCompat.logD(
                "[$tag] membership sign-in status response: " +
                    "code=$code, body=${safePreview(responseBody)}",
            )
            parseStatusResponse(code, responseBody)
        }.getOrElse { t ->
            MembershipSignInStatusResult.Unknown(
                "membership status request failed: ${t.javaClass.simpleName}: " +
                    (t.message ?: "unknown"),
            )
        }.also {
            connection?.disconnect()
        }
    }

    fun queryTaskCenterSignedIn(cookie: String, tag: String): MembershipSignInStatusResult {
        if (cookie.isBlank()) {
            return MembershipSignInStatusResult.Unknown("task-center status cookie unavailable")
        }

        var connection: HttpURLConnection? = null
        return runCatching {
            val body = formBody("ptype" to "5")
            connection = (URL(TASK_CENTER_SIGN_IN_LIST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                useCaches = false
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", REFERER)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Cookie", cookie)
                outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection?.responseCode ?: -1
            val responseBody = readResponseBody(connection, code)
            XposedCompat.logD(
                "[$tag] task-center sign-in status response: " +
                    "code=$code, body=${safePreview(responseBody)}",
            )
            parseTaskCenterStatusResponse(code, responseBody)
        }.getOrElse { t ->
            MembershipSignInStatusResult.Unknown(
                "task-center status request failed: ${t.javaClass.simpleName}: " +
                    (t.message ?: "unknown"),
            )
        }.also {
            connection?.disconnect()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection?, code: Int): String {
        val stream = if (code in 200..399) {
            connection?.inputStream
        } else {
            connection?.errorStream ?: connection?.inputStream
        } ?: return ""
        return stream.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun parseResponse(code: Int, body: String): MembershipSignInResult {
        if (code != HttpURLConnection.HTTP_OK) {
            return MembershipSignInResult.Failed("membership endpoint http $code")
        }
        if (body.isBlank()) {
            return MembershipSignInResult.Failed("membership endpoint returned empty body")
        }

        val json = runCatching { JSONObject(body) }.getOrElse { t ->
            return MembershipSignInResult.Failed("membership response parse failed: ${t.message}")
        }
        val message = firstNonBlank(
            json.optString("error_msg"),
            json.optString("show_msg"),
            json.optString("errmsg"),
            json.optString("msg"),
        )
        val errno = if (json.has("errno") && !json.isNull("errno")) json.optInt("errno") else null
        val errorCode = if (json.has("error_code") && !json.isNull("error_code")) {
            json.optInt("error_code")
        } else {
            null
        }
        if (errorCode == ERROR_CODE_REPEAT_SIGN_IN ||
            (errorCode == ERROR_CODE_SIGN_IN_NOT_ALLOWED && message.isSignInNotAllowedMessage()) ||
            message.isAlreadySignedInMessage() ||
            body.isAlreadySignedInMessage()
        ) {
            return MembershipSignInResult.AlreadySignedIn(message.ifBlank { "already signed" })
        }

        val points = if (json.has("points") && !json.isNull("points")) {
            json.optInt("points")
        } else {
            null
        }
        if (points != null || ((errno == 0 || errorCode == 0) && message.isBlank())) {
            return MembershipSignInResult.Success(points)
        }

        val detail = buildString {
            append("membership endpoint rejected sign-in")
            if (errno != null) append(": errno=").append(errno)
            if (errorCode != null) append(": error_code=").append(errorCode)
            if (message.isNotBlank()) append(", msg=").append(message.take(80))
        }
        return MembershipSignInResult.Failed(
            detail = detail,
            errorCode = errorCode,
            errno = errno,
            message = message,
        )
    }

    private fun parseStatusResponse(code: Int, body: String): MembershipSignInStatusResult {
        if (code != HttpURLConnection.HTTP_OK) {
            return MembershipSignInStatusResult.Unknown("membership status endpoint http $code")
        }
        if (body.isBlank()) {
            return MembershipSignInStatusResult.Unknown("membership status endpoint returned empty body")
        }

        val json = runCatching { JSONObject(body) }.getOrElse { t ->
            return MembershipSignInStatusResult.Unknown("membership status parse failed: ${t.message}")
        }
        val errno = if (json.has("errno") && !json.isNull("errno")) json.optInt("errno") else null
        val message = firstNonBlank(
            json.optString("error_msg"),
            json.optString("show_msg"),
            json.optString("errmsg"),
            json.optString("msg"),
        )
        if (json.has("today_signined") && !json.isNull("today_signined")) {
            return if (json.optBoolean("today_signined")) {
                MembershipSignInStatusResult.SignedIn
            } else {
                MembershipSignInStatusResult.NotSignedIn
            }
        }
        return MembershipSignInStatusResult.Unknown(
            buildString {
                append("membership status missing today_signined")
                if (errno != null) append(": errno=").append(errno)
                if (message.isNotBlank()) append(", msg=").append(message.take(80))
            },
        )
    }

    private fun parseTaskCenterStatusResponse(code: Int, body: String): MembershipSignInStatusResult {
        if (code != HttpURLConnection.HTTP_OK) {
            return MembershipSignInStatusResult.Unknown("task-center status endpoint http $code")
        }
        if (body.isBlank()) {
            return MembershipSignInStatusResult.Unknown("task-center status endpoint returned empty body")
        }

        val json = runCatching { JSONObject(body) }.getOrElse { t ->
            return MembershipSignInStatusResult.Unknown("task-center status parse failed: ${t.message}")
        }
        val errno = if (json.has("errno") && !json.isNull("errno")) json.optInt("errno") else null
        val message = firstNonBlank(
            json.optString("error_msg"),
            json.optString("show_msg"),
            json.optString("errmsg"),
            json.optString("msg"),
        )
        val data = json.optJSONObject("data")
        val signedToday = when {
            data?.has("signed_today") == true && !data.isNull("signed_today") ->
                data.optInt("signed_today")
            json.has("signed_today") && !json.isNull("signed_today") ->
                json.optInt("signed_today")
            else -> null
        }
        if (signedToday != null) {
            return if (signedToday > 0) {
                MembershipSignInStatusResult.SignedIn
            } else {
                MembershipSignInStatusResult.NotSignedIn
            }
        }
        return MembershipSignInStatusResult.Unknown(
            buildString {
                append("task-center status missing signed_today")
                if (errno != null) append(": errno=").append(errno)
                if (message.isNotBlank()) append(", msg=").append(message.take(80))
            },
        )
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun String.isAlreadySignedInMessage(): Boolean {
        return contains("已签到") ||
            contains("已经签到") ||
                contains("签过") ||
                contains("重复签到") ||
                contains("already", ignoreCase = true) ||
                contains("repeat signin", ignoreCase = true)
    }

    private fun String.isSignInNotAllowedMessage(): Boolean =
        contains("signin not allow", ignoreCase = true)

    private fun safePreview(body: String): String {
        return body
            .replace(Regex("BDUSS=[^;\"\\s]+"), "BDUSS=<redacted>")
            .replace(Regex("STOKEN=[^;\"\\s]+"), "STOKEN=<redacted>")
            .replace(Regex("PANPSC=[^;\"\\s]+"), "PANPSC=<redacted>")
            .replace(Regex("BAIDUCUID=[^;\"\\s]+"), "BAIDUCUID=<redacted>")
            .take(240)
    }

    private fun signInDateRange(): Pair<String, String> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = System.currentTimeMillis()
        return formatter.format(Date(now - SIGN_IN_STATUS_LOOKBACK_MS)) to formatter.format(Date(now))
    }

    private fun formBody(vararg params: Pair<String, String>): String {
        return params.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private const val ERROR_CODE_REPEAT_SIGN_IN = 421001
    private const val ERROR_CODE_SIGN_IN_NOT_ALLOWED = 421003
}
