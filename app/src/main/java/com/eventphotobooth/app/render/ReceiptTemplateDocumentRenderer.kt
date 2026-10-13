package com.eventphotobooth.app.render

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.eventphotobooth.app.data.BrandSnapshot
import com.eventphotobooth.app.data.LayoutDesign
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.ReceiptElement
import com.eventphotobooth.app.data.ReceiptElementType
import com.eventphotobooth.app.data.ReceiptTemplateDocument
import com.eventphotobooth.app.data.ReceiptTextAlignment
import com.eventphotobooth.app.data.StickerPlacement
import com.eventphotobooth.app.data.photoRegions
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Renders a complete operator-authored document without an implicit Classic layer. */
class ReceiptTemplateDocumentRenderer {
    fun render(
        document: ReceiptTemplateDocument,
        sourcePhotos: List<File>,
        brand: BrandSnapshot,
        layoutId: LayoutId,
        stickers: List<StickerPlacement>,
        outputFile: File,
        receiptNumber: String,
        receiptTimestampMs: Long,
        totalLabel: String,
        contentResolver: ContentResolver,
        layoutDesign: LayoutDesign,
        guestPhotoBrightness: Int,
    ): File {
        require(sourcePhotos.isNotEmpty()) { "At least one source photo is required" }
        val width = document.designWidth.roundToInt().coerceAtLeast(320)
        // Preserve the authored artwork height. The supplied 1-photo artwork
        // is 576x760; a legacy 800px floor added visible blank paper below it.
        val height = document.designHeight.roundToInt().coerceAtLeast(320)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(document.backgroundColor.toInt())
        val date = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(receiptTimestampMs))
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(receiptTimestampMs))

        var photosDrawn = false
        // Older duplicated templates saved their full-page border artwork as
        // the newest layer. Treat a canvas-sized IMAGE as artwork regardless
        // of its persisted z-index so those templates migrate safely at
        // render time instead of hiding the receipt underneath a white JPG.
        val artwork = document.elements
            .filter { it.type == ReceiptElementType.IMAGE && isCanvasArtwork(it, document) }
            .sortedBy { it.zIndex }
        val content = document.elements
            .filterNot { it.type == ReceiptElementType.IMAGE && isCanvasArtwork(it, document) }
            .sortedBy { it.zIndex }
        (artwork + content).forEach { element ->
            if (!element.visible) return@forEach
            val target = RectF(element.x, element.y, element.x + element.width, element.y + element.height)
            when (element.type) {
                ReceiptElementType.TEXT -> drawText(canvas, element, resolveText(element.text, brand, receiptNumber, date, time, totalLabel))
                ReceiptElementType.LOGO,
                ReceiptElementType.IMAGE -> loadBitmap(element.assetUri, contentResolver)?.let { source ->
                    if (element.type == ReceiptElementType.IMAGE) {
                        // Border artwork is authored at the complete receipt
                        // size. Fill its target exactly so the on-screen
                        // preview and 80 mm print share the same geometry.
                        canvas.drawBitmap(source, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
                    } else {
                        drawContained(canvas, source, target)
                    }
                    source.recycle()
                }
                ReceiptElementType.LINE -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = element.color.toInt()
                        strokeWidth = element.height.coerceIn(1f, 20f)
                    }
                    canvas.drawLine(target.left, target.centerY(), target.right, target.centerY(), paint)
                }
                ReceiptElementType.SPACER -> Unit
                ReceiptElementType.PHOTO_SLOT -> if (!photosDrawn) {
                    photosDrawn = true
                    document.photoRegions(layoutId).forEachIndexed { index, region ->
                        sourcePhotos.getOrNull(index)?.let { photo ->
                            BitmapFactory.decodeFile(photo.absolutePath)?.let { source ->
                                val regionTarget = RectF(region.x, region.y, region.x + region.width, region.y + region.height)
                                drawCenterCrop(canvas, source, regionTarget, guestPhotoBrightness, layoutDesign.frameColor)
                                source.recycle()
                            }
                        }
                    }
                }
                ReceiptElementType.BARCODE -> drawBarcode(canvas, "PB$receiptNumber", target)
            }
        }
        drawReceiptStickers(canvas, stickers, layoutId, width, height, contentResolver)

        FileOutputStream(outputFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Unable to save the template receipt" }
        }
        bitmap.recycle()
        return outputFile
    }

    private fun resolveText(value: String, brand: BrandSnapshot, receiptNumber: String, date: String, time: String, totalLabel: String): String = value
        .replace("{venue}", brand.organizerName)
        .replace("{header}", brand.header)
        .replace("{footer}", brand.footer)
        .replace("{receipt_number}", receiptNumber)
        .replace("{date}", date)
        .replace("{time}", time)
        .replace("{total}", totalLabel)
        .replace("{barcode}", "PB$receiptNumber")

    private fun drawText(canvas: Canvas, element: ReceiptElement, value: String) {
        if (value.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.color.toInt()
            textSize = element.fontSize.coerceIn(8f, 160f)
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                if (element.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
            textAlign = when (element.alignment) {
                ReceiptTextAlignment.LEFT -> Paint.Align.LEFT
                ReceiptTextAlignment.CENTER -> Paint.Align.CENTER
                ReceiptTextAlignment.RIGHT -> Paint.Align.RIGHT
            }
        }
        val maxWidth = element.width.coerceAtLeast(1f)
        val fittedSize = if (paint.measureText(value) > maxWidth) {
            paint.textSize * (maxWidth / paint.measureText(value))
        } else paint.textSize
        paint.textSize = fittedSize.coerceAtLeast(8f)
        val x = when (element.alignment) {
            ReceiptTextAlignment.LEFT -> element.x
            ReceiptTextAlignment.CENTER -> element.x + element.width / 2f
            ReceiptTextAlignment.RIGHT -> element.x + element.width
        }
        val baseline = element.y + ((element.height - (paint.descent() + paint.ascent())) / 2f)
        canvas.drawText(value, x, baseline, paint)
    }

    private fun isCanvasArtwork(element: ReceiptElement, document: ReceiptTemplateDocument): Boolean =
        element.x <= 1f && element.y <= 1f &&
            element.width >= document.designWidth * 0.9f &&
            element.height >= document.designHeight * 0.9f

    private fun loadBitmap(value: String, resolver: ContentResolver): Bitmap? = runCatching {
        value.takeIf(String::isNotBlank)?.let { raw ->
            // App-private border copies are absolute file paths, while
            // operator logos may be content:// URIs. Do not pass a raw path
            // through ContentResolver first: that throws on some Android
            // versions and prevents the file fallback from running.
            val uri = Uri.parse(raw)
            val fromUri = uri.scheme?.takeIf { it.isNotBlank() }?.let {
                resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
            fromUri ?: BitmapFactory.decodeFile(raw)
        }
    }.getOrNull()

    private fun drawContained(canvas: Canvas, source: Bitmap, target: RectF) {
        val scale = minOf(target.width() / source.width, target.height() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        canvas.drawBitmap(source, null, RectF(target.centerX() - width / 2f, target.centerY() - height / 2f, target.centerX() + width / 2f, target.centerY() + height / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, target: RectF, brightness: Int, frameColor: Long) {
        val sourceRect = centerCropSourceRect(source, target.width() / target.height())
        val offset = (brightness.coerceIn(0, 100) - 50) * 2f
        val matrix = ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, offset, 0f, 1f, 0f, 0f, offset, 0f, 0f, 1f, 0f, offset, 0f, 0f, 0f, 1f, 0f))
        canvas.drawBitmap(source, sourceRect, target, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        canvas.drawRect(target, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = frameColor.toInt() })
    }

    private fun drawBarcode(canvas: Canvas, value: String, target: RectF) {
        val matrix = Code128Writer().encode(value, BarcodeFormat.CODE_128, target.width().roundToInt(), 1)
        val paint = Paint().apply { color = Color.BLACK }
        for (x in 0 until matrix.width) if (matrix.get(x, 0)) {
            val left = target.left + x
            canvas.drawRect(left, target.top, left + 1f, target.bottom, paint)
        }
    }

}
