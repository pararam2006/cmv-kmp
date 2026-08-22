package com.pararam2006.cmv.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.outline_add_24
import custommusicvolume.shared.generated.resources.outline_remove_24
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderWithButtons(
    value: Float,
    onValueChange: (Float) -> Unit,
    initialRightButtonInteractionSource: MutableInteractionSource = MutableInteractionSource(),
    initialLeftButtonInteractionSource: MutableInteractionSource = MutableInteractionSource(),
    onRightButtonPress: () -> Unit,
    onRightButtonHold: () -> Unit,
    onRightButtonHoldEnd: () -> Unit,
    onLeftButtonPress: () -> Unit,
    onLeftButtonHold: () -> Unit,
    onLeftButtonHoldEnd: () -> Unit,
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
    val leftInteractionSource = remember { initialLeftButtonInteractionSource }
    val leftIsPressed by leftInteractionSource.collectIsPressedAsState()
    val rightInteractionSource = remember { initialRightButtonInteractionSource }
    val rightIsPressed by rightInteractionSource.collectIsPressedAsState()

    var leftHoldActive by remember { mutableStateOf(false) }
    var leftSuppressNextClick by remember { mutableStateOf(false) }
    var rightHoldActive by remember { mutableStateOf(false) }
    var rightSuppressNextClick by remember { mutableStateOf(false) }

    LaunchedEffect(leftIsPressed) {
        if (leftIsPressed) {
            leftHoldActive = false
            leftSuppressNextClick = false
            delay(BUTTON_HOLD_DELAY_MS.milliseconds)
            leftHoldActive = true
            leftSuppressNextClick = true
            onLeftButtonHold()
        } else if (leftHoldActive) {
            onLeftButtonHoldEnd()
            leftHoldActive = false
        }
    }

    LaunchedEffect(rightIsPressed) {
        if (rightIsPressed) {
            rightHoldActive = false
            rightSuppressNextClick = false
            delay(BUTTON_HOLD_DELAY_MS.milliseconds)
            rightHoldActive = true
            rightSuppressNextClick = true
            onRightButtonHold()
        } else if (rightHoldActive) {
            onRightButtonHoldEnd()
            rightHoldActive = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (leftHoldActive) onLeftButtonHoldEnd()
            if (rightHoldActive) onRightButtonHoldEnd()
        }
    }

    Row(modifier = rowModifier) {
        IconButton(
            onClick = {
                if (leftSuppressNextClick) {
                    leftSuppressNextClick = false
                } else {
                    onLeftButtonPress()
                }
            },
            interactionSource = leftInteractionSource,
        ) {
            Icon(
                painter = painterResource(Res.drawable.outline_remove_24),
                contentDescription = "",
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
            onClick = {
                if (rightSuppressNextClick) {
                    rightSuppressNextClick = false
                } else {
                    onRightButtonPress()
                }
            },
            interactionSource = rightInteractionSource,
        ) {
            Icon(
                painter = painterResource(Res.drawable.outline_add_24),
                contentDescription = "",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// @Preview
@Composable
private fun SliderWithButtonsPreview() {
    CustomMusicVolumeTheme {
        SliderWithButtons(
            value = 0f,
            onValueChange = {},
            onRightButtonPress = {},
            onRightButtonHold = {},
            onRightButtonHoldEnd = {},
            onLeftButtonPress = {},
            onLeftButtonHold = {},
            onLeftButtonHoldEnd = {},
            steps = 2,
        )
    }
}

private const val BUTTON_HOLD_DELAY_MS = 500L
