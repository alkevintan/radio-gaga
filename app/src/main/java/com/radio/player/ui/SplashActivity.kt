package com.radio.player.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.radio.player.MainActivity
import com.radio.player.util.SettingsManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1500)
    }

    private fun applyTheme() {
        when (SettingsManager.getTheme(this)) {
            SettingsManager.Theme.LIGHT -> setTheme(com.radio.player.R.style.Theme_RadioPlayer_Splash)
            SettingsManager.Theme.DARK -> setTheme(com.radio.player.R.style.Theme_RadioPlayer_Splash)
            SettingsManager.Theme.SYSTEM -> setTheme(com.radio.player.R.style.Theme_RadioPlayer_Splash)
        }
    }
}