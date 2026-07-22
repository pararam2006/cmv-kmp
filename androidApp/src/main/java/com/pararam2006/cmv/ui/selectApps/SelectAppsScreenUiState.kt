package com.pararam2006.cmv.ui.selectApps

import com.pararam2006.cmv.domain.model.AppInfo

data class SelectAppsScreenUiState(
    val input: String = "",
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
)