package com.pararam2006.cmv.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderWithButtons(
    value: Float,
    onValueChange: (Float) -> Unit,
    initialRightButtonInteractionSource: MutableInteractionSource = MutableInteractionSource(),
    initialLeftButtonInteractionSource: MutableInteractionSource = MutableInteractionSource(),
    onRightButtonPress: () -> Unit,
    onRightButtonHold: () -> Unit,
    onLeftButtonPress: () -> Unit,
    onLeftButtonHold: () -> Unit,
    steps: Int,
    sliderModifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    track: @Composable ((SliderState) -> Unit) = { sliderState ->
        SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = sliderState)
    },
) {
    val leftInteractionSource = remember { initialRightButtonInteractionSource }
    val leftIsPressed by leftInteractionSource.collectIsPressedAsState()

    val rightInteractionSource = remember { initialLeftButtonInteractionSource }
    val rightIsPressed by rightInteractionSource.collectIsPressedAsState()


    LaunchedEffect(leftIsPressed) {
        if (leftIsPressed) {
            onLeftButtonHold()
        }
    }

    LaunchedEffect(rightIsPressed) {
        if (rightIsPressed) {
            onRightButtonHold()
        }
    }

    if (leftIsPressed) {
        println("Слыш, отпусти слева!")
    } else {
        println("А ну нажал обратно слева")
    }

    if(rightIsPressed) {
        println("Слыш, отпусти справа!")
    } else {
        println("А ну нажал обратно справа")
    }


    Row(
        modifier = rowModifier,
    ) {
        IconButton(
            onClick = onLeftButtonPress,
            interactionSource = leftInteractionSource,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = ""
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            track = track,
            modifier = sliderModifier.weight(1f),
        )

        IconButton(
            onClick = onRightButtonPress,
            interactionSource = rightInteractionSource
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = ""
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SliderWithButtonsPreview() {
    CustomMusicVolumeTheme {
        SliderWithButtons(
            value = 0f,
            onValueChange = {},
            onRightButtonPress = {},
            onLeftButtonPress = {},
            steps = 2,
            onRightButtonHold = {},
            onLeftButtonHold = {},
        )
    }
}