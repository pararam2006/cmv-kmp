package com.pararam2006.cmv.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pararam2006.cmv.ui.changeMode.AppMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

class SettingsPreferences(
    context: Application,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val appModeFlow: Flow<AppMode> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == APP_MODE) {
                trySend(getAppMode())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .buffer(0) //Теперь новое значение заменяет старое
        .onStart {
        emit(getAppMode())
    }

    val userStoppedFlow: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_USER_STOPPED) {
                trySend(getUserStopped())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(getUserStopped()) }

    fun isSystemVolumeUiEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYSTEM_VOLUME_UI, true)
    }

    fun setSystemVolumeUiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SYSTEM_VOLUME_UI, enabled) }
    }

    fun setBasicVolumeChangingTime(time: Float) {
        prefs.edit { putFloat(KEY_BASIC_VOLUME_CHANGING_TIME, time) }
    }

    fun getBasicVolumeChangingTime(): Float {
        return prefs.getFloat(KEY_BASIC_VOLUME_CHANGING_TIME, 10f)
    }

    fun getAppMode(): AppMode {
        return when (prefs.getString(APP_MODE, AppMode.LEARNING.toString())) {
            "LEARNING" -> AppMode.LEARNING
            "JUST_CHANGING" -> AppMode.JUST_CHANGING
            else -> throw NoSuchElementException("Кто-то забыл добавить тип в AppMode :)")
        }
    }

    fun setAppMode(newMode: AppMode) {
        prefs.edit { putString(APP_MODE, newMode.toString()) }
    }

    fun getUserStopped(): Boolean {
        return prefs.getBoolean(KEY_USER_STOPPED, false)
    }

    fun setUserStopped(new: Boolean) {
        prefs.edit { putBoolean(KEY_USER_STOPPED, new) }
    }

    private companion object {
        private const val PREFS_NAME = "cmv_settings"
        private const val KEY_SYSTEM_VOLUME_UI = "system_volume_ui_enabled"
        private const val KEY_BASIC_VOLUME_CHANGING_TIME = "basic_volume_changing_time"
        private const val KEY_USER_STOPPED = "notification_listener_user_stopped"
        private const val APP_MODE = "app_mode"
    }
}