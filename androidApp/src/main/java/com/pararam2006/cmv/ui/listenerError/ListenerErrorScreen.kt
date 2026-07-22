package com.pararam2006.cmv.ui.listenerError

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pararam2006.cmv.R
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme

@Composable
fun ListenerErrorScreen(
    onOpenNotificationListenerSettings: () -> Unit,
    onNavigateToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.screenLayout(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_large)))

        Text(
            text = "Требуется разрешение",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

        ErrorCard {
            Text(
                text = stringResource(R.string.listener_error_screen_main_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_large)))

        Button(
            onClick = onOpenNotificationListenerSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.listener_error_screen_to_settings_button)
            )
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

        TextButton(
            onClick = onNavigateToMain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.listener_error_screen_to_main_button)
            )
        }
    }
}

@Composable
private fun ErrorCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun Modifier.screenLayout(): Modifier = this
    .fillMaxSize()
    .padding(dimensionResource(R.dimen.padding_large))

@Preview(showBackground = true)
@Composable
private fun ListenerErrorScreenPreview() {
    CustomMusicVolumeTheme {
        ListenerErrorScreen(
            onOpenNotificationListenerSettings = {},
            onNavigateToMain = {}
        )
    }
}