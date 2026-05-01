package com.radio.player.ui

import android.app.Dialog
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
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

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val icons = listOf(
            view.findViewById<ImageView>(R.id.iconOptionA),
            view.findViewById<ImageView>(R.id.iconOptionB),
            view.findViewById<ImageView>(R.id.iconOptionC),
            view.findViewById<ImageView>(R.id.iconOptionD)
        )
        val labels = listOf("A: Radio Tower", "B: Play + Waves", "C: Headphones", "D: Tuner Dial")

        icons.forEachIndexed { index, iv ->
            iv.setOnClickListener {
                Toast.makeText(requireContext(), "Selected: ${labels[index]}", Toast.LENGTH_SHORT).show()
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("About Radio Player $version")
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }
}