package com.radio.player.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.radio.player.data.AppDatabase
import com.radio.player.data.RadioStation
import com.radio.player.data.StationRepository
import kotlinx.coroutines.launch

class StationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StationRepository

    val allStations: LiveData<List<RadioStation>>
    val favoriteStations: LiveData<List<RadioStation>>

    val showFavoritesOnly = MutableLiveData(false)

    val displayedStations: MediatorLiveData<List<RadioStation>> = MediatorLiveData()

    init {
        val dao = AppDatabase.getInstance(application).stationDao()
        repository = StationRepository(dao)

        allStations = repository.allStations
        favoriteStations = repository.favoriteStations

        displayedStations.addSource(allStations) { updateDisplayedStations() }
        displayedStations.addSource(favoriteStations) { updateDisplayedStations() }
        displayedStations.addSource(showFavoritesOnly) { updateDisplayedStations() }
    }

    private fun updateDisplayedStations() {
        val favsOnly = showFavoritesOnly.value ?: false
        displayedStations.value = if (favsOnly) {
            favoriteStations.value ?: emptyList()
        } else {
            allStations.value ?: emptyList()
        }
    }

    fun addStation(station: RadioStation) {
        viewModelScope.launch {
            repository.insert(station)
        }
    }

    fun updateStation(station: RadioStation) {
        viewModelScope.launch {
            repository.update(station)
        }
    }

    fun deleteStation(station: RadioStation) {
        viewModelScope.launch {
            repository.delete(station)
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
        }
    }

    fun toggleFilter() {
        showFavoritesOnly.value = !(showFavoritesOnly.value ?: false)
    }
}