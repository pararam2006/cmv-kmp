package com.pararam2006.cmv.ui.settings

import androidx.lifecycle.ViewModel
import com.pararam2006.cmv.utils.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            showSystemVolumeUi = settingsPreferences.isSystemVolumeUiEnabled(),
            sliderPosition = settingsPreferences.getBasicVolumeChangingTime(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setShowSystemVolumeUi(enabled: Boolean) {
        settingsPreferences.setSystemVolumeUiEnabled(enabled)
        _uiState.update { it.copy(showSystemVolumeUi = enabled) }
    }

    fun changeSliderPositionState(newPosition: Float) {
        settingsPreferences.setBasicVolumeChangingTime(newPosition)
        _uiState.update { it.copy(sliderPosition = newPosition) }
    }
}
