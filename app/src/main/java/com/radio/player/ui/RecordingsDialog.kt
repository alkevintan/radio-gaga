package com.radio.player.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R
import com.radio.player.util.RecordingManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsDialog : DialogFragment() {

    private lateinit var adapter: RecordingsAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_recordings, null)
        val list = view.findViewById<RecyclerView>(R.id.recordingsList)
        val empty = view.findViewById<TextView>(R.id.recordingsEmpty)

        adapter = RecordingsAdapter(
            onPlay = { file -> playFile(file) },
            onDelete = { file -> confirmDelete(file) }
        )
        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = adapter

        refresh(empty)

        return MaterialAlertDialogBuilder(ctx)
            .setTitle("Recordings")
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun refresh(emptyView: TextView) {
        val files = RecordingManager.listRecordings(requireContext())
        adapter.submit(files)
        emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.updates",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Play recording"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete recording")
            .setMessage(file.name)
            .setPositiveButton("Delete") { _, _ ->
                if (file.delete()) {
                    val empty = dialog?.findViewById<TextView>(R.id.recordingsEmpty)
                    if (empty != null) refresh(empty)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class RecordingsAdapter(
        private val onPlay: (File) -> Unit,
        private val onDelete: (File) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

        private val items = mutableListOf<File>()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        fun submit(list: List<File>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.name.text = f.name
            val sizeKb = f.length() / 1024
            val sizeText = if (sizeKb >= 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
            holder.meta.text = "$sizeText  ·  ${dateFormat.format(Date(f.lastModified()))}"
            holder.itemView.setOnClickListener { onPlay(f) }
            holder.delete.setOnClickListener { onDelete(f) }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.recordingName)
            val meta: TextView = v.findViewById(R.id.recordingMeta)
            val delete: ImageButton = v.findViewById(R.id.recordingDelete)
        }
    }
}
