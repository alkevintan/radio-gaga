package com.radio.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.radio.player.data.RadioStation
import com.radio.player.service.RadioPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val emptyStation: MutableStateFlow<RadioStation?> = MutableStateFlow(null)
    private val emptyBool: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val emptyString: MutableStateFlow<String> = MutableStateFlow("")

    var radioService: RadioPlaybackService? = null

    val currentStation: StateFlow<RadioStation?>
        get() = radioService?.currentStation ?: emptyStation

    val isPlaying: StateFlow<Boolean>
        get() = radioService?.isPlaying ?: emptyBool

    val isBuffering: StateFlow<Boolean>
        get() = radioService?.isBuffering ?: emptyBool

    val isError: StateFlow<Boolean>
        get() = radioService?.isError ?: emptyBool

    val errorMessage: StateFlow<String>
        get() = radioService?.errorMessage ?: emptyString

    fun playStation(station: RadioStation) {
        radioService?.playStation(station)
    }

    fun togglePlayback() {
        val service = radioService ?: return
        if (service.isPlaying.value) {
            service.pause()
        } else {
            service.play()
        }
    }

    fun stopPlayback() {
        radioService?.stopPlayback()
    }
}