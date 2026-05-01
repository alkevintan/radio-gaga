package com.radio.player.ui

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R
import com.radio.player.util.SettingsManager

class SortDialog(
    private val currentSort: SettingsManager.SortOrder,
    private val onSortSelected: (SettingsManager.SortOrder) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = SettingsManager.SortOrder.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val currentIndex = options.indexOf(currentSort).coerceAtLeast(0)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                onSortSelected(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}