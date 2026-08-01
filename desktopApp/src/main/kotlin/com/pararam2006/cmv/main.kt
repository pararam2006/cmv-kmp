package com.pararam2006.cmv

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pararam2006.cmv.core.di.sharedModule
import com.pararam2006.cmv.platform.jvmPlatformModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(sharedModule, jvmPlatformModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Custom Music Volume",
        ) {
            App(appVersion = "1.1.0")
        }
    }
}
