package com.pararam2006.cmv.core.di

import com.pararam2006.cmv.data.repository.AppsInfoRepositoryImpl
import com.pararam2006.cmv.data.repository.TrackVolumeRepositoryImpl
import com.pararam2006.cmv.domain.repository.AppsInfoRepository
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import com.pararam2006.cmv.domain.repository.TrackVolumeRepository
import com.pararam2006.cmv.platform.AndroidAppDiscoveryService
import com.pararam2006.cmv.platform.AppDiscoveryService
import com.pararam2006.cmv.utils.HeadphonesRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule = module {
    single<AppDiscoveryService> { AndroidAppDiscoveryService(androidContext()) }
    single<TrackVolumeRepository> { TrackVolumeRepositoryImpl(get()) }
    single<AppsInfoRepository> { AppsInfoRepositoryImpl(get()) }
    single<HeadphonesRepository> {
        HeadphonesRepositoryImpl(
            scope = get(named("AppScope")),
            context = get()
        )
    }
}