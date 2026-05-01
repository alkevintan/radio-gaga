package com.radio.player.util

import android.content.Context
import android.os.Environment
import com.radio.player.data.RadioStation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingManager {

    fun recordingsDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        return File(base, "recordings").apply { mkdirs() }
    }

    fun newRecordingFile(context: Context, station: RadioStation): File {
        val ext = inferExtension(station.streamUrl)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${sanitize(station.name)}_$ts.$ext"
        return File(recordingsDir(context), name)
    }

    fun listRecordings(context: Context): List<File> =
        recordingsDir(context).listFiles { f -> f.isFile && f.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun isHls(streamUrl: String): Boolean =
        streamUrl.contains(".m3u8", ignoreCase = true)

    fun inferExtension(streamUrl: String): String {
        val lower = streamUrl.lowercase()
        return when {
            lower.contains(".aac") -> "aac"
            lower.contains(".ogg") || lower.contains(".oga") -> "ogg"
            lower.contains(".m4a") -> "m4a"
            lower.contains(".opus") -> "opus"
            lower.contains(".mp3") -> "mp3"
            else -> "mp3"
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_')
        return cleaned.ifEmpty { "recording" }.take(50)
    }
}
