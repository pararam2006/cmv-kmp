package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.platform.AppDiscoveryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDiscoveredAppsUseCaseTest {
    @Test
    fun addsNewAppsUsingDiscoveryDefaultSelection() = runTest {
        val repository = FakeAppsInfoRepository()
        val discovered = appInfo("player", selected = true)
        val useCase = SyncDiscoveredAppsUseCase(
            repository = repository,
            appDiscoveryService = FakeAppDiscoveryService(listOf(discovered)),
        )

        assertEquals(listOf(discovered), useCase())
        assertEquals(discovered, repository.getAppInfo("player"))
    }

    @Test
    fun preservesSavedSelectionForPreviouslyDiscoveredApp() = runTest {
        val saved = appInfo("player", selected = false)
        val repository = FakeAppsInfoRepository(listOf(saved))
        val useCase = SyncDiscoveredAppsUseCase(
            repository = repository,
            appDiscoveryService = FakeAppDiscoveryService(
                listOf(appInfo("player", selected = true)),
            ),
        )

        val result = useCase()

        assertEquals(false, result.single().selected)
        assertEquals(saved, repository.getAppInfo("player"))
    }

    private class FakeAppDiscoveryService(
        private val apps: List<AppInfo>,
    ) : AppDiscoveryService {
        override suspend fun discoverApps(): List<AppInfo> = apps
    }

    private class FakeAppsInfoRepository(
        initial: List<AppInfo> = emptyList(),
    ) : AppsInfoRepository {
        private val apps = MutableStateFlow(initial)

        override fun getAllAppsInfo(): Flow<List<AppInfo>> = apps
        override fun getAllSelectedAppsInfo(): Flow<List<AppInfo>> =
            apps.map { values -> values.filter { it.selected } }

        override suspend fun getAppInfo(packageName: String): AppInfo? =
            apps.value.firstOrNull { it.packageName == packageName }

        override suspend fun getAppInfo(id: Int): AppInfo? = apps.value.getOrNull(id)
        override suspend fun selectApp(id: Int) = Unit
        override suspend fun selectApp(packageName: String) = Unit
        override suspend fun unselectApp(id: Int) = Unit
        override suspend fun unselectApp(packageName: String) = Unit

        override suspend fun addAppInfo(appInfo: AppInfo) {
            apps.update { current ->
                current.filterNot { it.packageName == appInfo.packageName } + appInfo
            }
        }

        override suspend fun deleteAppInfo(appInfo: AppInfo) = Unit
        override suspend fun deleteAppInfo(id: Int) = Unit
    }

    private fun appInfo(packageName: String, selected: Boolean) = AppInfo(
        label = packageName,
        iconUri = "",
        packageName = packageName,
        name = packageName,
        selected = selected,
    )
}
