package com.pararam2006.cmv.domain.model

import android.net.Uri

data class AppInfo(
    val label: String,
    val iconUri: Uri,
    val packageName: String,
    val name: String,
    val selected: Boolean = false,
)