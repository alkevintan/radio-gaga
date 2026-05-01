package com.radio.player.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.radio.player.R
import com.radio.player.data.RadioStation
import com.radio.player.util.QrCodeGenerator

class ShareQrDialog : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(station: RadioStation): ShareQrDialog {
            return ShareQrDialog().apply {
                arguments = Bundle().apply {
                    putLong("id", station.id)
                    putString("name", station.name)
                    putString("streamUrl", station.streamUrl)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_share_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val name = args.getString("name") ?: ""
        val streamUrl = args.getString("streamUrl") ?: ""

        view.findViewById<TextView>(R.id.qrStationName).text = name
        view.findViewById<TextView>(R.id.qrStreamUrl).text = streamUrl

        val content = buildString {
            append(streamUrl)
            if (name.isNotBlank()) append("|").append(name)
        }

        try {
            val qrBitmap = QrCodeGenerator.generateBitmap(content, 512)
            view.findViewById<ImageView>(R.id.qrImageView).setImageBitmap(qrBitmap)
        } catch (_: Exception) {
            view.findViewById<ImageView>(R.id.qrImageView).setImageDrawable(null)
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.qrCopyButton).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", streamUrl))
            Toast.makeText(requireContext(), "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}