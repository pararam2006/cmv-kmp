package com.pararam2006.cmv.domain.repository

import kotlinx.coroutines.flow.SharedFlow

interface HeadphonesRepository {
    val isHeadsetFlow: SharedFlow<Boolean>
    fun computeIsHeadsetConnected(): Boolean
}