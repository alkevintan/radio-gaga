package com.radio.player.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.radio.player.data.Alarm
import com.radio.player.data.AppDatabase
import com.radio.player.data.RadioStation
import com.radio.player.util.AlarmScheduler
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmDao = AppDatabase.getInstance(application).alarmDao()
    private val stationDao = AppDatabase.getInstance(application).stationDao()

    val alarms: LiveData<List<Alarm>> = alarmDao.getAlarms()

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val id = alarmDao.insert(alarm)
            val newAlarm = alarm.copy(id = id)
            AlarmScheduler.scheduleAlarm(getApplication(), newAlarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmDao.update(alarm)
            if (alarm.isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), alarm)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmDao.delete(alarm)
            AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
        }
    }

    fun toggleAlarm(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            alarmDao.setEnabled(id, enabled)
            val alarm = alarmDao.getAlarmByIdSync(id)
            if (alarm != null) {
                if (enabled) {
                    AlarmScheduler.scheduleAlarm(getApplication(), alarm)
                } else {
                    AlarmScheduler.cancelAlarm(getApplication(), id)
                }
            }
        }
    }

    fun getStationForAlarm(stationId: Long): LiveData<RadioStation> {
        return stationDao.getStationByIdLiveData(stationId)
    }

    fun getAlarmById(id: Long): LiveData<Alarm> {
        return alarmDao.getAlarmById(id)
    }
}
