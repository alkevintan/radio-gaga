package com.radio.player.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Newline-delimited JSON messages exchanged between paired devices over TCP.
 *
 * Every frame is a single JSON object on its own line, terminated by `\n`.
 * The `type` field discriminates the variant.
 */
object PairingProtocol {

    const val TYPE_HELLO = "hello"
    const val TYPE_HELLO_ACK = "hello_ack"
    const val TYPE_STATE = "state"
    const val TYPE_CMD = "cmd"
    const val TYPE_STATIONS_REQ = "stations_req"
    const val TYPE_STATIONS = "stations"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"

    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_STOP = "stop"
    const val ACTION_PLAY_STATION = "play_station"
    const val ACTION_NEXT = "next"
    const val ACTION_PREV = "prev"

    private val gson = Gson()

    /**
     * Sent by the connecting client immediately after socket open.
     *
     * Carries the client's own identity (`selfDeviceId`/`selfToken`/`selfName`) so the
     * server can save it as a known peer. `authProof`, when present, is the server's
     * own token as obtained from a freshly scanned pair QR — it lets first-time
     * connections authenticate before the server has stored the client. Once paired,
     * subsequent connections rely on the stored peer match and `authProof` may be null.
     */
    data class Hello(
        val type: String = TYPE_HELLO,
        val selfDeviceId: String,
        val selfToken: String,
        val selfName: String,
        val authProof: String? = null,
        val protoVersion: Int = 1
    )

    data class HelloAck(
        val type: String = TYPE_HELLO_ACK,
        val deviceId: String,
        val name: String,
        val ok: Boolean,
        val pairedBack: Boolean = false,
        val error: String? = null
    )

    data class StationDto(
        val id: Long,
        val name: String,
        val streamUrl: String,
        val genre: String = "",
        val country: String = "",
        val favicon: String = ""
    )

    data class State(
        val type: String = TYPE_STATE,
        val playing: Boolean,
        val buffering: Boolean,
        val station: StationDto? = null,
        val playStartMs: Long = 0L
    )

    data class Cmd(
        val type: String = TYPE_CMD,
        val action: String,
        val stationId: Long? = null
    )

    data class StationsReq(val type: String = TYPE_STATIONS_REQ)

    data class Stations(
        val type: String = TYPE_STATIONS,
        val list: List<StationDto>
    )

    data class Ping(val type: String = TYPE_PING, val ts: Long = System.currentTimeMillis())
    data class Pong(val type: String = TYPE_PONG, val ts: Long)

    fun encode(msg: Any): String = gson.toJson(msg) + "\n"

    /** Returns null if the line is not parseable JSON. */
    fun typeOf(line: String): String? = try {
        val obj = JsonParser.parseString(line).asJsonObject
        obj.get("type")?.asString
    } catch (_: Exception) {
        null
    }

    inline fun <reified T> decode(line: String): T? = try {
        Gson().fromJson(line, T::class.java)
    } catch (_: Exception) {
        null
    }

    fun rawObject(line: String): JsonObject? = try {
        JsonParser.parseString(line).asJsonObject
    } catch (_: Exception) {
        null
    }
}
