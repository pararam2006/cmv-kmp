package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.domain.usecase.DeleteTrackVolumeUseCase
import com.pararam2006.cmv.domain.usecase.GetTrackVolumesUseCase
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val useCaseModule = module {
    singleOf(::DeleteTrackVolumeUseCase)
    singleOf(::GetTrackVolumesUseCase)
    singleOf(::SaveTrackVolumeUseCase)
}
