package com.pararam2006.cmv.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppsDao {
    @Query("SELECT * FROM apps")
    fun getAllAppsInfo() : Flow<List<AppInfoEntity>>

    @Query("SElECT * FROM apps WHERE selected = 1")
    fun getAllSelectedAppsInfo() : Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    suspend fun getAppInfo(packageName: String) : AppInfoEntity?

    @Query("UPDATE apps SET selected = 1 WHERE id = :id")
    suspend fun selectApp(id: Int)

    @Query("UPDATE apps SET selected = 1 WHERE packageName = :packageName")
    suspend fun selectApp(packageName: String)

    @Query("UPDATE apps SET selected = 0 WHERE id = :id")
    suspend fun unselectApp(id: Int)

    @Query("UPDATE apps SET selected = 0 WHERE packageName = :packageName")
    suspend fun unselectApp(packageName: String)

    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun getAppInfo(id: Int) : AppInfoEntity?

    @Upsert
    suspend fun addAppInfo(appInfo: AppInfoEntity)

    @Delete
    suspend fun deleteAppInfo(appInfo: AppInfoEntity)

    @Query("DELETE FROM apps WHERE id = :id")
    suspend fun deleteAppInfo(id: Int)
}
