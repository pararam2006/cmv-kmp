package com.pararam2006.cmv.domain.usecase

import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.platform.AppDiscoveryService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncDiscoveredAppsUseCase(
    private val repository: AppsInfoRepository,
    private val appDiscoveryService: AppDiscoveryService,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(): List<AppInfo> = mutex.withLock {
        appDiscoveryService.discoverApps().map { discoveredApp ->
            val savedApp = repository.getAppInfo(discoveredApp.packageName)
            if (savedApp == null) {
                repository.addAppInfo(discoveredApp)
                discoveredApp
            } else {
                discoveredApp.copy(selected = savedApp.selected)
            }
        }
    }
}
