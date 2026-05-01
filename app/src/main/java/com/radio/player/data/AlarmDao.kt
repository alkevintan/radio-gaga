package com.radio.player.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAlarms(): LiveData<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun getAlarmById(id: Long): LiveData<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmByIdSync(id: Long): Alarm?

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarmsSync(): List<Alarm>

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
