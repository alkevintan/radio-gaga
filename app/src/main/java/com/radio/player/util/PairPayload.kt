package com.radio.player.util

import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * Payload encoded into the pairing QR code.
 *
 * Carries everything the scanner needs to identify and authenticate against the issuer:
 * the issuer's device id, secret token, and friendly name. The connection target (host/port)
 * is rediscovered via NSD, so it is not embedded.
 *
 * A leading `radiogaga-pair:` scheme prefix lets the QR scanner distinguish pair codes
 * from station-share codes (which are bare URLs).
 */
object PairPayload {

    private const val SCHEME = "radiogaga-pair:"
    private val gson = Gson()

    data class Body(
        val v: Int = 1,
        val deviceId: String,
        val token: String,
        val name: String
    )

    fun encode(deviceId: String, token: String, name: String): String {
        val body = Body(deviceId = deviceId, token = token, name = name)
        return SCHEME + gson.toJson(body)
    }

    fun decode(qr: String): Body? {
        if (!qr.startsWith(SCHEME)) return null
        val json = qr.substring(SCHEME.length)
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            Body(
                v = obj.get("v")?.asInt ?: 1,
                deviceId = obj.get("deviceId")?.asString ?: return null,
                token = obj.get("token")?.asString ?: return null,
                name = obj.get("name")?.asString ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isPairCode(qr: String): Boolean = qr.startsWith(SCHEME)
}
