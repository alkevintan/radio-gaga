package com.radio.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.radio.player.R
import com.radio.player.databinding.SheetNowPlayingBinding
import com.radio.player.data.RadioStation
import com.radio.player.service.RadioPlaybackService
import kotlinx.coroutines.launch

class NowPlayingSheet : BottomSheetDialogFragment() {

    private var _binding: SheetNowPlayingBinding? = null
    private val binding get() = _binding!!

    private var radioService: RadioPlaybackService? = null

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
            country = args.getString("country") ?: ""
        )

        populateStationInfo(station)
        setupControls()
        observePlaybackState()
    }

    private fun populateStationInfo(station: RadioStation) {
        binding.sheetStationName.text = station.name
        binding.sheetStreamUrl.text = station.streamUrl

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

    private fun observePlaybackState() {
        lifecycleScope.launch {
            radioService?.isPlaying?.collect { playing ->
                _binding?.sheetPlayPauseButton?.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
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
        super.onDestroyView()
        _binding = null
    }
}