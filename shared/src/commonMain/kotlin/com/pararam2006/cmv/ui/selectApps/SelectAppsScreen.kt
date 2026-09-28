package com.pararam2006.cmv.ui.selectApps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pararam2006.cmv.core.ui.ServiceEnabledOverlay
import com.pararam2006.cmv.core.ui.SimpleCenteredColumn
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.ui.Dimens
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.select_apps_screen_empty
import custommusicvolume.shared.generated.resources.select_apps_screen_load_error
import custommusicvolume.shared.generated.resources.select_apps_screen_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun SelectAppsScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onToogle: (String, Boolean) -> Unit,
    onStopService: () -> Unit,
    isServiceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val contentModifier = if (isServiceEnabled) modifier.blur(15.dp) else modifier

        Box(modifier = contentModifier.fillMaxSize()) {
            when {
                isLoading -> {
                    SimpleCenteredColumn {
                        CircularProgressIndicator()
                    }
                }

                loadFailed -> {
                    SimpleCenteredColumn {
                        Text(text = stringResource(Res.string.select_apps_screen_load_error))
                        Button(onClick = onRetry) {
                            Text(text = stringResource(Res.string.select_apps_screen_retry))
                        }
                    }
                }

                apps.isEmpty() -> {
                    SimpleCenteredColumn {
                        Text(text = stringResource(Res.string.select_apps_screen_empty))
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimens.paddingMedium),
                        verticalArrangement = Arrangement.spacedBy(Dimens.paddingMedium),
                    ) {
                        items(
                            items = apps,
                            key = { it.packageName },
                        ) { app ->
                            SelectAppItem(
                                label = app.label,
                                iconUri = app.iconUri,
                                packageName = app.packageName,
                                selected = app.selected,
                                onToogle = { packageName, newState ->
                                    onToogle(
                                        packageName,
                                        newState
                                    )
                                },
                            )

                            Spacer(modifier = Modifier.height(Dimens.paddingSmall))
                        }
                    }
                }
            }
        }

        if (isServiceEnabled) {
            ServiceEnabledOverlay(
                onStopService = onStopService,
            )
        }
    }
}

@Composable
private fun SelectAppItem(
    label: String,
    iconUri: String,
    packageName: String,
    selected: Boolean,
    onToogle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
    ) {
        AsyncImage(
            model = iconUri,
            contentDescription = "Иконка $label",
            modifier = Modifier.size(50.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        key(packageName) {
            Switch(
                checked = selected,
                onCheckedChange = { newState ->
                    onToogle(packageName, newState)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectAppScreenWithOverlayPreview() {
    CustomMusicVolumeTheme {
        SelectAppsScreen(
            apps = listOf(
                AppInfo(
                    label = "Некий app",
                    iconUri = "android.resource://android/drawable/ic_launcher_foreground",
                    packageName = "com.example.pisun1",
                    name = "Некий app2",
                    selected = true,
                ),
                AppInfo(
                    label = "Некий app",
                    iconUri = "android.resource://android/drawable/ic_launcher_foreground",
                    packageName = "com.example.pisun2",
                    name = "Некий app2",
                    selected = false,
                ),
                AppInfo(
                    label = "Некий app",
                    iconUri = "android.resource://android/drawable/ic_launcher_foreground",
                    packageName = "com.example.pisun3",
                    name = "Некий app2",
                    selected = true,
                )
            ),
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onToogle = { string, boolean -> },
            onStopService = {},
            isServiceEnabled = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectAppScreenWithoutOverlayPreview() {
    CustomMusicVolumeTheme {
        SelectAppsScreen(
            apps = listOf(
                AppInfo(
                    label = "Некий app1",
                    iconUri = "",
                    packageName = "com.example.application1",
                    name = "Некий app1",
                    selected = true,
                ),
                AppInfo(
                    label = "Некий app2",
                    iconUri = "",
                    packageName = "com.example.application2",
                    name = "Некий app2",
                    selected = false,
                ),
                AppInfo(
                    label = "Некий app3",
                    iconUri = "android.resource://android/drawable/ic_launcher_foreground",
                    packageName = "com.example.application3",
                    name = "Некий app3",
                    selected = true,
                )
            ),
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onToogle = { string, boolean -> },
            onStopService = {},
            isServiceEnabled = false,
        )
    }
}