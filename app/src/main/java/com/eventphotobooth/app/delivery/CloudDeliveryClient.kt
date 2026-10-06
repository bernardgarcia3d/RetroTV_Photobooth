package com.eventphotobooth.app.delivery

import com.eventphotobooth.app.data.BoothConfiguration
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class DeliveryLinks(val photoUrl: String, val gifUrl: String, val expiresAt: Long)

class CloudDeliveryClient {
    fun upload(configuration: BoothConfiguration, sessionId: String, photo: File, gif: File): Result<DeliveryLinks> = runCatching {
        val baseUrl = configuration.deliveryWorkerUrl.trim().trimEnd('/')
        require(baseUrl.isNotBlank() && configuration.deliveryBoothId.isNotBlank() && configuration.deliveryDeviceKey.isNotBlank()) { "Cloud delivery is not configured" }
        val boundary = "----Fotoautomat${System.nanoTime()}"
        val connection = (URL("$baseUrl/v1/sessions/$sessionId/assets").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${configuration.deliveryDeviceKey}")
            setRequestProperty("X-Booth-Id", configuration.deliveryBoothId)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        connection.outputStream.use { output ->
            writeFilePart(output, boundary, "photo", "photo.png", "image/png", photo)
            writeFilePart(output, boundary, "gif", "animation.gif", "image/gif", gif)
            output.write("--$boundary--\r\n".toByteArray())
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error(JSONObject(body).optString("error", "Cloud upload failed"))
        val json = JSONObject(body)
        DeliveryLinks(json.getJSONObject("photo").getString("url"), json.getJSONObject("gif").getString("url"), json.getLong("expiresAt"))
    }

    private fun writeFilePart(output: java.io.OutputStream, boundary: String, field: String, fileName: String, contentType: String, file: File) {
        output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$fileName\"\r\nContent-Type: $contentType\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(output) }
        output.write("\r\n".toByteArray())
    }
}
