package com.pararam2006.cmv.core.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.platform.SettingsPreferences
import com.pararam2006.cmv.platform.SystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MyControlCenterTileService : TileService() {
    private val tileServiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serviceStateHolder: ListenerServiceStateHolder by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val systemService: SystemService by inject()
    private var listeningJob: Job? = null

    override fun onClick() {
        super.onClick()
        val serviceState = serviceStateHolder.state.value
        if (serviceState.isStarting) return

        val permissionGranted = systemService.isNotificationServiceEnabled()
        val serviceIsRunning =
            permissionGranted && serviceState.isConnected && !serviceState.userStopped

        if (!serviceIsRunning && !permissionGranted) {
            systemService.openNotificationSettings()
            return
        }

        val userStopped = serviceIsRunning
        settingsPreferences.setUserStopped(userStopped)
        serviceStateHolder.setUserStopped(userStopped)
        serviceStateHolder.setRestartResult(null)
        serviceStateHolder.setStarting(!serviceIsRunning)

        if (!systemService.toggleService(serviceIsRunning)) {
            settingsPreferences.setUserStopped(!userStopped)
            serviceStateHolder.setUserStopped(!userStopped)
            serviceStateHolder.setStarting(false)
            serviceStateHolder.setRestartResult(false)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = tileServiceScope.launch {
            serviceStateHolder.state
                .map { state ->
                    when {
                        state.isStarting -> Tile.STATE_UNAVAILABLE
                        state.isConnected && !state.userStopped -> Tile.STATE_ACTIVE
                        else -> Tile.STATE_INACTIVE
                    }
                }
                .distinctUntilChanged()
                .collect(::updateTileState)
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        tileServiceScope.cancel()
        super.onDestroy()
    }

    private fun updateTileState(state: Int) {
        qsTile?.let { tile ->
            if (tile.state != state) {
                tile.state = state
                tile.updateTile()
            }
        }
    }
}
