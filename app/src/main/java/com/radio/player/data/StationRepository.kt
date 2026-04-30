package com.radio.player.data

import androidx.lifecycle.LiveData

class StationRepository(private val dao: StationDao) {

    val allStations: LiveData<List<RadioStation>> = dao.getAllStations()
    val favoriteStations: LiveData<List<RadioStation>> = dao.getFavoriteStations()

    suspend fun insert(station: RadioStation): Long = dao.insert(station)

    suspend fun update(station: RadioStation) = dao.update(station)

    suspend fun delete(station: RadioStation) = dao.delete(station)

    suspend fun toggleFavorite(id: Long) {
        val station = dao.getStationById(id) ?: return
        dao.setFavorite(id, !station.isFavorite)
    }

    suspend fun getById(id: Long): RadioStation? = dao.getStationById(id)
}