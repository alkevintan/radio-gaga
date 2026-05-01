package com.radio.player.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.audiofx.LoudnessEnhancer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.radio.player.util.HttpClientFactory
import com.radio.player.util.RecordingManager
import com.radio.player.util.SwitchableFileSink
import com.radio.player.util.TeeingDataSourceFactory
import java.io.File
import com.google.android.exoplayer2.util.Util
import com.radio.player.MainActivity
import com.radio.player.R
import com.radio.player.data.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URL

class RadioPlaybackService : LifecycleService() {

    private val binder = RadioBinder()
    private var exoPlayer: ExoPlayer? = null

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var lostFocusWhilePlaying = false

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _playStartTime = MutableStateFlow(0L)
    val playStartTime: StateFlow<Long> = _playStartTime

    private val recordingSink = SwitchableFileSink()
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _recordingStartTime = MutableStateFlow(0L)
    val recordingStartTime: StateFlow<Long> = _recordingStartTime
    private val _lastRecordingFile = MutableStateFlow<File?>(null)
    val lastRecordingFile: StateFlow<File?> = _lastRecordingFile

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSession: Int = -1

    companion object {
        const val CHANNEL_ID = "radio_playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.radio.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.radio.player.ACTION_PAUSE"
        const val ACTION_STOP = "com.radio.player.ACTION_STOP"
    }

    private val audioFocusListener = OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                lostFocusWhilePlaying = _isPlaying.value
                pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                lostFocusWhilePlaying = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (lostFocusWhilePlaying) {
                    play()
                    lostFocusWhilePlaying = false
                }
            }
        }
    }

    inner class RadioBinder : Binder() {
        fun getService(): RadioPlaybackService = this@RadioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initPlayer()
        initMediaSession()
        initWakeLock()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> {
                val stationId = intent.getLongExtra("station_id", -1)
                val stationName = intent.getStringExtra("station_name") ?: ""
                val stationUrl = intent.getStringExtra("station_url") ?: ""

                if (stationId != -1L && stationUrl.isNotEmpty()) {
                    val station = com.radio.player.data.RadioStation(
                        id = stationId,
                        name = stationName,
                        streamUrl = stationUrl
                    )
                    playStation(station)
                } else {
                    play()
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    private fun initPlayer() {
        val httpDataSourceFactory = OkHttpDataSource.Factory(HttpClientFactory.get(this))
            .setUserAgent(Util.getUserAgent(this, "RadioPlayer"))

        val dataSourceFactory = DefaultDataSourceFactory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying && _playStartTime.value == 0L) {
                    _playStartTime.value = System.currentTimeMillis()
                }
                updateMediaSessionPlaybackState()
                updateNotification()
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    _isError.value = false
                }
                if (state == Player.STATE_IDLE && _isRecording.value) {
                    stopRecording()
                }
                updateMediaSessionPlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                _isError.value = true
                _errorMessage.value = error.message ?: "Unknown error"
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                rebuildLoudnessEnhancer(audioSessionId)
                applyVolumeGain(_currentStation.value?.volumeGain ?: 0f)
            }
        })
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "RadioPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    this@RadioPlaybackService.play()
                }
                override fun onPause() {
                    this@RadioPlaybackService.pause()
                }
                override fun onStop() {
                    this@RadioPlaybackService.stopPlayback()
                }
            })
            isActive = true
        }
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioPlayer::WakeLock")
        wakeLock.setReferenceCounted(false)
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .build()
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun updateMediaSessionPlaybackState() {
        val state = when {
            _isPlaying.value -> PlaybackStateCompat.STATE_PLAYING
            _isBuffering.value -> PlaybackStateCompat.STATE_BUFFERING
            _currentStation.value != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMediaSessionMetadata(station: RadioStation) {
        val metadata = MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.name)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.genre.ifBlank { station.country })
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Radio")
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
        }.build()
        mediaSession.setMetadata(metadata)
    }

    fun playStation(station: RadioStation) {
        if (_isRecording.value) stopRecording()
        _currentStation.value = station
        _isError.value = false
        _errorMessage.value = ""
        _playStartTime.value = 0L

        com.radio.player.util.SettingsManager.setLastPlayedStationId(this, station.id)

        updateMediaSessionMetadata(station)

        if (com.radio.player.util.SettingsManager.isTuningSoundEnabled(this)) {
            playTuningSound {
                startStationStream(station)
            }
        } else {
            startStationStream(station)
        }
    }

    private fun startStationStream(station: RadioStation) {
        val uri = android.net.Uri.parse(station.streamUrl)
        val mediaItem = MediaItem.fromUri(uri)

        val userAgent = Util.getUserAgent(this, "RadioPlayer")
        val httpDataSourceFactory = OkHttpDataSource.Factory(HttpClientFactory.get(this))
            .setUserAgent(userAgent)

        val baseFactory = DefaultDataSourceFactory(this, httpDataSourceFactory)
        val dataSourceFactory = TeeingDataSourceFactory(baseFactory, recordingSink)

        val mediaSource: MediaSource = if (station.streamUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            play()
        }

        applyVolumeGain(station.volumeGain)
        requestAudioFocus()
        acquireWakeLock()
        startForeground()
    }

    fun setLiveVolumeGain(gainDb: Float) {
        val current = _currentStation.value
        if (current != null) {
            _currentStation.value = current.copy(volumeGain = gainDb)
        }
        applyVolumeGain(gainDb)
    }

    private fun applyVolumeGain(gainDb: Float) {
        val player = exoPlayer ?: return
        val clamped = gainDb.coerceIn(-24f, 24f)
        if (clamped <= 0f) {
            player.volume = Math.pow(10.0, clamped.toDouble() / 20.0).toFloat().coerceIn(0f, 1f)
            try { loudnessEnhancer?.setEnabled(false) } catch (_: Exception) {}
        } else {
            player.volume = 1.0f
            try {
                rebuildLoudnessEnhancer(player.audioSessionId)
                loudnessEnhancer?.setTargetGain((clamped * 100).toInt())
                loudnessEnhancer?.setEnabled(true)
            } catch (_: Exception) { }
        }
    }

    private fun rebuildLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        if (loudnessEnhancerSession == audioSessionId && loudnessEnhancer != null) return
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = try { LoudnessEnhancer(audioSessionId) } catch (_: Exception) { null }
        loudnessEnhancerSession = audioSessionId
    }

    private var tuningMediaPlayer: android.media.MediaPlayer? = null
    private var tuningDoneCalled = false

    private fun playTuningSound(onDone: () -> Unit) {
        tuningDoneCalled = false
        tuningMediaPlayer?.release()
        tuningMediaPlayer = android.media.MediaPlayer().apply {
            try {
                val afd = assets.openFd("tuning.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = false
                setOnCompletionListener {
                    if (!tuningDoneCalled) {
                        tuningDoneCalled = true
                        tuningMediaPlayer?.release()
                        tuningMediaPlayer = null
                        onDone()
                    }
                }
                prepare()
                start()
            } catch (e: Exception) {
                release()
                tuningMediaPlayer = null
                if (!tuningDoneCalled) {
                    tuningDoneCalled = true
                    onDone()
                }
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            if (!tuningDoneCalled) {
                tuningDoneCalled = true
                tuningMediaPlayer?.release()
                tuningMediaPlayer = null
                onDone()
            }
        }
    }

    fun play() {
        requestAudioFocus()
        acquireWakeLock()
        exoPlayer?.play()
        updateNotification()
    }

    fun pause() {
        exoPlayer?.pause()
        releaseWakeLock()
        updateNotification()
    }

    fun stopPlayback() {
        if (_isRecording.value) stopRecording()
        exoPlayer?.stop()
        _isPlaying.value = false
        _isBuffering.value = false
        _playStartTime.value = 0L
        releaseWakeLock()
        abandonAudioFocus()
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun release() {
        if (_isRecording.value) stopRecording()
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        loudnessEnhancerSession = -1
        exoPlayer?.release()
        exoPlayer = null
        mediaSession.release()
        releaseWakeLock()
        abandonAudioFocus()
    }

    fun startRecording(): RecordingResult {
        val station = _currentStation.value
            ?: return RecordingResult.Error("No station selected")
        if (RecordingManager.isHls(station.streamUrl)) {
            return RecordingResult.Error("Recording HLS streams not supported yet")
        }
        if (_isRecording.value) return RecordingResult.Error("Already recording")
        if (!_isPlaying.value && !_isBuffering.value) {
            return RecordingResult.Error("Start playback first")
        }
        return try {
            val file = RecordingManager.newRecordingFile(this, station)
            recordingSink.start(file)
            _lastRecordingFile.value = file
            _recordingStartTime.value = System.currentTimeMillis()
            _isRecording.value = true
            RecordingResult.Success(file)
        } catch (e: Exception) {
            RecordingResult.Error(e.message ?: "Failed to start recording")
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) return null
        val file = recordingSink.stop()
        _isRecording.value = false
        _recordingStartTime.value = 0L
        return file
    }

    sealed class RecordingResult {
        data class Success(val file: File) : RecordingResult()
        data class Error(val message: String) : RecordingResult()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radio playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val station = _currentStation.value
        val playing = _isPlaying.value
        val buffering = _isBuffering.value

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = if (playing) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePending = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseText = if (playing) "Pause" else "Play"

        val contentText = when {
            buffering -> "Buffering…"
            playing -> (station?.genre?.ifBlank { station.country })?.let { "Playing • $it" } ?: "Playing"
            else -> "Paused"
        }

        val startTime = _playStartTime.value
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(station?.name ?: "Radio Player")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentIntent(contentPending)
            .addAction(playPauseIcon, playPauseText, playPausePending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)

        if (startTime > 0L) {
            builder.setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(startTime)
        } else {
            builder.setShowWhen(false)
        }

        if (station?.favicon?.isNotBlank() == true) {
            try {
                val bitmap = loadStationIcon(station.favicon)
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                }
            } catch (_: Exception) { }
        }

        return builder.build()
    }

    private fun loadStationIcon(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }
}