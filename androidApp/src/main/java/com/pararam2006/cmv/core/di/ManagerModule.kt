package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.data.manager.VolumeLearningManagerImpl
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

val managerModule = module {
    single<VolumeLearningManager> {
        Timber.tag("CMV.DI").d("lifecycle: creating VolumeLearningManager singleton")
        VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = get(),
            settingsPreferences = get(),
            scope = get(named("AppScope")),
        )
    }
}
