package com.xiyunmn.puredupan.hook.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class MemberCardBackgroundEditorView(
    context: Context,
    private val bitmap: Bitmap,
    private val cropAspectRatio: Float = 3f,
) : View(context) {
    init { contentDescription = UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_HINT }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val matrix = Matrix()
    private val cropRect = RectF()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scalePercent = (scalePercent * detector.scaleFactor).roundToInt()
                onScalePercentChanged?.invoke(scalePercent)
                return true
            }
        },
    )
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    var onScalePercentChanged: ((Int) -> Unit)? = null
    var onTransformChanged: (() -> Unit)? = null
    var onGestureFinished: (() -> Unit)? = null

    var scalePercent: Int = 100
        set(value) {
            val normalized = value.coerceIn(100, 300)
            if (field == normalized) return
            field = normalized
            invalidate()
            onTransformChanged?.invoke()
        }

    var rotationDegrees: Int = 0
        set(value) {
            val normalized = ((value % 360) + 360) % 360
            if (field == normalized) return
            field = normalized
            invalidate()
            onTransformChanged?.invoke()
        }

    var mirrored: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            onTransformChanged?.invoke()
        }

    var offsetXPermille: Int = 0
        set(value) {
            val normalized = value.coerceIn(-1000, 1000)
            if (field == normalized) return
            field = normalized
            invalidate()
            onTransformChanged?.invoke()
        }

    var offsetYPermille: Int = 0
        set(value) {
            val normalized = value.coerceIn(-1000, 1000)
            if (field == normalized) return
            field = normalized
            invalidate()
            onTransformChanged?.invoke()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val desiredHeight = min((width * 1.16f).toInt(), (resources.displayMetrics.heightPixels * 0.45f).toInt()).coerceAtLeast(1)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }.coerceAtLeast(1)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(24, 24, 24))
        updateCropRect()
        configureMatrix(cropRect)
        canvas.drawBitmap(bitmap, matrix, imagePaint)
        drawCropOverlay(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.getX(event.findPointerIndex(activePointerId).coerceAtLeast(0))
                lastY = event.getY(event.findPointerIndex(activePointerId).coerceAtLeast(0))
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.getX(event.findPointerIndex(activePointerId).coerceAtLeast(0))
                lastY = event.getY(event.findPointerIndex(activePointerId).coerceAtLeast(0))
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    val nextIndex = if (event.actionIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(nextIndex)
                }
                val index = event.findPointerIndex(activePointerId)
                lastX = event.getX(index)
                lastY = event.getY(index)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    updateCropRect()
                    val overflow = calculateOverflow(cropRect)
                    if (overflow.first > 1f) {
                        offsetXPermille += (((x - lastX) / overflow.first) * 1000f).toInt()
                    }
                    if (overflow.second > 1f) {
                        offsetYPermille += (((y - lastY) / overflow.second) * 1000f).toInt()
                    }
                }
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                onGestureFinished?.invoke()
                return true
            }
        }
        return true
    }

    private fun updateCropRect() {
        val horizontalInset = resources.displayMetrics.density
        val availableWidth = (width - horizontalInset * 2f).coerceAtLeast(1f)
        val availableHeight = (height - horizontalInset * 2f).coerceAtLeast(1f)
        val safeAspectRatio = cropAspectRatio.coerceIn(0.35f, 6f)
        var targetWidth = availableWidth
        var targetHeight = targetWidth / safeAspectRatio
        if (targetHeight > availableHeight) {
            targetHeight = availableHeight
            targetWidth = targetHeight * safeAspectRatio
        }
        val left = (width - targetWidth) / 2f
        val top = (height - targetHeight) / 2f
        cropRect.set(left, top, left + targetWidth, top + targetHeight)
    }

    private fun configureMatrix(target: RectF) {
        val targetWidth = target.width().coerceAtLeast(1f)
        val targetHeight = target.height().coerceAtLeast(1f)
        val sourceWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val sourceHeight = bitmap.height.toFloat().coerceAtLeast(1f)
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (normalizedRotation % 180 == 0) sourceWidth else sourceHeight
        val rotatedHeight = if (normalizedRotation % 180 == 0) sourceHeight else sourceWidth
        val coverScale = max(targetWidth / rotatedWidth, targetHeight / rotatedHeight)
        val userScale = scalePercent.coerceIn(100, 300) / 100f
        val drawScale = coverScale * userScale
        val overflowX = ((rotatedWidth * drawScale) - targetWidth).coerceAtLeast(0f) / 2f
        val overflowY = ((rotatedHeight * drawScale) - targetHeight).coerceAtLeast(0f) / 2f
        val offsetX = overflowX * offsetXPermille.coerceIn(-1000, 1000) / 1000f
        val offsetY = overflowY * offsetYPermille.coerceIn(-1000, 1000) / 1000f

        matrix.reset()
        matrix.postTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
        matrix.postRotate(normalizedRotation.toFloat())
        // 旋转后沿取景框水平方向镜像，拖动偏移仍使用屏幕坐标。
        matrix.postScale(if (mirrored) -drawScale else drawScale, drawScale)
        matrix.postTranslate(
            target.left + targetWidth / 2f + offsetX,
            target.top + targetHeight / 2f + offsetY,
        )
    }

    private fun calculateOverflow(target: RectF): Pair<Float, Float> {
        val targetWidth = target.width().coerceAtLeast(1f)
        val targetHeight = target.height().coerceAtLeast(1f)
        val sourceWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val sourceHeight = bitmap.height.toFloat().coerceAtLeast(1f)
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (normalizedRotation % 180 == 0) sourceWidth else sourceHeight
        val rotatedHeight = if (normalizedRotation % 180 == 0) sourceHeight else sourceWidth
        val coverScale = max(targetWidth / rotatedWidth, targetHeight / rotatedHeight)
        val userScale = scalePercent.coerceIn(100, 300) / 100f
        val drawScale = coverScale * userScale
        return Pair(
            ((rotatedWidth * drawScale) - targetWidth).coerceAtLeast(0f) / 2f,
            ((rotatedHeight * drawScale) - targetHeight).coerceAtLeast(0f) / 2f,
        )
    }

    private fun drawCropOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect, framePaint)
    }
}
