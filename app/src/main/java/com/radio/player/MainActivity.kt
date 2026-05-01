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
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
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
import com.radio.player.ui.UpdateDialog
import com.radio.player.util.M3uHelper
import com.radio.player.util.SettingsManager
import com.radio.player.util.UpdateChecker
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
    private var filterMenuItem: MenuItem? = null
    private var searchMenuItem: MenuItem? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var dragInProgress = false
    private var orderChangedDuringDrag = false

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

    private var lastThemeKey: String? = null

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
        applyTheme()
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
        setupGenreTabs()
        observeStations()

        startAndBindService()
        maybeAutoCheckUpdate()
    }

    private fun maybeAutoCheckUpdate() {
        if (!SettingsManager.isAutoUpdateCheck(this)) return
        val owner = getString(R.string.update_github_owner)
        val repo = getString(R.string.update_github_repo)
        if (owner.isBlank() || repo.isBlank()) return

        val last = SettingsManager.getLastUpdateCheck(this)
        val now = System.currentTimeMillis()
        if (now - last < 24L * 60 * 60 * 1000) return

        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: return
        } catch (_: Exception) { return }

        lifecycleScope.launch {
            val release = try { UpdateChecker.fetchLatest(this@MainActivity) } catch (_: Exception) { null }
            SettingsManager.setLastUpdateCheck(this@MainActivity, System.currentTimeMillis())
            if (release != null && UpdateChecker.isNewer(release, currentVersion)) {
                if (!isFinishing && !isDestroyed) {
                    UpdateDialog.show(this@MainActivity, release, currentVersion)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentKey = SettingsManager.getTheme(this).name
        if (lastThemeKey != null && lastThemeKey != currentKey) {
            recreate()
            return
        }
        lastThemeKey = currentKey
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter(
            onStationClick = { station -> playStation(station) },
            onFavoriteClick = { station -> stationViewModel.toggleFavorite(station.id) },
            onLongClick = { station -> showEditDialog(station) },
            onShareClick = { station -> showShareQr(station) },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )
        binding.stationList.adapter = adapter
        binding.stationList.layoutManager = LinearLayoutManager(this)

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                return if (canReorder()) super.getMovementFlags(rv, vh) else 0
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.moveItem(from, to)
                orderChangedDuringDrag = true
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dragInProgress = true
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (dragInProgress && orderChangedDuringDrag) {
                    stationViewModel.reorderStations(adapter.snapshotOrder())
                }
                dragInProgress = false
                orderChangedDuringDrag = false
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.stationList)

        binding.swipeRefresh.setOnRefreshListener {
            stationViewModel.showFavoritesOnly.value = stationViewModel.showFavoritesOnly.value ?: false
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun canReorder(): Boolean {
        val sort = stationViewModel.sortOrder.value ?: return false
        if (!sort.isManual) return false
        if (stationViewModel.showFavoritesOnly.value == true) return false
        if (stationViewModel.getSelectedGenre() != null) return false
        val query = (searchMenuItem?.actionView as? androidx.appcompat.widget.SearchView)?.query?.toString().orEmpty()
        if (query.isNotEmpty()) return false
        return true
    }

    private fun updateDragMode() {
        adapter.setDragHandlesVisible(canReorder())
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

        stationViewModel.genres.observe(this) { genres ->
            val tabLayout = binding.genreTabs
            val currentSelection = tabLayout.selectedTabPosition
            tabLayout.removeAllTabs()

            val allTab = tabLayout.newTab().setText("All")
            tabLayout.addTab(allTab)

            for (genre in genres) {
                tabLayout.addTab(tabLayout.newTab().setText(genre))
            }

            if (currentSelection >= 0 && currentSelection < tabLayout.tabCount) {
                tabLayout.selectTab(tabLayout.getTabAt(currentSelection))
            }

            tabLayout.visibility = if (genres.isEmpty()) View.GONE else View.VISIBLE
        }

        stationViewModel.showFavoritesOnly.observe(this) {
            updateFilterMenuIcon()
            updateDragMode()
        }

        stationViewModel.sortOrder.observe(this) { updateDragMode() }
    }

    private fun setupGenreTabs() {
        binding.genreTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val genre = if (tab.position == 0) null else tab.text?.toString()
                stationViewModel.setSelectedGenre(genre)
                updateDragMode()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
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

        filterMenuItem = menu.findItem(R.id.action_filter)
        searchMenuItem = menu.findItem(R.id.action_search)

        updateFilterMenuIcon()

        val searchView = searchMenuItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                stationViewModel.setSearchQuery(query ?: "")
                updateDragMode()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                stationViewModel.setSearchQuery(newText ?: "")
                updateDragMode()
                return true
            }
        })
        searchView?.setOnCloseListener {
            stationViewModel.setSearchQuery("")
            updateDragMode()
            false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                stationViewModel.toggleFilter()
                updateFilterMenuIcon()
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

    private fun updateFilterMenuIcon() {
        val isFavsOnly = stationViewModel.showFavoritesOnly.value ?: false
        filterMenuItem?.apply {
            setIcon(if (isFavsOnly) R.drawable.ic_favorite else R.drawable.ic_favorite_off)
            title = if (isFavsOnly) "Show All" else "Favorites"
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

    private fun applyTheme() {
        val theme = SettingsManager.getTheme(this)
        if (theme == SettingsManager.Theme.FREDDIE_WEMBLEY) {
            setTheme(R.style.Theme_RadioPlayer_FreddieWembley)
        } else {
            when (theme) {
                SettingsManager.Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                SettingsManager.Theme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                SettingsManager.Theme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> {}
            }
        }
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