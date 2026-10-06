package com.eventphotobooth.app.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class GalleryExportResult(
    val photoUri: Uri,
    val gifUri: Uri,
)

/**
 * Publishes completed booth outputs to the tablet's Gallery through MediaStore.
 *
 * The app targets Android 10+, so RELATIVE_PATH keeps these files in a visible
 * Gallery album without broad storage permissions. IS_PENDING prevents Gallery
 * apps from seeing a partially copied file.
 */
class GalleryMediaExporter(
    private val contentResolver: ContentResolver,
) {
    fun export(sessionId: String, photoFile: File, gifFile: File): GalleryExportResult {
        val photoUri = publish(
            sourceFile = photoFile,
            displayName = "retro-tv-$sessionId-photo.png",
            mimeType = "image/png",
        )

        return try {
            val gifUri = publish(
                sourceFile = gifFile,
                displayName = "retro-tv-$sessionId-animation.gif",
                mimeType = "image/gif",
            )
            GalleryExportResult(photoUri = photoUri, gifUri = gifUri)
        } catch (error: Throwable) {
            contentResolver.delete(photoUri, null, null)
            throw error
        }
    }

    private fun publish(sourceFile: File, displayName: String, mimeType: String): Uri {
        require(sourceFile.isFile) { "Gallery export source does not exist: ${sourceFile.name}" }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_DIRECTORY)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create a Gallery item for ${sourceFile.name}")

        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open the Gallery item for ${sourceFile.name}")

            val published = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            check(contentResolver.update(uri, published, null, null) == 1) {
                "Unable to publish ${sourceFile.name} to the Gallery"
            }
            return uri
        } catch (error: Throwable) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private companion object {
        const val GALLERY_DIRECTORY = "Pictures/RetroTV PhotoBooth"
    }
}
