package com.eventphotobooth.app.data

import android.content.Context
import java.security.MessageDigest

class OperatorPinStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = preferences.contains(PIN_HASH_KEY)

    fun setPin(pin: String) {
        require(pin.length >= 4) { "Use at least four digits." }
        preferences.edit().putString(PIN_HASH_KEY, hash(pin)).apply()
    }

    fun matches(pin: String): Boolean = preferences.getString(PIN_HASH_KEY, null) == hash(pin)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "event_photobooth_operator"
        const val PIN_HASH_KEY = "operator_pin_hash"
    }
}
