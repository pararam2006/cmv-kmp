package com.pararam2006.cmv.ui.main.widget

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pararam2006.cmv.R
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrackItem(
    track: TrackVolume,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDeleting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(1f) }
    val offsetText = when {
        track.volumeOffset == 1f -> {
            stringResource(R.string.main_screen_without_offset_text)
        }

        track.volumeOffset > 1f -> {
            val percentage = ((track.volumeOffset - 1f) * 100).roundToInt()
            "${stringResource(R.string.main_screen_offset_text_louder)} $percentage%"

        }

        track.volumeOffset < 1f -> {
            val percentage = abs(((track.volumeOffset - 1f) * 100).roundToInt())
            "${stringResource(R.string.main_screen_offset_text_quiet)} $percentage%"
        }

        else -> {
            ""
        }
    }
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            var timeLeft = 3000L
            val step = 16L
            while (timeLeft > 0) {
                delay(step)
                timeLeft -= step
                progress = timeLeft / 3000f
            }
            if (isDeleting) {
                onDelete()
                isDeleting = false // Reset state just in case
            }
        } else {
            progress = 1f
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
                .padding(dimensionResource(R.dimen.padding_medium))
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

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

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
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.errorContainer,
                    )
                    IconButton(onClick = { isDeleting = false }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Undo Delete",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Track",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { isDeleting = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
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
private fun TrackItemQuietPreview() {
    CustomMusicVolumeTheme {
        TrackItem(
            track = TrackVolume(
                id = 0,
                trackTitle = "Milosc W Zakopanem",
                artistName = "Slavomir",
                volumeOffset = 0f,
            ),
            onEdit = {},
            onDelete = {},
            onDownload = {},
        )
    }
}

@Preview
@Composable
private fun TrackItemLoudPreview() {
    CustomMusicVolumeTheme {
        TrackItem(
            track = TrackVolume(
                id = 0,
                trackTitle = "Milosc W Zakopanem",
                artistName = "Slavomir",
                volumeOffset = 1.4f,
            ),
            onEdit = {},
            onDelete = {},
            onDownload = {},
        )
    }
}

@Preview
@Composable
private fun TrackItemNopePreview() {
    CustomMusicVolumeTheme {
        TrackItem(
            track = TrackVolume(
                id = 0,
                trackTitle = "Milosc W Zakopanem",
                artistName = "Slavomir",
                volumeOffset = 0f,
            ),
            onEdit = {},
            onDelete = {},
            onDownload = {},
        )
    }
}