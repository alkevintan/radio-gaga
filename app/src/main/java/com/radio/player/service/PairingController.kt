package com.radio.player.service

import android.content.Context
import android.util.Log
import com.radio.player.data.RadioStation
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
import kotlinx.coroutines.launch

/**
 * Glue layer combining [PairingNsd] + [PairingServer] + [PairingClient] into a single facade
 * for the rest of the app.
 *
 * Lifecycle is owned by [RadioPlaybackService]: [start] is called from `onCreate`, [stop]
 * from `onDestroy`. While running, the controller advertises this device on the local
 * network, accepts inbound connections from a paired peer, and (if a peer is configured)
 * tries to connect outbound via NSD discovery.
 *
 * The controller holds two views of the world:
 *  - [outboundPeerState] — what the peer is doing, populated from STATE frames received
 *    over the [PairingClient] connection. Used by the local UI to drive Remote mode.
 *  - inbound commands → [onIncomingCommand] callback, invoked when a remote peer asks us
 *    to play / pause / stop / play a specific station. The host service implements this.
 */
class PairingController(
    private val context: Context,
    /** Provide the current player snapshot so we can announce STATE on demand. */
    private val snapshotState: () -> PairingProtocol.State,
    /** Provide the current station list (for STATIONS_REQ replies). */
    private val snapshotStations: () -> List<PairingProtocol.StationDto>,
    /** Apply an incoming control command from the peer. */
    private val onIncomingCommand: (PairingProtocol.Cmd) -> Unit
) {

    companion object { private const val TAG = "PairingController" }

    private val nsd = PairingNsd(context)
    private val server: PairingServer = PairingServer(
        context = context,
        onMessage = { conn, line -> handleServerMessage(conn, line) },
        onConnected = { conn ->
            Log.d(TAG, "peer connected (inbound): ${conn.peerName}")
            // Send current state so the remote UI is correct immediately.
            try { conn.send(snapshotState()) } catch (_: Exception) {}
        },
        onDisconnected = { conn ->
            Log.d(TAG, "peer disconnected (inbound): ${conn.peerName}")
        }
    )
    private val client: PairingClient = PairingClient(context) { line ->
        handleClientMessage(line)
    }

    private val _outboundPeerState = MutableStateFlow<PairingProtocol.State?>(null)
    val outboundPeerState: StateFlow<PairingProtocol.State?> = _outboundPeerState

    private val _outboundPeerStations = MutableStateFlow<List<PairingProtocol.StationDto>>(emptyList())
    val outboundPeerStations: StateFlow<List<PairingProtocol.StationDto>> = _outboundPeerStations

    val clientState: StateFlow<PairingClient.State> get() = client.state
    val clientPeerName: StateFlow<String> get() = client.peerName

    private var running = false
    private val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reconnectJob: Job? = null

    fun start() {
        if (running) return
        running = true
        val port = server.start()
        nsd.advertise(
            localDeviceId = PairingManager.localDeviceId(context),
            localName = PairingManager.localName(context),
            port = port
        )
        beginOutboundDiscovery()
        startReconnectWatcher()
    }

    private fun startReconnectWatcher() {
        reconnectJob?.cancel()
        reconnectJob = supervisorScope.launch {
            client.state.collect { state ->
                // If we got disconnected and a peer is still configured, restart NSD
                // discovery so we re-fire on the next onServiceFound callback.
                if (state == PairingClient.State.IDLE || state == PairingClient.State.ERROR) {
                    if (PairingManager.peer(context) != null && running) {
                        delay(1500)
                        if (client.state.value == PairingClient.State.IDLE ||
                            client.state.value == PairingClient.State.ERROR) {
                            beginOutboundDiscovery()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        reconnectJob?.cancel()
        reconnectJob = null
        nsd.stopDiscovery()
        nsd.stopAdvertise()
        client.disconnect()
        server.stop()
    }

    fun shutdown() {
        stop()
        client.shutdown()
        server.shutdown()
        supervisorScope.cancel()
    }

    /** Re-trigger discovery after the paired peer changes (or a re-pair occurs). */
    fun onPeerChanged() {
        client.disconnect()
        _outboundPeerState.value = null
        _outboundPeerStations.value = emptyList()
        beginOutboundDiscovery()
    }

    /** Push a state update to all connected inbound peers (i.e. anyone who paired with us). */
    fun broadcastState(state: PairingProtocol.State) {
        server.broadcast(state)
    }

    /** Send a control command to the outbound peer (the one we connected to via NSD). */
    fun sendCommand(action: String, stationId: Long? = null) {
        if (client.isConnected) {
            client.send(PairingProtocol.Cmd(action = action, stationId = stationId))
        }
    }

    fun requestPeerStations() {
        if (client.isConnected) {
            client.send(PairingProtocol.StationsReq())
        }
    }

    private fun beginOutboundDiscovery() {
        val peer = PairingManager.peer(context) ?: return
        nsd.stopDiscovery()
        nsd.discover(
            targetDeviceId = peer.deviceId,
            onFound = { host ->
                if (client.state.value != PairingClient.State.CONNECTED &&
                    client.state.value != PairingClient.State.CONNECTING) {
                    Log.d(TAG, "discovered peer ${host.name} at ${host.host.hostAddress}:${host.port}")
                    client.connect(host.host, host.port)
                }
            },
            onLost = {
                Log.d(TAG, "peer service lost on network")
            }
        )
    }

    // --- Server side: handle frames from inbound peer ---

    private fun handleServerMessage(conn: PairingServer.Conn, line: String) {
        when (PairingProtocol.typeOf(line)) {
            PairingProtocol.TYPE_CMD -> {
                val cmd = PairingProtocol.decode<PairingProtocol.Cmd>(line) ?: return
                onIncomingCommand(cmd)
                // Echo updated state quickly after a command so the remote sees the change.
                try { conn.send(snapshotState()) } catch (_: Exception) {}
            }
            PairingProtocol.TYPE_STATIONS_REQ -> {
                conn.send(PairingProtocol.Stations(list = snapshotStations()))
            }
            PairingProtocol.TYPE_PING -> {
                val ping = PairingProtocol.decode<PairingProtocol.Ping>(line) ?: return
                conn.send(PairingProtocol.Pong(ts = ping.ts))
            }
            else -> { /* ignore unknown */ }
        }
    }

    // --- Client side: handle frames from outbound peer ---

    private fun handleClientMessage(line: String) {
        when (PairingProtocol.typeOf(line)) {
            PairingProtocol.TYPE_STATE -> {
                val state = PairingProtocol.decode<PairingProtocol.State>(line) ?: return
                _outboundPeerState.value = state
            }
            PairingProtocol.TYPE_STATIONS -> {
                val s = PairingProtocol.decode<PairingProtocol.Stations>(line) ?: return
                _outboundPeerStations.value = s.list
            }
            else -> { /* ignore */ }
        }
    }
}

/** Helper to map a [RadioStation] into the wire DTO. */
fun RadioStation.toDto(): PairingProtocol.StationDto = PairingProtocol.StationDto(
    id = id,
    name = name,
    streamUrl = streamUrl,
    genre = genre,
    country = country,
    favicon = favicon
)
