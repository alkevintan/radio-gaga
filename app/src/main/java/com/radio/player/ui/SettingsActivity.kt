package com.radio.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.radio.player.R
import com.radio.player.data.Alarm
import com.radio.player.databinding.ActivitySettingsBinding
import com.radio.player.viewmodel.AlarmViewModel
import com.radio.player.util.SettingsManager
import com.radio.player.util.UpdateChecker
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var alarmViewModel: AlarmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = SettingsManager.getTheme(this)
        if (theme == SettingsManager.Theme.FREDDIE_WEMBLEY) {
            setTheme(R.style.Theme_RadioPlayer_FreddieWembley)
        }
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        setupAutoReconnect()
        setupTuningSound()
        setupThemeSelector()
        setupShowStreamUrls()
        setupUpdates()
        setupAlarms()
    }

    private fun setupUpdates() {
        binding.autoUpdateCheckSwitch.isChecked = SettingsManager.isAutoUpdateCheck(this)
        binding.autoUpdateCheckSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoUpdateCheck(this, isChecked)
        }

        val owner = getString(R.string.update_github_owner)
        val repo = getString(R.string.update_github_repo)
        if (owner.isBlank() || repo.isBlank()) {
            binding.updateStatusLabel.text = "GitHub repo not configured"
            binding.checkUpdatesRow.isEnabled = false
            return
        }

        val current = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        binding.updateStatusLabel.text = "Current: v$current"

        binding.checkUpdatesRow.setOnClickListener {
            checkForUpdates(current, manual = true)
        }
    }

    private fun checkForUpdates(currentVersion: String, manual: Boolean) {
        binding.updateProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val release = try {
                UpdateChecker.fetchLatest(this@SettingsActivity)
            } catch (_: Exception) { null }
            binding.updateProgress.visibility = View.GONE
            SettingsManager.setLastUpdateCheck(this@SettingsActivity, System.currentTimeMillis())

            if (release == null) {
                if (manual) Toast.makeText(this@SettingsActivity, "Update check failed", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (UpdateChecker.isNewer(release, currentVersion)) {
                UpdateDialog.show(this@SettingsActivity, release, currentVersion)
            } else if (manual) {
                UpdateDialog.showUpToDate(this@SettingsActivity, currentVersion)
            }
        }
    }

    private fun setupAutoReconnect() {
        binding.autoReconnectSwitch.isChecked = SettingsManager.isAutoReconnect(this)
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoReconnect(this, isChecked)
        }
    }

    private fun setupTuningSound() {
        binding.tuningSoundSwitch.isChecked = SettingsManager.isTuningSoundEnabled(this)
        binding.tuningSoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setTuningSoundEnabled(this, isChecked)
        }
    }

    private fun setupShowStreamUrls() {
        binding.showStreamUrlsSwitch.isChecked = SettingsManager.isShowStreamUrls(this)
        binding.showStreamUrlsSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowStreamUrls(this, isChecked)
        }
    }

    private fun setupThemeSelector() {
        val currentTheme = SettingsManager.getTheme(this)
        updateThemeLabel(currentTheme)

        binding.themeSelector.setOnClickListener {
            showThemeDialog()
        }
    }

    private fun showThemeDialog() {
        val options = SettingsManager.Theme.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val currentIndex = options.indexOf(SettingsManager.getTheme(this))

        AlertDialog.Builder(this)
            .setTitle("Choose theme")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selected = options[which]
                SettingsManager.setTheme(this, selected)
                applyTheme(selected)
                updateThemeLabel(selected)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateThemeLabel(theme: SettingsManager.Theme) {
        binding.themeLabel.text = theme.label
    }

    private fun applyTheme(theme: SettingsManager.Theme) {
        if (theme == SettingsManager.Theme.FREDDIE_WEMBLEY) {
            recreate()
        } else {
            when (theme) {
                SettingsManager.Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                SettingsManager.Theme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                SettingsManager.Theme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> {}
            }
        }
    }

    private fun setupAlarms() {
        alarmViewModel = androidx.lifecycle.ViewModelProvider(this)[AlarmViewModel::class.java]

        binding.addAlarmButton.setOnClickListener {
            showAlarmDialog(null)
        }

        alarmViewModel.alarms.observe(this) { alarms ->
            binding.alarmsContainer.removeAllViews()
            binding.noAlarmsText.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE

            for (alarm in alarms) {
                addAlarmView(alarm)
            }
        }
    }

    private fun addAlarmView(alarm: Alarm) {
        val alarmView = LayoutInflater.from(this).inflate(R.layout.item_alarm, binding.alarmsContainer, false)

        val timeText = alarmView.findViewById<TextView>(R.id.alarmTime)
        val stationText = alarmView.findViewById<TextView>(R.id.alarmStation)
        val repeatText = alarmView.findViewById<TextView>(R.id.alarmRepeat)
        val toggle = alarmView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.alarmToggle)
        val editBtn = alarmView.findViewById<TextView>(R.id.alarmEdit)
        val deleteBtn = alarmView.findViewById<TextView>(R.id.alarmDelete)

        timeText.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
        toggle.isChecked = alarm.isEnabled

        repeatText.text = getRepeatText(alarm.repeatDays)

        alarmViewModel.getStationForAlarm(alarm.stationId).observe(this) { station ->
            stationText.text = station?.name ?: "Unknown Station"
        }

        toggle.setOnCheckedChangeListener { _, isChecked ->
            alarmViewModel.toggleAlarm(alarm.id, isChecked)
        }

        editBtn.setOnClickListener {
            showAlarmDialog(alarm)
        }

        deleteBtn.setOnClickListener {
            showDeleteDialog(alarm)
        }

        binding.alarmsContainer.addView(alarmView)
    }

    private fun getRepeatText(repeatDays: Int): String {
        return when (repeatDays) {
            0 -> "Once"
            127 -> "Daily"
            62 -> "Weekdays (Mon-Fri)"
            65 -> "Weekends (Sat-Sun)"
            else -> {
                val days = mutableListOf<String>()
                if (repeatDays and 1 != 0) days.add("Sun")
                if (repeatDays and 2 != 0) days.add("Mon")
                if (repeatDays and 4 != 0) days.add("Tue")
                if (repeatDays and 8 != 0) days.add("Wed")
                if (repeatDays and 16 != 0) days.add("Thu")
                if (repeatDays and 32 != 0) days.add("Fri")
                if (repeatDays and 64 != 0) days.add("Sat")
                days.joinToString(", ")
            }
        }
    }

    private fun showAlarmDialog(alarm: Alarm?) {
        val dialog = AlarmDialog.newInstance(alarm)
        dialog.setOnSaveListener { newAlarm ->
            if (alarm == null) {
                alarmViewModel.addAlarm(newAlarm)
            } else {
                alarmViewModel.updateAlarm(newAlarm)
            }
        }
        dialog.show(supportFragmentManager, "alarm_dialog")
    }

    private fun showDeleteDialog(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle("Delete Alarm")
            .setMessage("Are you sure you want to delete this alarm?")
            .setPositiveButton("Delete") { _, _ ->
                alarmViewModel.deleteAlarm(alarm)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}