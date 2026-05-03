package com.radio.player.util

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import java.security.SecureRandom
import java.util.UUID

/**
 * Persistent identity + paired-peer storage for device pairing.
 *
 * `localDeviceId` and `localToken` are generated once per install and persist for the life
 * of the app. The paired peer's identity (deviceId, token, friendly name) is stored after a
 * successful pair; clearing it un-pairs.
 */
object PairingManager {

    private const val KEY_LOCAL_DEVICE_ID = "pair_local_device_id"
    private const val KEY_LOCAL_TOKEN = "pair_local_token"
    private const val KEY_LOCAL_NAME = "pair_local_name"
    private const val KEY_PEER_DEVICE_ID = "pair_peer_device_id"
    private const val KEY_PEER_TOKEN = "pair_peer_token"
    private const val KEY_PEER_NAME = "pair_peer_name"
    /** True once the server confirmed it has saved us as its peer (bidirectional pair). */
    private const val KEY_PEER_PAIRED_BACK = "pair_peer_paired_back"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun localDeviceId(context: Context): String {
        val p = prefs(context)
        var id = p.getString(KEY_LOCAL_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_LOCAL_DEVICE_ID, id).apply()
        }
        return id
    }

    fun localToken(context: Context): String {
        val p = prefs(context)
        var token = p.getString(KEY_LOCAL_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = randomToken()
            p.edit().putString(KEY_LOCAL_TOKEN, token).apply()
        }
        return token
    }

    fun localName(context: Context): String {
        val stored = prefs(context).getString(KEY_LOCAL_NAME, null)
        if (!stored.isNullOrBlank()) return stored
        val model = Build.MODEL.takeIf { it.isNotBlank() } ?: "Radio Gaga"
        return model
    }

    fun setLocalName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_LOCAL_NAME, name).apply()
    }

    /** Roll a fresh token (invalidates existing pair). */
    fun regenerateLocalToken(context: Context): String {
        val token = randomToken()
        prefs(context).edit().putString(KEY_LOCAL_TOKEN, token).apply()
        return token
    }

    data class Peer(val deviceId: String, val token: String, val name: String)

    fun peer(context: Context): Peer? {
        val p = prefs(context)
        val id = p.getString(KEY_PEER_DEVICE_ID, null) ?: return null
        val token = p.getString(KEY_PEER_TOKEN, null) ?: return null
        val name = p.getString(KEY_PEER_NAME, "") ?: ""
        return Peer(id, token, name)
    }

    fun setPeer(context: Context, peer: Peer) {
        prefs(context).edit()
            .putString(KEY_PEER_DEVICE_ID, peer.deviceId)
            .putString(KEY_PEER_TOKEN, peer.token)
            .putString(KEY_PEER_NAME, peer.name)
            .putBoolean(KEY_PEER_PAIRED_BACK, false)
            .apply()
    }

    fun clearPeer(context: Context) {
        prefs(context).edit()
            .remove(KEY_PEER_DEVICE_ID)
            .remove(KEY_PEER_TOKEN)
            .remove(KEY_PEER_NAME)
            .remove(KEY_PEER_PAIRED_BACK)
            .apply()
    }

    fun isPeerPairedBack(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PEER_PAIRED_BACK, false)

    fun setPeerPairedBack(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PEER_PAIRED_BACK, value).apply()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }
}
