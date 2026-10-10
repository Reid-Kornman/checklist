package com.example.checklist

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ChecklistApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val darkModeEnabled = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(DARK_MODE_KEY, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        const val PREFERENCES_NAME = "checklist_preferences"
        const val DARK_MODE_KEY = "dark_mode_enabled"
    }
}