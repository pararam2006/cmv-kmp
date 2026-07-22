package com.pararam2006.cmv.ui.selectApps

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pararam2006.cmv.R
import com.pararam2006.cmv.domain.model.AppInfo

@Composable
fun SelectAppsScreen(
    apps: List<AppInfo>,
    onToogle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
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
    iconUri: Uri,
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

        // Workaround: M3 Switch ≤1.4.0 animates on LazyColumn reuse (b/455909150).
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