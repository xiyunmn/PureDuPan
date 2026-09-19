package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** Resolve the decision at Navigate's cold-ad call site, not similar booleans elsewhere. */
internal object DomesticColdStartSplashDexKitResolver {
    const val CACHE_ID = "domestic_cold_start_splash_gate_v2"
    private const val TAG = "DomesticColdStartSplashDexKitResolver"
    private val loadPrefix = listOf(
        "android.app.Activity", "android.view.ViewGroup", BaiduDomesticHookPoints.ANDROIDX_LIFECYCLE_OWNER,
    )

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { validateRef(cl, it) }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val entry = bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(BaiduDomesticHookPoints.NAVIGATE_ACTIVITY)
                        .name(BaiduDomesticHookPoints.NAVIGATE_SHOW_FLASH_SCREEN)
                        .returnType("void").paramCount(0),
                ),
            ).singleOrNull()
            val calls = entry?.invokes.orEmpty()
            calls.filter { gate ->
                !Modifier.isStatic(gate.modifiers) && gate.returnTypeName == "boolean" &&
                    gate.paramTypeNames == listOf("android.app.Activity") &&
                    calls.any { load ->
                        load.className == gate.className && !Modifier.isStatic(load.modifiers) &&
                            load.returnTypeName == "void" && load.paramTypeNames.size == 4 &&
                            load.paramTypeNames.take(3) == loadPrefix
                    }
            }.map { DexKitCompat.MethodRef(it.className, it.name) }.distinct()
        }
        if (candidates == null) {
            return validateRef(
                cl,
                DexKitCompat.MethodRef(
                    BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_STABLE,
                    BaiduStartupHookPoints.DOMESTIC_SPLASH_MANAGER_IS_SHOW_SPLASH_STABLE_METHOD,
                ),
            )
        }
        val method = candidates.singleOrNull()?.let { validateRef(cl, it) }
        if (method == null) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "Navigate cold-gate candidates=${candidates.size}")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, DexKitCompat.MethodRef(method.declaringClass.name, method.name))
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "dexkit:${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val owner = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val methods = owner.declaredMethods
        if (methods.none {
                !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                    it.parameterCount == 4 && it.parameterTypes.take(3).map(Class<*>::getName) == loadPrefix &&
                    it.parameterTypes.last().isInterface
            }
        ) return null
        return methods.singleOrNull {
            it.name == ref.methodName && !Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(Activity::class.java))
        }?.apply { isAccessible = true }
    }
}
