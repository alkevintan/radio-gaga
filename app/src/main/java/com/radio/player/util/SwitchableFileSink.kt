package com.radio.player.util

import com.google.android.exoplayer2.upstream.DataSink
import com.google.android.exoplayer2.upstream.DataSpec
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class SwitchableFileSink : DataSink {

    private val lock = Any()
    private var output: BufferedOutputStream? = null
    private var bytes: Long = 0L
    private var currentFile: File? = null

    fun start(file: File) {
        synchronized(lock) {
            closeQuietly()
            file.parentFile?.mkdirs()
            output = BufferedOutputStream(FileOutputStream(file, false))
            bytes = 0L
            currentFile = file
        }
    }

    fun stop(): File? {
        synchronized(lock) {
            val f = currentFile
            closeQuietly()
            currentFile = null
            return f
        }
    }

    fun isRecording(): Boolean = synchronized(lock) { output != null }

    fun bytesWritten(): Long = synchronized(lock) { bytes }

    fun currentFile(): File? = synchronized(lock) { currentFile }

    override fun open(dataSpec: DataSpec) {}

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        synchronized(lock) {
            output?.let {
                it.write(buffer, offset, length)
                bytes += length
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closeQuietly()
            currentFile = null
        }
    }

    private fun closeQuietly() {
        try { output?.flush() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        output = null
    }
}
