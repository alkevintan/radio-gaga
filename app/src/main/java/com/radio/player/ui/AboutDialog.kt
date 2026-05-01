package com.radio.player.ui

import android.app.Dialog
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R

class AboutDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val version = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            "v${pInfo.versionName}"
        } catch (_: Exception) {
            "v1.0"
        }

        val message = """
            Radio Player $version

            A simple internet radio streaming app for Android.

            • Stream MP3, AAC, and HLS radio stations
            • Manage and organize your favorite stations
            • Import/export M3U playlists
            • Share stations via QR code

            Built with ExoPlayer, Room, and Material Design.
        """.trimIndent()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }
}