package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticIconResourceDownloadResolver {
    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER, cl)
            ?: return null
        val function2 = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.KOTLIN_FUNCTION2, cl)
            ?: return null
        return XposedCompat.findMethodOrNull(
            owner, BaiduDomesticHookPoints.ICON_DOWNLOAD_MANAGER_START_DOWNLOAD_METHOD, function2, function2,
        )?.takeIf { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE }
    }
}
