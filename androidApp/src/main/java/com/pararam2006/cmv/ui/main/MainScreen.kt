package com.pararam2006.cmv.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pararam2006.cmv.R
import com.pararam2006.cmv.core.Constants
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.ui.main.widget.PermissionWarning
import com.pararam2006.cmv.ui.main.widget.TrackDialog
import com.pararam2006.cmv.ui.main.widget.TrackItem
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun MainScreen(
    uiState: MainScreenUiState,
    listenerUiState: MainViewModel.ListenerUiState,
    onTrackDelete: (Int) -> Unit,
    onSaveTrackVolume: (String, String?, Float) -> Unit,
    onTrackSearch: (String) -> Unit,
    onCloseAddDialog: () -> Unit,
    onStartEdit: (TrackVolume) -> Unit,
    onStopEdit: () -> Unit,
    onPermissionWarningRefresh: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onDismissHeadsetNotConnectedDialog: () -> Unit,
    onArtistChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onStartIncrementing: () -> Unit,
    onStopIncrementing: () -> Unit,
    onStartDecrementing: () -> Unit,
    onStopDecrementing: () -> Unit,
    onOffsetChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.screenLayout()
    ) {
        if (!listenerUiState.permissionGranted) {
            PermissionWarning(
                onRefresh = onPermissionWarningRefresh,
                onOpenPermissionSettings = onOpenPermissionSettings,
            )
        }
        when (uiState.isTracksLoading) {
            true -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            false -> {
                if (uiState.tracks.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_tiny))
                    ) {
                        items(
                            items = uiState.tracks,
                            key = { it.id }
                        ) { track ->
                            TrackItem(
                                track = track,
                                onEdit = { onStartEdit(track) },
                                onDelete = { onTrackDelete(track.id) },
                                onDownload = {
                                    val query =
                                        "${track.trackTitle} ${track.artistName ?: ""} скачать".trim()
                                    onTrackSearch(query)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.main_screen_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        MainScreenDialogs(
            uiState = uiState,
            onSaveTrackVolume = onSaveTrackVolume,
            onCloseAddDialog = onCloseAddDialog,
            onStopEdit = onStopEdit,
            onDismissHeadsetNotConnectedDialog = onDismissHeadsetNotConnectedDialog,
            onArtistChange = onArtistChange,
            onTitleChange = onTitleChange,
            onStartIncrementing = onStartIncrementing,
            onStopIncrementing = onStopIncrementing,
            onStartDecrementing = onStartDecrementing,
            onStopDecrementing = onStopDecrementing,
            onOffsetChange = onOffsetChange,
        )
    }
}

@Composable
private fun Modifier.screenLayout(): Modifier = this
    .fillMaxSize()
    .padding(dimensionResource(R.dimen.padding_medium))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenDialogs(
    uiState: MainScreenUiState,
    onSaveTrackVolume: (String, String?, Float) -> Unit,
    onCloseAddDialog: () -> Unit,
    onStopEdit: () -> Unit,
    onDismissHeadsetNotConnectedDialog: () -> Unit,
    onArtistChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onStartIncrementing: () -> Unit,
    onStopIncrementing: () -> Unit,
    onStartDecrementing: () -> Unit,
    onStopDecrementing: () -> Unit,
    onOffsetChange: (Float) -> Unit,
) {
    if (uiState.showAddDialog) {
        TrackDialog(
            initialTitle = uiState.currentPlayingTrack ?: "",
            initialArtist = uiState.currentPlayingArtist ?: "",
            initialOffset = uiState.offsetToNewTrack,
            enabled = false,
            onDismiss = onCloseAddDialog,
            onConfirm = { title, artist, offset ->
                onSaveTrackVolume(title, artist, offset)
                onCloseAddDialog()
            },
            onTitleChange = onTitleChange,
            onArtistChange = onArtistChange,
            onStartIncrementing = onStartIncrementing,
            onStopIncrementing = onStopIncrementing,
            onStartDecrementing = onStartDecrementing,
            onStopDecrementing = onStopDecrementing,
            onOffsetChange = onOffsetChange,
        )
    }

    if (uiState.showHeadsetNotConnectedDialog) {
        AlertDialog(
            title = {
                Text(
                    text = stringResource(R.string.main_screen_no_headphones_title)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.main_screen_no_headphones_text),
                    textAlign = TextAlign.Center
                )
            },
            onDismissRequest = onDismissHeadsetNotConnectedDialog,
            confirmButton = {
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissHeadsetNotConnectedDialog) {
                        Text(
                            text = stringResource(R.string.main_screen_ok)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        )
    }

    if (uiState.showEditDialog) {
        val editingTrack = uiState.dialogTrack
        val roundedOffset =
            BigDecimal((editingTrack?.volumeOffset ?: 1f).toDouble()).setScale(
                Constants.NUMBERS_AFTER_DOT,
                RoundingMode.HALF_UP
            )
                .toFloat() // Округление до двух знаков после запятой

        TrackDialog(
            initialTitle = editingTrack?.trackTitle ?: "",
            initialArtist = editingTrack?.artistName ?: "",
            initialOffset = roundedOffset,
            onDismiss = onStopEdit,
            onConfirm = { title, artist, offset ->
                onSaveTrackVolume(title, artist, offset)
                onStopEdit()
            },
            onTitleChange = onTitleChange,
            onArtistChange = onArtistChange,
            onStartIncrementing = onStartIncrementing,
            onStopIncrementing = onStopIncrementing,
            onStartDecrementing = onStartDecrementing,
            onStopDecrementing = onStopDecrementing,
            onOffsetChange = onOffsetChange,
        )
    }
}
