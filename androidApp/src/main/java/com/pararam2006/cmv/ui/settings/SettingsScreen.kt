package com.pararam2006.cmv.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.pararam2006.cmv.R
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.core.Constants.SETTINGS_BASIC_VOLUME_REDACTING_TIME_STEP
import com.pararam2006.cmv.core.ui.SettingsItem
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSetShowSystemVolumeUi: (Boolean) -> Unit,
    onSliderPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToChangeMode: () -> Unit,
    onNavigateToApps: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.screenLayout(scrollState),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium))
    ) {
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_screen_show_volume_ui),
                trailingContent = {
                    Switch(
                        checked = uiState.showSystemVolumeUi,
                        onCheckedChange = onSetShowSystemVolumeUi,
                        modifier = Modifier.padding(
                            start = dimensionResource(R.dimen.padding_small),
                            end = dimensionResource(R.dimen.padding_small)
                        )
                    )
                },
            )
        }

        SettingsCard {
            Text(
                text = "Время обучения и сохранения: ${uiState.sliderPosition.toInt()} сек",
                style = MaterialTheme.typography.bodyLarge
            )

            Slider(
                value = uiState.sliderPosition,
                onValueChange = onSliderPositionChange,
                steps = SETTINGS_BASIC_VOLUME_REDACTING_TIME_STEP,
                valueRange = Constants.SETTINGS_BASIC_VOLUME_REDACTING_TIME_RANGE,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard(
            modifier = Modifier.clickable {
                onNavigateToChangeMode()
            }
        ) {
            SettingsItem(
                title = "Выбрать режим работы",
                trailingContent = {
                    IconButton(
                        onClick = onNavigateToChangeMode,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.settings_screen_change_workmode_button_desc)
                        )
                    }
                },
            )
        }

        SettingsCard(
            modifier = Modifier.clickable {
                onNavigateToApps()
            }
        ) {
            SettingsItem(
                title = "Выбрать приложение",
                trailingContent = {
                    IconButton(
                        onClick = onNavigateToApps,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.settings_screen_apps_button_desc)
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            content = content
        )
    }
}

@Composable
private fun Modifier.screenLayout(scrollState: ScrollState): Modifier = this
    .fillMaxSize()
    .verticalScroll(scrollState)
    .padding(dimensionResource(R.dimen.padding_medium))

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CustomMusicVolumeTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            onSetShowSystemVolumeUi = {},
            onSliderPositionChange = {},
            onNavigateToChangeMode = {},
            onNavigateToApps = {},
        )
    }
}