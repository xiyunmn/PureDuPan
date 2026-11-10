package com.xiyunmn.puredupan.hook.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

internal class ZoomableImageView(
    context: Context,
    private val bitmap: Bitmap,
) : View(context) {
    init {
        contentDescription = UiText.Settings.MEMBER_CARD_BACKGROUND_VIEWER_HINT
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var baseScale = 1f
    private var userScale = 1f
    private var translationX = 0f
    private var translationY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                userScale = 1f
                translationX = 0f
                translationY = 0f
                invalidate()
                return true
            }
        },
    ).apply { setIsLongpressEnabled(false) }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = userScale
                val newScale = (userScale * detector.scaleFactor).coerceIn(1f, 6f)
                if (newScale == oldScale) return true
                val factor = newScale / oldScale
                val centerX = width / 2f
                val centerY = height / 2f
                translationX = detector.focusX - centerX -
                    (detector.focusX - centerX - translationX) * factor
                translationY = detector.focusY - centerY -
                    (detector.focusY - centerY - translationY) * factor
                userScale = newScale
                constrainTranslation()
                invalidate()
                return true
            }
        },
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configureBaseScale()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        configureMatrix()
        canvas.drawBitmap(bitmap, matrix, imagePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) activePointerId = event.getPointerId(0)
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
                    translationX += x - lastX
                    translationY += y - lastY
                    constrainTranslation()
                    invalidate()
                }
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun configureBaseScale() {
        val viewWidth = width.coerceAtLeast(1).toFloat()
        val viewHeight = height.coerceAtLeast(1).toFloat()
        baseScale = min(
            viewWidth / bitmap.width.toFloat().coerceAtLeast(1f),
            viewHeight / bitmap.height.toFloat().coerceAtLeast(1f),
        )
        constrainTranslation()
        invalidate()
    }

    private fun configureMatrix() {
        val sourceWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val sourceHeight = bitmap.height.toFloat().coerceAtLeast(1f)
        val drawScale = baseScale * userScale
        matrix.reset()
        matrix.postTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
        matrix.postScale(drawScale, drawScale)
        matrix.postTranslate(width / 2f + translationX, height / 2f + translationY)
    }

    private fun constrainTranslation() {
        val drawWidth = bitmap.width * baseScale * userScale
        val drawHeight = bitmap.height * baseScale * userScale
        // 初始比例也允许拖动；保留图像中心附近的可见区域，双击回到居中。
        val maxX = max((drawWidth - width) / 2f, width * 0.35f)
        val maxY = max((drawHeight - height) / 2f, height * 0.35f)
        translationX = translationX.coerceIn(-maxX, maxX)
        translationY = translationY.coerceIn(-maxY, maxY)
    }
}
