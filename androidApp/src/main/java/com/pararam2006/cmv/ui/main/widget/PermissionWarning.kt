package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.pararam2006.cmv.R

@Composable
fun PermissionWarning(
    onRefresh: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.padding_large))
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.padding_large))) {
            Text(
                text = stringResource(R.string.permission_warning_screen_notification_access_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_tiny)))

            Text(
                text = stringResource(R.string.permission_warning_screen_notifications_access_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_large)))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.permission_warning_screen_already_granted))
                }
                Button(
                    onClick = onOpenPermissionSettings
                ) {
                    Text(stringResource(R.string.permission_warning_screen_grant_permission))
                }
            }
        }
    }
}