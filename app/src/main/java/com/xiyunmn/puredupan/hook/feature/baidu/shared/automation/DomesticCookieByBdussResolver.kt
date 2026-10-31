package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAutomationHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticCookieByBdussResolver {
    private const val TAG = "DomesticCookieByBdussResolver"
    private const val PROBE_BDUSS = "__puredupan_cookie_probe__"

    fun cookieFor(cl: ClassLoader, bduss: String): String {
        val owner = XposedCompat.findClassOrNull(BaiduAutomationHookPoints.COOKIE_UTILS, cl) ?: return ""
        val method = XposedCompat.findMethodOrNull(owner, "getCookieByBduss", String::class.java) ?: return ""
        if (!Modifier.isStatic(method.modifiers) || method.returnType != String::class.java) return ""
        if (!probeLooksLikeBdussCookie(method)) return ""
        return invokeCookieMethod(method, bduss)
    }

    private fun invokeCookieMethod(method: Method, bduss: String): String {
        return runCatching { method.invoke(null, bduss) as? String }
            .getOrElse { t ->
                XposedCompat.logD("[$TAG] invoke cookie helper failed: ${t.message}")
                null
            }
            .orEmpty()
    }

    private fun probeLooksLikeBdussCookie(method: Method): Boolean {
        val value = invokeCookieMethod(method, PROBE_BDUSS)
        return value.contains(PROBE_BDUSS) &&
            (value.contains("BDUSS=$PROBE_BDUSS") || value.contains("Cookie: BDUSS=$PROBE_BDUSS"))
    }

}
