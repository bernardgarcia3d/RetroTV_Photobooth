package com.eventphotobooth.app.digital

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class LocalGifRenderer {
    fun renderAnimatedGifStrip(
        sourcePhotos: List<File>,
        finalStripFile: File,
        brand: BrandSnapshot,
        outputFile: File,
        guestPhotoBrightness: Int = 50,
    ): File {
        require(sourcePhotos.isNotEmpty()) { "At least one source photo is required for GIF output." }
        val finalStrip = BitmapFactory.decodeFile(finalStripFile.absolutePath)
            ?: error("The final print strip could not be opened for GIF output.")

        val targetWidth = 360
        val targetHeight = (finalStrip.height * (targetWidth.toFloat() / finalStrip.width)).toInt()
            .coerceIn(520, 920)
        val frames = buildList {
            sourcePhotos.forEachIndexed { index, photo ->
                BitmapFactory.decodeFile(photo.absolutePath)?.let { source ->
                    add(renderSourceFrame(source, brand, targetWidth, targetHeight, index + 1, sourcePhotos.size, guestPhotoBrightness))
                    source.recycle()
                }
            }
            add(finalStrip.scaleToWidth(targetWidth, targetHeight))
        }
        finalStrip.recycle()

        FileOutputStream(outputFile).use { stream ->
            GifWriter.write(
                width = targetWidth,
                height = targetHeight,
                frames = frames,
                output = stream,
            )
        }
        frames.forEach(Bitmap::recycle)
        return outputFile
    }

    private fun renderSourceFrame(
        source: Bitmap,
        brand: BrandSnapshot,
        width: Int,
        height: Int,
        index: Int,
        total: Int,
        guestPhotoBrightness: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand.primaryColor.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), 10f, accentPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 25, 27)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        textPaint.textSize = 24f
        canvas.drawText(brand.header.uppercase(), width / 2f, 46f, textPaint)
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        textPaint.textSize = 14f
        canvas.drawText("${brand.organizerName} · Photo $index of $total", width / 2f, 70f, textPaint)

        val imageRect = Rect(18, 92, width - 18, height - 88)
        drawCenterCrop(canvas, source, imageRect, guestPhotoBrightness)

        textPaint.textSize = 13f
        canvas.drawText(brand.footer, width / 2f, (height - 42).toFloat(), textPaint)
        textPaint.textSize = 11f
        canvas.drawText("KEEP THIS MOMENT", width / 2f, (height - 20).toFloat(), textPaint)
        return bitmap
    }

    private fun Bitmap.scaleToWidth(targetWidth: Int, targetHeight: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        val frame = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== this) scaled.recycle()
        return frame
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, destination: Rect, guestPhotoBrightness: Int) {
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
        canvas.drawBitmap(
            source,
            sourceRect,
            RectF(destination),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
    }
}

private object GifWriter {
    fun write(width: Int, height: Int, frames: List<Bitmap>, output: FileOutputStream) {
        require(frames.isNotEmpty()) { "At least one GIF frame is required." }
        output.writeAscii("GIF89a")
        output.writeShort(width)
        output.writeShort(height)
        output.write(0xF7)
        output.write(0x00)
        output.write(0x00)
        repeat(256) { tone ->
            output.write(tone)
            output.write(tone)
            output.write(tone)
        }
        writeLoopExtension(output)
        frames.forEachIndexed { index, bitmap ->
            writeFrame(output, bitmap, delayCentiseconds = if (index == frames.lastIndex) 140 else 65)
        }
        output.write(0x3B)
    }

    private fun writeLoopExtension(output: FileOutputStream) {
        output.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        output.writeAscii("NETSCAPE2.0")
        output.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    private fun writeFrame(output: FileOutputStream, bitmap: Bitmap, delayCentiseconds: Int) {
        output.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
        output.writeShort(delayCentiseconds)
        output.write(byteArrayOf(0x00, 0x00))
        output.write(0x2C)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(bitmap.width)
        output.writeShort(bitmap.height)
        output.write(0x00)
        output.write(8)
        output.writeSubBlocks(LzwEncoder.encode(bitmap.toGrayIndices()))
    }

    private fun Bitmap.toGrayIndices(): ByteArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return ByteArray(pixels.size) { index ->
            val pixel = pixels[index]
            val alpha = Color.alpha(pixel)
            val red = blendOnWhite(Color.red(pixel), alpha)
            val green = blendOnWhite(Color.green(pixel), alpha)
            val blue = blendOnWhite(Color.blue(pixel), alpha)
            (red * 0.299f + green * 0.587f + blue * 0.114f).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun blendOnWhite(value: Int, alpha: Int): Int =
        ((value * alpha + 255 * (255 - alpha)) / 255).coerceIn(0, 255)
}

private object LzwEncoder {
    fun encode(indices: ByteArray): ByteArray {
        require(indices.isNotEmpty()) { "GIF frame must contain pixels." }
        val clearCode = 256
        val endCode = 257
        val output = BitWriter()
        // A clear code before every literal keeps the code width at 9 bits and
        // avoids decoder differences around dictionary/code-width boundaries.
        // The resulting GIF is larger, but remains broadly compatible with
        // Android, browsers, Cloudflare previews, and mobile downloaders.
        output.write(clearCode, 9)
        indices.forEach { pixel ->
            output.write(clearCode, 9)
            output.write(pixel.toInt() and 0xFF, 9)
        }
        output.write(endCode, 9)
        return output.toByteArray()
    }
}

private class BitWriter {
    private val bytes = ByteArrayOutputStream()
    private var current = 0
    private var bitCount = 0

    fun write(code: Int, size: Int) {
        current = current or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            bytes.write(current and 0xFF)
            current = current ushr 8
            bitCount -= 8
        }
    }

    fun toByteArray(): ByteArray {
        if (bitCount > 0) bytes.write(current and 0xFF)
        return bytes.toByteArray()
    }
}

private fun FileOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun FileOutputStream.writeShort(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}

private fun FileOutputStream.writeSubBlocks(data: ByteArray) {
    var offset = 0
    while (offset < data.size) {
        val length = minOf(255, data.size - offset)
        write(length)
        write(data, offset, length)
        offset += length
    }
    write(0)
}
