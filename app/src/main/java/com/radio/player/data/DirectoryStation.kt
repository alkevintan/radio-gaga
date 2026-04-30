package com.radio.player.data

import com.google.gson.annotations.SerializedName

data class DirectoryStation(
    @SerializedName("stationuuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val streamUrl: String,
    @SerializedName("homepage") val homepage: String,
    @SerializedName("favicon") val favicon: String,
    @SerializedName("country") val country: String,
    @SerializedName("tags") val tags: String
) {
    fun toRadioStation(order: Int = 0): RadioStation {
        return RadioStation(
            name = name,
            streamUrl = streamUrl,
            homepage = homepage,
            genre = tags,
            country = country,
            favicon = favicon,
            order = order
        )
    }
}