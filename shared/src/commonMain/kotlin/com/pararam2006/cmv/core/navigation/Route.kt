package com.pararam2006.cmv.core.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    object Main : Route
    @Serializable
    object Settings : Route
    @Serializable
    object ListenerError : Route
    @Serializable
    object About : Route
    @Serializable
    object ChangeMode : Route
    @Serializable
    object SelectApps
}