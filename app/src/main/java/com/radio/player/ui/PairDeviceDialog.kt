package com.radio.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.radio.player.R
import com.radio.player.util.PairPayload
import com.radio.player.util.PairingManager
import com.radio.player.util.QrCodeGenerator

/**
 * Bottom sheet that drives device pairing. Always shows the local pair QR (so the other
 * device can scan it); a Scan button opens the QR scanner to read a peer's code; an Unpair
 * button is shown only when a peer is currently configured.
 *
 * Pairing is committed by the host activity (see [onPaired]) so it can also kick the
 * playback service to refresh discovery.
 */
class PairDeviceDialog : BottomSheetDialogFragment() {

    /** Set by host activity. Called when the user scans a peer's QR; activity persists it. */
    var onScanRequested: (() -> Unit)? = null
    var onUnpair: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_pair_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderState(view)

        view.findViewById<MaterialButton>(R.id.pairScanButton).setOnClickListener {
            onScanRequested?.invoke()
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.pairUnpairButton).setOnClickListener {
            onUnpair?.invoke()
            renderState(view)
            Toast.makeText(requireContext(), "Device unpaired", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderState(view: View) {
        val ctx = requireContext()
        val deviceId = PairingManager.localDeviceId(ctx)
        val token = PairingManager.localToken(ctx)
        val name = PairingManager.localName(ctx)
        val qrPayload = PairPayload.encode(deviceId, token, name)

        try {
            val qr = QrCodeGenerator.generateBitmap(qrPayload, 512)
            view.findViewById<ImageView>(R.id.pairQrImage).setImageBitmap(qr)
        } catch (_: Exception) {
            view.findViewById<ImageView>(R.id.pairQrImage).setImageDrawable(null)
        }

        val peer = PairingManager.peer(ctx)
        val statusView = view.findViewById<TextView>(R.id.pairStatus)
        val unpairButton = view.findViewById<MaterialButton>(R.id.pairUnpairButton)
        if (peer != null) {
            statusView.text = getString(R.string.paired_with, peer.name.ifBlank { "Device" })
            unpairButton.visibility = View.VISIBLE
        } else {
            statusView.text = getString(R.string.not_paired)
            unpairButton.visibility = View.GONE
        }
    }
}
