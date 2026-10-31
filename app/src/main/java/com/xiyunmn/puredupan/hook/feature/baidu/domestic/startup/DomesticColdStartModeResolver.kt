package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticColdStartModeResolver {
    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.MAIN_PROCESS_INIT_KT, cl)
            ?: return null
        return XposedCompat.findMethodOrNull(owner, BaiduDomesticHookPoints.MAIN_AD_PARALLEL_IS_OPEN)
            ?.takeIf { Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType }
    }
}
