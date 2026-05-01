package com.radio.player.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.radio.player.data.AppDatabase
import com.radio.player.data.RadioStation
import com.radio.player.data.StationRepository
import com.radio.player.util.SettingsManager
import kotlinx.coroutines.launch

class StationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StationRepository
    private val dao = AppDatabase.getInstance(application).stationDao()

    private val _sortOrder = MutableLiveData(SettingsManager.SortOrder.DATE_ADDED)
    val sortOrder: LiveData<SettingsManager.SortOrder> = _sortOrder

    val favoriteStations: LiveData<List<RadioStation>> = dao.getFavoriteStations()

    val showFavoritesOnly = MutableLiveData(false)

    val genres: LiveData<List<String>> = dao.getDistinctGenres()

    private val searchQuery = MutableLiveData("")

    private val selectedGenre = MutableLiveData<String?>(null)

    private var sortedStations: LiveData<List<RadioStation>> = dao.getAllStationsByDateAdded()

    val displayedStations: MediatorLiveData<List<RadioStation>> = MediatorLiveData()

    init {
        val settingsOrder = SettingsManager.getSortOrder(application)
        _sortOrder.value = settingsOrder
        repository = StationRepository(dao)
        repository.setSortOrder(settingsOrder)
        sortedStations = repository.getSortedStations()

        displayedStations.addSource(sortedStations) { updateDisplayedStations() }
        displayedStations.addSource(favoriteStations) { updateDisplayedStations() }
        displayedStations.addSource(showFavoritesOnly) { updateDisplayedStations() }
        displayedStations.addSource(selectedGenre) { updateDisplayedStations() }
        displayedStations.addSource(searchQuery) { updateDisplayedStations() }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query.trim()
    }

    private fun updateDisplayedStations() {
        val favsOnly = showFavoritesOnly.value ?: false
        val genre = selectedGenre.value
        val query = searchQuery.value ?: ""

        var base = if (favsOnly) {
            favoriteStations.value ?: emptyList()
        } else {
            sortedStations.value ?: emptyList()
        }

        if (genre != null) {
            base = base.filter { it.genre.equals(genre, ignoreCase = true) }
        }

        if (query.isNotEmpty()) {
            base = base.filter { it.name.contains(query, ignoreCase = true) }
        }

        displayedStations.value = base
    }

    fun setSelectedGenre(genre: String?) {
        selectedGenre.value = genre
    }

    fun getSelectedGenre(): String? = selectedGenre.value

    fun setSortOrder(order: SettingsManager.SortOrder) {
        _sortOrder.value = order
        SettingsManager.setSortOrder(getApplication(), order)
        repository.setSortOrder(order)
        displayedStations.removeSource(sortedStations)
        sortedStations = repository.getSortedStations()
        displayedStations.addSource(sortedStations) { updateDisplayedStations() }
        updateDisplayedStations()
    }

    fun addStation(station: RadioStation) {
        viewModelScope.launch {
            repository.insert(station)
        }
    }

    fun addStations(stations: List<RadioStation>) {
        viewModelScope.launch {
            repository.insertAll(stations)
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

    fun reorderStations(reordered: List<RadioStation>) {
        viewModelScope.launch {
            repository.reorder(reordered)
        }
    }

    fun setVolumeGain(stationId: Long, gain: Float) {
        viewModelScope.launch {
            repository.setVolumeGain(stationId, gain)
        }
    }
}