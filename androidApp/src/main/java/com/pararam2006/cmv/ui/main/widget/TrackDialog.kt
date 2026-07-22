package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.pararam2006.cmv.R
import com.pararam2006.cmv.core.Constants.VOLUME_SLIDER_STEPS_COUNT
import com.pararam2006.cmv.core.Constants.VOLUME_SLIDER_VALUE_RANGE
import com.pararam2006.cmv.core.ui.SliderTransparentTrack
import com.pararam2006.cmv.core.ui.SliderWithButtons
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDialog(
    initialTitle: String,
    initialArtist: String,
    modifier: Modifier = Modifier,
    initialOffset: Float = 1f,
    isEdit: Boolean = false,
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Float) -> Unit,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onStartIncrementing: () -> Unit,
    onStopIncrementing: () -> Unit,
    onStartDecrementing: () -> Unit,
    onStopDecrementing: () -> Unit,
    onOffsetChange: (Float) -> Unit,
) {
    val offsetPercent = (initialOffset * 100).roundToInt().toFloat()

    val leftInteractionSource = remember { MutableInteractionSource() }
    val leftIsPressed by leftInteractionSource.collectIsPressedAsState()
    val leftButtonPressAction = if (leftIsPressed) onStartDecrementing else onStopDecrementing

    val rightInteractionSource = remember { MutableInteractionSource() }
    val rightIsPressed by rightInteractionSource.collectIsPressedAsState()
    val rightButtonPressAction = if (rightIsPressed) onStartIncrementing else onStopIncrementing

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) {
                    stringResource(R.string.main_screen_redacting_dialog_title)
                } else {
                    stringResource(
                        R.string.main_screen_add_dialog_title
                    )
                }
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = initialTitle,
                    onValueChange = { onTitleChange(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.main_screen_dialog_track_name_label)
                        )
                    },
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_tiny)))

                TextField(
                    value = initialArtist,
                    onValueChange = { onArtistChange(it) },
                    label = {
                        Text(
                            text = stringResource(R.string.main_screen_dialog_artist_label)
                        )
                    },
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_tiny)))

//                Text(
//                    text = "${stringResource(R.string.main_screen_dialog_offset_label)}:"
//                )
                Text(
                    text = when {
                        offsetPercent > 100f -> "Громче на ${offsetPercent.toInt() - 100}%"
                        offsetPercent < 100f -> "Тише на ${100 - offsetPercent.toInt()}%"
                        else -> stringResource(R.string.main_screen_without_offset_text)
                    },
                    fontSize = 18.sp,
                )
                SliderWithButtons(
                    value = offsetPercent,
                    onValueChange = { onOffsetChange((it.roundToInt() / 100f)) },
                    valueRange = VOLUME_SLIDER_VALUE_RANGE,
                    steps = VOLUME_SLIDER_STEPS_COUNT,
                    track = { sliderState -> SliderTransparentTrack(sliderState = sliderState) },
                    onRightButtonPress = rightButtonPressAction,
                    onRightButtonHold = onStartIncrementing,
                    onLeftButtonPress = leftButtonPressAction,
                    onLeftButtonHold = onStartDecrementing,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (initialTitle.trim().isNotEmpty()) {
                    val offsetFloat = offsetPercent / 100f

                    onConfirm(
                        initialTitle,
                        initialArtist.ifBlank { null },
                        offsetFloat
                    )
                }
            }) {
                Text(
                    if (isEdit) {
                        stringResource(R.string.main_screen_redacting_dialog_save_label)
                    } else {
                        stringResource(R.string.main_screen_add_dialog_save_label)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.main_screen_add_dialog_dismiss_label))
            }
        }
    )
}