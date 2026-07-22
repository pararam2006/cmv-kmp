package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.ui.changeMode.ChangeModeScreenViewModel
import com.pararam2006.cmv.ui.main.MainViewModel
import com.pararam2006.cmv.ui.selectApps.SelectAppsScreenViewModel
import com.pararam2006.cmv.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ChangeModeScreenViewModel)
    viewModelOf(::SelectAppsScreenViewModel)
}
