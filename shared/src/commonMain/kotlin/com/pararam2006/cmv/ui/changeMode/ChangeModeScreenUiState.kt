package com.pararam2006.cmv.ui.changeMode

import com.pararam2006.cmv.domain.model.AppMode

data class ChangeModeScreenUiState(
    val mode: AppMode = AppMode.LEARNING
)