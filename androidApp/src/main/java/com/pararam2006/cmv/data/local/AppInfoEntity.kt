package com.pararam2006.cmv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val iconUri: String,
    val packageName: String,
    val name: String,
    val selected: Boolean = false,
)
