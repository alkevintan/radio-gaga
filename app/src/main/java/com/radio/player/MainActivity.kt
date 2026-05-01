package com.radio.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.radio.player.data.RadioStation
import com.radio.player.databinding.ActivityMainBinding
import com.radio.player.service.RadioPlaybackService
import com.radio.player.ui.NowPlayingSheet
import com.radio.player.ui.StationAdapter
import com.radio.player.ui.StationDialog
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
            onLongClick = { station -> showEditDialog(station) }
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
            showAddDialog()
        }
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
                }
            }
        }

        lifecycleScope.launch {
            radioService?.isPlaying?.collect { playing ->
                binding.playPauseButton.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
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
            R.id.action_add_preset -> {
                showAddPresetDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
}