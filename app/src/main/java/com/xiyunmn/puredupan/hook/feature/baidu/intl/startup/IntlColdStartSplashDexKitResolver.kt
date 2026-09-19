package com.xiyunmn.puredupan.hook.feature.baidu.intl.startup

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** Resolve the cold-ad decision called by Navigate, without depending on obfuscated names. */
internal object IntlColdStartSplashDexKitResolver {
    const val CACHE_ID = "intl_cold_start_splash_gate_v1"
    private const val TAG = "IntlColdStartSplashDexKitResolver"
    private const val FRAGMENT_ACTIVITY = "androidx.fragment.app.FragmentActivity"
    private val loadParams = listOf(
        FRAGMENT_ACTIVITY,
        "android.view.ViewGroup",
        BaiduIntlHookPoints.AD_FINISH_LISTENER,
    )

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { validateRef(cl, it) }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        // A null bridge means scanning is unavailable/deferred, not a negative resolution.
        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val entry = bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create()
                        .declaredClass(BaiduIntlHookPoints.NAVIGATE_ACTIVITY)
                        .name(BaiduIntlHookPoints.NAVIGATE_SHOW_FLASH_SCREEN)
                        .returnType("void")
                        .paramCount(0),
                ),
            ).singleOrNull()
            val calls = entry?.invokes.orEmpty()
            calls.filter { gate ->
                !gate.isConstructor && !Modifier.isStatic(gate.modifiers) &&
                    gate.returnTypeName == "boolean" && gate.paramTypeNames == listOf(FRAGMENT_ACTIVITY) &&
                    // Same manager starts the cold strategy and records ColdAdCloseType reasons.
                    calls.any { load ->
                        load.className == gate.className && load.returnTypeName == "void" &&
                            load.paramTypeNames == loadParams && load.invokes.any {
                                it.isConstructor && it.className == BaiduIntlHookPoints.SPLASH_ADS_LOAD_STRATEGY
                            }
                    } && gate.invokes.any {
                        it.className == gate.className &&
                            it.paramTypeNames == listOf(BaiduIntlHookPoints.COLD_AD_CLOSE_TYPE)
                    }
            }.map { DexKitCompat.MethodRef(it.className, it.name) }.distinct()
        } ?: return null

        // Refuse ambiguous matches; never pick the first similarly shaped boolean method.
        val method = candidates.singleOrNull()?.let { validateRef(cl, it) }
        if (method == null) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "cold gate candidates=${candidates.size}")
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
        if (methods.none { it.returnType == Void.TYPE && it.parameterTypes.map(Class<*>::getName) == loadParams }) return null
        if (methods.none { it.returnType == Void.TYPE && it.parameterTypes.map(Class<*>::getName) == listOf(BaiduIntlHookPoints.COLD_AD_CLOSE_TYPE) }) return null
        return methods.singleOrNull {
            it.name == ref.methodName && !Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.map(Class<*>::getName) == listOf(FRAGMENT_ACTIVITY)
        }?.apply { isAccessible = true }
    }
}
