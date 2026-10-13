package com.eventphotobooth.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.eventphotobooth.app.data.BorderEdgeMode
import com.eventphotobooth.app.data.BuiltInBorderStyle
import com.eventphotobooth.app.data.ReceiptBorderSource
import com.eventphotobooth.app.data.ResponsiveBorderTemplate
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a border after the receipt content so uploaded artwork remains a frame. */
class ResponsiveBorderRenderer {
    fun draw(canvas: Canvas, template: ResponsiveBorderTemplate, width: Int, height: Int) {
        if (!template.enabled || !template.isRenderable()) return
        // Only an explicitly style-based bundled entry may use the built-in
        // renderer. Image-backed templates must never fall back to Classic if
        // their hydrated asset is missing; an empty border is safer than
        // silently drawing a different design over the user's selection.
        if (template.source == ReceiptBorderSource.BUNDLED && template.bundledAssetPath.isNullOrBlank()) {
            template.builtInStyle?.let { drawBundled(canvas, it, width, height) }
            return
        }
        val path = template.normalizedAssetPath ?: return
        val source = BitmapFactory.decodeFile(File(path).absolutePath) ?: return
        try {
            drawNineSlice(canvas, source, template, width, height)
        } finally {
            source.recycle()
        }
    }

    private fun drawNineSlice(
        canvas: Canvas,
        source: Bitmap,
        template: ResponsiveBorderTemplate,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        val guides = template.guides.sanitized()
        val sourceLeft = (source.width * guides.left).roundToInt().coerceIn(1, source.width - 2)
        val sourceRight = (source.width * guides.right).roundToInt().coerceIn(sourceLeft + 1, source.width - 1)
        val sourceTop = (source.height * guides.top).roundToInt().coerceIn(1, source.height - 2)
        val sourceBottom = (source.height * guides.bottom).roundToInt().coerceIn(sourceTop + 1, source.height - 1)

        // Uniformly scale corner artwork, then let only the edge segments flex.
        val cornerScale = min(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
        val targetLeft = max(1, (sourceLeft * cornerScale).roundToInt()).coerceAtMost(targetWidth / 2)
        val targetRight = (targetWidth - max(1, ((source.width - sourceRight) * cornerScale).roundToInt())).coerceAtLeast(targetLeft + 1)
        val targetTop = max(1, (sourceTop * cornerScale).roundToInt()).coerceAtMost(targetHeight / 2)
        val targetBottom = (targetHeight - max(1, ((source.height - sourceBottom) * cornerScale).roundToInt())).coerceAtLeast(targetTop + 1)

        val x = intArrayOf(0, sourceLeft, sourceRight, source.width)
        val y = intArrayOf(0, sourceTop, sourceBottom, source.height)
        val dx = intArrayOf(0, targetLeft, targetRight, targetWidth)
        val dy = intArrayOf(0, targetTop, targetBottom, targetHeight)

        // Fixed corners.
        drawSlice(canvas, source, x[0], y[0], x[1], y[1], dx[0], dy[0], dx[1], dy[1])
        drawSlice(canvas, source, x[2], y[0], x[3], y[1], dx[2], dy[0], dx[3], dy[1])
        drawSlice(canvas, source, x[0], y[2], x[1], y[3], dx[0], dy[2], dx[1], dy[3])
        drawSlice(canvas, source, x[2], y[2], x[3], y[3], dx[2], dy[2], dx[3], dy[3])

        if (template.edgeMode == BorderEdgeMode.REPEAT) {
            drawHorizontalRepeat(canvas, source, Rect(x[1], y[0], x[2], y[1]), Rect(dx[1], dy[0], dx[2], dy[1]))
            drawHorizontalRepeat(canvas, source, Rect(x[1], y[2], x[2], y[3]), Rect(dx[1], dy[2], dx[2], dy[3]))
            drawVerticalRepeat(canvas, source, Rect(x[0], y[1], x[1], y[2]), Rect(dx[0], dy[1], dx[1], dy[2]))
            drawVerticalRepeat(canvas, source, Rect(x[2], y[1], x[3], y[2]), Rect(dx[2], dy[1], dx[3], dy[2]))
        } else {
            drawSlice(canvas, source, x[1], y[0], x[2], y[1], dx[1], dy[0], dx[2], dy[1])
            drawSlice(canvas, source, x[1], y[2], x[2], y[3], dx[1], dy[2], dx[2], dy[3])
            drawSlice(canvas, source, x[0], y[1], x[1], y[2], dx[0], dy[1], dx[1], dy[2])
            drawSlice(canvas, source, x[2], y[1], x[3], y[2], dx[2], dy[1], dx[3], dy[2])
        }
        // The center is intentionally not drawn: it is the transparent photo/text area.
    }

    private fun drawSlice(canvas: Canvas, source: Bitmap, left: Int, top: Int, right: Int, bottom: Int, targetLeft: Int, targetTop: Int, targetRight: Int, targetBottom: Int) {
        if (right <= left || bottom <= top || targetRight <= targetLeft || targetBottom <= targetTop) return
        canvas.drawBitmap(source, Rect(left, top, right, bottom), Rect(targetLeft, targetTop, targetRight, targetBottom), bitmapPaint)
    }

    private fun drawHorizontalRepeat(canvas: Canvas, source: Bitmap, sourceRect: Rect, targetRect: Rect) {
        if (sourceRect.isEmpty || targetRect.isEmpty) return
        val scale = targetRect.height().toFloat() / sourceRect.height()
        val tileWidth = max(1f, sourceRect.width() * scale)
        canvas.save()
        canvas.clipRect(targetRect)
        var left = targetRect.left.toFloat()
        while (left < targetRect.right) {
            canvas.drawBitmap(source, sourceRect, RectF(left, targetRect.top.toFloat(), left + tileWidth, targetRect.bottom.toFloat()), bitmapPaint)
            left += tileWidth
        }
        canvas.restore()
    }

    private fun drawVerticalRepeat(canvas: Canvas, source: Bitmap, sourceRect: Rect, targetRect: Rect) {
        if (sourceRect.isEmpty || targetRect.isEmpty) return
        val scale = targetRect.width().toFloat() / sourceRect.width()
        val tileHeight = max(1f, sourceRect.height() * scale)
        canvas.save()
        canvas.clipRect(targetRect)
        var top = targetRect.top.toFloat()
        while (top < targetRect.bottom) {
            canvas.drawBitmap(source, sourceRect, RectF(targetRect.left.toFloat(), top, targetRect.right.toFloat(), top + tileHeight), bitmapPaint)
            top += tileHeight
        }
        canvas.restore()
    }

    private fun drawBundled(canvas: Canvas, style: BuiltInBorderStyle, width: Int, height: Int) {
        if (style == BuiltInBorderStyle.NONE) return
        val outer = RectF(10f, 10f, width - 10f, height - 10f)
        when (style) {
            BuiltInBorderStyle.NONE -> return
            BuiltInBorderStyle.CLASSIC -> {
                strokePaint.color = Color.rgb(72, 90, 73)
                strokePaint.strokeWidth = 7f
                canvas.drawRoundRect(outer, 22f, 22f, strokePaint)
                strokePaint.strokeWidth = 2f
                canvas.drawRoundRect(RectF(20f, 20f, width - 20f, height - 20f), 16f, 16f, strokePaint)
            }
            BuiltInBorderStyle.HEARTS -> {
                strokePaint.color = Color.rgb(180, 45, 65)
                strokePaint.strokeWidth = 6f
                canvas.drawRoundRect(outer, 26f, 26f, strokePaint)
                symbolPaint.color = Color.rgb(180, 45, 65)
                symbolPaint.textSize = 34f
                symbolPaint.textAlign = Paint.Align.CENTER
                (72 until width - 50 step 72).forEach { x ->
                    canvas.drawText("♥", x.toFloat(), 42f, symbolPaint)
                    canvas.drawText("♥", x.toFloat(), height - 20f, symbolPaint)
                }
            }
            BuiltInBorderStyle.FILM -> {
                fillPaint.color = Color.rgb(26, 26, 29)
                canvas.drawRect(0f, 0f, width.toFloat(), 28f, fillPaint)
                canvas.drawRect(0f, height - 28f, width.toFloat(), height.toFloat(), fillPaint)
                fillPaint.color = Color.WHITE
                (18 until width - 18 step 46).forEach { x ->
                    canvas.drawRoundRect(RectF(x.toFloat(), 7f, (x + 24).toFloat(), 20f), 3f, 3f, fillPaint)
                    canvas.drawRoundRect(RectF(x.toFloat(), height - 21f, (x + 24).toFloat(), height - 8f), 3f, 3f, fillPaint)
                }
                strokePaint.color = Color.rgb(26, 26, 29)
                strokePaint.strokeWidth = 5f
                canvas.drawRect(8f, 8f, width - 8f, height - 8f, strokePaint)
            }
            BuiltInBorderStyle.MIDNIGHT -> {
                strokePaint.color = Color.rgb(61, 43, 93)
                strokePaint.strokeWidth = 10f
                canvas.drawRoundRect(outer, 32f, 32f, strokePaint)
                strokePaint.color = Color.rgb(187, 157, 230)
                strokePaint.strokeWidth = 2f
                canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 20f, 20f, strokePaint)
            }
        }
    }

    private companion object {
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
    }
}
