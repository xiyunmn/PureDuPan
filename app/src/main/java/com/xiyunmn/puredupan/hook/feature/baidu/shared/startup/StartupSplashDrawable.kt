package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.LevelListDrawable
import com.xiyunmn.puredupan.hook.config.StartupSplashSettingsSync
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import java.util.WeakHashMap

/** 宿主仅持有框架 Drawable，模块退役后由新代接管 level 更新。 */
internal object StartupSplashDrawable {
    const val DARK_BACKGROUND: Int = -0xededee
    private val darkBitmaps = WeakHashMap<Bitmap, Bitmap>()
    private val darkBrandingBitmaps = WeakHashMap<Bitmap, Bitmap>()
    private val drawables = WeakHashMap<LevelListDrawable, Array<Any>>()
    private var configurationHooked = false

    fun isSystemDark(): Boolean = Resources.getSystem().configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun installConfigurationHook(application: Application) {
        if (ModuleLifetime.isRetiring || configurationHooked) return
        val module = XposedCompat.module ?: return
        val method = application.javaClass.getMethod("onConfigurationChanged", Configuration::class.java)
        module.hook(method).setId("StartupSplash:applicationConfiguration").intercept { chain ->
            val result = chain.proceed()
            if (chain.thisObject === application && !ModuleLifetime.isRetiring) updateLevels()
            result
        }
        configurationHooked = true
        updateLevels()
    }

    fun updateLevels() {
        if (ModuleLifetime.isRetiring) return
        val dark = isSystemDark()
        synchronized(drawables) {
            drawables.forEach { (drawable, identity) ->
                val uid = identity[1] as Int
                val enabled = if (uid < 0) HookSettings.settingsSnapshot().isAdaptiveStartupSplashEnabled
                    else StartupSplashSettingsSync.isEnabled(uid, identity[0] as String)
                drawable.level = if (dark && enabled) 1 else 0
            }
        }
    }

    fun exportState(): ArrayList<Array<Any?>> = synchronized(drawables) {
        ArrayList(drawables.map { (drawable, identity) -> arrayOf<Any?>(drawable, identity[0], identity[1]) })
    }

    fun restoreState(state: List<Array<Any?>>) {
        synchronized(drawables) {
            state.forEach { entry ->
                drawables[entry[0] as LevelListDrawable] = arrayOf(requireNotNull(entry[1]), requireNotNull(entry[2]))
            }
        }
        updateLevels()
    }

    fun disposeForReload() {
        synchronized(drawables) { drawables.clear() }
        synchronized(darkBitmaps) { darkBitmaps.clear() }
        synchronized(darkBrandingBitmaps) { darkBrandingBitmaps.clear() }
    }

    fun wrap(original: Drawable, resources: Resources, packageName: String = "", uid: Int = -1): Drawable? {
        if (ModuleLifetime.isRetiring) return null
        synchronized(drawables) { if (original is LevelListDrawable && drawables.containsKey(original)) return original }
        val light = original.constantState?.newDrawable()?.mutate() ?: return null
        val dark = when (original) {
            is ColorDrawable -> ColorDrawable(DARK_BACKGROUND)
            is LayerDrawable -> {
                if (original.numberOfLayers != 3 || (1..2).any { original.getDrawable(it) !is BitmapDrawable }) return null
                val layers = original.constantState?.newDrawable()?.mutate() as? LayerDrawable ?: return null
                layers.setDrawable(0, ColorDrawable(DARK_BACKGROUND))
                for (index in 1..2) {
                    val artwork = original.getDrawable(index) as BitmapDrawable
                    layers.setDrawable(index, BitmapDrawable(resources, darkBitmap(artwork.bitmap, index == 2)).apply {
                        gravity = artwork.gravity
                        alpha = artwork.alpha
                        isAutoMirrored = artwork.isAutoMirrored
                        isFilterBitmap = artwork.isFilterBitmap
                        setTileModeXY(artwork.tileModeX, artwork.tileModeY)
                    })
                }
                layers
            }
            else -> return null
        }
        val drawable = LevelListDrawable().apply {
            addLevel(0, 0, light)
            addLevel(1, 1, dark)
            bounds = original.bounds
            state = original.state
            layoutDirection = original.layoutDirection
            alpha = original.alpha
        }
        synchronized(drawables) {
            if (ModuleLifetime.isRetiring) return null
            drawables[drawable] = arrayOf<Any>(packageName, uid)
        }
        updateLevels()
        return drawable
    }

    private fun darkBitmap(bitmap: Bitmap, branding: Boolean): Bitmap {
        val cache = if (branding) darkBrandingBitmaps else darkBitmaps
        return synchronized(cache) { cache.getOrPut(bitmap) {
            val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
                checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            } else bitmap
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val bubble = if (branding) null else speechBubble(pixels, source.width, source.height)
            for (index in pixels.indices) {
                val pixel = pixels[index]
                val alpha = Color.alpha(pixel)
                if (alpha == 0) continue
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val high = maxOf(red, green, blue)
                val low = minOf(red, green, blue)
                val inBubble = bubble?.get(index) == true
                pixels[index] = when {
                    // 调亮品牌文字、弱化次要文字，并保留标志原色。
                    branding && high - low <= 40 && high <= 210 -> {
                        if (high < 80) Color.argb(alpha, 218, 221, 224)
                        else Color.argb(alpha, 142, 147, 153)
                    }
                    branding -> pixel
                    // 对独立气泡的中性色填充和文字反色，保留吉祥物白色高光。
                    inBubble && high - low <= 24 -> {
                        val ink = 255 - (red + green + blue) / 3
                        Color.argb(alpha, 18 + 200 * ink / 255,
                            18 + 203 * ink / 255, 18 + 206 * ink / 255)
                    }
                    high < 160 && high - low <= 24 -> Color.argb(alpha, 218, 221, 224)
                    low >= 140 -> {
                        // 将底台淡彩背景转为中性色，保留透明度、曲线和倒影。
                        // 柔化不透明的白色区域。
                        val wash = if (inBubble) 0 else ((248 - alpha) * 255 / 56).coerceIn(0, 255)
                        val gray = 26 + (red + green + blue) / 60
                        val lightPercent = if (inBubble) 90 else 84
                        Color.argb(alpha,
                            (red * lightPercent / 100 * (255 - wash) + gray * wash) / 255,
                            (green * lightPercent / 100 * (255 - wash) + gray * wash) / 255,
                            (blue * lightPercent / 100 * (255 - wash) + gray * wash) / 255)
                    }
                    else -> pixel
                }
            }
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888).apply {
                density = bitmap.density
            }
        } }
    }

    private fun speechBubble(pixels: IntArray, width: Int, height: Int): BooleanArray? {
        // 通过连通区域定位右上方独立气泡。
        val seed = pixels.indexOfFirst { Color.alpha(it) != 0 && (it and 0x00ffffff) == 0x00ffffff }
        if (seed < 0) return null
        val mask = BooleanArray(pixels.size)
        val pending = ArrayDeque<Int>()
        fun visit(index: Int) {
            if (!mask[index] && Color.alpha(pixels[index]) != 0) {
                mask[index] = true
                pending.addLast(index)
            }
        }
        visit(seed)
        while (pending.isNotEmpty()) {
            val index = pending.removeFirst()
            val x = index % width
            val y = index / width
            // 保留与吉祥物或背景相连的白色区域。
            if (x < width / 2 || y >= height / 3) return null
            if (x > 0) visit(index - 1)
            if (x + 1 < width) visit(index + 1)
            if (y > 0) visit(index - width)
            if (y + 1 < height) visit(index + width)
        }
        return mask
    }
}
