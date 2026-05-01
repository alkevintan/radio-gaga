package com.radio.player.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object SettingsManager {

    private const val KEY_SORT_ORDER = "sort_order"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_THEME = "theme"

    enum class SortOrder(val label: String) {
        NAME_ASC("Name (A-Z)"),
        NAME_DESC("Name (Z-A)"),
        DATE_ADDED("Recently Added"),
        GENRE("Genre"),
        COUNTRY("Country")
    }

    enum class Theme(val label: String) {
        SYSTEM("System"),
        LIGHT("Light"),
        DARK("Dark")
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
}