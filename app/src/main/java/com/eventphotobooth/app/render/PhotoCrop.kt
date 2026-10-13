package com.eventphotobooth.app.render

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.roundToInt

/** Returns the exact centered source crop used by preview, print, and GIF. */
fun centerCropSourceRect(source: Bitmap, destinationAspectRatio: Float): Rect =
    centerCropSourceRect(source.width, source.height, destinationAspectRatio)

fun centerCropSourceRect(sourceWidth: Int, sourceHeight: Int, destinationAspectRatio: Float): Rect {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source image dimensions must be positive." }
    val safeRatio = destinationAspectRatio.coerceAtLeast(0.01f)
    val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceRatio > safeRatio) {
        val cropWidth = (sourceHeight * safeRatio).roundToInt().coerceIn(1, sourceWidth)
        val left = (sourceWidth - cropWidth) / 2
        Rect(left, 0, left + cropWidth, sourceHeight)
    } else {
        val cropHeight = (sourceWidth / safeRatio).roundToInt().coerceIn(1, sourceHeight)
        val top = (sourceHeight - cropHeight) / 2
        Rect(0, top, sourceWidth, top + cropHeight)
    }
}
