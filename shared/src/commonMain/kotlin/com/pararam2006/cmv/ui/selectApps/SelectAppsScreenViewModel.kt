package com.pararam2006.cmv.ui.selectApps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.platform.AppDiscoveryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SelectAppsScreenViewModel(
    private val repository: AppsInfoRepository,
    private val appDiscoveryService: AppDiscoveryService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelectAppsScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun retry() = loadApps()

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            runCatching {
                appDiscoveryService.discoverApps().map { discoveredApp ->
                    val savedApp = repository.getAppInfo(discoveredApp.packageName)
                    if (savedApp == null) {
                        repository.addAppInfo(discoveredApp)
                        discoveredApp
                    } else {
                        discoveredApp.copy(selected = savedApp.selected)
                    }
                }
            }.onSuccess { apps ->
                _uiState.update { it.copy(apps = apps, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun toogleApp(packageName: String, newState: Boolean) {
        _uiState.update { currentState ->
            val updatedApps = currentState.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(selected = newState)
                } else {
                    app
                }
            }
            currentState.copy(apps = updatedApps)
        }

        viewModelScope.launch {
            if (newState) {
                repository.selectApp(packageName)
            } else {
                repository.unselectApp(packageName)
            }
        }
    }
}