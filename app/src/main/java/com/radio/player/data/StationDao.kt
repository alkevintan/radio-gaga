package com.radio.player.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY `order` ASC, createdAt ASC")
    fun getAllStations(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY `order` ASC, createdAt ASC")
    fun getFavoriteStations(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY name ASC")
    fun getAllStationsByNameAsc(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY name DESC")
    fun getAllStationsByNameDesc(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY createdAt DESC")
    fun getAllStationsByDateAdded(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY genre ASC, name ASC")
    fun getAllStationsByGenre(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY country ASC, name ASC")
    fun getAllStationsByCountry(): LiveData<List<RadioStation>>

    @Query("SELECT * FROM stations ORDER BY name ASC")
    suspend fun getAllStationsSync(): List<RadioStation>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getStationById(id: Long): RadioStation?

    @Query("SELECT * FROM stations WHERE id = :id")
    fun getStationByIdLiveData(id: Long): LiveData<RadioStation>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(station: RadioStation): Long

    @Insert
    suspend fun insertAll(stations: List<RadioStation>): List<Long>

    @Update
    suspend fun update(station: RadioStation)

    @Delete
    suspend fun delete(station: RadioStation)

    @Query("UPDATE stations SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT DISTINCT genre FROM stations WHERE genre != '' ORDER BY genre ASC")
    fun getDistinctGenres(): LiveData<List<String>>
}