package com.eventphotobooth.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android-side portion of the PayMongo QR Ph flow.
 *
 * This client can use only the non-secret public key and the short-lived client
 * key issued by the payment backend. Price selection, Payment Intent creation,
 * status verification, and session authorization stay in the Edge Function.
 */
data class PaymongoCheckout(
    val orderId: String,
    val requestId: String,
    val printQuantity: Int,
    val amountMinor: Int,
    val currency: String,
    val paymentIntentId: String,
    val clientKey: String,
    val publicKey: String,
)

data class PaymongoPaymentStatus(
    val orderId: String,
    val paymentStatus: String,
    val verifiedPaid: Boolean,
)

data class PaymongoSessionClaim(
    val sessionId: String,
    val resumed: Boolean,
)

data class PaymongoAuthorizedSession(
    val orderId: String,
    val sessionId: String,
    val printQuantity: Int,
    val amountMinor: Int,
    val currency: String,
)

data class PaymongoQrCode(
    val imageData: String,
    val expiresAtEpochMillis: Long,
) {
    fun decodeBitmap(): Bitmap? = decodePaymongoQrImage(imageData)
}

class PaymongoPaymentClient(
    private val backendUrl: String,
    private val publishableSupabaseKey: String,
    private val boothSharedKey: String,
    private val boothId: String,
) {
    private val endpoint: String
        get() = "${backendUrl.trimEnd('/')}/functions/v1/paymongo-payments"

    fun configurationIssue(): String? = when {
        !backendUrl.trim().startsWith("https://") -> "PayMongo payment backend URL is not configured."
        boothSharedKey.isBlank() -> "This tablet has no PayMongo booth key. Ask the owner to provision it."
        !boothId.matches(Regex("[A-Za-z0-9_-]{1,64}")) -> "This tablet has no valid booth ID. Ask the owner to provision it."
        publishableSupabaseKey.isBlank() -> "Supabase publishable key is not configured."
        else -> null
    }

    suspend fun health(): Result<Boolean> = callBackend(JSONObject().put("action", "health")).map {
        it.optBoolean("ok", false) && it.optBoolean("paymentConfigured", false)
    }

    suspend fun createOrder(printQuantity: Int, requestId: String): Result<PaymongoCheckout> = callBackend(
        JSONObject()
            .put("action", "create_order")
            .put("boothId", boothId)
            .put("printQuantity", printQuantity)
            .put("requestId", requestId),
    ).map { response ->
        PaymongoCheckout(
            orderId = response.requiredString("orderId"),
            requestId = requestId,
            printQuantity = response.optInt("printQuantity", printQuantity).coerceIn(1, 10),
            amountMinor = response.optInt("amountMinor", -1).also { require(it >= 100) { "Payment server returned an invalid amount." } },
            currency = response.requiredString("currency"),
            paymentIntentId = response.requiredString("paymentIntentId"),
            clientKey = response.requiredString("clientKey"),
            publicKey = response.requiredString("publicKey"),
        )
    }

    /** Creates a fresh QR Ph Payment Method, then attaches it to the existing Payment Intent. */
    suspend fun createQrCode(checkout: PaymongoCheckout, expirySeconds: Int = QR_EXPIRY_SECONDS): Result<PaymongoQrCode> = withContext(Dispatchers.IO) {
        runCatching {
            require(expirySeconds in 60..9000) { "QR expiry must be between 60 and 9000 seconds." }
            val paymentMethod = callPaymongo(
                path = "/v1/payment_methods",
                publicKey = checkout.publicKey,
                body = JSONObject().put("data", JSONObject().put("attributes", JSONObject()
                    .put("type", "qrph")
                    .put("expiry_seconds", expirySeconds))),
            )
            val paymentMethodId = paymentMethod.getJSONObject("data").getString("id")
            val intent = callPaymongo(
                path = "/v1/payment_intents/${checkout.paymentIntentId}/attach",
                publicKey = checkout.publicKey,
                body = JSONObject().put("data", JSONObject().put("attributes", JSONObject()
                    .put("payment_method", paymentMethodId)
                    .put("client_key", checkout.clientKey))),
            )
            val image = intent.getJSONObject("data")
                .getJSONObject("attributes")
                .getJSONObject("next_action")
                .getJSONObject("code")
                .getString("image_url")
            require(decodePaymongoQrImage(image) != null) { "PayMongo returned an unreadable QR code." }
            PaymongoQrCode(
                imageData = image,
                expiresAtEpochMillis = System.currentTimeMillis() + expirySeconds * 1_000L,
            )
        }.recoverCatching { error ->
            throw IllegalStateException(
                if (error is IllegalArgumentException || error is IllegalStateException) {
                    error.message ?: "QR payment could not be prepared."
                } else {
                    "QR payment could not be prepared. Please try again."
                },
            )
        }
    }

    suspend fun paymentStatus(orderId: String): Result<PaymongoPaymentStatus> = callBackend(
        JSONObject().put("action", "payment_status").put("boothId", boothId).put("orderId", orderId),
    ).map { response ->
        PaymongoPaymentStatus(
            orderId = response.requiredString("orderId"),
            paymentStatus = response.optString("paymentStatus", "pending"),
            verifiedPaid = response.optBoolean("verifiedPaid", false),
        )
    }

    suspend fun claimSession(orderId: String): Result<PaymongoSessionClaim> = callBackend(
        JSONObject().put("action", "claim_session").put("boothId", boothId).put("orderId", orderId),
    ).map { response ->
        PaymongoSessionClaim(
            sessionId = response.requiredString("sessionId"),
            resumed = response.optBoolean("resumed", false),
        )
    }

    suspend fun completeSession(orderId: String, sessionId: String): Result<Unit> = callBackend(
        JSONObject()
            .put("action", "complete_session")
            .put("boothId", boothId)
            .put("orderId", orderId)
            .put("sessionId", sessionId),
    ).map { Unit }

    private suspend fun callBackend(body: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            configurationIssue()?.let(::error)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setRequestProperty("apikey", publishableSupabaseKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-booth-key", boothSharedKey)
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.take(180)
                        ?: "Payment service is unavailable. Please ask the attendant."
                    error(message)
                }
                JSONObject(responseText)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun callPaymongo(path: String, publicKey: String, body: JSONObject): JSONObject {
        require(publicKey.startsWith("pk_")) { "Payment configuration is invalid." }
        val connection = URL("https://api.paymongo.com$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val credentials = Base64.encodeToString("$publicKey:".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $credentials")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("PayMongo could not prepare this QR. Please try again.")
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.requiredString(name: String): String = getString(name).trim().also {
        require(it.isNotBlank() && it != "null") { "Payment server returned incomplete checkout data." }
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val QR_EXPIRY_SECONDS = 180
    }
}

fun decodePaymongoQrImage(value: String): Bitmap? = runCatching {
    val encoded = value.substringAfter("base64,", value).replace("\n", "").replace("\r", "")
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
