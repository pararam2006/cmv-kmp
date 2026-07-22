package com.pararam2006.cmv.core.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single(named("AppScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    includes(
        viewModelModule,
        repositoryModule,
        useCaseModule,
        databaseModule,
        managerModule,
        settingsModule,
        stateHolderModule,
    )
}
