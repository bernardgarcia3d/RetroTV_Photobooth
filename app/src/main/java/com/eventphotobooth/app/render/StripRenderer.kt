package com.eventphotobooth.app.render

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.eventphotobooth.app.data.BrandSnapshot
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.LayoutDesign
import com.eventphotobooth.app.data.PhotoLayoutSpecs
import com.eventphotobooth.app.data.ReceiptTemplate
import com.eventphotobooth.app.data.ResponsiveBorderTemplate
import com.eventphotobooth.app.data.asReceiptDocument
import com.eventphotobooth.app.data.StickerPlacement
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
        receiptTemplate: ReceiptTemplate,
        receiptNumber: String,
        receiptTimestampMs: Long,
        totalLabel: String,
        contentResolver: ContentResolver,
        layoutDesign: LayoutDesign = LayoutDesign(layoutId.displayName, brand.primaryColor),
        guestPhotoBrightness: Int = 50,
        borderTemplate: ResponsiveBorderTemplate? = null,
    ): File {
        require(sourcePhotos.isNotEmpty()) { "At least one source photo is required" }

        // Resolve the bundled artwork at the final render boundary as well as
        // in Compose state. This prevents a stale catalog object (or a render
        // launched during recomposition) from falling back to the legacy tall
        // receipt document.
        val resolvedBorderTemplate = borderTemplate?.asReceiptDocument(receiptTemplate)

        // A selected template is always a complete, fixed-size receipt. Do
        // not fall through to the old adaptive receipt renderer: it was able
        // to stretch the artwork across a taller canvas than the uploaded
        // design, causing a blank tail in both preview and print.
        resolvedBorderTemplate?.let { template ->
            val document = requireNotNull(template.document) {
                "The selected template could not be prepared for ${layoutId.displayName}."
            }
            return ReceiptTemplateDocumentRenderer().render(
                document = document,
                sourcePhotos = sourcePhotos,
                brand = brand,
                layoutId = layoutId,
                stickers = stickers,
                outputFile = outputFile,
                receiptNumber = receiptNumber,
                receiptTimestampMs = receiptTimestampMs,
                totalLabel = totalLabel,
                contentResolver = contentResolver,
                layoutDesign = layoutDesign,
                guestPhotoBrightness = guestPhotoBrightness,
            )
        }

        val width = 576
        // Fixed header slots prevent a large/adaptive logo from colliding
        // with the custom headline, memory line, or date on thermal paper.
        val headerHeight = 360
        // Keep an open writing area before the note label.
        val footerHeight = 500
        val gap = 12
        val isGrid = layoutId == LayoutId.FOUR_GRID || layoutId == LayoutId.SIX_GRID
        val rows = if (isGrid) (sourcePhotos.size + 1) / 2 else sourcePhotos.size
        val safeArea = borderTemplate?.safeArea()
        val contentLeft = maxOf(18f, width * (safeArea?.left ?: 0f))
        val contentRight = minOf(width - 18f, width * (safeArea?.right ?: 1f))
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(220f)
        val layoutSpec = PhotoLayoutSpecs.forLayout(layoutId)
        val gridPhotoWidth = if (isGrid) ((contentWidth - gap) / 2f) else contentWidth
        val photoHeight = (gridPhotoWidth / layoutSpec.photoAspectRatio).roundToInt().coerceAtLeast(1)
        val photoSectionHeight = rows * photoHeight + (rows - 1) * gap
        val baseHeight = headerHeight + footerHeight + photoSectionHeight
        // Preserve the existing receipt typography exactly when no border is
        // selected; only responsive templates constrain text to their safe area.
        val textMaxWidth = if (borderTemplate == null) 520f else contentWidth
        // The top/bottom slice guide becomes a content-safe margin for dynamic
        // receipt text. This keeps custom text inside a border across layouts.
        val topInset = ((safeArea?.top ?: 0f) * baseHeight).toInt().coerceAtMost(baseHeight / 4)
        val bottomInset = ((1f - (safeArea?.bottom ?: 1f)) * baseHeight).toInt().coerceAtMost(baseHeight / 4)
        val height = topInset + baseHeight + bottomInset
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 25, 27)
            textAlign = Paint.Align.CENTER
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 2f }
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        textPaint.textSize = 26f
        canvas.drawText(receiptTemplate.title.uppercase(), contentLeft, topInset + 34f, textPaint.apply { textAlign = Paint.Align.LEFT })
        textPaint.textSize = 18f
        canvas.drawText("NO: $receiptNumber", contentRight, topInset + 32f, textPaint.apply { textAlign = Paint.Align.RIGHT })
        loadLogo(receiptTemplate.logoUri, contentResolver)?.let { logo ->
            // Give the uploaded logo a large 1000%-style presentation area,
            // constrained to the 80mm paper so it never clips the receipt.
            drawContained(canvas, logo, RectF(contentLeft + 20f, topInset + 38f, contentRight - 20f, topInset + 178f))
            logo.recycle()
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        // The custom headline is intentionally authored at 500% of the old
        // size; drawFittedLine scales it only when the configured text cannot
        // fit on one 80mm line.
        textPaint.textSize = 120f
        drawFittedLine(canvas, receiptTemplate.primaryText, width / 2f, topInset + 240f, textMaxWidth, textPaint)
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        textPaint.textSize = 26f
        drawFittedLine(canvas, receiptTemplate.secondaryText, width / 2f, topInset + 278f, textMaxWidth, textPaint)
        drawDivider(canvas, topInset + 296f, dividerPaint, contentLeft, contentRight)
        val date = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(receiptTimestampMs))
        textPaint.textSize = 26f
        drawFittedLine(canvas, "DATE: $date", width / 2f, topInset + 334f, textMaxWidth, textPaint)
        drawDivider(canvas, topInset + 350f, dividerPaint, contentLeft, contentRight)

        sourcePhotos.forEachIndexed { index, file ->
            val source = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Unable to decode " + file.name)
            val row = if (isGrid) index / 2 else index
            val column = if (isGrid) index % 2 else 0
            val photoWidth = if (isGrid) ((contentWidth - 18f) / 2f).toInt() else contentWidth.toInt()
            val left = if (isGrid) (contentLeft + column * (photoWidth + 18)).toInt() else contentLeft.toInt()
            val top = topInset + headerHeight + row * (photoHeight + gap)
            drawCenterCrop(
                canvas = canvas,
                source = source,
                destination = Rect(left, top, left + photoWidth, top + photoHeight),
                guestPhotoBrightness = guestPhotoBrightness,
                frameColor = layoutDesign.frameColor,
            )
            source.recycle()
        }

        drawReceiptStickers(canvas, stickers, layoutId, width, height, contentResolver)

        val footerTop = topInset + headerHeight + photoSectionHeight
        drawDivider(canvas, footerTop + 14f, dividerPaint, contentLeft, contentRight)
        textPaint.textSize = 22f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        canvas.drawText("TOTAL:", contentLeft, footerTop + 48f, textPaint.apply { textAlign = Paint.Align.LEFT })
        canvas.drawText(totalLabel, contentRight, footerTop + 48f, textPaint.apply { textAlign = Paint.Align.RIGHT })
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        textPaint.textSize = 26f
        drawFittedLine(canvas, receiptTemplate.postTotalText, width / 2f, footerTop + 88f, textMaxWidth, textPaint)
        drawDivider(canvas, footerTop + 100f, dividerPaint, contentLeft, contentRight)
        // Open writing area: intentionally blank, with no ruled lines.
        textPaint.textSize = 26f
        drawFittedLine(canvas, receiptTemplate.noteLabel.uppercase(), width / 2f, footerTop + 320f, textMaxWidth, textPaint)
        textPaint.textSize = 15f
        drawSingleLine(canvas, receiptTemplate.noteText, width / 2f, footerTop + 345f, textMaxWidth, textPaint)
        canvas.drawLine(contentLeft + 92f, footerTop + 357f, contentRight - 92f, footerTop + 357f, dividerPaint)
        val barcodeText = "PB$receiptNumber"
        drawBarcode(canvas, barcodeText, Rect((contentLeft + 30f).toInt(), footerTop + 375, (contentRight - 30f).toInt(), footerTop + 433))
        textPaint.textSize = 12f
        canvas.drawText(barcodeText, width / 2f, footerTop + 449f, textPaint)
        textPaint.textSize = 30f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        drawFittedLine(canvas, receiptTemplate.barcodeCaption, width / 2f, footerTop + 480f, textMaxWidth, textPaint)

        borderTemplate?.let { ResponsiveBorderRenderer().draw(canvas, it, width, height) }

        FileOutputStream(outputFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to save the print strip"
            }
        }
        bitmap.recycle()
        return outputFile
    }

    private fun loadLogo(value: String, contentResolver: ContentResolver): Bitmap? = runCatching {
        value.takeIf(String::isNotBlank)?.let { uri ->
            contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)?.let(::trimLogoMargins)
        }
    }.getOrNull()

    private fun trimLogoMargins(source: Bitmap): Bitmap {
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        pixels.forEachIndexed { index, color ->
            val alpha = android.graphics.Color.alpha(color)
            val visible = alpha > 20 && (
                android.graphics.Color.red(color) < 245 ||
                    android.graphics.Color.green(color) < 245 ||
                    android.graphics.Color.blue(color) < 245
                )
            if (visible) {
                val x = index % source.width
                val y = index / source.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return source
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    }

    private fun drawContained(canvas: Canvas, source: Bitmap, target: RectF) {
        val scale = minOf(target.width() / source.width, target.height() / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = target.centerX() - drawWidth / 2f
        val top = target.centerY() - drawHeight / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + drawWidth, top + drawHeight), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawDivider(canvas: Canvas, y: Float, paint: Paint, left: Float, right: Float) {
        canvas.drawLine(left, y, right, y, paint)
    }

    private fun drawSingleLine(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val text = value.trim()
        if (text.isBlank()) return
        val visible = android.text.TextUtils.ellipsize(
            text,
            android.text.TextPaint(paint),
            maxWidth,
            android.text.TextUtils.TruncateAt.END,
        ).toString()
        canvas.drawText(visible, x, y, paint)
    }

    private fun drawFittedLine(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val text = value.trim()
        if (text.isBlank()) return
        val originalSize = paint.textSize
        if (paint.measureText(text) > maxWidth) {
            paint.textSize = originalSize * (maxWidth / paint.measureText(text))
        }
        canvas.drawText(text, x, y, paint)
        paint.textSize = originalSize
    }

    private fun drawBarcode(canvas: Canvas, value: String, target: Rect) {
        val matrix = Code128Writer().encode(value, BarcodeFormat.CODE_128, target.width(), 1)
        val paint = Paint().apply { color = Color.BLACK }
        for (x in 0 until matrix.width) {
            if (matrix.get(x, 0)) {
                canvas.drawRect((target.left + x).toFloat(), target.top.toFloat(), (target.left + x + 1).toFloat(), target.bottom.toFloat(), paint)
            }
        }
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        source: Bitmap,
        destination: Rect,
        guestPhotoBrightness: Int,
        frameColor: Long,
    ) {
        val sourceRect = centerCropSourceRect(source, destination.width().toFloat() / destination.height().toFloat())
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
