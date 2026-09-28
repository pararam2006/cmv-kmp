package com.pararam2006.cmv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pararam2006.cmv.ui.Dimens
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.select_apps_screen_disable_service
import custommusicvolume.shared.generated.resources.select_apps_screen_service_enabled
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServiceEnabledOverlay(
    onStopService: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {} // Без эффекта клика
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.select_apps_screen_service_enabled),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium // Сделаем текст выразительнее поверх размытия
            )

            Spacer(modifier = Modifier.height(Dimens.paddingMedium))

            Button(onClick = onStopService) {
                Text(text = stringResource(Res.string.select_apps_screen_disable_service))
            }
        }
    }
}
