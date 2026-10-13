package com.eventphotobooth.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Keeps the operator's welcome-screen logo inside the app's private storage.
 *
 * We copy and normalize the selected file instead of retaining a document URI.
 * That keeps the welcome screen available offline even when Android revokes an
 * external provider's permission or the original file is moved.
 */
class LocalBrandingAssetStore(private val context: Context) {
    private val root = File(context.filesDir, ROOT_DIRECTORY).apply { mkdirs() }

    @Synchronized
    fun importWelcomeLogo(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readBoundedBytes)
            ?: error("The selected logo could not be opened.")
        val bitmap = decodeAndNormalize(bytes)
        try {
            val destination = File(root, "welcome-logo-${UUID.randomUUID()}.png")
            FileOutputStream(destination).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "The logo could not be saved."
                }
            }
            return destination.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    @Synchronized
    fun remove(path: String) {
        if (path.isBlank()) return
        val candidate = File(path)
        if (candidate.parentFile?.canonicalFile == root.canonicalFile) {
            candidate.delete()
        }
    }

    private fun readBoundedBytes(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            check(output.size() + count <= MAX_SOURCE_BYTES) {
                "Choose a logo smaller than 12 MB."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeAndNormalize(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid PNG, JPG, or WebP logo." }
        check(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_SOURCE_PIXELS) {
            "Choose a logo smaller than 20 megapixels."
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: error("The selected logo could not be decoded.")
        val largestSide = max(decoded.width, decoded.height)
        if (largestSide <= MAX_NORMALIZED_SIDE) return decoded

        val scale = MAX_NORMALIZED_SIDE.toFloat() / largestSide
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (max(width, height) / (sampleSize * 2) >= MAX_NORMALIZED_SIDE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val ROOT_DIRECTORY = "branding"
        const val MAX_SOURCE_BYTES = 12 * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 20_000_000L
        const val MAX_NORMALIZED_SIDE = 2_048
    }
}
