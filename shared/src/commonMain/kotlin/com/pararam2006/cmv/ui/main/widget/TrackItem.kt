package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.ui.Dimens
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.main_screen_offset_text_louder
import custommusicvolume.shared.generated.resources.main_screen_offset_text_quiet
import custommusicvolume.shared.generated.resources.main_screen_without_offset_text
import custommusicvolume.shared.generated.resources.outline_delete_24
import custommusicvolume.shared.generated.resources.outline_refresh_24
import custommusicvolume.shared.generated.resources.outline_search_24
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrackItem(
    track: TrackVolume,
    onEdit: () -> Unit,
    onStartDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    isDeleting: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetText = when {
        track.volumeOffset == 1f -> {
            stringResource(Res.string.main_screen_without_offset_text)
        }

        track.volumeOffset > 1f -> {
            val percentage = ((track.volumeOffset - 1f) * 100).roundToInt()
            "${stringResource(Res.string.main_screen_offset_text_louder)} $percentage%"

        }

        track.volumeOffset < 1f -> {
            val percentage = abs(((track.volumeOffset - 1f) * 100).roundToInt())
            "${stringResource(Res.string.main_screen_offset_text_quiet)} $percentage%"
        }

        else -> {
            ""
        }
    }

    Surface(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.paddingMedium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = track.trackTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 2000,
                        initialDelayMillis = 1000,
                        velocity = 30.dp
                    ),
                )

                track.artistName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 2000,
                            initialDelayMillis = 1000,
                            velocity = 30.dp
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.paddingMedium))

                Text(
                    text = offsetText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isDeleting) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(Dimens.paddingSmall),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.errorContainer,
                    )
                    IconButton(onClick = onCancelDelete) {
                        Icon(
                            painter = painterResource(Res.drawable.outline_refresh_24),
                            contentDescription = "Undo Delete",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        painter = painterResource(Res.drawable.outline_search_24),
                        contentDescription = "Search Track",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onStartDelete) {
                    Icon(
                        painter = painterResource(Res.drawable.outline_delete_24),
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun TrackItemPreview() {
    CustomMusicVolumeTheme {
        TrackItem(
            track = TrackVolume(
                id = 0,
                trackTitle = "Milosc W Zakopanem",
                artistName = "Slavomir",
                volumeOffset = 1f,
            ),
            onEdit = {},
            onStartDelete = {},
            onDownload = {},
            onCancelDelete = {},
            isDeleting = false,
            progress = 1f,
        )
    }
}
