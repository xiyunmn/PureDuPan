package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object BottomAiTabResolver {
    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.AI_CLOUD_TAB_AMIS_KT, cl)
            ?: return null
        return XposedCompat.findMethodOrNull(owner, BaiduDomesticHookPoints.AI_CLOUD_TAB_AMIS_GET_MODE_METHOD)
            ?.takeIf { Modifier.isStatic(it.modifiers) && it.returnType == Long::class.javaPrimitiveType }
    }
}
