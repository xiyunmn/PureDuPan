package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticFloatViewStartupResolver {
    fun resolveInitAudioCircleView(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_STABLE_CLASS, cl)
            ?: return null
        return XposedCompat.findMethodOrNull(
            owner, BaiduDomesticDexKitHookPoints.FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_STABLE_METHOD,
        )?.takeIf { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE }
    }
}
