package com.eventphotobooth.app.data

import android.content.Context
import org.json.JSONObject

enum class PendingPaymongoOrderState {
    AWAITING_PAYMENT,
    SESSION_CLAIMED,
}

/**
 * The minimum local recovery record for an in-progress PayMongo session.
 * It deliberately excludes every PayMongo merchant secret; the server can
 * reissue the client checkout data from the persisted request UUID when needed.
 */
data class PendingPaymongoOrder(
    val orderId: String,
    val requestId: String,
    val boothId: String,
    val printQuantity: Int,
    val amountMinor: Int,
    val currency: String,
    val state: PendingPaymongoOrderState = PendingPaymongoOrderState.AWAITING_PAYMENT,
    val sessionId: String? = null,
    val qrImageData: String? = null,
    val qrExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

class PaymongoPendingOrderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PendingPaymongoOrder? = preferences.getString(PENDING_ORDER_KEY, null)?.let { raw ->
        runCatching {
            val value = JSONObject(raw)
            PendingPaymongoOrder(
                orderId = value.getString("orderId"),
                requestId = value.getString("requestId"),
                boothId = value.getString("boothId"),
                printQuantity = value.getInt("printQuantity").coerceIn(1, 10),
                amountMinor = value.getInt("amountMinor").coerceAtLeast(100),
                currency = value.optString("currency", "PHP"),
                state = runCatching { PendingPaymongoOrderState.valueOf(value.optString("state")) }
                    .getOrDefault(PendingPaymongoOrderState.AWAITING_PAYMENT),
                sessionId = value.optString("sessionId").takeIf { it.isNotBlank() },
                qrImageData = value.optString("qrImageData").takeIf { it.isNotBlank() },
                qrExpiresAtEpochMillis = value.optLong("qrExpiresAtEpochMillis", 0).takeIf { it > 0 },
                createdAtEpochMillis = value.optLong("createdAtEpochMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    fun save(order: PendingPaymongoOrder) {
        preferences.edit().putString(
            PENDING_ORDER_KEY,
            JSONObject()
                .put("orderId", order.orderId)
                .put("requestId", order.requestId)
                .put("boothId", order.boothId)
                .put("printQuantity", order.printQuantity)
                .put("amountMinor", order.amountMinor)
                .put("currency", order.currency)
                .put("state", order.state.name)
                .put("sessionId", order.sessionId)
                .put("qrImageData", order.qrImageData)
                .put("qrExpiresAtEpochMillis", order.qrExpiresAtEpochMillis)
                .put("createdAtEpochMillis", order.createdAtEpochMillis)
                .toString(),
        ).apply()
    }

    fun clear() {
        preferences.edit().remove(PENDING_ORDER_KEY).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "event_photobooth_paymongo"
        const val PENDING_ORDER_KEY = "pending_order"
    }
}
