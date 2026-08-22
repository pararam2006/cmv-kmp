package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.pararam2006.cmv.core.Constants.DIALOG_OFFSET_STEP
import com.pararam2006.cmv.core.Constants.VOLUME_SLIDER_STEPS_COUNT
import com.pararam2006.cmv.core.Constants.VOLUME_SLIDER_VALUE_RANGE
import com.pararam2006.cmv.core.ui.SliderTransparentTrack
import com.pararam2006.cmv.core.ui.SliderWithButtons
import com.pararam2006.cmv.ui.Dimens
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDialog(
    initialTitle: String,
    initialArtist: String,
    modifier: Modifier = Modifier,
    initialOffset: Float = 0f,
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
    val offsetDb = initialOffset.coerceIn(VOLUME_SLIDER_VALUE_RANGE)
    val displayOffsetDb = (offsetDb * 2).roundToInt() / 2f


    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) {
                    stringResource(Res.string.main_screen_redacting_dialog_title)
                } else {
                    stringResource(Res.string.main_screen_add_dialog_title)
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
                            text = stringResource(Res.string.main_screen_dialog_track_name_label)
                        )
                    },
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(Dimens.paddingTiny))

                TextField(
                    value = initialArtist,
                    onValueChange = { onArtistChange(it) },
                    label = {
                        Text(
                            text = stringResource(Res.string.main_screen_dialog_artist_label)
                        )
                    },
                    enabled = enabled
                )

                Spacer(modifier = Modifier.height(Dimens.paddingTiny))

                Text(
                    text = when {
                        displayOffsetDb > 0f -> "Громче на +${displayOffsetDb.asDbText()} dB"
                        displayOffsetDb < 0f -> "Тише на ${(-displayOffsetDb).asDbText()} dB"
                        else -> stringResource(Res.string.main_screen_without_offset_text)
                    },
                    fontSize = 18.sp,
                )
                SliderWithButtons(
                    value = offsetDb,
                    onValueChange = { onOffsetChange((it * 2).roundToInt() / 2f) },
                    valueRange = VOLUME_SLIDER_VALUE_RANGE,
                    steps = VOLUME_SLIDER_STEPS_COUNT,
                    track = { sliderState -> SliderTransparentTrack(sliderState = sliderState) },
                    onRightButtonPress = {
                        onOffsetChange((offsetDb + DIALOG_OFFSET_STEP).coerceIn(VOLUME_SLIDER_VALUE_RANGE))
                    },
                    onRightButtonHold = onStartIncrementing,
                    onRightButtonHoldEnd = onStopIncrementing,
                    onLeftButtonPress = {
                        onOffsetChange((offsetDb - DIALOG_OFFSET_STEP).coerceIn(VOLUME_SLIDER_VALUE_RANGE))
                    },
                    onLeftButtonHold = onStartDecrementing,
                    onLeftButtonHoldEnd = onStopDecrementing,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (initialTitle.trim().isNotEmpty()) {
                    onConfirm(
                        initialTitle,
                        initialArtist.ifBlank { null },
                        displayOffsetDb
                    )
                }
            }) {
                Text(
                    if (isEdit) {
                        stringResource(Res.string.main_screen_redacting_dialog_save_label)
                    } else {
                        stringResource(Res.string.main_screen_add_dialog_save_label)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.main_screen_add_dialog_dismiss_label))
            }
        }
    )
}

private fun Float.asDbText(): String =
    if (this % 1f == 0f) toInt().toString() else toString()
