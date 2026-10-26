package com.eventphotobooth.app.delivery

import com.eventphotobooth.app.data.BoothConfiguration
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONObject

data class DeliveryLinks(val photoUrl: String, val gifUrl: String, val expiresAt: Long)

/**
 * Uploads are best-effort: a failed cloud copy must never discard a completed local session.
 * The failure message is intentionally safe to show in the kiosk UI and never includes keys,
 * URLs, or server response bodies.
 */
data class DeliveryUploadResult(
    val links: DeliveryLinks? = null,
    val failureMessage: String? = null,
    val attempts: Int,
)

class CloudDeliveryClient {
    fun upload(configuration: BoothConfiguration, sessionId: String, photo: File, gif: File): DeliveryUploadResult {
        var lastFailure: Exception? = null
        for (attempt in 1..MAX_UPLOAD_ATTEMPTS) {
            try {
                return DeliveryUploadResult(
                    links = uploadOnce(configuration, sessionId, photo, gif),
                    attempts = attempt,
                )
            } catch (failure: Exception) {
                lastFailure = failure
                if (!failure.isRetryableUploadFailure() || attempt == MAX_UPLOAD_ATTEMPTS) {
                    return DeliveryUploadResult(
                        failureMessage = failure.userFacingMessage(attempt),
                        attempts = attempt,
                    )
                }
                try {
                    Thread.sleep(RETRY_DELAYS_MS[attempt - 1])
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return DeliveryUploadResult(
                        failureMessage = "Cloud upload was interrupted. Your copies are saved on this tablet.",
                        attempts = attempt,
                    )
                }
            }
        }
        return DeliveryUploadResult(
            failureMessage = lastFailure?.userFacingMessage(MAX_UPLOAD_ATTEMPTS)
                ?: "Cloud upload did not complete. Your copies are saved on this tablet.",
            attempts = MAX_UPLOAD_ATTEMPTS,
        )
    }

    private fun uploadOnce(configuration: BoothConfiguration, sessionId: String, photo: File, gif: File): DeliveryLinks {
        val baseUrl = configuration.deliveryWorkerUrl.trim().trimEnd('/')
        require(baseUrl.isNotBlank() && configuration.deliveryBoothId.isNotBlank() && configuration.deliveryDeviceKey.isNotBlank()) {
            "Cloud delivery is not configured"
        }
        require(photo.isFile && gif.isFile) { "Digital files are not available for upload" }

        val boundary = "----Fotoautomat${System.nanoTime()}"
        val connection = (URL("$baseUrl/v1/sessions/$sessionId/assets").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            // Multi-frame GIFs for 3/4/6-photo layouts are larger than a one-photo GIF.
            // Leave enough time for a mobile/Wi-Fi upload and Worker response before retrying.
            readTimeout = 90_000
            setChunkedStreamingMode(16 * 1024)
            setRequestProperty("Authorization", "Bearer ${configuration.deliveryDeviceKey}")
            setRequestProperty("X-Booth-Id", configuration.deliveryBoothId)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { output ->
                writeFilePart(output, boundary, "photo", "photo.png", "image/png", photo)
                writeFilePart(output, boundary, "gif", "animation.gif", "image/gif", gif)
                output.write("--$boundary--\r\n".toByteArray())
            }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                throw CloudUploadException(responseCode, responseMessage(responseCode, body))
            }
            val json = JSONObject(body)
            return DeliveryLinks(
                photoUrl = json.getJSONObject("photo").getString("url"),
                gifUrl = json.getJSONObject("gif").getString("url"),
                expiresAt = json.getLong("expiresAt"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun writeFilePart(output: java.io.OutputStream, boundary: String, field: String, fileName: String, contentType: String, file: File) {
        output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$fileName\"\r\nContent-Type: $contentType\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(output) }
        output.write("\r\n".toByteArray())
    }

    private fun responseMessage(responseCode: Int, body: String): String {
        val workerMessage = runCatching { JSONObject(body).optString("error") }.getOrNull()
        return when {
            workerMessage.equals("Unauthorized booth", ignoreCase = true) -> "Cloud delivery credentials were rejected"
            responseCode == 401 || responseCode == 403 -> "Cloud delivery credentials were rejected"
            responseCode == 404 -> "Cloud delivery service was not found"
            responseCode == 413 -> "Digital files are too large for cloud delivery"
            else -> workerMessage?.takeIf(String::isNotBlank) ?: "Cloud delivery returned HTTP $responseCode"
        }
    }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L)
    }
}

private class CloudUploadException(val responseCode: Int, message: String) : IOException(message)

private fun Exception.isRetryableUploadFailure(): Boolean = when (this) {
    is CloudUploadException -> responseCode == 408 || responseCode == 429 || responseCode in 500..599 && message != "Cloud delivery credentials were rejected"
    is IOException -> true
    else -> false
}

private fun Exception.userFacingMessage(attempts: Int): String = when (this) {
    is SocketTimeoutException -> "Cloud upload timed out after $attempts attempts. Your copies are saved on this tablet."
    is CloudUploadException -> when {
        message == "Cloud delivery credentials were rejected" -> "Cloud delivery credentials need attention. Your copies are saved on this tablet."
        responseCode == 404 -> "Cloud delivery setup needs attention. Your copies are saved on this tablet."
        responseCode == 413 -> "Digital files are too large to upload. Your copies are saved on this tablet."
        else -> "Cloud upload is unavailable after $attempts attempts. Your copies are saved on this tablet."
    }
    is IOException -> "Cloud upload could not reach the service after $attempts attempts. Your copies are saved on this tablet."
    is IllegalArgumentException -> "Cloud delivery is not configured. Your copies are saved on this tablet."
    else -> "Cloud upload failed. Your copies are saved on this tablet."
}
