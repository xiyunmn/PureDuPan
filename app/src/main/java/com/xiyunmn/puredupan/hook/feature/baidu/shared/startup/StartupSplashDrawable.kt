package com.xiyunmn.puredupan.hook.feature.baidu.shared.startup

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import java.util.WeakHashMap

/** Keeps the native artwork and geometry, including backgrounds retained by the host's launch cache. */
internal class StartupSplashDrawable private constructor(
    private val light: Drawable,
    private val dark: Drawable,
    private val enabled: () -> Boolean,
) : Drawable() {
    override fun draw(canvas: Canvas) {
        (if (enabled() && isSystemDark()) dark else light).draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        light.bounds = bounds
        dark.bounds = bounds
    }

    override fun onStateChange(state: IntArray): Boolean = light.setState(state) or dark.setState(state)
    override fun onLevelChange(level: Int): Boolean = light.setLevel(level) or dark.setLevel(level)
    override fun onLayoutDirectionChanged(direction: Int): Boolean =
        light.setLayoutDirection(direction) or dark.setLayoutDirection(direction)
    override fun isStateful(): Boolean = light.isStateful || dark.isStateful
    override fun getPadding(padding: Rect): Boolean = light.getPadding(padding)
    override fun getIntrinsicWidth(): Int = light.intrinsicWidth
    override fun getIntrinsicHeight(): Int = light.intrinsicHeight
    override fun getAlpha(): Int = light.alpha

    override fun setAlpha(alpha: Int) {
        light.alpha = alpha
        dark.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        light.colorFilter = filter
        dark.colorFilter = filter
        invalidateSelf()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = if (light.opacity == dark.opacity) light.opacity else PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState? {
        val lightState = light.constantState ?: return null
        val darkState = dark.constantState ?: return null
        return object : ConstantState() {
            override fun newDrawable(): Drawable = StartupSplashDrawable(
                lightState.newDrawable().mutate(), darkState.newDrawable().mutate(), enabled,
            )
            override fun getChangingConfigurations(): Int =
                lightState.changingConfigurations or darkState.changingConfigurations
        }
    }

    companion object {
        const val DARK_BACKGROUND: Int = -0xededee // #FF121212
        private val darkBitmaps = WeakHashMap<Bitmap, Bitmap>()
        private val darkBrandingBitmaps = WeakHashMap<Bitmap, Bitmap>()

        fun isSystemDark(): Boolean = Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        fun wrap(original: Drawable, resources: Resources, enabled: () -> Boolean): Drawable? {
            if (original is StartupSplashDrawable) return original
            val light = original.constantState?.newDrawable()?.mutate() ?: return null
            val dark = when (original) {
                is ColorDrawable -> ColorDrawable(DARK_BACKGROUND)
                is LayerDrawable -> {
                    // The host welcome resource contains a background, slogan and bottom branding.
                    if (original.numberOfLayers != 3 || (1..2).any { original.getDrawable(it) !is BitmapDrawable }) {
                        return null
                    }
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
            return StartupSplashDrawable(light, dark, enabled).apply {
                bounds = original.bounds
                state = original.state
                level = original.level
                layoutDirection = original.layoutDirection
                alpha = original.alpha
            }
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
                        // K3: light brand text, muted secondary text, original colored logo.
                        branding && high - low <= 40 && high <= 210 -> {
                            if (high < 80) Color.argb(alpha, 218, 221, 224)
                            else Color.argb(alpha, 142, 147, 153)
                        }
                        branding -> pixel
                        // Invert only the isolated speech bubble's neutral fill and lettering.
                        // White highlights on the mascot must not turn into dark holes.
                        inBubble && high - low <= 24 -> {
                            val ink = 255 - (red + green + blue) / 3
                            Color.argb(alpha, 18 + 200 * ink / 255,
                                18 + 203 * ink / 255, 18 + 206 * ink / 255)
                        }
                        high < 160 && high - low <= 24 -> Color.argb(alpha, 218, 221, 224)
                        low >= 140 -> {
                            // The native platform is a translucent pastel wash. Neutralize it
                            // while retaining its alpha, curve and reflection; soften opaque whites.
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
            // Locate the native, isolated top-right bubble by connectivity, not fixed pixel coordinates.
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
                // A connected mascot/background is not a speech bubble; leave its whites intact.
                if (x < width / 2 || y >= height / 3) return null
                if (x > 0) visit(index - 1)
                if (x + 1 < width) visit(index + 1)
                if (y > 0) visit(index - width)
                if (y + 1 < height) visit(index + width)
            }
            return mask
        }
    }
}
