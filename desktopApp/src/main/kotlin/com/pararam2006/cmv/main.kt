package com.pararam2006.cmv

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CustomMusicVolume",
    ) {
        App()
    }
}