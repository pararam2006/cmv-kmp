package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.data.manager.VolumeLearningManagerImpl
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.service.PlaybackTrackingCoordinator
import com.pararam2006.cmv.platform.SettingsPreferences
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

val managerModule = module {
    single<VolumeLearningManager> {
        Timber.tag("CMV.DI").d("lifecycle: creating VolumeLearningManager singleton")
        val settingsPreferences = get<SettingsPreferences>()
        VolumeLearningManagerImpl(
            saveTrackVolumeUseCase = get(),
            appModeFlow = settingsPreferences.appModeFlow,
            learningTimeSeconds = { settingsPreferences.learningTimeSeconds },
            volumeJumpProtectionEnabled = { settingsPreferences.volumeJumpProtectionEnabled },
            scope = get(named("AppScope")),
            nowMillis = System::currentTimeMillis,
            logger = { message -> Timber.tag("CMV.VolumeLearningManager").d(message) },
        )
    }
    single {
        PlaybackTrackingCoordinator(
            appsInfoRepository = get(),
            trackVolumeRepository = get(),
            volumeLearningManager = get(),
            scope = get(named("AppScope")),
            logger = { message -> Timber.tag("CMV.PlaybackCoordinator").d(message) },
        )
    }
}
