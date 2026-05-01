package com.radio.player.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object SettingsManager {

    private const val KEY_SORT_ORDER = "sort_order"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_THEME = "theme"
    private const val KEY_TUNING_SOUND = "tuning_sound"
    private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

    enum class SortOrder(val label: String, val isManual: Boolean = false) {
        NAME_ASC("Name (A-Z)"),
        NAME_DESC("Name (Z-A)"),
        DATE_ADDED("Recently Added"),
        GENRE("Genre"),
        COUNTRY("Country"),
        MANUAL("Custom (drag to reorder)", isManual = true)
    }

    enum class Theme(val label: String, val themeResId: Int = 0, val splashThemeResId: Int = 0) {
        SYSTEM("System"),
        LIGHT("Light"),
        DARK("Dark"),
        FREDDIE_WEMBLEY("Freddie Wembley",
            com.radio.player.R.style.Theme_RadioPlayer_FreddieWembley,
            com.radio.player.R.style.Theme_RadioPlayer_Splash_FreddieWembley)
    }

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getSortOrder(context: Context): SortOrder {
        val name = prefs(context).getString(KEY_SORT_ORDER, SortOrder.DATE_ADDED.name)
        return try { SortOrder.valueOf(name!!) } catch (_: Exception) { SortOrder.DATE_ADDED }
    }

    fun setSortOrder(context: Context, order: SortOrder) {
        prefs(context).edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun isAutoReconnect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
    }

    fun getTheme(context: Context): Theme {
        val name = prefs(context).getString(KEY_THEME, Theme.SYSTEM.name)
        return try { Theme.valueOf(name!!) } catch (_: Exception) { Theme.SYSTEM }
    }

    fun setTheme(context: Context, theme: Theme) {
        prefs(context).edit().putString(KEY_THEME, theme.name).apply()
    }

    fun isTuningSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TUNING_SOUND, true)

    fun setTuningSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TUNING_SOUND, enabled).apply()
    }

    fun isAutoUpdateCheck(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE_CHECK, true)

    fun setAutoUpdateCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE_CHECK, enabled).apply()
    }

    fun getLastUpdateCheck(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(context: Context, ts: Long) {
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK, ts).apply()
    }
}