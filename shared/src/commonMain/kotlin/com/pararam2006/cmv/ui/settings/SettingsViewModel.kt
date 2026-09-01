package com.pararam2006.cmv.ui.settings

import androidx.lifecycle.ViewModel
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.platform.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel(), SettingsViewModelInterface {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            showSystemVolumeUi = settingsPreferences.showSystemVolumeUi,
            sliderPosition = settingsPreferences.learningTimeSeconds.toFloat(),
            appMode = settingsPreferences.appMode,
            volumeJumpProtectionEnabled = settingsPreferences.volumeJumpProtectionEnabled,
        )
    )
    override val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    override fun setShowSystemVolumeUi(enabled: Boolean) {
        settingsPreferences.showSystemVolumeUi = enabled
        _uiState.update { it.copy(showSystemVolumeUi = enabled) }
    }

    override fun changeSliderPositionState(newPosition: Float) {
        settingsPreferences.learningTimeSeconds = newPosition.toInt()
        _uiState.update { it.copy(sliderPosition = newPosition) }
    }

    override fun setAppMode(mode: AppMode) {
        settingsPreferences.appMode = mode
        _uiState.update { it.copy(appMode = mode) }
    }

    override fun setVolumeJumpProtectionEnabled(enabled: Boolean) {
        settingsPreferences.volumeJumpProtectionEnabled = enabled
        _uiState.update { it.copy(volumeJumpProtectionEnabled = enabled) }
    }

}

interface SettingsViewModelInterface {
    val uiState: StateFlow<SettingsUiState>
    fun setShowSystemVolumeUi(enabled: Boolean)
    fun changeSliderPositionState(newPosition: Float)
    fun setAppMode(mode: AppMode)
    fun setVolumeJumpProtectionEnabled(enabled: Boolean)
}