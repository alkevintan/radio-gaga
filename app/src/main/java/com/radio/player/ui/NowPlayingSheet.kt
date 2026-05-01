package com.radio.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.radio.player.R
import com.radio.player.databinding.SheetNowPlayingBinding
import com.radio.player.data.RadioStation
import com.radio.player.service.RadioPlaybackService
import com.radio.player.viewmodel.StationViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NowPlayingSheet : BottomSheetDialogFragment() {

    private var _binding: SheetNowPlayingBinding? = null
    private val binding get() = _binding!!

    private var radioService: RadioPlaybackService? = null
    private var stationId: Long = -1L
    private var elapsedTickerJob: Job? = null

    companion object {
        fun newInstance(station: RadioStation): NowPlayingSheet {
            return NowPlayingSheet().apply {
                arguments = Bundle().apply {
                    putLong("id", station.id)
                    putString("name", station.name)
                    putString("streamUrl", station.streamUrl)
                    putString("homepage", station.homepage)
                    putString("genre", station.genre)
                    putString("country", station.country)
                    putFloat("volumeGain", station.volumeGain)
                }
            }
        }
    }

    fun setRadioService(service: RadioPlaybackService?) {
        radioService = service
    }

    var onEditClick: ((RadioStation) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val station = RadioStation(
            id = args.getLong("id"),
            name = args.getString("name") ?: "",
            streamUrl = args.getString("streamUrl") ?: "",
            homepage = args.getString("homepage") ?: "",
            genre = args.getString("genre") ?: "",
            country = args.getString("country") ?: "",
            volumeGain = args.getFloat("volumeGain", 0f)
        )
        stationId = station.id

        populateStationInfo(station)
        setupControls()
        setupVolumeSlider(station.volumeGain)
        observePlaybackState()
        startElapsedTicker()

        val isCurrentlyPlaying = radioService?.isPlaying?.value ?: false
        binding.sheetSpectrumVisualizer.setPlaying(isCurrentlyPlaying)
    }

    private fun populateStationInfo(station: RadioStation) {
        binding.sheetStationName.text = station.name
        binding.sheetStreamUrl.text = station.streamUrl

        binding.sheetStreamUrlRow.visibility =
            if (com.radio.player.util.SettingsManager.isShowStreamUrls(requireContext())) View.VISIBLE else View.GONE

        if (station.genre.isNotBlank()) {
            binding.sheetStationGenre.text = station.genre
            binding.sheetGenreRow.visibility = View.VISIBLE
            binding.sheetGenre.text = station.genre
        } else {
            binding.sheetStationGenre.text = station.country.ifBlank { "Radio Station" }
        }

        if (station.homepage.isNotBlank()) {
            binding.sheetHomepageRow.visibility = View.VISIBLE
            binding.sheetHomepage.text = station.homepage
            binding.sheetHomepage.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(station.homepage))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Cannot open URL", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (station.country.isNotBlank()) {
            binding.sheetCountryRow.visibility = View.VISIBLE
            binding.sheetCountry.text = station.country
        }

        binding.sheetEditButton.setOnClickListener {
            onEditClick?.invoke(station)
            dismiss()
        }
    }

    private fun setupControls() {
        binding.sheetPlayPauseButton.setOnClickListener {
            val service = radioService ?: return@setOnClickListener
            if (service.isPlaying.value) {
                service.pause()
            } else {
                service.play()
            }
        }

        binding.sheetStopButton.setOnClickListener {
            radioService?.stopPlayback()
            dismiss()
        }
    }

    private fun setupVolumeSlider(initialGain: Float) {
        binding.sheetVolumeSlider.value = initialGain.coerceIn(-12f, 12f)
        updateVolumeLabel(binding.sheetVolumeSlider.value)

        binding.sheetVolumeSlider.addOnChangeListener { _, value, fromUser ->
            updateVolumeLabel(value)
            if (fromUser) {
                radioService?.setLiveVolumeGain(value)
            }
        }
        binding.sheetVolumeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val vm = ViewModelProvider(requireActivity())[StationViewModel::class.java]
                vm.setVolumeGain(stationId, slider.value)
            }
        })
    }

    private fun updateVolumeLabel(gain: Float) {
        val rounded = gain.toInt()
        val sign = if (rounded > 0) "+" else ""
        binding.sheetVolumeLabel.text = "$sign$rounded dB"
    }

    private fun startElapsedTicker() {
        elapsedTickerJob?.cancel()
        elapsedTickerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                updateElapsedText()
                delay(1000)
            }
        }
    }

    private fun updateElapsedText() {
        val view = _binding?.sheetElapsedTime ?: return
        val start = radioService?.playStartTime?.value ?: 0L
        val playing = radioService?.isPlaying?.value ?: false
        if (start <= 0L || !playing) {
            view.text = if (start > 0L) formatElapsed(System.currentTimeMillis() - start) + " (paused)" else ""
            return
        }
        view.text = formatElapsed(System.currentTimeMillis() - start)
    }

    private fun formatElapsed(millis: Long): String {
        val sec = (millis / 1000).coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            radioService?.isPlaying?.collect { playing ->
                _binding?.sheetPlayPauseButton?.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
                _binding?.sheetSpectrumVisualizer?.setPlaying(playing)
            }
        }

        lifecycleScope.launch {
            radioService?.isBuffering?.collect { buffering ->
                _binding?.sheetBufferingIndicator?.visibility =
                    if (buffering) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        elapsedTickerJob?.cancel()
        elapsedTickerJob = null
        _binding?.sheetSpectrumVisualizer?.setPlaying(false)
        super.onDestroyView()
        _binding = null
    }
}
