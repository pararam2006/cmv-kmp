package com.pararam2006.cmv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.ui.changeMode.ChangeModeScreenViewModel
import com.pararam2006.cmv.ui.main.MainViewModel
import com.pararam2006.cmv.ui.settings.SettingsViewModel
import com.pararam2006.cmv.platform.SystemService
import com.pararam2006.cmv.ui.selectApps.SelectAppsScreenViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class NotificationListenerServiceLifecycleTest {

    @Test
    fun runtimeDependencyGraphResolves() {
        val koin = GlobalContext.get()

        assertNotNull(koin.get<VolumeLearningManager>())
        assertNotNull(koin.get<PlaybackTrackingCoordinator>())
        assertNotNull(koin.get<MainViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<ChangeModeScreenViewModel>())
        assertNotNull(koin.get<SelectAppsScreenViewModel>())
    }

    @Test
    fun selectAppsViewModelCanBeCreatedAndLoadsApps() = runBlocking {
        val viewModel = GlobalContext.get().get<SelectAppsScreenViewModel>()

        val state = withTimeout(10_000L.milliseconds) {
            viewModel.uiState.first { !it.isLoading }
        }

        assertFalse(state.isLoading)
    }

    @Test
    fun enabledListenerCanBeReboundWithoutRevokingNotificationAccess() = runBlocking {
        val koin = GlobalContext.get()
        val systemService = koin.get<SystemService>()
        val stateHolder = koin.get<ListenerServiceStateHolder>()

        assumeTrue(
            "Notification Access must be granted before running this device test",
            systemService.isNotificationServiceEnabled(),
        )

        stateHolder.setConnected(false)
        stateHolder.setStarting(true)
        assertTrue(systemService.toggleService(isOn = false))

        withTimeout(30_000L.milliseconds) {
            stateHolder.state.first { it.isConnected }
        }

        assertTrue(systemService.isNotificationServiceEnabled())
    }
}
