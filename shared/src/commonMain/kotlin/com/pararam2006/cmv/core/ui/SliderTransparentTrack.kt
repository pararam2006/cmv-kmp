package com.pararam2006.cmv.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderTransparentTrack(sliderState: SliderState) {
    SliderDefaults.Track(
        sliderState = sliderState,
        colors = SliderDefaults.colors(
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        drawStopIndicator = null
    )
}