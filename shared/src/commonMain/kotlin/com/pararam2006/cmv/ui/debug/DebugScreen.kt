package com.pararam2006.cmv.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pararam2006.cmv.domain.manager.activePlayingTimeMs
import com.pararam2006.cmv.domain.manager.isSavingThresholdReached
import com.pararam2006.cmv.domain.manager.timeSinceLastManualChangeMs
import com.pararam2006.cmv.ui.Dimens
import kotlin.math.roundToInt

@Composable
fun DebugScreen(
    uiState: DebugScreenUiState,
    modifier: Modifier = Modifier,
) {
    val service = uiState.serviceState
    val learning = uiState.volumeState
    val snapshot = service.systemVolume ?: learning.currentSystemVolume
    val route = service.audioRoute
    val activePackage = service.activeSessionPackageName
    val activeApp = service.selectedApps.firstOrNull { it.packageName == activePackage }
    val thresholdMs = uiState.learningTimeSeconds * 1_000L
    val activePlayingTimeMs = learning.activePlayingTimeMs(uiState.observedAtMs)
    val sinceManualChangeMs = learning.timeSinceLastManualChangeMs(uiState.observedAtMs)
    val canProcessVolume = learning.currentTrackTitle != null &&
        learning.isPlaying && learning.isHeadsetConnected && learning.hasAudioFocus

    val serviceStatus = when {
        !uiState.serviceSupported -> "не поддерживается"
        service.isStarting -> "запускается"
        service.isConnected && !service.userStopped -> "работает"
        service.userStopped -> "остановлен пользователем"
        else -> "отключён"
    }

    val sections = listOf(
        DebugSectionData(
            title = "Сервис и разрешения",
            rows = listOf(
                DebugRowData("Состояние", serviceStatus),
                DebugRowData("Runtime status", service.runtimeState.status.name),
                DebugRowData("Runtime message", service.runtimeState.message.debugValue()),
                DebugRowData("Платформа поддерживается", uiState.serviceSupported.yesNo()),
                DebugRowData("Разрешение выдано", uiState.notificationPermissionGranted.yesNo()),
                DebugRowData("isConnected", service.isConnected.toString()),
                DebugRowData("isStarting", service.isStarting.toString()),
                DebugRowData("userStopped", service.userStopped.toString()),
                DebugRowData("restartResult", service.restartResult.debugValue()),
                DebugRowData("Последнее обновление, epoch ms", uiState.observedAtMs.toString()),
            ),
        ),
        DebugSectionData(
            title = "Приложение и воспроизведение",
            rows = listOf(
                DebugRowData("Активное приложение", activeApp?.label.debugValue()),
                DebugRowData("Пакет активной сессии", activePackage.debugValue()),
                DebugRowData(
                    "Сессия входит в выбранные",
                    (activePackage != null && activeApp != null).yesNo(),
                ),
                DebugRowData(
                    "Выбранные приложения (${service.selectedApps.size})",
                    service.selectedApps.joinToString("\n") { "${it.label} — ${it.packageName}" }
                        .ifEmpty { "—" },
                ),
                DebugRowData("Трек от платформы", service.currentTrackTitle.debugValue()),
                DebugRowData("Артист от платформы", service.currentTrackArtist.debugValue()),
                DebugRowData("Трек принят координатором", uiState.coordinatorTrack?.title.debugValue()),
                DebugRowData("Артист принят координатором", uiState.coordinatorTrack?.artist.debugValue()),
                DebugRowData("Поколение координатора", uiState.coordinatorTrack?.generation.debugValue()),
                DebugRowData("Трек в алгоритме", learning.currentTrackTitle.debugValue()),
                DebugRowData("Артист в алгоритме", learning.currentTrackArtist.debugValue()),
                DebugRowData("Пакет в алгоритме", learning.activeSessionPackageName.debugValue()),
                DebugRowData("isPlaying", learning.isPlaying.toString()),
                DebugRowData("hasAudioFocus", learning.hasAudioFocus.toString()),
            ),
        ),
        DebugSectionData(
            title = "Аудиоустройство",
            rows = listOf(
                DebugRowData("Backend", route?.backendName.debugValue()),
                DebugRowData("Маршрут", route?.name.debugValue()),
                DebugRowData("ID маршрута", route?.id.debugValue()),
                DebugRowData("Маршрут — наушники", route?.isHeadphones.debugValue()),
                DebugRowData("Детектор видит наушники", uiState.detectorSeesHeadphones.debugValue()),
                DebugRowData("Алгоритм видит наушники", learning.isHeadsetConnected.toString()),
            ),
        ),
        DebugSectionData(
            title = "Системная громкость",
            rows = if (snapshot == null) {
                listOf(DebugRowData("Снимок громкости", "нет данных"))
            } else {
                listOf(
                    DebugRowData("Текущий уровень", snapshot.currentVolume.toString()),
                    DebugRowData("Минимальный уровень", "0"),
                    DebugRowData("Максимальный уровень", snapshot.maxVolume.toString()),
                    DebugRowData("Звук выключен", snapshot.isMuted.yesNo()),
                DebugRowData("Текущая громкость", snapshot.currentVolumeDb.db()),
                    DebugRowData("Минимальная громкость", snapshot.minVolumeDb.db()),
                    DebugRowData("Максимальная громкость", snapshot.maxVolumeDb.db()),
                    DebugRowData("Локальный шаг", snapshot.volumeStepDb.db()),
                    DebugRowData(
                        "Источник снимка",
                        if (service.systemVolume != null) "платформа" else "алгоритм",
                    ),
                    DebugRowData(
                        "Кривая уровня → dB (${snapshot.volumeDbByStep.size} точек)",
                        snapshot.volumeDbByStep
                            .mapIndexed { index, db -> "$index:${db.db()}" }
                            .chunked(6)
                            .joinToString("\n") { it.joinToString("   ") },
                    ),
                )
            },
        ),
        DebugSectionData(
            title = "Алгоритм и обучение",
            rows = listOf(
                DebugRowData("Режим", uiState.appMode.name),
                DebugRowData("Время обучения", "${uiState.learningTimeSeconds} с"),
                DebugRowData("Показывать системный UI", uiState.showSystemVolumeUi.yesNo()),
                DebugRowData("Условия обработки выполнены", canProcessVolume.yesNo()),
                DebugRowData("Базовая громкость", learning.baseVolumeDb.db()),
                DebugRowData("Текущий offset", learning.currentLearnedOffsetDb.db()),
                DebugRowData("Ожидаемая программная громкость", learning.expectedProgrammaticVolumeDb.db()),
                DebugRowData("Offset изменён пользователем", learning.hasLearnedOffsetChanged.yesNo()),
                DebugRowData("Порог сохранения достигнут", learning.isSavingThresholdReached(uiState.observedAtMs, thresholdMs).yesNo()),
                DebugRowData("Активное время трека", activePlayingTimeMs.duration()),
                DebugRowData("До конца окна обучения", (thresholdMs - activePlayingTimeMs).coerceAtLeast(0).duration()),
                DebugRowData("После ручного изменения", sinceManualChangeMs.duration()),
                DebugRowData("trackStartTimeMs", learning.trackStartTimeMs.toString()),
                DebugRowData("currentPlayChunkStartMs", learning.currentPlayChunkStartMs.toString()),
                DebugRowData("accumulatedPlayingTimeMs", learning.accumulatedPlayingTimeMs.toString()),
                DebugRowData("lastManualVolumeChangeTimeMs", learning.lastManualVolumeChangeTimeMs.toString()),
                DebugRowData("trackGeneration", learning.trackGeneration.toString()),
            ),
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingMedium),
    ) {
        item {
            Text(
                text = "Значения обновляются автоматически. Названия переменных оставлены рядом с описаниями, чтобы их было проще сопоставлять с логами.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(sections, key = { it.title }) { section ->
            DebugSection(section)
        }
    }
}

@Composable
private fun DebugSection(section: DebugSectionData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = CardDefaults.outlinedCardBorder(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(Dimens.paddingMedium)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Dimens.paddingTiny),
            )
            section.rows.forEachIndexed { index, row ->
                DebugRow(row)
                if (index != section.rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimens.paddingExtraTiny),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugRow(row: DebugRowData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingMedium),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        SelectionContainer(modifier = Modifier.weight(1.1f)) {
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private data class DebugSectionData(
    val title: String,
    val rows: List<DebugRowData>,
)

private data class DebugRowData(
    val label: String,
    val value: String,
)

private fun Boolean.yesNo(): String = if (this) "да" else "нет"
private fun Any?.debugValue(): String = this?.toString()?.ifBlank { "—" } ?: "—"

private fun Float.db(): String {
    if (!isFinite()) return "—"
    val rounded = (this * 100f).roundToInt() / 100f
    return "$rounded dB"
}

private fun Long.duration(): String = "${this / 1_000}.${(this % 1_000) / 100} с ($this мс)"
