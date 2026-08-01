package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.core.service.MyNotificationListenerService
import com.pararam2006.cmv.platform.SystemService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val settingsModule = module {
    single<SystemService> {
        SystemService.create(
            context = androidContext(),
            notificationListenerServiceClass = MyNotificationListenerService::class.java,
            startAction = MyNotificationListenerService.ACTION_START,
            stopAction = MyNotificationListenerService.ACTION_STOP,
        )
    }
}
