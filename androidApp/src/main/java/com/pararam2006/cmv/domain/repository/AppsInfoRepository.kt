package com.pararam2006.cmv.domain.repository

import com.pararam2006.cmv.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppsInfoRepository {
    fun getAllAppsInfo(): Flow<List<AppInfo>>
    fun getAllSelectedAppsInfo(): Flow<List<AppInfo>>
    suspend fun getAppInfo(packageName: String): AppInfo?
    suspend fun selectApp(id: Int)
    suspend fun selectApp(packageName: String)
    suspend fun unselectApp(id: Int)
    suspend fun unselectApp(packageName: String)
    suspend fun getAppInfo(id: Int): AppInfo?
    suspend fun addAppInfo(appInfo: AppInfo)
    suspend fun deleteAppInfo(appInfo: AppInfo)
    suspend fun deleteAppInfo(id: Int)
}