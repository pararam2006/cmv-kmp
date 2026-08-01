package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pararam2006.cmv.ui.Dimens
import org.jetbrains.compose.resources.stringResource
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.*

@Composable
fun PermissionWarning(
    onRefresh: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.paddingLarge)
    ) {
        Column(modifier = Modifier.padding(Dimens.paddingLarge)) {
            Text(
                text = stringResource(Res.string.permission_warning_screen_notification_access_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(Dimens.paddingTiny))

            Text(
                text = stringResource(Res.string.permission_warning_screen_notifications_access_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(Dimens.paddingLarge))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(Res.string.permission_warning_screen_already_granted))
                }
                Button(
                    onClick = onOpenPermissionSettings
                ) {
                    Text(stringResource(Res.string.permission_warning_screen_grant_permission))
                }
            }
        }
    }
}
