package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticColdStartSplashResolver {
    private val loadPrefix = listOf(
        "android.app.Activity", "android.view.ViewGroup", BaiduDomesticHookPoints.ANDROIDX_LIFECYCLE_OWNER,
    )

    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_STABLE, cl)
            ?: return null
        val methods = owner.declaredMethods
        if (methods.none {
                !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                    it.parameterCount == 4 && it.parameterTypes.take(3).map(Class<*>::getName) == loadPrefix &&
                    it.parameterTypes.last().isInterface
            }
        ) return null
        return methods.singleOrNull {
            it.name == BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_IS_SHOW_SPLASH_STABLE_METHOD && !Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(Activity::class.java))
        }?.apply { isAccessible = true }
    }
}
