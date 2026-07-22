package com.pararam2006.cmv.data.repository

import com.pararam2006.cmv.data.local.AppsDao
import com.pararam2006.cmv.data.mapper.toDomain
import com.pararam2006.cmv.data.mapper.toEntity
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppsInfoRepositoryImpl(
    private val dao: AppsDao,
) : AppsInfoRepository {
    override fun getAllAppsInfo(): Flow<List<AppInfo>> {
        return dao.getAllAppsInfo().map { appInfoList ->
            appInfoList.map { it.toDomain() }
        }
    }

    override fun getAllSelectedAppsInfo(): Flow<List<AppInfo>> {
        return dao.getAllSelectedAppsInfo().map { appInfoList ->
            appInfoList.map { it.toDomain() }
        }
    }

    override suspend fun getAppInfo(packageName: String): AppInfo? {
        return dao.getAppInfo(packageName)?.toDomain()
    }

    override suspend fun selectApp(id: Int) = dao.selectApp(id)
    override suspend fun selectApp(packageName: String) {
        dao.selectApp(packageName)
    }

    override suspend fun unselectApp(id: Int) = dao.unselectApp(id)
    override suspend fun unselectApp(packageName: String) =
        dao.unselectApp(packageName)

    override suspend fun getAppInfo(id: Int): AppInfo? = dao.getAppInfo(id)?.toDomain()
    override suspend fun addAppInfo(appInfo: AppInfo) = dao.addAppInfo(appInfo.toEntity())
    override suspend fun deleteAppInfo(appInfo: AppInfo) = dao.deleteAppInfo(appInfo.toEntity())
    override suspend fun deleteAppInfo(id: Int) = dao.deleteAppInfo(id)
}