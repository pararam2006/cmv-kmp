package com.pararam2006.cmv.ui.selectApps

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.platform.AppDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SelectAppsScreenViewModelTest {
    @Test
    fun discoveryFailureStopsLoadingAndExposesRetryState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = SelectAppsScreenViewModel(
                repository = EmptyAppsRepository(),
                appDiscoveryService = object : AppDiscoveryService {
                    override suspend fun discoverApps(): List<AppInfo> {
                        error("discovery failed")
                    }
                },
            )

            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.loadFailed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class EmptyAppsRepository : AppsInfoRepository {
        private val apps = MutableStateFlow<List<AppInfo>>(emptyList())

        override fun getAllAppsInfo(): Flow<List<AppInfo>> = apps
        override fun getAllSelectedAppsInfo(): Flow<List<AppInfo>> = apps
        override suspend fun getAppInfo(packageName: String): AppInfo? = null
        override suspend fun getAppInfo(id: Int): AppInfo? = null
        override suspend fun selectApp(id: Int) = Unit
        override suspend fun selectApp(packageName: String) = Unit
        override suspend fun unselectApp(id: Int) = Unit
        override suspend fun unselectApp(packageName: String) = Unit
        override suspend fun addAppInfo(appInfo: AppInfo) = Unit
        override suspend fun deleteAppInfo(appInfo: AppInfo) = Unit
        override suspend fun deleteAppInfo(id: Int) = Unit
    }
}
