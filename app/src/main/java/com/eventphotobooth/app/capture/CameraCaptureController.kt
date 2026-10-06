package com.eventphotobooth.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraCaptureController(
    private val context: Context,
) {
    private val cameraExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var imageCapture: ImageCapture? = null

    suspend fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val provider = context.cameraProvider()
        // Follow the tablet display so preview and capture stay aligned in either
        // portrait or landscape while preserving the 4:3 source-photo contract.
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            requireNotNull(imageCapture),
        )
    }

    suspend fun capture(target: File): File {
        val capture = checkNotNull(imageCapture) { "Camera is not ready" }
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                options,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        runCatching { normalizeJpeg(target) }
                            .onSuccess { continuation.resume(target) }
                            .onFailure { continuation.resumeWithException(it) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    fun release() {
        ProcessCameraProvider.getInstance(context).get().unbindAll()
        imageCapture = null
    }

    /**
     * BitmapFactory does not apply JPEG EXIF orientation. Normalizing at the
     * capture boundary means every layout, preview, and print renderer receives
     * a consistently oriented image.
     */
    private fun normalizeJpeg(file: File) {
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return
        }
        val original = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Unable to decode captured photo")
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
        val normalized = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        FileOutputStream(file).use { output ->
            check(normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Unable to save upright captured photo"
            }
        }
        if (normalized !== original) normalized.recycle()
        original.recycle()
    }
}

private const val JPEG_QUALITY = 95

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { provider -> continuation.resume(provider) }
                    .onFailure { error -> continuation.resumeWithException(error) }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
