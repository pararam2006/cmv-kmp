package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.utils.SettingsPreferences
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val settingsModule = module {
    singleOf(::SettingsPreferences)
}
