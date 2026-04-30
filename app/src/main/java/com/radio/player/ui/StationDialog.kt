package com.radio.player.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R
import com.radio.player.data.RadioStation
import com.radio.player.databinding.DialogStationBinding

class StationDialog private constructor(
    private val existingStation: RadioStation?,
    private val onSave: (RadioStation) -> Unit,
    private val onDelete: ((RadioStation) -> Unit)?
) : DialogFragment() {

    private var _binding: DialogStationBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            station: RadioStation? = null,
            onSave: (RadioStation) -> Unit,
            onDelete: ((RadioStation) -> Unit)? = null
        ): StationDialog {
            return StationDialog(station, onSave, onDelete)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStationBinding.inflate(layoutInflater)

        existingStation?.let {
            binding.inputName.setText(it.name)
            binding.inputStreamUrl.setText(it.streamUrl)
            binding.inputHomepage.setText(it.homepage)
            binding.inputGenre.setText(it.genre)
            binding.inputCountry.setText(it.country)
            binding.inputFavicon.setText(it.favicon)
        }

        val title = if (existingStation != null) "Edit Station" else "Add Station"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ -> saveStation() }
            .setNegativeButton("Cancel", null)

        if (existingStation != null && onDelete != null) {
            dialog.setNeutralButton("Delete") { _, _ ->
                onDelete.invoke(existingStation)
            }
        }

        return dialog.create()
    }

    private fun saveStation() {
        val name = binding.inputName.text.toString().trim()
        val streamUrl = binding.inputStreamUrl.text.toString().trim()

        if (name.isBlank()) {
            Toast.makeText(context, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (streamUrl.isBlank()) {
            Toast.makeText(context, "Stream URL is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.WEB_URL.matcher(streamUrl).matches()) {
            Toast.makeText(context, "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }

        val station = RadioStation(
            id = existingStation?.id ?: 0,
            name = name,
            streamUrl = streamUrl,
            homepage = binding.inputHomepage.text.toString().trim(),
            genre = binding.inputGenre.text.toString().trim(),
            country = binding.inputCountry.text.toString().trim(),
            favicon = binding.inputFavicon.text.toString().trim(),
            isFavorite = existingStation?.isFavorite ?: false,
            order = existingStation?.order ?: 0
        )

        onSave.invoke(station)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}