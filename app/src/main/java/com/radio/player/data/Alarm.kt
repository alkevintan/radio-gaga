package com.radio.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Long,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
