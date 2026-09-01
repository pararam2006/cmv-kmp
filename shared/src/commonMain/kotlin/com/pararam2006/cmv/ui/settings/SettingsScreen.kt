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
import androidx.compose.ui.tooling.preview.Preview
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.core.ui.SettingsItem
import com.pararam2006.cmv.ui.Dimens
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import org.jetbrains.compose.resources.stringResource
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSetShowSystemVolumeUi: (Boolean) -> Unit,
    onSliderPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToChangeMode: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onSetVolumeJumpProtectionEnabled: (Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.screenLayout(scrollState),
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingMedium)
    ) {
        SettingsCard {
            SettingsItem(
                title = stringResource(Res.string.settings_screen_show_volume_ui),
                trailingContent = { trailingModifier ->
                    Switch(
                        checked = uiState.showSystemVolumeUi,
                        onCheckedChange = onSetShowSystemVolumeUi,
                        modifier = trailingModifier.padding(
                            start = Dimens.paddingSmall,
                            end = Dimens.paddingSmall
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
                steps = Constants.SETTINGS_BASIC_VOLUME_REDACTING_TIME_STEP,
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
                trailingContent = { trailingModifier ->
                    IconButton(
                        onClick = onNavigateToChangeMode,
                        modifier = trailingModifier
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.outline_arrow_forward_24),
                            contentDescription = stringResource(Res.string.settings_screen_change_workmode_button_desc)
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
                trailingContent = { trailingModifier ->
                    IconButton(
                        onClick = onNavigateToApps,
                        modifier = trailingModifier
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.outline_arrow_forward_24),
                            contentDescription = stringResource(Res.string.settings_screen_apps_button_desc)
                        )
                    }
                },
            )
        }

        SettingsCard(
            modifier = Modifier.clickable(onClick = onNavigateToDebug)
        ) {
            SettingsItem(
                title = stringResource(Res.string.settings_screen_debug),
                trailingContent = { trailingModifier ->
                    IconButton(
                        onClick = onNavigateToDebug,
                        modifier = trailingModifier,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.outline_arrow_forward_24),
                            contentDescription = stringResource(Res.string.settings_screen_debug_button_desc),
                        )
                    }
                },
            )
        }

        SettingsCard {
            SettingsItem(
                title = stringResource(Res.string.settings_screen_volume_jump_protection),
                trailingContent = { trailingModifier ->
                    Switch(
                        checked = uiState.volumeJumpProtectionEnabled,
                        onCheckedChange = onSetVolumeJumpProtectionEnabled,
                        modifier = trailingModifier.padding(
                            start = Dimens.paddingSmall,
                            end = Dimens.paddingSmall
                        )
                    )
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
            modifier = Modifier.padding(Dimens.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingSmall),
            content = content
        )
    }
}

@Composable
private fun Modifier.screenLayout(scrollState: ScrollState): Modifier = this
    .fillMaxSize()
    .verticalScroll(scrollState)
    .padding(Dimens.paddingMedium)

@Composable
@Preview(
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF000000
)
private fun SettingsScreenPreview() {
    CustomMusicVolumeTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            onSetShowSystemVolumeUi = {},
            onSliderPositionChange = {},
            modifier = Modifier,
            onNavigateToChangeMode = {},
            onNavigateToApps = {},
            onNavigateToDebug = {},
            onSetVolumeJumpProtectionEnabled = {},
        )
    }
}
