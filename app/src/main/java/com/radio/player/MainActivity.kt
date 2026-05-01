package com.radio.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.integration.android.IntentIntegrator
import com.radio.player.data.RadioStation
import com.radio.player.databinding.ActivityMainBinding
import com.radio.player.service.RadioPlaybackService
import com.radio.player.ui.AboutDialog
import com.radio.player.ui.NowPlayingSheet
import com.radio.player.ui.SettingsActivity
import com.radio.player.ui.ShareQrDialog
import com.radio.player.ui.SortDialog
import com.radio.player.ui.StationAdapter
import com.radio.player.ui.StationDialog
import com.radio.player.util.M3uHelper
import com.radio.player.util.SettingsManager
import com.radio.player.viewmodel.PlayerViewModel
import com.radio.player.viewmodel.StationViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var stationViewModel: StationViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var adapter: StationAdapter

    private var radioService: RadioPlaybackService? = null
    private var isBound = false
    private var isFabShifted = false
    private var isSpeedDialOpen = false

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importM3u(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mpegurl")) { uri ->
        uri?.let { exportM3u(it) }
    }

    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val content = scanResult?.contents
        if (!content.isNullOrBlank()) {
            handleQrResult(content)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioPlaybackService.RadioBinder
            radioService = binder.getService()
            playerViewModel.radioService = radioService
            isBound = true
            observePlayerState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
            playerViewModel.radioService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        stationViewModel = androidx.lifecycle.ViewModelProvider(this)[StationViewModel::class.java]
        playerViewModel = androidx.lifecycle.ViewModelProvider(this)[PlayerViewModel::class.java]

        val savedSort = SettingsManager.getSortOrder(this)
        stationViewModel.setSortOrder(savedSort)

        setupRecyclerView()
        setupFab()
        setupPlayerBar()
        observeStations()

        startAndBindService()
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter(
            onStationClick = { station -> playStation(station) },
            onFavoriteClick = { station -> stationViewModel.toggleFavorite(station.id) },
            onLongClick = { station -> showEditDialog(station) },
            onShareClick = { station -> showShareQr(station) }
        )
        binding.stationList.adapter = adapter
        binding.stationList.layoutManager = LinearLayoutManager(this)

        binding.swipeRefresh.setOnRefreshListener {
            stationViewModel.showFavoritesOnly.value = stationViewModel.showFavoritesOnly.value ?: false
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            toggleSpeedDial()
        }

        binding.speedDialScrim.setOnClickListener {
            closeSpeedDial()
        }

        binding.fabAddManual.setOnClickListener {
            closeSpeedDial()
            showAddDialog()
        }

        binding.fabScanQr.setOnClickListener {
            closeSpeedDial()
            launchQrScanner()
        }
    }

    private fun toggleSpeedDial() {
        if (isSpeedDialOpen) {
            closeSpeedDial()
        } else {
            openSpeedDial()
        }
    }

    private fun openSpeedDial() {
        isSpeedDialOpen = true
        binding.fabAdd.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        binding.speedDialContainer.visibility = View.VISIBLE
        binding.speedDialScrim.visibility = View.VISIBLE
        binding.speedDialAddManual.alpha = 0f
        binding.speedDialAddManual.animate().alpha(1f).setDuration(150).start()
        binding.speedDialScanQr.alpha = 0f
        binding.speedDialScanQr.animate().alpha(1f).setDuration(150).setStartDelay(50).start()
        binding.speedDialScrim.alpha = 0f
        binding.speedDialScrim.animate().alpha(1f).setDuration(200).start()
    }

    private fun closeSpeedDial() {
        isSpeedDialOpen = false
        binding.fabAdd.setImageResource(R.drawable.ic_add)
        binding.speedDialContainer.visibility = View.GONE
        binding.speedDialScrim.visibility = View.GONE
    }

    private fun launchQrScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan a radio station QR code")
        integrator.setBeepEnabled(true)
        integrator.setOrientationLocked(false)
        qrScanLauncher.launch(integrator.createScanIntent())
    }

    private fun handleQrResult(content: String) {
        var url = content
        var name = ""
        if (content.contains("|")) {
            val parts = content.split("|", limit = 2)
            url = parts[0].trim()
            name = parts[1].trim()
        }
        val prefill = RadioStation(
            name = name.ifBlank { "Scanned Station" },
            streamUrl = url
        )
        StationDialog.newInstance(
            station = prefill,
            onSave = { station -> stationViewModel.addStation(station) }
        ).show(supportFragmentManager, "add_station")
    }

    private fun setupPlayerBar() {
        binding.playerBar.setOnClickListener {
            val station = radioService?.currentStation?.value ?: return@setOnClickListener
            val sheet = NowPlayingSheet.newInstance(station)
            sheet.setRadioService(radioService)
            sheet.onEditClick = { s -> showEditDialog(s) }
            sheet.show(supportFragmentManager, "now_playing")
        }

        binding.playPauseButton.setOnClickListener {
            playerViewModel.togglePlayback()
        }

        binding.stopButton.setOnClickListener {
            playerViewModel.stopPlayback()
            binding.playerBar.visibility = View.GONE
            binding.spectrumVisualizer.setPlaying(false)
            animateFabForPlayerBar(false)
        }
    }

    private fun observeStations() {
        stationViewModel.displayedStations.observe(this) { stations ->
            adapter.submitList(stations)
            binding.emptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            radioService?.currentStation?.collect { station ->
                station?.let {
                    binding.playerBar.visibility = View.VISIBLE
                    binding.nowPlayingName.text = it.name
                    animateFabForPlayerBar(true)
                }
            }
        }

        lifecycleScope.launch {
            radioService?.isPlaying?.collect { playing ->
                binding.playPauseButton.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
                binding.spectrumVisualizer.setPlaying(playing)
            }
        }

        lifecycleScope.launch {
            radioService?.isBuffering?.collect { buffering ->
                binding.bufferingIndicator.visibility = if (buffering) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            radioService?.isError?.collect { error ->
                if (error) {
                    val msg = radioService?.errorMessage?.value ?: "Playback error"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playStation(station: RadioStation) {
        binding.playerBar.visibility = View.VISIBLE
        binding.nowPlayingName.text = station.name
        animateFabForPlayerBar(true)
        binding.spectrumVisualizer.setPlaying(true)
        adapter.setCurrentlyPlaying(station.id)
        playerViewModel.playStation(station)
    }

    private fun showAddDialog() {
        StationDialog.newInstance(
            onSave = { station ->
                stationViewModel.addStation(station)
            }
        ).show(supportFragmentManager, "add_station")
    }

    private fun showEditDialog(station: RadioStation) {
        StationDialog.newInstance(
            station = station,
            onSave = { updatedStation ->
                stationViewModel.updateStation(updatedStation)
            },
            onDelete = { deletedStation ->
                stationViewModel.deleteStation(deletedStation)
                if (radioService?.currentStation?.value?.id == deletedStation.id) {
                    playerViewModel.stopPlayback()
                    binding.playerBar.visibility = View.GONE
                    binding.spectrumVisualizer.setPlaying(false)
                    animateFabForPlayerBar(false)
                }
            }
        ).show(supportFragmentManager, "edit_station")
    }

    private fun startAndBindService() {
        val intent = Intent(this, RadioPlaybackService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                stationViewModel.toggleFilter()
                val showingFavs = stationViewModel.showFavoritesOnly.value ?: false
                item.title = if (showingFavs) "Show All" else "Favorites"
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_add_preset -> {
                showAddPresetDialog()
                true
            }
            R.id.action_import_m3u -> {
                importLauncher.launch(arrayOf("*/*"))
                true
            }
            R.id.action_export_m3u -> {
                exportLauncher.launch("stations.m3u")
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                AboutDialog().show(supportFragmentManager, "about")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val currentSort = stationViewModel.sortOrder.value ?: SettingsManager.SortOrder.DATE_ADDED
        SortDialog(currentSort) { order ->
            stationViewModel.setSortOrder(order)
        }.show(supportFragmentManager, "sort")
    }

    private fun importM3u(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val content = stream.bufferedReader().readText()
                val stations = M3uHelper.importM3u(content)
                if (stations.isEmpty()) {
                    Toast.makeText(this, "No stations found in file", Toast.LENGTH_SHORT).show()
                } else {
                    stationViewModel.addStations(stations)
                    Toast.makeText(this, "Imported ${stations.size} station(s)", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportM3u(uri: Uri) {
        try {
            val stations = stationViewModel.displayedStations.value ?: emptyList()
            if (stations.isEmpty()) {
                Toast.makeText(this, "No stations to export", Toast.LENGTH_SHORT).show()
                return
            }
            val content = M3uHelper.exportM3u(stations)
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray())
            }
            Toast.makeText(this, "Exported ${stations.size} station(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showShareQr(station: RadioStation) {
        ShareQrDialog.newInstance(station).show(supportFragmentManager, "share_qr")
    }

    private fun showAddPresetDialog() {
        val presets = listOf(
            "BBC World Service" to "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            "NPR" to "https://npr-ice.streamguys1.com/live.mp3",
            "Jazz FM" to "https://edge-bauermz-01-gos2.sharp-stream.com/jazzfm.mp3",
            "Classic FM" to "https://media-ice.musicradio.com/ClassicFMMP3",
            "KEXP" to "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"
        )

        val names = presets.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Preset Station")
            .setItems(names) { _, which ->
                val (name, url) = presets[which]
                stationViewModel.addStation(RadioStation(name = name, streamUrl = url))
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun animateFabForPlayerBar(visible: Boolean) {
        if (visible && !isFabShifted) {
            binding.playerBar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.playerBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val translation = binding.playerBar.height +
                        (binding.playerBar.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
                    binding.fabAdd.animate()
                        .translationY(-translation.toFloat())
                        .setDuration(250)
                        .start()
                    binding.speedDialContainer.animate()
                        .translationY(-translation.toFloat())
                        .setDuration(250)
                        .start()
                }
            })
            isFabShifted = true
        } else if (!visible && isFabShifted) {
            binding.fabAdd.animate()
                .translationY(0f)
                .setDuration(250)
                .start()
            binding.speedDialContainer.animate()
                .translationY(0f)
                .setDuration(250)
                .start()
            isFabShifted = false
        }
    }

    override fun onBackPressed() {
        if (isSpeedDialOpen) {
            closeSpeedDial()
        } else {
            super.onBackPressed()
        }
    }
}