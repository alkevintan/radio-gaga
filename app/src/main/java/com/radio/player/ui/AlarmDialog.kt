package com.radio.player.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R
import com.radio.player.data.Alarm
import com.radio.player.data.AppDatabase
import com.radio.player.databinding.DialogAlarmBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmDialog : DialogFragment() {

    private lateinit var binding: DialogAlarmBinding
    private var selectedStationId: Long = -1
    private var stationIds: List<Long> = emptyList()

    private val existingAlarm: Alarm?
        get() = arguments?.let { args ->
            if (args.containsKey("alarm_id")) Alarm(
                id = args.getLong("alarm_id"),
                stationId = args.getLong("alarm_station_id"),
                hour = args.getInt("alarm_hour"),
                minute = args.getInt("alarm_minute"),
                repeatDays = args.getInt("alarm_repeat_days"),
                isEnabled = args.getBoolean("alarm_enabled"),
                createdAt = args.getLong("alarm_created_at")
            ) else null
        }

    private var onSave: ((Alarm) -> Unit)? = null

    fun setOnSaveListener(listener: (Alarm) -> Unit) {
        onSave = listener
    }

    companion object {
        fun newInstance(alarm: Alarm? = null): AlarmDialog {
            val dialog = AlarmDialog()
            if (alarm != null) {
                dialog.arguments = Bundle().apply {
                    putLong("alarm_id", alarm.id)
                    putLong("alarm_station_id", alarm.stationId)
                    putInt("alarm_hour", alarm.hour)
                    putInt("alarm_minute", alarm.minute)
                    putInt("alarm_repeat_days", alarm.repeatDays)
                    putBoolean("alarm_enabled", alarm.isEnabled)
                    putLong("alarm_created_at", alarm.createdAt)
                }
            }
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAlarmBinding.inflate(LayoutInflater.from(requireContext()))

        loadStations()
        setupRepeatOptions()
        populateFields()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingAlarm == null) "Add Alarm" else "Edit Alarm")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ -> saveAlarm() }
            .setNegativeButton("Cancel") { _, _ -> dismiss() }
            .create()

        return dialog
    }

    private fun loadStations() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(requireContext())
            val stations = db.stationDao().getAllStationsSync()
            val stationNames = stations.map { it.name }
            stationIds = stations.map { it.id }

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, stationNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.stationSpinner.adapter = adapter

                binding.stationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedStationId = stationIds.getOrNull(position) ?: -1
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }

                if (existingAlarm != null) {
                    val index = stationIds.indexOf(existingAlarm!!.stationId)
                    if (index >= 0) binding.stationSpinner.setSelection(index)
                }
            }
        }
    }

    private fun setupRepeatOptions() {
        binding.repeatGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.customDaysLayout.visibility = if (checkedId == R.id.rbCustom) View.VISIBLE else View.GONE
        }
    }

    private fun populateFields() {
        if (existingAlarm != null) {
            binding.timePicker.hour = existingAlarm!!.hour
            binding.timePicker.minute = existingAlarm!!.minute

            when (existingAlarm!!.repeatDays) {
                0 -> binding.rbOnce.isChecked = true
                127 -> binding.rbDaily.isChecked = true
                62 -> binding.rbWeekdays.isChecked = true
                65 -> binding.rbWeekends.isChecked = true
                else -> {
                    binding.rbCustom.isChecked = true
                    binding.customDaysLayout.visibility = View.VISIBLE
                    if (existingAlarm!!.repeatDays and 1 != 0) binding.cbSun.isChecked = true
                    if (existingAlarm!!.repeatDays and 2 != 0) binding.cbMon.isChecked = true
                    if (existingAlarm!!.repeatDays and 4 != 0) binding.cbTue.isChecked = true
                    if (existingAlarm!!.repeatDays and 8 != 0) binding.cbWed.isChecked = true
                    if (existingAlarm!!.repeatDays and 16 != 0) binding.cbThu.isChecked = true
                    if (existingAlarm!!.repeatDays and 32 != 0) binding.cbFri.isChecked = true
                    if (existingAlarm!!.repeatDays and 64 != 0) binding.cbSat.isChecked = true
                }
            }
        }
    }

    private fun saveAlarm() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute

        if (selectedStationId == -1L) {
            return
        }

        val repeatDays = when {
            binding.rbOnce.isChecked -> 0
            binding.rbDaily.isChecked -> 127
            binding.rbWeekdays.isChecked -> 62
            binding.rbWeekends.isChecked -> 65
            binding.rbCustom.isChecked -> {
                var days = 0
                if (binding.cbSun.isChecked) days = days or 1
                if (binding.cbMon.isChecked) days = days or 2
                if (binding.cbTue.isChecked) days = days or 4
                if (binding.cbWed.isChecked) days = days or 8
                if (binding.cbThu.isChecked) days = days or 16
                if (binding.cbFri.isChecked) days = days or 32
                if (binding.cbSat.isChecked) days = days or 64
                days
            }
            else -> 0
        }

        val alarm = Alarm(
            id = existingAlarm?.id ?: 0,
            stationId = selectedStationId,
            hour = hour,
            minute = minute,
            repeatDays = repeatDays,
            isEnabled = existingAlarm?.isEnabled ?: true
        )

        onSave?.invoke(alarm)
        dismiss()
    }
}
