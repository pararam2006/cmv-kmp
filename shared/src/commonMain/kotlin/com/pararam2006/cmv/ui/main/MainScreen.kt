package com.pararam2006.cmv.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pararam2006.cmv.domain.model.TrackVolume
import com.pararam2006.cmv.ui.Dimens
import com.pararam2006.cmv.ui.main.widget.PermissionWarning
import com.pararam2006.cmv.ui.main.widget.TrackDialog
import com.pararam2006.cmv.ui.main.widget.TrackItem
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.*

@Composable
fun MainScreen(
    uiState: MainScreenUiState,
    listenerUiState: MainViewModel.ListenerUiState,
    onStartTrackDelete: (Int) -> Unit,
    onCancelTrackDelete: (Int) -> Unit,
    onSaveTrackVolume: (String, String?, Float, Int) -> Unit,
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
    manualTrackEntryEnabled: Boolean,
) {
    Column(
        modifier = Modifier.screenLayout()
    ) {
        if (listenerUiState.serviceSupported && !listenerUiState.permissionGranted) {
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
                        verticalArrangement = Arrangement.spacedBy(Dimens.paddingTiny)
                    ) {
                        items(
                            items = uiState.tracks,
                            key = { it.id }
                        ) { track ->
                            TrackItem(
                                track = track,
                                onEdit = { onStartEdit(track) },
                                onStartDelete = { onStartTrackDelete(track.id) },
                                onCancelDelete = { onCancelTrackDelete(track.id) },
                                isDeleting = track.id in uiState.trackDeletionProgress,
                                progress = uiState.trackDeletionProgress[track.id] ?: 1f,
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
                            text = stringResource(Res.string.main_screen_empty),
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
            manualTrackEntryEnabled = manualTrackEntryEnabled,
        )
    }
}

@Composable
private fun Modifier.screenLayout(): Modifier = this
    .fillMaxSize()
    .padding(Dimens.paddingMedium)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenDialogs(
    uiState: MainScreenUiState,
    onSaveTrackVolume: (String, String?, Float, Int) -> Unit,
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
    manualTrackEntryEnabled: Boolean,
) {
    if (uiState.showAddDialog) {
        TrackDialog(
            initialTitle = uiState.currentPlayingTrack ?: "",
            initialArtist = uiState.currentPlayingArtist ?: "",
            initialOffset = uiState.offsetToNewTrack,
            enabled = manualTrackEntryEnabled,
            onDismiss = onCloseAddDialog,
            onConfirm = { title, artist, offset ->
                onSaveTrackVolume(title, artist, offset, 0)
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
                    text = stringResource(Res.string.main_screen_no_headphones_title)
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.main_screen_no_headphones_text),
                    textAlign = TextAlign.Center
                )
            },
            onDismissRequest = onDismissHeadsetNotConnectedDialog,
            confirmButton = {
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissHeadsetNotConnectedDialog) {
                        Text(
                            text = stringResource(Res.string.main_screen_ok)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        )
    }

    if (uiState.showEditDialog) {
        val editingTrack = uiState.dialogTrack
        val roundedOffset = ((editingTrack?.volumeOffsetDb ?: 0f) * 2).roundToInt() / 2f

        TrackDialog(
            initialTitle = editingTrack?.trackTitle ?: "",
            initialArtist = editingTrack?.artistName ?: "",
            initialOffset = roundedOffset,
            isEdit = true,
            onDismiss = onStopEdit,
            onConfirm = { title, artist, offset ->
                onSaveTrackVolume(title, artist, offset, editingTrack?.id ?: 0)
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
