package com.radio.player.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.radio.player.MainActivity
import com.radio.player.util.SettingsManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(com.radio.player.R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1500)
    }

    private fun applyTheme() {
        val theme = SettingsManager.getTheme(this)
        if (theme == SettingsManager.Theme.FREDDIE_WEMBLEY) {
            setTheme(com.radio.player.R.style.Theme_RadioPlayer_Splash_FreddieWembley)
        } else {
            when (theme) {
                SettingsManager.Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                SettingsManager.Theme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                SettingsManager.Theme.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> {}
            }
            setTheme(com.radio.player.R.style.Theme_RadioPlayer_Splash)
        }
    }
}