package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticThumbnailOperatorResolver {
    fun resolveClientComputeInit(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_STABLE_CLASS, cl)
            ?: return null
        return XposedCompat.findMethodOrNull(owner, BaiduDomesticDexKitHookPoints.CLIENT_COMPUTE_MANAGER_INIT_METHOD, Context::class.java)
            ?.takeIf { !Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType }
    }

    fun resolveThumbnailAddJob(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.THUMBNAIL_OPERATOR_UTIL, cl)
            ?: return null
        return clazz.declaredMethods.singleOrNull { method ->
            val params = method.parameterTypes
            Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                params.size == 4 && params[0] == Context::class.java &&
                params[2].name == BaiduDomesticDexKitHookPoints.CONFIG_COMPRESS_IMAGE &&
                params[3] == String::class.java
        }?.apply { isAccessible = true }
    }
}
