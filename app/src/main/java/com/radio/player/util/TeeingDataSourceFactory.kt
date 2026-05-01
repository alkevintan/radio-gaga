package com.radio.player.util

import com.google.android.exoplayer2.upstream.DataSink
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.TeeDataSource

class TeeingDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val sink: DataSink
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        TeeDataSource(upstream.createDataSource(), sink)
}
