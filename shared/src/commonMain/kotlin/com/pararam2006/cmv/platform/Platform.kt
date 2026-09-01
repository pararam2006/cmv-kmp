package com.pararam2006.cmv.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.pararam2006.cmv.domain.model.AppMode
import kotlinx.coroutines.flow.StateFlow

expect class SettingsPreferences {
    // Basic settings (used by shared ViewModels)
    var showSystemVolumeUi: Boolean
    var learningTimeSeconds: Int
    var appMode: AppMode
    var volumeJumpProtectionEnabled: Boolean

    // Flow-based settings (used by Android service)
    val appModeFlow: StateFlow<AppMode>
    val userStoppedFlow: StateFlow<Boolean>

    // Methods (used by Android service)
    fun getUserStopped(): Boolean
    fun setUserStopped(new: Boolean)
    fun isSystemVolumeUiEnabled(): Boolean
}


// Dynamic theming (Android 12+)
expect fun isDynamicColorAvailable(): Boolean
@Composable
expect fun dynamicLightColorScheme(): ColorScheme
@Composable
expect fun dynamicDarkColorScheme(): ColorScheme

expect fun currentTimeMillis(): Long
