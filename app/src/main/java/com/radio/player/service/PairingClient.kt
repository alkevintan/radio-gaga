package com.radio.player.service

import android.content.Context
import android.util.Log
import com.radio.player.util.PairingManager
import com.radio.player.util.PairingProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Outbound connection to a paired peer's [PairingServer].
 *
 * On connect, sends HELLO with our local identity. Tracks state through [State]:
 * IDLE → CONNECTING → CONNECTED (after HELLO_ACK ok) or ERROR. The caller observes
 * frames via [onMessage] and the latest [State] via [state].
 */
class PairingClient(
    private val context: Context,
    private val onMessage: (String) -> Unit
) {

    companion object {
        private const val TAG = "PairingClient"
        private const val CONNECT_TIMEOUT_MS = 4000
    }

    enum class State { IDLE, CONNECTING, CONNECTED, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null
    private var socket: Socket? = null

    /** Connect to [host]:[port]; replaces any prior connection. */
    fun connect(host: InetAddress, port: Int) {
        disconnect()
        connectJob = scope.launch {
            _state.value = State.CONNECTING
            val s = Socket()
            try {
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.tcpNoDelay = true
                socket = s
            } catch (e: Exception) {
                Log.d(TAG, "connect failed: ${e.message}")
                _state.value = State.ERROR
                return@launch
            }

            val out = s.getOutputStream()
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

            // Send HELLO with our own identity. authProof = peer's token from the QR scan,
            // sent until the server confirms it has saved us as its peer.
            val peer = PairingManager.peer(context)
            if (peer == null) {
                _state.value = State.ERROR
                closeQuiet(s); return@launch
            }
            val authProof = if (!PairingManager.isPeerPairedBack(context)) peer.token else null
            val hello = PairingProtocol.Hello(
                selfDeviceId = PairingManager.localDeviceId(context),
                selfToken = PairingManager.localToken(context),
                selfName = PairingManager.localName(context),
                authProof = authProof
            )
            try {
                out.write(PairingProtocol.encode(hello).toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
                _state.value = State.ERROR
                closeQuiet(s); return@launch
            }

            // Await HELLO_ACK with a short timeout.
            val ackLine = withTimeoutOrNull(4000) {
                try { reader.readLine() } catch (_: Exception) { null }
            }
            val ack = ackLine?.let { PairingProtocol.decode<PairingProtocol.HelloAck>(it) }
            if (ack == null || !ack.ok) {
                _state.value = State.ERROR
                closeQuiet(s); return@launch
            }
            if (ack.pairedBack && !PairingManager.isPeerPairedBack(context)) {
                PairingManager.setPeerPairedBack(context, true)
            }
            _peerName.value = ack.name
            _state.value = State.CONNECTED

            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    onMessage(line)
                }
            } catch (e: Exception) {
                Log.d(TAG, "client read ended: ${e.message}")
            } finally {
                if (_state.value == State.CONNECTED) _state.value = State.IDLE
                closeQuiet(s)
                if (socket === s) socket = null
            }
        }
    }

    fun send(msg: Any) {
        val s = socket ?: return
        scope.launch {
            try {
                val line = PairingProtocol.encode(msg)
                s.getOutputStream().write(line.toByteArray(Charsets.UTF_8))
                s.getOutputStream().flush()
            } catch (_: Exception) {
                _state.value = State.ERROR
                closeQuiet(s)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        socket?.let { closeQuiet(it) }
        socket = null
        _state.value = State.IDLE
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    /** True if we are actively talking to the peer. */
    val isConnected: Boolean get() = _state.value == State.CONNECTED

    private fun closeQuiet(s: Socket) {
        try { s.close() } catch (_: Exception) {}
    }
}
