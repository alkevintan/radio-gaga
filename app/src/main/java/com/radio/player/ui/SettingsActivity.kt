package com.radio.player.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.radio.player.R
import com.radio.player.databinding.ActivitySettingsBinding
import com.radio.player.util.SettingsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        setupAutoReconnect()
        setupThemeSelector()
    }

    private fun setupAutoReconnect() {
        binding.autoReconnectSwitch.isChecked = SettingsManager.isAutoReconnect(this)
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoReconnect(this, isChecked)
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
        when (theme) {
            SettingsManager.Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.Theme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            SettingsManager.Theme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}