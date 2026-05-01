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
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToAdd = findNextDay(dayBits, currentDayOfWeek, calendar.timeInMillis <= now)

        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
        return calendar.timeInMillis
    }

    private fun findNextDay(dayBits: Int, currentDay: Int, includeToday: Boolean): Int {
        val dayMapping = mapOf(
            Calendar.SUNDAY to 1,
            Calendar.MONDAY to 2,
            Calendar.TUESDAY to 4,
            Calendar.WEDNESDAY to 8,
            Calendar.THURSDAY to 16,
            Calendar.FRIDAY to 32,
            Calendar.SATURDAY to 64
        )

        for (i in 0 until 7) {
            val checkDay = if (includeToday) (currentDay + i - 1) % 7 + 1 else (currentDay + i - 1) % 7 + 1
            val bit = dayMapping[checkDay] ?: continue
            if (dayBits and bit != 0) {
                return i
            }
        }
        return 7
    }
}
