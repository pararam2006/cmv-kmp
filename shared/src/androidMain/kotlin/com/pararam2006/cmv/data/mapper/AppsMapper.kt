package com.pararam2006.cmv.data.mapper

import com.pararam2006.cmv.data.local.AppInfoEntity
import com.pararam2006.cmv.domain.model.AppInfo

fun AppInfoEntity.toDomain(): AppInfo {
    return AppInfo(
        label = this.label,
        iconUri = this.iconUri,
        packageName = this.packageName,
        name = this.name,
        selected = this.selected,
    )
}

fun AppInfo.toEntity(): AppInfoEntity {
    return AppInfoEntity(
        label = this.label,
        iconUri = this.iconUri,
        packageName = this.packageName,
        name = this.name,
        selected = this.selected,
    )
}