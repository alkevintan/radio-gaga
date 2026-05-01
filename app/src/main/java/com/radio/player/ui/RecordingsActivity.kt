package com.radio.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R
import com.radio.player.databinding.ActivityRecordingsBinding
import com.radio.player.util.RecordingManager
import com.radio.player.util.SettingsManager
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var adapter: RecordingsAdapter
    private var pendingExportFile: File? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/*")
    ) { uri ->
        uri?.let { exportTo(it) }
        pendingExportFile = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = SettingsManager.getTheme(this)
        if (theme == SettingsManager.Theme.FREDDIE_WEMBLEY) {
            setTheme(R.style.Theme_RadioPlayer_FreddieWembley)
        }
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.recordingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.recordingsToolbar.setNavigationOnClickListener { finish() }

        adapter = RecordingsAdapter(
            onPlay = { playFile(it) },
            onMenu = { anchor, file -> showPopup(anchor, file) }
        )
        binding.recordingsList.layoutManager = LinearLayoutManager(this)
        binding.recordingsList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val files = RecordingManager.listRecordings(this)
        adapter.submit(files)
        binding.recordingsEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playFile(file: File) {
        try {
            val uri = providerUri(file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Play recording"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = providerUri(file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("audio/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Share recording"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startExport(file: File) {
        pendingExportFile = file
        try {
            exportLauncher.launch(file.name)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
            pendingExportFile = null
        }
    }

    private fun exportTo(destUri: Uri) {
        val src = pendingExportFile ?: return
        try {
            contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(src).use { it.copyTo(out) }
            } ?: throw IllegalStateException("Cannot open destination")
            Toast.makeText(this, "Exported ${src.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete recording")
            .setMessage(file.name)
            .setPositiveButton("Delete") { _, _ ->
                if (file.delete()) {
                    refresh()
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPopup(anchor: View, file: File) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Play")
        popup.menu.add(0, 2, 1, "Share")
        popup.menu.add(0, 3, 2, "Export…")
        popup.menu.add(0, 4, 3, "Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { playFile(file); true }
                2 -> { shareFile(file); true }
                3 -> { startExport(file); true }
                4 -> { confirmDelete(file); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun providerUri(file: File): Uri =
        FileProvider.getUriForFile(this, "${packageName}.updates", file)

    private class RecordingsAdapter(
        private val onPlay: (File) -> Unit,
        private val onMenu: (anchor: View, file: File) -> Unit
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
            holder.menu.setOnClickListener { onMenu(it, f) }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.recordingName)
            val meta: TextView = v.findViewById(R.id.recordingMeta)
            val menu: ImageButton = v.findViewById(R.id.recordingMenu)
        }
    }
}
