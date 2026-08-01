package com.pararam2006.cmv.ui.settings

import com.pararam2006.cmv.domain.model.AppMode

data class SettingsUiState(
    val showSystemVolumeUi: Boolean = true,
    val sliderPosition: Float = 15f,
    val appMode: AppMode = AppMode.LEARNING,
)