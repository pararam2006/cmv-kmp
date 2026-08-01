package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.platform.SettingsPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidSettingsModule = module {
    single<SettingsPreferences> {
        SettingsPreferences(
            androidContext().getSharedPreferences("app_settings", 0),
        )
    }
}

val androidSharedModule = module {
    includes(
        databaseModule,
        repositoryModule,
        managerModule,
        androidSettingsModule,
    )
}
