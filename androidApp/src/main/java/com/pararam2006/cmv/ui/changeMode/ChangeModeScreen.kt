package com.pararam2006.cmv.ui.changeMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pararam2006.cmv.R
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme

enum class AppMode {
    LEARNING, JUST_CHANGING,
}

@Composable
fun ChangeModeScreen(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var icon: ImageVector
    var changeAction: () -> Unit
    var iconContentDescription: String
    var modeTitle: String
    val modeButtonSize = 120.dp
    val modeButtonIconSize = modeButtonSize - (modeButtonSize / 3)
    val modeDesctiption: String

    when (mode) {
        AppMode.LEARNING -> {
            icon = Icons.Default.Analytics
            changeAction = { onModeChange(AppMode.JUST_CHANGING) }
            iconContentDescription = "" //TODO Сделать description тут
            modeTitle = stringResource(R.string.change_mode_screen_learning_title)
            modeDesctiption = stringResource(R.string.change_mode_screen_learning_desc)
        }

        AppMode.JUST_CHANGING -> {
            icon = Icons.AutoMirrored.Filled.VolumeUp
            changeAction = { onModeChange(AppMode.LEARNING) }
            iconContentDescription = "" //TODO Сделать description тут
            modeTitle = stringResource(R.string.change_mode_screen_just_changing_title)
            modeDesctiption = stringResource(R.string.change_mode_screen_just_changing_desc)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // Центрируем содержимое Box
    ) {
        // 1. Эта колонка всегда будет строго по центру экрана
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = changeAction,
                modifier = Modifier.size(modeButtonSize)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(modeButtonIconSize)
                )
            }

            // 2. Используем Box вокруг текста, чтобы зафиксировать его позицию
            // или просто выносим его ниже, не давая ему влиять на центр
            Column(
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.padding_small)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
            ) {
                Text(
                    text = modeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = modeDesctiption,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )


            }
        }

        Text(
            text = "(Нажмите на иконку для изменения)",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimensionResource(R.dimen.padding_medium))
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChangeModeScreenPreview() {
    CustomMusicVolumeTheme() {
        ChangeModeScreen(
            mode = AppMode.LEARNING,
            onModeChange = {},
        )
    }
}