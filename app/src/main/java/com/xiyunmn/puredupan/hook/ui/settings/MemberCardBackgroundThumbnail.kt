package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.xiyunmn.puredupan.hook.ui.UiStyle

/** 仅在设置页可见时读取缩略图，换图后丢弃旧请求。 */
internal class MemberCardBackgroundThumbnail(context: Context) : ImageView(context) {
    private var imageUri: String? = null
    private var request = 0

    init {
        scaleType = ScaleType.CENTER_CROP
        clipToOutline = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        UiStyle.bindTheme(this) { tokens ->
            background = GradientDrawable().apply {
                setColor(tokens.surfaceAlt)
                cornerRadius = 6 * resources.displayMetrics.density
            }
        }
    }

    fun bind(uri: String?) {
        imageUri = uri
        request++
        setImageDrawable(null)
        visibility = if (uri.isNullOrBlank()) View.GONE else View.VISIBLE
        if (isAttachedToWindow) load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    override fun onDetachedFromWindow() {
        request++
        setImageDrawable(null)
        super.onDetachedFromWindow()
    }

    private fun load() {
        val uri = imageUri?.takeIf { it.isNotBlank() } ?: return
        val currentRequest = ++request
        val target = (44 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        ModuleLifetime.launchWorker("PureDuPan-card-thumbnail") {
            val bitmap = runCatching {
                val resolver = context.contentResolver
                val parsed = Uri.parse(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null
                var sample = 1
                while (options.outWidth / (sample * 2) >= target && options.outHeight / (sample * 2) >= target) {
                    sample *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
                resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
            ModuleLifetime.postResult(android.os.Handler(android.os.Looper.getMainLooper())) {
                if (currentRequest != request || !isAttachedToWindow) {
                    bitmap?.recycle()
                } else {
                    setImageBitmap(bitmap)
                }
            }
        }
    }
}
