package com.eventphotobooth.app.render

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.eventphotobooth.app.data.LayoutId
import com.eventphotobooth.app.data.PhotoLayoutSpecs
import com.eventphotobooth.app.data.StickerArtworkId
import com.eventphotobooth.app.data.StickerPlacement

private val stickerArtworkCache = mutableMapOf<StickerArtworkId, Bitmap?>()

/**
 * Single source of truth for sticker drawing and its visible guest-safe zone.
 * Every destination receives the same receipt-space coordinates: live editor,
 * saved PNG, GIF source PNG, and the XP-80T printer raster.
 */
fun drawReceiptStickers(
    canvas: Canvas,
    stickers: List<StickerPlacement>,
    layoutId: LayoutId,
    receiptWidth: Int,
    receiptHeight: Int,
    contentResolver: ContentResolver? = null,
) {
    val spec = PhotoLayoutSpecs.forLayout(layoutId)
    val xScale = receiptWidth / 576f
    val yScale = receiptHeight / spec.defaultCanvasHeight
    val glyphScale = minOf(xScale, yScale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 30, 30)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val artworkCache = mutableMapOf<StickerArtworkId, Bitmap?>()
    stickers.forEach { placement ->
        val sticker = spec.stickerSafeArea.clamp(placement)
        val artwork = sticker.artwork?.let { id ->
            artworkCache.getOrPut(id) { contentResolver?.let { loadStickerArtwork(it, id) } }
        }
        if (artwork != null) {
            val longestSide = 108f * sticker.scale * glyphScale
            val centerX = sticker.x * xScale
            val centerY = sticker.y * yScale
            val aspectRatio = artwork.width.toFloat() / artwork.height.coerceAtLeast(1)
            val artworkWidth = if (aspectRatio >= 1f) longestSide else longestSide * aspectRatio
            val artworkHeight = if (aspectRatio >= 1f) longestSide / aspectRatio else longestSide
            canvas.drawBitmap(
                artwork,
                null,
                RectF(centerX - artworkWidth / 2f, centerY - artworkHeight / 2f, centerX + artworkWidth / 2f, centerY + artworkHeight / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            paint.textSize = 58f * sticker.scale * glyphScale
            canvas.drawText(sticker.sticker.glyph, sticker.x * xScale, sticker.y * yScale, paint)
        }
    }
}

/** Decodes a single tile from one of the supplied sticker sheets. */
fun loadStickerArtwork(contentResolver: ContentResolver, artwork: StickerArtworkId): Bitmap? = synchronized(stickerArtworkCache) {
    stickerArtworkCache[artwork] ?: decodeStickerArtwork(contentResolver, artwork).also { decoded ->
        stickerArtworkCache[artwork] = decoded
    }
}

private fun decodeStickerArtwork(contentResolver: ContentResolver, artwork: StickerArtworkId): Bitmap? = runCatching {
    val sheet = artwork.sheet
    val uri = Uri.parse("android.resource://com.eventphotobooth.app/drawable/${sheet.drawableName}")
    val source = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
    try {
        val left = (source.width * (sheet.left + artwork.column * (sheet.cellWidth + sheet.horizontalGap))).toInt()
        val top = (source.height * (sheet.top + artwork.row * (sheet.cellHeight + sheet.verticalGap))).toInt()
        val width = (source.width * sheet.cellWidth).toInt().coerceAtLeast(1)
        val height = (source.height * sheet.cellHeight).toInt().coerceAtLeast(1)
        val boundedLeft = left.coerceIn(0, source.width - 1)
        val boundedTop = top.coerceIn(0, source.height - 1)
        // The source is the supplied border-free PNG. Its cells include
        // transparent breathing room, so preserve the entire selection tile
        // and trim only transparent pixels after cleaning tiny PNG speckles.
        // Read beyond the tile boundary. Some supplied decals deliberately
        // break the grid cell (the Expressions set, the film strip, and the
        // ribbon all do), so a strict cell crop visibly cuts them.
        val bleedX = (width * 0.16f).toInt().coerceAtLeast(8)
        val bleedY = (height * 0.16f).toInt().coerceAtLeast(8)
        val expandedLeft = (boundedLeft - bleedX).coerceAtLeast(0)
        val expandedTop = (boundedTop - bleedY).coerceAtLeast(0)
        val expandedRight = (boundedLeft + width.coerceAtMost(source.width - boundedLeft) + bleedX).coerceAtMost(source.width)
        val expandedBottom = (boundedTop + height.coerceAtMost(source.height - boundedTop) + bleedY).coerceAtMost(source.height)
        val rawCell = Bitmap.createBitmap(
            source,
            expandedLeft,
            expandedTop,
            expandedRight - expandedLeft,
            expandedBottom - expandedTop,
        )
        try {
            val selectionCell = Rect(
                boundedLeft - expandedLeft,
                boundedTop - expandedTop,
                boundedLeft - expandedLeft + width.coerceAtMost(source.width - boundedLeft),
                boundedTop - expandedTop + height.coerceAtMost(source.height - boundedTop),
            )
            val keyed = rawCell.cleanedStickerArtwork(selectionCell)
            try {
                keyed.trimTransparentPadding()
            } finally {
                keyed.recycle()
            }
        } finally {
            if (rawCell != source) rawCell.recycle()
        }
    } finally {
        source.recycle()
    }
}.getOrNull()

private fun Bitmap.cleanedStickerArtwork(selectionCell: Rect): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    pixels.indices.forEach { index ->
        val color = pixels[index]
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        if (Color.alpha(color) < 32) {
            pixels[index] = Color.TRANSPARENT
        } else {
            // Thermal print turns the original deep greens into dense black.
            // Lift the artwork toward paper white so every sticker remains
            // legible without becoming a heavy blob on the receipt.
            val lift = 0.34f
            pixels[index] = Color.argb(
                Color.alpha(color),
                (red + (255 - red) * lift).toInt(),
                (green + (255 - green) * lift).toInt(),
                (blue + (255 - blue) * lift).toInt(),
            )
        }
    }
    removeTinyComponents(pixels, width, height, selectionCell)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

/** Drops isolated export speckles while retaining every meaningful sticker detail. */
private fun removeTinyComponents(pixels: IntArray, width: Int, height: Int, selectionCell: Rect) {
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    val minimumStickerDetailPixels = 36
    pixels.indices.forEach { start ->
        if (visited[start] || Color.alpha(pixels[start]) < 32) return@forEach
        var head = 0
        var tail = 0
        var xTotal = 0L
        var yTotal = 0L
        queue[tail++] = start
        visited[start] = true
        while (head < tail) {
            val point = queue[head++]
            val x = point % width
            val y = point / width
            xTotal += x
            yTotal += y
            for (offsetY in -1..1) for (offsetX in -1..1) {
                if (offsetX == 0 && offsetY == 0) continue
                val nextX = x + offsetX
                val nextY = y + offsetY
                if (nextX !in 0 until width || nextY !in 0 until height) continue
                val next = nextY * width + nextX
                if (!visited[next] && Color.alpha(pixels[next]) >= 32) {
                    visited[next] = true
                    queue[tail++] = next
                }
            }
        }
        val centerX = xTotal.toFloat() / tail.coerceAtLeast(1)
        val centerY = yTotal.toFloat() / tail.coerceAtLeast(1)
        // The generous bleed can overlap an adjacent selection. Keep only
        // components centered inside the requested tile; this preserves all
        // parts of the chosen decal without borrowing its neighbour.
        val belongsToSelection = centerX in selectionCell.left.toFloat()..selectionCell.right.toFloat() &&
            centerY in selectionCell.top.toFloat()..selectionCell.bottom.toFloat()
        if (tail < minimumStickerDetailPixels || !belongsToSelection) {
            for (index in 0 until tail) pixels[queue[index]] = Color.TRANSPARENT
        }
    }
}

/** Removes transparent paper around the decal without changing its proportions. */
private fun Bitmap.trimTransparentPadding(): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    pixels.forEachIndexed { index, color ->
        if (Color.alpha(color) > 12) {
            val x = index % width
            val y = index / width
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
    }
    if (right < left || bottom < top) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val padding = (minOf(width, height) * 0.04f).toInt().coerceAtLeast(2)
    val trimmedLeft = (left - padding).coerceAtLeast(0)
    val trimmedTop = (top - padding).coerceAtLeast(0)
    val trimmedRight = (right + padding).coerceAtMost(width - 1)
    val trimmedBottom = (bottom + padding).coerceAtMost(height - 1)
    return Bitmap.createBitmap(this, trimmedLeft, trimmedTop, trimmedRight - trimmedLeft + 1, trimmedBottom - trimmedTop + 1)
}

fun drawStickerSafeArea(
    canvas: Canvas,
    layoutId: LayoutId,
    receiptWidth: Int,
    receiptHeight: Int,
) {
    val spec = PhotoLayoutSpecs.forLayout(layoutId)
    val xScale = receiptWidth / 576f
    val yScale = receiptHeight / spec.defaultCanvasHeight
    val safe = spec.stickerSafeArea
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 117, 91)
        style = Paint.Style.STROKE
        strokeWidth = 2f * minOf(xScale, yScale)
    }
    canvas.drawRect(
        RectF(safe.left * xScale, safe.top * yScale, safe.right * xScale, safe.bottom * yScale),
        paint,
    )
}
