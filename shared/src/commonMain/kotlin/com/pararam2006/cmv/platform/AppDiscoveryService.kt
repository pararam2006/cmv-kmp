package com.pararam2006.cmv.platform

import com.pararam2006.cmv.domain.model.AppInfo

interface AppDiscoveryService {
    suspend fun discoverApps(): List<AppInfo>
}