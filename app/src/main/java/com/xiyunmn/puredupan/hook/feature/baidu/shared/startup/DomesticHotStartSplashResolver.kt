package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.lang.reflect.Modifier

internal object DomesticHotStartSplashResolver {
    data class ResolveResult(val className: String, val methodName: String)

    fun resolve(cl: ClassLoader): ResolveResult? {
        val owner = XposedCompat.findClassOrNull(BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_STABLE, cl)
            ?: return null
        val method = XposedCompat.findMethodOrNull(
            owner, BaiduStartupHookPoints.DOMESTIC_HOT_START_MANAGER_ON_RESUME_STABLE_METHOD,
            Activity::class.java,
        ) ?: return null
        if (!Modifier.isStatic(method.modifiers) || method.returnType != Boolean::class.javaPrimitiveType) return null
        return ResolveResult(owner.name, method.name)
    }
}
