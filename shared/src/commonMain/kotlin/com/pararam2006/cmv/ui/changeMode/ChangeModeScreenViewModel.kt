package com.pararam2006.cmv.ui.changeMode

import androidx.lifecycle.ViewModel
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.platform.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ChangeModeScreenViewModel(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChangeModeScreenUiState(
            mode = settingsPreferences.appMode
        )
    )
    val uiState = _uiState.asStateFlow()

    fun setAppMode(newMode: AppMode) {
        settingsPreferences.appMode = newMode
        _uiState.update { it.copy(mode = newMode) }
    }
}