package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticUpdateDialogResolver {
    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.VERSION_UPDATE_HELPER, cl)
            ?: return null
        return owner.declaredMethods.singleOrNull {
            it.name == BaiduDomesticHookPoints.VERSION_UPDATE_HELPER_SHOW_LC_VERSION_DIALOG_METHOD &&
                !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterTypes.map(Class<*>::getName) == listOf(
                    "android.app.Activity", BaiduDomesticHookPoints.UPDATE_INFO,
                    BaiduDomesticHookPoints.PRIORITY_DIALOG_INFO, "java.lang.String",
                )
        }?.apply { isAccessible = true }
    }
}
