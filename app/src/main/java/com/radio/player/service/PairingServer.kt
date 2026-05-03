package com.radio.player.service

import android.util.Log
import com.radio.player.util.PairingManager
import com.radio.player.util.PairingProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Lightweight TCP listener that accepts inbound pairing connections.
 *
 * On accept the server waits for a HELLO frame, validates the token against the locally
 * stored peer credentials, and either replies with HELLO_ACK or closes the socket. Each
 * accepted connection runs its own read coroutine; outbound writes go through the
 * connection's [Conn] handle.
 */
class PairingServer(
    private val context: android.content.Context,
    private val onMessage: (Conn, String) -> Unit,
    private val onConnected: (Conn) -> Unit,
    private val onDisconnected: (Conn) -> Unit,
    /** Fired when an inbound HELLO carrying a valid authProof saves a new peer. */
    private val onAutoPaired: () -> Unit = {}
) {

    companion object { private const val TAG = "PairingServer" }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conns = Collections.synchronizedList(mutableListOf<Conn>())

    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = serverSocket?.isClosed == false

    fun start(): Int {
        if (isRunning) return port
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(0))
        serverSocket = ss
        acceptJob = scope.launch {
            while (isActive) {
                val socket = try { ss.accept() } catch (_: Exception) { break }
                scope.launch { handle(socket) }
            }
        }
        return ss.localPort
    }

    fun stop() {
        acceptJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        synchronized(conns) {
            conns.toList().forEach { it.close() }
            conns.clear()
        }
    }

    /** Broadcast a message to every authenticated connection. */
    fun broadcast(msg: Any) {
        val line = PairingProtocol.encode(msg)
        synchronized(conns) {
            conns.toList().forEach { it.sendRaw(line) }
        }
    }

    private fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        val out = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

        // Authenticate first frame.
        val firstLine = try { reader.readLine() } catch (_: Exception) { null }
        if (firstLine == null) { closeQuiet(socket); return }
        val type = PairingProtocol.typeOf(firstLine)
        if (type != PairingProtocol.TYPE_HELLO) { closeQuiet(socket); return }
        val hello = PairingProtocol.decode<PairingProtocol.Hello>(firstLine) ?: run {
            closeQuiet(socket); return
        }

        // Auth: accept either a known peer (reconnect) OR a fresh authProof carrying our
        // own local token (proof the client saw our pair QR). On authProof, auto-save the
        // client as our peer so future reconnects match the known-peer branch.
        val stored = PairingManager.peer(context)
        val matchesStored = stored != null &&
            stored.deviceId == hello.selfDeviceId &&
            stored.token == hello.selfToken
        val proofValid = hello.authProof != null &&
            hello.authProof == PairingManager.localToken(context)
        val ok = matchesStored || proofValid

        var autoPaired = false
        if (ok && !matchesStored) {
            PairingManager.setPeer(
                context,
                PairingManager.Peer(
                    deviceId = hello.selfDeviceId,
                    token = hello.selfToken,
                    name = hello.selfName
                )
            )
            autoPaired = true
        }

        val ack = PairingProtocol.HelloAck(
            deviceId = PairingManager.localDeviceId(context),
            name = PairingManager.localName(context),
            ok = ok,
            pairedBack = ok,
            error = if (ok) null else "unauthorized"
        )
        try {
            out.write(PairingProtocol.encode(ack).toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (_: Exception) { closeQuiet(socket); return }

        if (!ok) { closeQuiet(socket); return }

        if (autoPaired) onAutoPaired()

        val conn = Conn(socket, out, hello.selfDeviceId, hello.selfName)
        conns.add(conn)
        onConnected(conn)

        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                onMessage(conn, line)
            }
        } catch (e: Exception) {
            Log.d(TAG, "conn read ended: ${e.message}")
        } finally {
            conns.remove(conn)
            conn.close()
            onDisconnected(conn)
        }
    }

    private fun closeQuiet(s: Socket) {
        try { s.close() } catch (_: Exception) {}
    }

    inner class Conn(
        private val socket: Socket,
        private val out: OutputStream,
        val peerDeviceId: String,
        val peerName: String
    ) {
        @Synchronized
        fun send(msg: Any) {
            sendRaw(PairingProtocol.encode(msg))
        }

        @Synchronized
        fun sendRaw(line: String) {
            try {
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
                close()
            }
        }

        fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
