package com.radio.player.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    }

    private fun setupAutoReconnect() {
        binding.autoReconnectSwitch.isChecked = SettingsManager.isAutoReconnect(this)
        binding.autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoReconnect(this, isChecked)
        }
    }
}