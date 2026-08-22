package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.domain.usecase.*
import com.pararam2006.cmv.core.service.ListenerServiceStateHolder
import com.pararam2006.cmv.core.service.MyNotificationListenerServiceStateHolder
import com.pararam2006.cmv.ui.changeMode.ChangeModeScreenViewModel
import com.pararam2006.cmv.ui.debug.DebugScreenViewModel
import com.pararam2006.cmv.ui.main.MainViewModel
import com.pararam2006.cmv.ui.selectApps.SelectAppsScreenViewModel
import com.pararam2006.cmv.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::GetTrackVolumesUseCase)
    factoryOf(::SaveTrackVolumeUseCase)
    factoryOf(::DeleteTrackVolumeUseCase)
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ChangeModeScreenViewModel)
    viewModelOf(::SelectAppsScreenViewModel)
    viewModelOf(::DebugScreenViewModel)
}

val serviceStateModule = module {
    single<ListenerServiceStateHolder> { MyNotificationListenerServiceStateHolder() }
}

val sharedModule = module {
    includes(useCaseModule, viewModelModule, serviceStateModule)
}
