package com.pararam2006.cmv

import androidx.compose.runtime.Composable
import com.pararam2006.cmv.core.navigation.RootNavGraph

@Composable
fun App(appVersion: String = "1.2") {
    RootNavGraph(appVersion = appVersion)
}
