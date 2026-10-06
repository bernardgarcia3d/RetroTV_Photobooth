package com.eventphotobooth.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.eventphotobooth.app.data.BrandSnapshot
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.LayoutDesign
import com.eventphotobooth.app.data.StickerPlacement
import java.io.File
import java.io.FileOutputStream

class StripRenderer {
    /**
     * Creates a high-contrast, printer-ready bitmap in app-private storage.
     * GIF generation belongs to Release 2 and is intentionally not coupled to
     * the print path.
     */
    fun renderPrintStrip(
        sourcePhotos: List<File>,
        brand: BrandSnapshot,
        layoutId: LayoutId,
        stickers: List<StickerPlacement>,
        outputFile: File,
        layoutDesign: LayoutDesign = LayoutDesign(layoutId.displayName, brand.primaryColor),
        guestPhotoBrightness: Int = 50,
    ): File {
        require(sourcePhotos.isNotEmpty()) { "At least one source photo is required" }

        val width = 576
        val headerHeight = 112
        val footerHeight = 92
        val photoHeight = if (layoutId == LayoutId.ONE_PHOTO) 594 else 312
        val gap = 12
        val isGrid = layoutId == LayoutId.FOUR_GRID || layoutId == LayoutId.SIX_GRID
        val rows = if (isGrid) (sourcePhotos.size + 1) / 2 else sourcePhotos.size
        val height = headerHeight + footerHeight + rows * photoHeight + (rows - 1) * gap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(layoutDesign.backgroundColor.toInt())

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 25, 27)
            textAlign = Paint.Align.CENTER
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layoutDesign.accentColor.toInt()
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, accentPaint)

        textPaint.textSize = 31f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 1)
        canvas.drawText(brand.header.uppercase(), width / 2f, 53f, textPaint)
        textPaint.textSize = 18f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 0)
        canvas.drawText(brand.organizerName, width / 2f, 82f, textPaint)

        sourcePhotos.forEachIndexed { index, file ->
            val source = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Unable to decode " + file.name)
            val row = if (isGrid) index / 2 else index
            val column = if (isGrid) index % 2 else 0
            val photoWidth = if (isGrid) (width - 54) / 2 else width - 36
            val left = if (isGrid) 18 + column * (photoWidth + 18) else 18
            val top = headerHeight + row * (photoHeight + gap)
            drawCenterCrop(
                canvas = canvas,
                source = source,
                destination = Rect(left, top, left + photoWidth, top + photoHeight),
                guestPhotoBrightness = guestPhotoBrightness,
                frameColor = layoutDesign.frameColor,
            )
            source.recycle()
        }

        drawStickers(canvas, stickers, width, height)

        textPaint.textSize = 17f
        canvas.drawText(brand.footer, width / 2f, (height - 42).toFloat(), textPaint)
        textPaint.textSize = 14f
        canvas.drawText("KEEP THIS MOMENT", width / 2f, (height - 18).toFloat(), textPaint)

        FileOutputStream(outputFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to save the print strip"
            }
        }
        bitmap.recycle()
        return outputFile
    }

    private fun drawStickers(
        canvas: Canvas,
        stickers: List<StickerPlacement>,
        width: Int,
        height: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        stickers.forEach { sticker ->
            val x = sticker.x.coerceIn(0.08f, 0.92f) * width
            val y = sticker.y.coerceIn(0.06f, 0.94f) * height
            paint.textSize = 58f * sticker.scale.coerceIn(0.5f, 2.5f)
            canvas.drawText(sticker.sticker.glyph, x, y, paint)
        }
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        source: Bitmap,
        destination: Rect,
        guestPhotoBrightness: Int,
        frameColor: Long,
    ) {
        val sourceRatio = source.width.toFloat() / source.height
        val destinationRatio = destination.width().toFloat() / destination.height()
        val sourceRect = if (sourceRatio > destinationRatio) {
            val cropWidth = (source.height * destinationRatio).toInt()
            val left = (source.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / destinationRatio).toInt()
            val top = (source.height - cropHeight) / 2
            Rect(0, top, source.width, top + cropHeight)
        }
        val brightnessOffset = (guestPhotoBrightness.coerceIn(0, 100) - 50) * 2f
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, sourceRect, RectF(destination), paint)
        canvas.drawRect(
            RectF(destination),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = frameColor.toInt()
            },
        )
    }
}
