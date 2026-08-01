package com.pararam2006.cmv.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val label: String,
    val iconUri: String,
    val packageName: String,
    val name: String,
    val selected: Boolean = false,
)