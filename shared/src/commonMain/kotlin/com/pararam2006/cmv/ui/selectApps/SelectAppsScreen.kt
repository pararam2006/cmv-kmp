package com.pararam2006.cmv.ui.selectApps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.ui.Dimens
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
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    } else if (loadFailed) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(Res.string.select_apps_screen_load_error))
            Button(onClick = onRetry) {
                Text(text = stringResource(Res.string.select_apps_screen_retry))
            }
        }
    } else if (apps.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(Res.string.select_apps_screen_empty))
        }
    } else {
        LazyColumn(
            modifier = modifier
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
                    onToogle = { packageName, newState -> onToogle(packageName, newState) },
                )

                Spacer(modifier = Modifier.height(10.dp))
            }
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
