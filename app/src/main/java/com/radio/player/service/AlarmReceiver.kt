package com.radio.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.radio.player.R
import com.radio.player.data.AppDatabase
import com.radio.player.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID_BASE = 10000
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                AlarmScheduler.rescheduleAll(context)
            }
            AlarmScheduler.ACTION_ALARM_TRIGGER -> {
                val alarmId = intent.getLongExtra("alarm_id", -1)
                val stationId = intent.getLongExtra("station_id", -1)

                if (alarmId != -1L && stationId != -1L) {
                    handleAlarmTrigger(context, alarmId, stationId)
                }
            }
        }
    }

    private fun handleAlarmTrigger(context: Context, alarmId: Long, stationId: Long) {
        val db = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = db.alarmDao().getAlarmByIdSync(alarmId)
            val station = db.stationDao().getStationById(stationId)

            if (alarm != null && station != null) {
                // Show notification
                showAlarmNotification(context, station.name, alarmId, stationId)

                // Start playback service
                val serviceIntent = Intent(context, RadioPlaybackService::class.java).apply {
                    action = RadioPlaybackService.ACTION_PLAY
                    putExtra("station_id", stationId)
                    putExtra("station_name", station.name)
                    putExtra("station_url", station.streamUrl)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Reschedule if repeating, deactivate if once
                if (alarm.repeatDays != 0) {
                    AlarmScheduler.scheduleAlarm(context, alarm)
                } else {
                    // "Once" alarm: deactivate after triggering
                    db.alarmDao().setEnabled(alarm.id, false)
                }
            }
        }
    }

    private fun showAlarmNotification(
        context: Context,
        stationName: String,
        alarmId: Long,
        stationId: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled alarms"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.radio.player.ALARM_DISMISS"
            putExtra("alarm_id", alarmId)
        }
        val dismissFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + 5000,
            dismissIntent,
            dismissFlags
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle("Alarm: $stationName")
            .setContentText("Tap to dismiss")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + alarmId.toInt(), notification)
    }
}
