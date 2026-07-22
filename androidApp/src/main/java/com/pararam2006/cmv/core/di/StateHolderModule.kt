package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.core.service.MyNotificationListenerServiceStateHolder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val stateHolderModule = module {
    singleOf(::MyNotificationListenerServiceStateHolder)
}