package com.pararam2006.cmv.data.manager

import com.pararam2006.cmv.core.Constants.TINY_DELAY
import com.pararam2006.cmv.domain.manager.VolumeCommand
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.manager.activePlayingTimeMs
import com.pararam2006.cmv.domain.manager.isSavingThresholdReached
import com.pararam2006.cmv.domain.manager.timeSinceLastManualChangeMs
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.domain.model.VolumeOffsetModel
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.platform.SystemVolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private sealed interface LearningEvent {
    data class TrackChanged(
        val title: String,
        val artist: String?,
        val volumeOffset: Float,
        val offsetModel: VolumeOffsetModel,
        val systemVolume: SystemVolumeSnapshot,
        val trackGeneration: Long,
    ) : LearningEvent

    data class VolumeChanged(val systemVolume: SystemVolumeSnapshot) : LearningEvent
    data class PlaybackChanged(val isPlaying: Boolean) : LearningEvent
    object SaveTimerStart : LearningEvent
    object SaveTimerCancel : LearningEvent
    object SaveIfNeeded : LearningEvent
    object SessionDetached : LearningEvent
    data class ActiveSessionChanged(val packageName: String?) : LearningEvent
    data class HeadsetChanged(val isConnected: Boolean) : LearningEvent
    data class AudioFocusChanged(val hasFocus: Boolean) : LearningEvent
    object ServiceStopped : LearningEvent
    class AppModeChanged(val newMode: AppMode) : LearningEvent
}

/**
 * Реализация менеджера обучения громкости.
 * Отвечает за:
 * 1. Автоматическую установку громкости при смене трека.
 * 2. Обучение: запоминание специфических предпочтений пользователя для каждого трека.
 * 3. Глобальную адаптацию: подстройку базового уровня громкости под внешние условия.
 */
class VolumeLearningManagerImpl(
    private val saveTrackVolumeUseCase: SaveTrackVolumeUseCase,
    private val appModeFlow: StateFlow<AppMode>,
    private val learningTimeSeconds: () -> Int,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val logger: (String) -> Unit = {},
) : VolumeLearningManager {

    private val instanceId = hashCode()
    private val state = MutableStateFlow(VolumeState())
    override val debugState: StateFlow<VolumeState> = state.asStateFlow()

    private val volumeCommandChannel = Channel<VolumeCommand>(Channel.CONFLATED)
    override val volumeCommands: Flow<VolumeCommand> = volumeCommandChannel.receiveAsFlow()

    private var scheduledSaveJob: Job? = null
    private val events = Channel<LearningEvent>(Channel.UNLIMITED)
    private var appMode = appModeFlow.value

    init {
        logLifecycle("instanceId=$instanceId, appMode=$appMode")

        scope.launch {
            logLifecycle("events channel started")
            for (event in events) {
                handleEvent(event)
            }
        }

        scope.launch {
            logLifecycle("appModeFlow started")
            appModeFlow.collect { newMode ->
                onAppModeChanged(newMode)
            }
        }
    }

    private suspend fun handleEvent(event: LearningEvent) {
        logDebug("event: ${event::class.simpleName}")
        when (event) {
            is LearningEvent.ActiveSessionChanged -> handleActiveSessionChanged(event.packageName)
            is LearningEvent.AudioFocusChanged -> handleAudioFocusChanged(event.hasFocus)
            is LearningEvent.HeadsetChanged -> handleHeadsetStateChanged(event.isConnected)
            is LearningEvent.PlaybackChanged -> handlePlaybackStateChanged(event)
            LearningEvent.SaveIfNeeded -> handleSavePendingOffset()
            is LearningEvent.TrackChanged -> handleTrackChanged(event)
            is LearningEvent.VolumeChanged -> handleVolumeChanged(event)
            LearningEvent.SaveTimerCancel -> handleSaveTimerCancel(event)
            LearningEvent.SaveTimerStart -> handleSaveTimerStart(event)
            LearningEvent.SessionDetached -> handleSessionDetached()
            LearningEvent.ServiceStopped -> handleServiceStopped()
            is LearningEvent.AppModeChanged -> handleAppModeChanged(event)
        }
    }

    override fun onActiveSessionPackageNameChanged(newPackageName: String?) {
        logDebug("activeSession=$newPackageName")
        events.trySend(LearningEvent.ActiveSessionChanged(newPackageName))
    }

    override fun onTrackChanged(
        title: String,
        artist: String?,
        volumeOffset: Float,
        offsetModel: VolumeOffsetModel,
        systemVolume: SystemVolumeSnapshot,
        trackGeneration: Long,
    ) {
        logDebug(
            "title=$title, artist=$artist, offset=$volumeOffset/$offsetModel, " +
                "vol=${systemVolume.currentVolumeDb}dB " +
                "(${systemVolume.currentVolume}/${systemVolume.maxVolume}), " +
                "generation=$trackGeneration",
        )
        events.trySend(
            LearningEvent.TrackChanged(
                title = title,
                artist = artist,
                volumeOffset = volumeOffset,
                offsetModel = offsetModel,
                systemVolume = systemVolume,
                trackGeneration = trackGeneration,
            ),
        )
    }

    private suspend fun handleTrackChanged(event: LearningEvent.TrackChanged) {
        val now = nowMillis()

        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        handleSavePendingOffset()

        val snapshot = event.systemVolume
        val currentBaseDb = state.value.baseVolumeDb
        val newBaseDb = if (currentBaseDb.isNaN()) {
            snapshot.currentVolumeDb
        } else {
            snapshot.clampDb(currentBaseDb)
        }
        val resolvedOffsetDb = when (event.offsetModel) {
            VolumeOffsetModel.DECIBEL -> event.volumeOffset
            VolumeOffsetModel.LEGACY_RATIO -> snapshot.legacyRatioToOffsetDb(
                baseNativeVolume = snapshot.nativeVolumeForDb(newBaseDb),
                ratio = event.volumeOffset,
            )
        }
        if (event.offsetModel == VolumeOffsetModel.LEGACY_RATIO) {
            logDebug(
                "Migrating legacy offset ${event.volumeOffset} for ${event.title} " +
                    "to ${resolvedOffsetDb}dB",
            )
            saveTrackVolumeUseCase(
                title = event.title,
                artist = event.artist,
                offsetDb = resolvedOffsetDb,
                id = 0,
            )
        }

        var commandToEmit: VolumeCommand? = null
        updateState(
            "onTrackChanged",
            "title=${event.title}, dbOffset=$resolvedOffsetDb",
        ) { currentState ->
            val canApplyRule = currentState.isHeadsetConnected &&
                currentState.hasAudioFocus &&
                currentState.isPlaying
            val newExpectedVolumeDb = calculateExpectedVolumeDb(
                baseDb = newBaseDb,
                offsetDb = resolvedOffsetDb,
                snapshot = snapshot,
                canApplyRule = canApplyRule,
            )

            if (!newExpectedVolumeDb.isNaN()) {
                commandToEmit = VolumeCommand(
                    targetVolumeDb = newExpectedVolumeDb,
                    trackTitle = event.title,
                    trackArtist = event.artist,
                    trackGeneration = event.trackGeneration,
                )
            }

            currentState.copy(
                currentTrackTitle = event.title,
                currentTrackArtist = event.artist,
                trackStartTimeMs = now,
                accumulatedPlayingTimeMs = 0,
                currentPlayChunkStartMs = if (currentState.isPlaying) now else 0,
                baseVolumeDb = newBaseDb,
                expectedProgrammaticVolumeDb = newExpectedVolumeDb,
                currentLearnedOffsetDb = resolvedOffsetDb,
                currentSystemVolume = snapshot,
                hasLearnedOffsetChanged = false,
                lastManualVolumeChangeTimeMs = 0,
                trackGeneration = event.trackGeneration,
            )
        }

        commandToEmit?.let { command ->
            logDebug(
                "volumeCommand: emit target=${command.targetVolumeDb}dB, " +
                    "generation=${command.trackGeneration}",
            )
            if (volumeCommandChannel.trySend(command).isFailure) {
                logDebug("volumeCommand: failed to enqueue generation=${command.trackGeneration}")
            }
        }
    }

    private fun handleSaveTimerCancel(event: LearningEvent) {
        if (scheduledSaveJob != null) {
            logDebug("saveTimer cancelled")
        }
        scheduledSaveJob?.cancel()
        scheduledSaveJob = null
    }

    /**
     * Управляет корутиной отложенного сохранения.
     * Ждет выполнения условий: время проигрывания >= порога И время с последней правки >= порога.
     */
    private suspend fun handleSaveTimerStart(event: LearningEvent) {
        val currentState = state.value

        // Нет правок или не играем — сохранять нечего
        if (!currentState.hasLearnedOffsetChanged || !currentState.isPlaying) return

        val thresholdMs = getThresholdMs()
        val now = nowMillis()

        // Считаем сколько осталось до выполнения обоих условий стабилизации
        val remaining = maxOf(
            thresholdMs - currentState.activePlayingTimeMs(now),
            thresholdMs - currentState.timeSinceLastManualChangeMs(now)
        )

        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)

        if (remaining <= 0) {
            handleSavePendingOffset()
            return
        } else {
            val delay = remaining + TINY_DELAY
            scheduledSaveJob = scope.launch {
                logDebug("saveTimer scheduled in ${delay}ms")
                delay(delay.milliseconds)
                logDebug("saveTimer fired after ${delay}ms")
                events.trySend(LearningEvent.SaveIfNeeded)
            }
        }
    }


    override fun onVolumeChanged(systemVolume: SystemVolumeSnapshot) {
        logDebug(
            "onVolumeChanged: ${systemVolume.currentVolumeDb}dB " +
                "(${systemVolume.currentVolume}/${systemVolume.maxVolume})",
        )
        events.trySend(LearningEvent.VolumeChanged(systemVolume))
    }

    /**
     * A manual change is interpreted directly on the platform-provided dB curve.
     */
    private fun handleVolumeChanged(event: LearningEvent.VolumeChanged) {
        var shouldStartTimer = false
        val snapshot = event.systemVolume
        val newVolumeDb = snapshot.currentVolumeDb

        updateState("onVolumeChanged", "newVolume=${newVolumeDb}dB") { currentState ->
            val stateWithSnapshot = currentState.copy(currentSystemVolume = snapshot)
            if (isEchoChange(newVolumeDb, snapshot.volumeStepDb, currentState)) {
                return@updateState stateWithSnapshot.copy(
                    expectedProgrammaticVolumeDb = Float.NaN,
                )
            }

            if (!canLearnVolume(currentState)) return@updateState stateWithSnapshot

            val now = nowMillis()
            val thresholdMs = getThresholdMs()
            if (isWithinTrackLearningWindow(currentState, now, thresholdMs)) {
                shouldStartTimer = true
                applyTrackSpecificCorrection(stateWithSnapshot, newVolumeDb, now)
            } else {
                applyGlobalBaseCorrection(stateWithSnapshot, newVolumeDb)
            }
        }

        if (shouldStartTimer && appMode == AppMode.LEARNING) {
            events.trySend(LearningEvent.SaveTimerStart)
        }
    }

    /**
     * Обработка паузы/воспроизведения для точного учета активного времени звучания трека.
     */
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        logDebug("isPlaying=$isPlaying")
        events.trySend(LearningEvent.PlaybackChanged(isPlaying))
    }

    private suspend fun handlePlaybackStateChanged(event: LearningEvent.PlaybackChanged) {
        val now = nowMillis()
        val isPlaying = event.isPlaying
        val wasPlaying = state.value.isPlaying

        updateState("onPlaybackStateChanged", "isPlaying=$isPlaying") { currentState ->
            if (currentState.isPlaying == isPlaying) return@updateState currentState

            if (isPlaying) {
                // Начали играть — запоминаем время старта куска
                currentState.copy(isPlaying = true, currentPlayChunkStartMs = now)
            } else {
                // Встали на паузу — плюсуем время звучания к общей копилке
                val chunkTime =
                    if (currentState.currentPlayChunkStartMs > 0) now - currentState.currentPlayChunkStartMs else 0L
                currentState.copy(
                    isPlaying = false,
                    accumulatedPlayingTimeMs = currentState.accumulatedPlayingTimeMs + chunkTime,
                    currentPlayChunkStartMs = 0
                )
            }
        }

        if (isPlaying) {
            if (!wasPlaying) emitCurrentRuleIfReady("playback resumed")
            events.trySend(LearningEvent.SaveTimerStart)
        } else {
            // На паузе таймеры сохранения сбрасываем, но пробуем сохранить накопленное
            handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
            handleSavePendingOffset()
        }
    }

    override fun onHeadsetStateChanged(isConnected: Boolean) {
        logLifecycle("headsetConnected=$isConnected")
        events.trySend(LearningEvent.HeadsetChanged(isConnected))
    }

    override fun onAudioFocusChanged(hasFocus: Boolean) {
        logLifecycle("hasFocus=$hasFocus")
        events.trySend(LearningEvent.AudioFocusChanged(hasFocus))
    }

    override fun onSessionDetached() {
        logLifecycle("session detached")
        events.trySend(LearningEvent.SessionDetached)
    }

    override fun onServiceStopped() {
        logLifecycle("instanceId=$instanceId")
        events.trySend(LearningEvent.ServiceStopped)
    }

    override fun onAppModeChanged(newMode: AppMode) {
        logLifecycle("newMode=$newMode")
        events.trySend(LearningEvent.AppModeChanged(newMode))
    }

    // --- Internal Logic ---

    private fun calculateExpectedVolumeDb(
        baseDb: Float,
        offsetDb: Float,
        snapshot: SystemVolumeSnapshot,
        canApplyRule: Boolean,
    ): Float {
        if (!canApplyRule || baseDb.isNaN() || abs(offsetDb) < MIN_OFFSET_DB) return Float.NaN
        return snapshot.clampDb(baseDb + offsetDb)
    }

    private fun isEchoChange(
        newVolumeDb: Float,
        volumeStepDb: Float,
        state: VolumeState,
    ): Boolean {
        val expectedDb = state.expectedProgrammaticVolumeDb
        if (expectedDb.isNaN()) return false
        val toleranceDb = maxOf(MIN_ECHO_TOLERANCE_DB, volumeStepDb / 2f)
        return abs(newVolumeDb - expectedDb) <= toleranceDb
    }

    private fun canLearnVolume(state: VolumeState): Boolean {
        return state.isHeadsetConnected &&
            state.hasAudioFocus &&
            state.isPlaying &&
            state.currentTrackTitle != null
    }

    private fun isWithinTrackLearningWindow(
        state: VolumeState,
        now: Long,
        thresholdMs: Long,
    ): Boolean {
        return state.trackStartTimeMs > 0 && state.activePlayingTimeMs(now) <= thresholdMs
    }

    private fun applyTrackSpecificCorrection(
        state: VolumeState,
        newVolumeDb: Float,
        now: Long,
    ): VolumeState {
        val learnedOffsetDb = (newVolumeDb - state.baseVolumeDb)
            .coerceIn(MIN_TRACK_OFFSET_DB, MAX_TRACK_OFFSET_DB)
        return state.copy(
            currentLearnedOffsetDb = learnedOffsetDb,
            hasLearnedOffsetChanged = true,
            lastManualVolumeChangeTimeMs = now,
            expectedProgrammaticVolumeDb = Float.NaN,
        )
    }

    private fun applyGlobalBaseCorrection(
        state: VolumeState,
        newVolumeDb: Float,
    ): VolumeState {
        val snapshot = state.currentSystemVolume ?: return state
        val newBaseVolumeDb = snapshot.clampDb(newVolumeDb - state.currentLearnedOffsetDb)
        return state.copy(
            baseVolumeDb = newBaseVolumeDb,
            expectedProgrammaticVolumeDb = Float.NaN,
        )
    }

    private fun getThresholdMs(): Long =
        learningTimeSeconds() * 1000L

    private suspend fun handleSavePendingOffset() {
        val currentState = state.value
        if (!currentState.hasLearnedOffsetChanged) return

        if (appMode != AppMode.LEARNING) {
            logDebug("Pending offset discarded: regulation mode is active")
            updateState("skipSaveInRegulation") {
                it.copy(hasLearnedOffsetChanged = false)
            }
            return
        }

        val now = nowMillis()
        val thresholdMs = getThresholdMs()
        if (!currentState.isSavingThresholdReached(now, thresholdMs)) {
            logDebug(
                "Pending offset is not stable yet: playing=${currentState.activePlayingTimeMs(now)}ms, " +
                    "sinceChange=${currentState.timeSinceLastManualChangeMs(now)}ms, threshold=${thresholdMs}ms",
            )
            return
        }

        val title = currentState.currentTrackTitle ?: return
        logDebug("Saving learned offset ${currentState.currentLearnedOffsetDb}dB for $title")
        saveTrackVolumeUseCase(
            title = title,
            artist = currentState.currentTrackArtist,
            offsetDb = currentState.currentLearnedOffsetDb,
            id = 0,
        )
        updateState("saveOffsetSuccess") { it.copy(hasLearnedOffsetChanged = false) }
    }

    private fun handleActiveSessionChanged(packageName: String?) {
        updateState("onActiveSessionChanged", "packageName=$packageName") {
            it.copy(activeSessionPackageName = packageName)
        }
    }

    private fun handleHeadsetStateChanged(isConnected: Boolean) {
        val wasConnected = state.value.isHeadsetConnected
        updateState("onHeadsetStateChanged", "isConnected=$isConnected") {
            it.copy(isHeadsetConnected = isConnected)
        }
        if (isConnected && !wasConnected) emitCurrentRuleIfReady("headphones connected")
    }

    private fun handleAudioFocusChanged(hasFocus: Boolean) {
        val hadFocus = state.value.hasAudioFocus
        updateState("onAudioFocusChanged", "hasFocus=$hasFocus") {
            it.copy(hasAudioFocus = hasFocus)
        }
        if (hasFocus && !hadFocus) emitCurrentRuleIfReady("audio focus restored")
    }

    private fun emitCurrentRuleIfReady(trigger: String) {
        val currentState = state.value
        val title = currentState.currentTrackTitle ?: return
        val snapshot = currentState.currentSystemVolume ?: return
        if (currentState.baseVolumeDb.isNaN() || currentState.trackGeneration <= 0) return

        val targetVolumeDb = calculateExpectedVolumeDb(
            baseDb = currentState.baseVolumeDb,
            offsetDb = currentState.currentLearnedOffsetDb,
            snapshot = snapshot,
            canApplyRule = currentState.isHeadsetConnected &&
                currentState.hasAudioFocus &&
                currentState.isPlaying,
        )
        if (targetVolumeDb.isNaN()) return

        val command = VolumeCommand(
            targetVolumeDb = targetVolumeDb,
            trackTitle = title,
            trackArtist = currentState.currentTrackArtist,
            trackGeneration = currentState.trackGeneration,
        )
        updateState("applyCurrentRule", "trigger=$trigger, target=${targetVolumeDb}dB") {
            it.copy(expectedProgrammaticVolumeDb = targetVolumeDb)
        }
        logDebug(
            "volumeCommand: emit target=${targetVolumeDb}dB, " +
                "generation=${currentState.trackGeneration}, trigger=$trigger",
        )
        if (volumeCommandChannel.trySend(command).isFailure) {
            logDebug("volumeCommand: failed to enqueue generation=${currentState.trackGeneration}")
        }
    }

    private suspend fun handleSessionDetached() {
        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        handleSavePendingOffset()
        updateState("onSessionDetached") { currentState ->
            currentState.copy(
                currentTrackTitle = null,
                currentTrackArtist = null,
                trackStartTimeMs = 0,
                accumulatedPlayingTimeMs = 0,
                currentPlayChunkStartMs = 0,
                isPlaying = false,
                expectedProgrammaticVolumeDb = Float.NaN,
                currentLearnedOffsetDb = 0f,
                currentSystemVolume = null,
                hasLearnedOffsetChanged = false,
                lastManualVolumeChangeTimeMs = 0,
                trackGeneration = 0,
            )
        }
    }

    private fun handleServiceStopped() {
        logDebug("resetting state, instanceId=$instanceId")
        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        updateState("onServiceStopped") { VolumeState() }
    }

    /**
     * При изменении режима работы:
     * 1. Сохраняется правило для текущего трека
     * 2. Останавливается таймер сохранения
     * 3. Оффсеты высчитываются, но не сохраняются
     * **/
    private suspend fun handleAppModeChanged(event: LearningEvent.AppModeChanged) {
        val newMode = event.newMode
        if (appMode == newMode) return

        logDebug("Изменение режима работы с $appMode на $newMode")
        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        if (appMode == AppMode.LEARNING) {
            handleSavePendingOffset()
        }
        if (state.value.hasLearnedOffsetChanged) {
            updateState("discardPendingOnModeChange") {
                it.copy(hasLearnedOffsetChanged = false)
            }
        }
        appMode = newMode
    }

    private fun logState(event: String, state: VolumeState, extra: String = "") {
        logDebug(
            "state[$event]: track=${state.currentTrackTitle}/${state.currentTrackArtist}, " +
                "base=${state.baseVolumeDb}dB, offset=${state.currentLearnedOffsetDb}dB, " +
                "expectedVol=${state.expectedProgrammaticVolumeDb}dB, headset=${state.isHeadsetConnected}, " +
                "focus=${state.hasAudioFocus}, playing=${state.isPlaying}" +
                if (extra.isNotEmpty()) ", $extra" else "",
        )
    }

    private fun logDebug(message: String) {
        logger(message)
    }

    private fun logLifecycle(detail: String = "") {
        logger(if (detail.isEmpty()) "LIFECYCLE" else "LIFECYCLE — $detail")
    }

    /**
     * Единая точка обновления состояния. Автоматизирует логирование и проверку изменений.
     */
    private fun updateState(
        event: String,
        extra: String = "",
        block: (VolumeState) -> VolumeState
    ) {
        state.update { currentState ->
            val newState = block(currentState)
            // Логируем, если состояние реально изменилось или это важное событие смены трека
            if (newState != currentState || event == "onTrackChanged") { // Log always on track change
                logState(event, newState, extra)
            }
            newState
        }
    }

    private companion object {
        const val MIN_TRACK_OFFSET_DB = -24f
        const val MAX_TRACK_OFFSET_DB = 12f
        const val MIN_OFFSET_DB = 0.01f
        const val MIN_ECHO_TOLERANCE_DB = 0.1f
    }
}
