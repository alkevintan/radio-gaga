package com.radio.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class RadioStation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val homepage: String = "",
    val genre: String = "",
    val country: String = "",
    val favicon: String = "",
    val isFavorite: Boolean = false,
    val order: Int = 0,
    val volumeGain: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)