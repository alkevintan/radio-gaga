package com.radio.player.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.radio.player.data.Alarm
import com.radio.player.service.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_ALARM_TRIGGER = "com.radio.player.ALARM_TRIGGER"

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra("alarm_id", alarm.id)
            putExtra("station_id", alarm.stationId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarm.id.toInt(), intent, flags)

        val triggerTime = calculateNextTriggerTime(alarm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        val db = com.radio.player.data.AppDatabase.getInstance(context)
        val alarms = kotlinx.coroutines.runBlocking {
            db.alarmDao().getEnabledAlarmsSync()
        }
        for (alarm in alarms) {
            scheduleAlarm(context, alarm)
        }
    }

    private fun calculateNextTriggerTime(alarm: Alarm): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays == 0) {
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        val dayBits = alarm.repeatDays
        val includeToday = calendar.timeInMillis > now
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayMapping = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64)

        val startOffset = if (includeToday) 0 else 1
        for (offset in startOffset until 7 + startOffset) {
            val checkDay = ((currentDayOfWeek - 1 + offset) % 7) + 1
            val bit = dayMapping[checkDay]
            if (dayBits and bit != 0) {
                calendar.add(Calendar.DAY_OF_YEAR, offset)
                break
            }
        }

        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }
}
