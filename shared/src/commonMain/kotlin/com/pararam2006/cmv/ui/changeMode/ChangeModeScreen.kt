package com.pararam2006.cmv.ui.changeMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.ui.Dimens
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.change_mode_screen_just_changing_desc
import custommusicvolume.shared.generated.resources.change_mode_screen_just_changing_title
import custommusicvolume.shared.generated.resources.change_mode_screen_learning_desc
import custommusicvolume.shared.generated.resources.change_mode_screen_learning_title
import custommusicvolume.shared.generated.resources.outline_analytics_24
import custommusicvolume.shared.generated.resources.outline_volume_up_24
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChangeModeScreen(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon: Painter
    val changeAction: () -> Unit
    val iconContentDescription: String = ""
    val modeTitle: String
    val modeButtonSize = 120.dp
    val modeButtonIconSize = modeButtonSize - (modeButtonSize / 3)
    val modeDescription: String

    when (mode) {
        AppMode.LEARNING -> {
            icon = painterResource(Res.drawable.outline_analytics_24)
            changeAction = { onModeChange(AppMode.JUST_CHANGING) }
            modeTitle = stringResource(Res.string.change_mode_screen_learning_title)
            modeDescription = stringResource(Res.string.change_mode_screen_learning_desc)
        }

        AppMode.JUST_CHANGING -> {
            icon = painterResource(Res.drawable.outline_volume_up_24)
            changeAction = { onModeChange(AppMode.LEARNING) }
            modeTitle = stringResource(Res.string.change_mode_screen_just_changing_title)
            modeDescription = stringResource(Res.string.change_mode_screen_just_changing_desc)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = changeAction,
                modifier = Modifier.size(modeButtonSize)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(modeButtonIconSize)
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = Dimens.paddingSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingSmall)
            ) {
                Text(
                    text = modeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = "(Нажмите на иконку для изменения)",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.paddingMedium)
        )
    }
}
