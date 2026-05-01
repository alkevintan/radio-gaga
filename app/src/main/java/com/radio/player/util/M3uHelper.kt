package com.radio.player.util

import com.radio.player.data.RadioStation

object M3uHelper {

    fun exportM3u(stations: List<RadioStation>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        for (station in stations) {
            val info = buildString {
                append("-1")
                if (station.name.isNotBlank()) append(",${station.name}")
            }
            sb.appendLine("#EXTINF:$info")
            if (station.genre.isNotBlank()) sb.appendLine("#EXTGENRE:${station.genre}")
            if (station.country.isNotBlank()) sb.appendLine("#EXTCOUNTRY:${station.country}")
            if (station.homepage.isNotBlank()) sb.appendLine("#EXTHOMEPAGE:${station.homepage}")
            if (station.favicon.isNotBlank()) sb.appendLine("#EXTICON:${station.favicon}")
            sb.appendLine(station.streamUrl)
        }
        return sb.toString()
    }

    fun importM3u(content: String): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || !lines[0].startsWith("#EXTM3U")) return emptyList()

        var name = ""
        var genre = ""
        var country = ""
        var homepage = ""
        var favicon = ""

        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXTINF:") -> {
                    val commaIdx = line.indexOf(',')
                    name = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
                }
                line.startsWith("#EXTGENRE:") -> genre = line.substringAfter("#EXTGENRE:").trim()
                line.startsWith("#EXTCOUNTRY:") -> country = line.substringAfter("#EXTCOUNTRY:").trim()
                line.startsWith("#EXTHOMEPAGE:") -> homepage = line.substringAfter("#EXTHOMEPAGE:").trim()
                line.startsWith("#EXTICON:") -> favicon = line.substringAfter("#EXTICON:").trim()
                line.startsWith("#") -> { }
                else -> {
                    if (line.startsWith("http")) {
                        stations.add(RadioStation(
                            name = name.ifBlank { "Station" },
                            streamUrl = line,
                            genre = genre,
                            country = country,
                            homepage = homepage,
                            favicon = favicon
                        ))
                        name = ""
                        genre = ""
                        country = ""
                        homepage = ""
                        favicon = ""
                    }
                }
            }
        }
        return stations
    }
}