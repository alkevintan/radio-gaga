package com.radio.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.radio.player.R
import com.radio.player.service.PairingClient
import com.radio.player.service.PairingController
import com.radio.player.util.PairingProtocol
import kotlinx.coroutines.launch

/**
 * Pure remote controller surface: shows what the paired peer is playing and lets the user
 * drive transport + pick a station from the peer's library.
 *
 * Local playback is paused while this sheet is in front (handled by the host activity).
 *
 * The sheet observes [PairingController.outboundPeerState] (last STATE frame from the
 * peer) and [PairingController.outboundPeerStations] (response to STATIONS_REQ). Buttons
 * send CMD frames back through the controller.
 */
class RemoteSheet : BottomSheetDialogFragment() {

    var controller: PairingController? = null

    private var stationName: TextView? = null
    private var stationGenre: TextView? = null
    private var peerNameView: TextView? = null
    private var bufferingBar: ProgressBar? = null
    private var playPauseBtn: ImageButton? = null
    private var stopBtn: ImageButton? = null
    private var stationsBtn: ImageButton? = null
    private var offlineHint: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_remote, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stationName = view.findViewById(R.id.remoteStationName)
        stationGenre = view.findViewById(R.id.remoteStationGenre)
        peerNameView = view.findViewById(R.id.remotePeerName)
        bufferingBar = view.findViewById(R.id.remoteBuffering)
        playPauseBtn = view.findViewById(R.id.remotePlayPauseButton)
        stopBtn = view.findViewById(R.id.remoteStopButton)
        stationsBtn = view.findViewById(R.id.remoteStationsButton)
        offlineHint = view.findViewById(R.id.remoteOfflineHint)

        playPauseBtn?.setOnClickListener {
            val state = controller?.outboundPeerState?.value
            val action = if (state?.playing == true) PairingProtocol.ACTION_PAUSE else PairingProtocol.ACTION_PLAY
            controller?.sendCommand(action)
        }
        stopBtn?.setOnClickListener {
            controller?.sendCommand(PairingProtocol.ACTION_STOP)
        }
        stationsBtn?.setOnClickListener {
            controller?.requestPeerStations()
            showStationPicker()
        }

        observe()
    }

    private fun observe() {
        val ctrl = controller ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            ctrl.outboundPeerState.collect { state -> renderState(state) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ctrl.clientState.collect { cs -> renderConnection(cs) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ctrl.clientPeerName.collect { name -> peerNameView?.text = name }
        }
    }

    private fun renderState(state: PairingProtocol.State?) {
        if (state == null) {
            stationName?.text = "—"
            stationGenre?.text = ""
            bufferingBar?.visibility = View.GONE
            playPauseBtn?.setImageResource(R.drawable.ic_play)
            return
        }
        val s = state.station
        stationName?.text = s?.name ?: "—"
        stationGenre?.text = s?.genre?.ifBlank { s.country } ?: ""
        bufferingBar?.visibility = if (state.buffering) View.VISIBLE else View.GONE
        playPauseBtn?.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun renderConnection(cs: PairingClient.State) {
        val online = cs == PairingClient.State.CONNECTED
        offlineHint?.visibility = if (online) View.GONE else View.VISIBLE
        playPauseBtn?.isEnabled = online
        stopBtn?.isEnabled = online
        stationsBtn?.isEnabled = online
    }

    private fun showStationPicker() {
        val ctrl = controller ?: return
        val stations = ctrl.outboundPeerStations.value
        if (stations.isEmpty()) {
            // Wait briefly for stations to arrive — re-trigger picker on next emission.
            viewLifecycleOwner.lifecycleScope.launch {
                ctrl.outboundPeerStations.collect { list ->
                    if (list.isNotEmpty()) {
                        renderPicker(list)
                        return@collect
                    }
                }
            }
            return
        }
        renderPicker(stations)
    }

    private fun renderPicker(stations: List<PairingProtocol.StationDto>) {
        val ctx = context ?: return
        val labels = stations.map { it.name }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle("Play on peer")
            .setItems(labels) { _, which ->
                controller?.sendCommand(
                    PairingProtocol.ACTION_PLAY_STATION,
                    stationId = stations[which].id
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
