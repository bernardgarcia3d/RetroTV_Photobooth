package com.eventphotobooth.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RedeemedAccessCode(val accessCodeId: String, val packageId: String)
data class GeneratedAccessBatch(
    val batchId: String,
    val packageLabel: String,
    val expiresAt: String,
    val codes: List<String>,
    val codeIds: List<String>,
)

data class AccessCodeTotals(val issued: Int, val claimed: Int, val completed: Int, val expired: Int, val cancelled: Int)

class AccessCodeClient(
    private val baseUrl: String,
    private val publishableKey: String,
    private val sharedKey: String,
    private val boothId: String,
) {
    private val endpoint get() = "${baseUrl.trimEnd('/')}/functions/v1/photo-access"

    suspend fun health(): Result<String> = call(JSONObject().put("action", "health")).map { it.optString("businessDate") }

    suspend fun createBatch(packageId: String, packageLabel: String, quantity: Int): Result<GeneratedAccessBatch> = call(
        JSONObject().put("action", "create_batch")
            .put("packageId", packageId).put("packageLabel", packageLabel).put("quantity", quantity),
    ).map { response ->
        val codes = buildList { val values = response.getJSONArray("codes"); for (index in 0 until values.length()) add(values.getString(index)) }
        val codeIds = buildList { val values = response.optJSONArray("codeIds"); if (values != null) for (index in 0 until values.length()) add(values.getString(index)) }
        GeneratedAccessBatch(response.getString("batchId"), response.getString("packageLabel"), response.getString("expiresAt"), codes, codeIds)
    }

    suspend fun redeem(code: String): Result<RedeemedAccessCode> = call(
        JSONObject().put("action", "redeem").put("code", code).put("boothId", boothId),
    ).map { RedeemedAccessCode(it.getString("accessCodeId"), it.getString("packageId")) }

    suspend fun complete(accessCodeId: String, sessionId: String): Result<Unit> = call(
        JSONObject().put("action", "complete").put("accessCodeId", accessCodeId).put("sessionId", sessionId).put("boothId", boothId),
    ).map { Unit }

    suspend fun maintenance(): Result<Unit> = call(JSONObject().put("action", "maintenance").put("boothId", boothId)).map { Unit }

    suspend fun totals(): Result<AccessCodeTotals> = call(JSONObject().put("action", "totals")).map {
        AccessCodeTotals(it.optInt("issued"), it.optInt("claimed"), it.optInt("completed"), it.optInt("expired"), it.optInt("cancelled"))
    }

    suspend fun cancel(accessCodeId: String): Result<Unit> = call(
        JSONObject().put("action", "cancel").put("accessCodeId", accessCodeId),
    ).map { Unit }

    private suspend fun call(body: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            require(sharedKey.isNotBlank()) { "Code access is not configured. Add the shared access key in Operator Settings." }
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-booth-key", sharedKey)
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val serverMessage = JSONObject(text).optString("error", "Code service request failed")
                if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    error("RetroRoll rejected the shared access key. Set BOOTH_SHARED_KEY in Supabase Edge Function Secrets, then save the same key in Operator Settings.")
                }
                error(serverMessage)
            }
            JSONObject(text)
        }
    }
}
