package com.radio.player.data

import androidx.lifecycle.LiveData
import com.radio.player.util.SettingsManager

class StationRepository(private val dao: StationDao) {

    private var currentSort = SettingsManager.SortOrder.DATE_ADDED

    fun setSortOrder(order: SettingsManager.SortOrder) {
        currentSort = order
    }

    fun getSortedStations(): LiveData<List<RadioStation>> = when (currentSort) {
        SettingsManager.SortOrder.NAME_ASC -> dao.getAllStationsByNameAsc()
        SettingsManager.SortOrder.NAME_DESC -> dao.getAllStationsByNameDesc()
        SettingsManager.SortOrder.DATE_ADDED -> dao.getAllStationsByDateAdded()
        SettingsManager.SortOrder.GENRE -> dao.getAllStationsByGenre()
        SettingsManager.SortOrder.COUNTRY -> dao.getAllStationsByCountry()
        SettingsManager.SortOrder.MANUAL -> dao.getAllStations()
    }

    val favoriteStations: LiveData<List<RadioStation>> = dao.getFavoriteStations()

    suspend fun insert(station: RadioStation): Long = dao.insert(station)

    suspend fun insertAll(stations: List<RadioStation>): List<Long> = dao.insertAll(stations)

    suspend fun update(station: RadioStation) = dao.update(station)

    suspend fun delete(station: RadioStation) = dao.delete(station)

    suspend fun toggleFavorite(id: Long) {
        val station = dao.getStationById(id) ?: return
        dao.setFavorite(id, !station.isFavorite)
    }

    suspend fun reorder(reordered: List<RadioStation>) {
        val withOrders = reordered.mapIndexed { index, st -> st.copy(order = index) }
        dao.updateAll(withOrders)
    }

    suspend fun setVolumeGain(id: Long, gain: Float) = dao.setVolumeGain(id, gain)

    suspend fun getById(id: Long): RadioStation? = dao.getStationById(id)
}