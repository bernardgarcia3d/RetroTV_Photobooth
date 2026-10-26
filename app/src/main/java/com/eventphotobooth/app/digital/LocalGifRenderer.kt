package com.eventphotobooth.app.digital

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class LocalGifRenderer {
    /**
     * Builds one animated version of the final branded strip. Every photo
     * window plays its pre-flash clip in sync, then the final printed strip
     * holds as the last loop frame.
     */
    fun renderBrandedTemplateGif(
        clipFiles: List<File?>,
        finalStripFile: File,
        photoRegions: List<RectF>,
        outputFile: File,
    ): File {
        require(finalStripFile.isFile) { "The final print strip is not available for GIF output." }
        require(photoRegions.isNotEmpty()) { "The selected template has no photo windows for GIF output." }
        val finalStrip = BitmapFactory.decodeFile(finalStripFile.absolutePath)
            ?: error("The final print strip could not be opened for GIF output.")
        val clips = clipFiles.map { file -> file?.takeIf(File::isFile)?.let(::GifMomentSource) }
        try {
            val frames = buildList {
                repeat(MOVING_FRAME_COUNT) { index ->
                    val frame = finalStrip.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(frame)
                    clips.forEachIndexed { photoIndex, clip ->
                        val region = photoRegions.getOrNull(photoIndex) ?: return@forEachIndexed
                        // Keep the static template's photo-frame edge visible
                        // around the moving content.
                        val movingTarget = RectF(region).apply { inset(2f, 2f) }
                        clip?.frameAt((index + 1f) / (MOVING_FRAME_COUNT + 1f))?.let { movingPhoto ->
                            try {
                                canvas.drawBitmap(
                                    movingPhoto,
                                    centerCropRect(movingPhoto, movingTarget.width().toInt(), movingTarget.height().toInt()),
                                    movingTarget,
                                    Paint(Paint.FILTER_BITMAP_FLAG),
                                )
                            } finally {
                                movingPhoto.recycle()
                            }
                        }
                    }
                    // Give the moving photo areas more palette space than the
                    // mostly-white strip background. This keeps skin tones and
                    // shadows smoother within GIF's 256-colour limit.
                    add(GifFrame(frame, MOVING_FRAME_DELAY_CENTISECONDS, photoRegions))
                }
                add(GifFrame(finalStrip.copy(Bitmap.Config.ARGB_8888, false), FINAL_STILL_DELAY_CENTISECONDS))
            }
            try {
                FileOutputStream(outputFile).use { stream ->
                    GifWriter.write(finalStrip.width, finalStrip.height, frames, stream)
                }
            } finally {
                frames.forEach { it.bitmap.recycle() }
            }
        } finally {
            clips.forEach { it?.close() }
            finalStrip.recycle()
        }
        return outputFile
    }

    private fun centerCropRect(source: Bitmap, targetWidth: Int, targetHeight: Int): android.graphics.Rect {
        val targetRatio = targetWidth.toFloat() / targetHeight
        val sourceRatio = source.width.toFloat() / source.height
        return if (sourceRatio > targetRatio) {
            val cropWidth = (source.height * targetRatio).toInt().coerceAtMost(source.width)
            val left = (source.width - cropWidth) / 2
            android.graphics.Rect(left, 0, left + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / targetRatio).toInt().coerceAtMost(source.height)
            val top = (source.height - cropHeight) / 2
            android.graphics.Rect(0, top, source.width, top + cropHeight)
        }
    }
}

private data class GifFrame(
    val bitmap: Bitmap,
    val delayCentiseconds: Int,
    val prioritizedRegions: List<RectF> = emptyList(),
)

private class GifMomentSource(file: File) : AutoCloseable {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
    private val durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull()?.coerceAtLeast(1L) ?: DEFAULT_GIF_MOMENT_DURATION_MILLIS

    fun frameAt(fraction: Float): Bitmap? = runCatching {
        retriever.getFrameAtTime(
            (durationMillis * fraction.coerceIn(0f, 1f) * 1_000L).toLong(),
            MediaMetadataRetriever.OPTION_CLOSEST,
        )
    }.getOrNull()

    override fun close() = retriever.release()
}

// Eight evenly spaced frames keep the 1.2-second motion readable without
// unnecessarily inflating the cloud-delivery download.
private const val MOVING_FRAME_COUNT = 8
private const val MOVING_FRAME_DELAY_CENTISECONDS = 15
private const val FINAL_STILL_DELAY_CENTISECONDS = 100
private const val DEFAULT_GIF_MOMENT_DURATION_MILLIS = 1_500L

private object GifWriter {
    fun write(width: Int, height: Int, frames: List<GifFrame>, output: FileOutputStream) {
        require(frames.isNotEmpty()) { "At least one GIF frame is required." }
        output.writeAscii("GIF89a")
        output.writeShort(width)
        output.writeShort(height)
        // Each photo frame carries an adaptive local palette. A global fixed
        // 3-3-2 palette was the cause of the dull, low-colour GIFs.
        output.write(0x70)
        output.write(0x00)
        output.write(0x00)
        writeLoopExtension(output)
        frames.forEach { frame ->
            writeFrame(output, frame)
        }
        output.write(0x3B)
    }

    private fun writeLoopExtension(output: FileOutputStream) {
        output.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        output.writeAscii("NETSCAPE2.0")
        output.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    private fun writeFrame(output: FileOutputStream, frame: GifFrame) {
        val bitmap = frame.bitmap
        output.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
        output.writeShort(frame.delayCentiseconds)
        output.write(byteArrayOf(0x00, 0x00))
        output.write(0x2C)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(bitmap.width)
        output.writeShort(bitmap.height)
        output.write(0x87) // 256-entry local colour table
        val palette = AdaptiveGifPalette.from(bitmap, frame.prioritizedRegions)
        palette.writeTo(output)
        output.write(8)
        output.writeSubBlocks(LzwEncoder.encode(palette.indicesFor(bitmap)))
    }
}

/**
 * Per-frame adaptive 256-colour palette, using a 12-bit colour histogram.
 * GIF cannot exceed 256 colours, so moving photo regions are weighted above
 * the surrounding printed artwork and Floyd-Steinberg dithering softens the
 * remaining colour transitions.
 */
private class AdaptiveGifPalette private constructor(
    private val colors: IntArray,
    private val binToPalette: IntArray,
) {
    fun writeTo(output: FileOutputStream) {
        colors.forEach { color ->
            output.write(Color.red(color))
            output.write(Color.green(color))
            output.write(Color.blue(color))
        }
    }

    fun indicesFor(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val indices = ByteArray(pixels.size)
        var currentErrors = FloatArray((bitmap.width + 2) * COLOR_COMPONENTS)
        var nextErrors = FloatArray((bitmap.width + 2) * COLOR_COMPONENTS)

        pixels.forEachIndexed { pixelIndex, pixel ->
            val x = pixelIndex % bitmap.width
            if (x == 0 && pixelIndex > 0) {
                currentErrors = nextErrors
                nextErrors = FloatArray((bitmap.width + 2) * COLOR_COMPONENTS)
            }
            val errorOffset = (x + 1) * COLOR_COMPONENTS
            val alpha = Color.alpha(pixel)
            val red = (blendOnWhite(Color.red(pixel), alpha) + currentErrors[errorOffset]).roundToChannel()
            val green = (blendOnWhite(Color.green(pixel), alpha) + currentErrors[errorOffset + 1]).roundToChannel()
            val blue = (blendOnWhite(Color.blue(pixel), alpha) + currentErrors[errorOffset + 2]).roundToChannel()
            val paletteIndex = binToPalette[binFor(red, green, blue)]
            indices[pixelIndex] = paletteIndex.toByte()
            val paletteColor = colors[paletteIndex]
            val redError = red - Color.red(paletteColor)
            val greenError = green - Color.green(paletteColor)
            val blueError = blue - Color.blue(paletteColor)
            if (x < bitmap.width - 1) {
                distributeError(currentErrors, errorOffset + COLOR_COMPONENTS, redError, greenError, blueError, 7f / 16f)
            }
            if (x > 0) {
                distributeError(nextErrors, errorOffset - COLOR_COMPONENTS, redError, greenError, blueError, 3f / 16f)
            }
            distributeError(nextErrors, errorOffset, redError, greenError, blueError, 5f / 16f)
            if (x < bitmap.width - 1) {
                distributeError(nextErrors, errorOffset + COLOR_COMPONENTS, redError, greenError, blueError, 1f / 16f)
            }
        }
        return indices
    }

    companion object {
        fun from(bitmap: Bitmap, prioritizedRegions: List<RectF>): AdaptiveGifPalette {
            val counts = IntArray(COLOR_BIN_COUNT)
            val sumRed = LongArray(COLOR_BIN_COUNT)
            val sumGreen = LongArray(COLOR_BIN_COUNT)
            val sumBlue = LongArray(COLOR_BIN_COUNT)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.forEachIndexed { index, pixel ->
                val bin = binFor(pixel)
                val alpha = Color.alpha(pixel)
                val red = blendOnWhite(Color.red(pixel), alpha)
                val green = blendOnWhite(Color.green(pixel), alpha)
                val blue = blendOnWhite(Color.blue(pixel), alpha)
                val x = index % bitmap.width
                val y = index / bitmap.width
                val weight = if (prioritizedRegions.any { it.contains(x.toFloat(), y.toFloat()) }) {
                    PHOTO_REGION_WEIGHT
                } else {
                    1
                }
                counts[bin] += weight
                sumRed[bin] += red.toLong() * weight
                sumGreen[bin] += green.toLong() * weight
                sumBlue[bin] += blue.toLong() * weight
            }
            val selectedBins = counts.indices
                .filter { counts[it] > 0 }
                .sortedByDescending { counts[it] }
                .take(PALETTE_SIZE)
            val colors = IntArray(PALETTE_SIZE) { Color.WHITE }
            selectedBins.forEachIndexed { index, bin ->
                colors[index] = Color.rgb(
                    (sumRed[bin] / counts[bin]).toInt(),
                    (sumGreen[bin] / counts[bin]).toInt(),
                    (sumBlue[bin] / counts[bin]).toInt(),
                )
            }
            val binToPalette = IntArray(COLOR_BIN_COUNT)
            counts.indices.forEach { bin ->
                val representative = Color.rgb(
                    ((bin shr 8) and 0x0F) * 17,
                    ((bin shr 4) and 0x0F) * 17,
                    (bin and 0x0F) * 17,
                )
                binToPalette[bin] = colors.indices.minByOrNull { paletteIndex ->
                    colorDistance(representative, colors[paletteIndex])
                } ?: 0
            }
            return AdaptiveGifPalette(colors, binToPalette)
        }

        private fun colorDistance(first: Int, second: Int): Int {
            val red = Color.red(first) - Color.red(second)
            val green = Color.green(first) - Color.green(second)
            val blue = Color.blue(first) - Color.blue(second)
            return red * red + green * green + blue * blue
        }

        private fun blendOnWhite(value: Int, alpha: Int): Int =
            ((value * alpha + 255 * (255 - alpha)) / 255).coerceIn(0, 255)
    }
}

private fun distributeError(
    errors: FloatArray,
    offset: Int,
    red: Int,
    green: Int,
    blue: Int,
    weight: Float,
) {
    errors[offset] += red * weight
    errors[offset + 1] += green * weight
    errors[offset + 2] += blue * weight
}

private fun Float.roundToChannel(): Int = toInt().coerceIn(0, 255)

private const val PALETTE_SIZE = 256
private const val COLOR_BIN_COUNT = 4_096
private const val COLOR_COMPONENTS = 3
private const val PHOTO_REGION_WEIGHT = 5

private fun binFor(pixel: Int): Int =
    binFor(Color.red(pixel), Color.green(pixel), Color.blue(pixel))

private fun binFor(red: Int, green: Int, blue: Int): Int =
    ((red ushr 4) shl 8) or
        ((green ushr 4) shl 4) or
        (blue ushr 4)

private object LzwEncoder {
    fun encode(indices: ByteArray): ByteArray {
        require(indices.isNotEmpty()) { "GIF frame must contain pixels." }
        val clearCode = 256
        val endCode = 257
        val output = BitWriter()
        // Keep the simple, broadly compatible stream. Some mobile QR-browser
        // viewers reject the dictionary-growing form of GIF LZW, even though
        // desktop decoders accept it. File size is a trade-off; dependable
        // guest downloads take priority.
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
