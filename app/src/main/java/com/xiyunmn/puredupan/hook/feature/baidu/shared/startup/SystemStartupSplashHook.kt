package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Application
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import com.xiyunmn.puredupan.hook.config.StartupSplashSettingsSync
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStartupSplashHookPoints
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/** Runs only in SystemUI; no host configuration, DexKit session or ordinary host plan is initialized here. */
internal object SystemStartupSplashHook {
    private const val TAG = "SystemStartupSplashHook"
    private var installed = false
    private val failureLogged = AtomicBoolean(false)

    @Synchronized
    fun hook(cl: ClassLoader) {
        if (installed || Build.VERSION.SDK_INT < 31) return
        val module = XposedCompat.module ?: return
        val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true }
        module.hook(attach).intercept { chain ->
            val result = chain.proceed()
            safely { StartupSplashSettingsSync.initializeSystemUi(chain.args[0] as Context) }
            result
        }
        installed = true

        runCatching {
            val builder = Class.forName(BaiduStartupSplashHookPoints.SPLASH_BUILDER, false, cl)
            val view = Class.forName(BaiduStartupSplashHookPoints.SPLASH_VIEW, false, cl)
            val build = builder.getDeclaredMethod("build")
            val contextField = builder.getDeclaredField("mContext").apply { isAccessible = true }
            val overlay = builder.getDeclaredField("mOverlayDrawable").apply { isAccessible = true }
            val iconBackground = builder.getDeclaredField("mIconBackground").apply { isAccessible = true }
            val maskPaint = runCatching {
                Class.forName(BaiduStartupSplashHookPoints.ICON_MASK_BACKGROUND, false, cl)
                    .getDeclaredField("mBackgroundPaint").apply { isAccessible = true }
            }.onFailure { XposedCompat.logW("[$TAG] icon mask unavailable: ${it.message}") }.getOrNull()
            val background = builder.getDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType).apply { isAccessible = true }
            check(build.returnType == view && contextField.type == Context::class.java &&
                overlay.type == Drawable::class.java && !Modifier.isFinal(overlay.modifiers) && background.returnType == builder)
            module.hook(build).intercept { chain ->
                safely {
                    val instance = chain.thisObject!!
                    val context = contextField.get(instance) as Context
                    val info = context.applicationInfo
                    if (StartupSplashSettingsSync.isEnabled(info) && StartupSplashDrawable.isSystemDark()) {
                        val original = overlay.get(instance) as? Drawable
                        val replacement = original?.let {
                            checkNotNull(StartupSplashDrawable.wrap(it, context.resources) { StartupSplashSettingsSync.isEnabled(info) }) {
                                "Unsupported system splash overlay: ${it.javaClass.name}"
                            }
                        }
                        background.invoke(instance, StartupSplashDrawable.DARK_BACKGROUND)
                        if (replacement != null) overlay.set(instance, replacement)
                        val mask = iconBackground.get(instance)
                        if (maskPaint != null && mask != null && maskPaint.declaringClass.isInstance(mask)) {
                            // This is the system's separate mask, not the app icon drawable.
                            (maskPaint.get(mask) as? Paint)?.color = StartupSplashDrawable.DARK_BACKGROUND
                        }
                    }
                }
                chain.proceed()
            }
            XposedCompat.log("[$TAG] framework splash hook INSTALLED")
        }.onFailure { XposedCompat.logW("[$TAG] framework install failed: ${it.message}") }

        hookLegacyIconMask(cl)

        runCatching {
            val manager = XposedCompat.findClassOrNull(BaiduStartupSplashHookPoints.COLOR_OS_STARTING_WINDOW, cl)
                ?: return@runCatching
            val view = Class.forName(BaiduStartupSplashHookPoints.SPLASH_VIEW, false, cl)
            val method = manager.getDeclaredMethod("setContentViewBackground", view, Drawable::class.java)
            check(!Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE)
            module.hook(method).intercept { chain ->
                var replacement: Drawable? = null
                safely {
                    val splash = chain.args[0] as? View ?: return@safely
                    val original = chain.args[1] as? Drawable ?: return@safely
                    val info = splash.context.applicationInfo
                    if (StartupSplashSettingsSync.isEnabled(info) && StartupSplashDrawable.isSystemDark()) {
                        replacement = checkNotNull(StartupSplashDrawable.wrap(original, splash.resources) {
                            StartupSplashSettingsSync.isEnabled(info)
                        }) { "Unsupported ColorOS splash background: ${original.javaClass.name}" }
                    }
                }
                // ColorOS captures this argument in a queued assignment after Builder.build().
                if (replacement == null) chain.proceed() else chain.proceed(arrayOf(chain.args[0], replacement))
            }
            XposedCompat.log("[$TAG] ColorOS splash hook INSTALLED")
        }.onFailure { XposedCompat.logW("[$TAG] ColorOS install failed: ${it.message}") }
    }

    private fun hookLegacyIconMask(cl: ClassLoader) {
        val module = XposedCompat.module ?: return
        runCatching {
            val builder = Class.forName(BaiduStartupSplashHookPoints.SHELL_SPLASH_BUILDER, false, cl)
            val factory = Class.forName(BaiduStartupSplashHookPoints.SHAPE_ICON_FACTORY, false, cl)
            val baseFactory = factory.superclass
            val contextField = builder.getDeclaredField("mContext").apply { isAccessible = true }
            val build = builder.getDeclaredMethod("build")
            // Current SettingsLib uses IconOptions; the older AOSP implementation normalizes directly.
            val wrap = baseFactory.declaredMethods.singleOrNull {
                it.name == "wrapToAdaptiveIcon" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == Drawable::class.java &&
                    // Optimized SystemUI DEX can omit InnerClass metadata; simpleName then includes the outer name.
                    it.parameterTypes[1].name == "${baseFactory.name}\$IconOptions"
            } ?: baseFactory.getDeclaredMethod(
                "normalizeAndWrapToAdaptiveIcon", Drawable::class.java, Boolean::class.javaPrimitiveType,
                RectF::class.java, FloatArray::class.java,
            )
            check(Drawable::class.java.isAssignableFrom(wrap.returnType) && !Modifier.isStatic(wrap.modifiers))
            val darkMask = ThreadLocal<Boolean>()
            module.hook(wrap).intercept { chain ->
                val result = chain.proceed()
                if (darkMask.get() == true && factory.isInstance(chain.thisObject) &&
                    chain.args[0] !is AdaptiveIconDrawable && result is AdaptiveIconDrawable
                ) safely {
                    // Only a wrapper just created for a legacy icon is eligible. Its foreground,
                    // scaling and original pixels remain owned by the system and host.
                    (result.background as? ColorDrawable)?.color = StartupSplashDrawable.DARK_BACKGROUND
                }
                result
            }
            module.hook(build).intercept { chain ->
                val previous = darkMask.get()
                var adapt = false
                safely {
                    val context = contextField.get(chain.thisObject) as Context
                    adapt = StartupSplashSettingsSync.isEnabled(context.applicationInfo) && StartupSplashDrawable.isSystemDark()
                }
                darkMask.set(adapt)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) darkMask.remove() else darkMask.set(previous)
                }
            }
            XposedCompat.log("[$TAG] legacy icon mask hook INSTALLED")
        }.onFailure { XposedCompat.logW("[$TAG] legacy icon mask install failed: ${it.message}") }
    }

    private inline fun safely(action: () -> Unit) {
        runCatching(action).onFailure {
            if (failureLogged.compareAndSet(false, true)) {
                XposedCompat.logW("[$TAG] instance skipped: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }
}
