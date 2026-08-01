package com.pararam2006.cmv.data.manager

import com.pararam2006.cmv.core.Constants.TINY_DELAY
import com.pararam2006.cmv.domain.manager.VolumeCommand
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.manager.activePlayingTimeMs
import com.pararam2006.cmv.domain.manager.isSavingThresholdReached
import com.pararam2006.cmv.domain.manager.timeSinceLastManualChangeMs
import com.pararam2006.cmv.domain.model.AppMode
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
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
import kotlin.math.roundToInt

private sealed interface LearningEvent {
    data class TrackChanged(
        val title: String,
        val artist: String?,
        val offsetFromDb: Float,
        val currentVolume: Int,
        val maxVolume: Int,
        val trackGeneration: Long,
    ) : LearningEvent

    data class VolumeChanged(val newVolume: Int) : LearningEvent
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
        offsetFromDb: Float,
        currentSystemVolume: Int,
        maxVolume: Int,
        trackGeneration: Long,
    ) {
        logDebug(
            "title=$title, artist=$artist, offset=$offsetFromDb, " +
                "vol=$currentSystemVolume/$maxVolume, generation=$trackGeneration",
        )
        events.trySend(
            LearningEvent.TrackChanged(
                title = title,
                artist = artist,
                offsetFromDb = offsetFromDb,
                currentVolume = currentSystemVolume,
                maxVolume = maxVolume,
                trackGeneration = trackGeneration,
            ),
        )
    }

    private suspend fun handleTrackChanged(event: LearningEvent.TrackChanged) {
        val now = nowMillis()

        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        handleSavePendingOffset()

        var commandToEmit: VolumeCommand? = null
        updateState(
            "onTrackChanged",
            "title=${event.title}, dbOffset=${event.offsetFromDb}",
        ) { currentState ->
            val newBaseVolume =
                if (currentState.baseVolume == -1) event.currentVolume else currentState.baseVolume
            val canApplyRule = currentState.isHeadsetConnected &&
                currentState.hasAudioFocus &&
                currentState.isPlaying
            val newExpectedVolume = calculateExpectedVolume(
                base = newBaseVolume,
                offsetRatio = event.offsetFromDb,
                max = event.maxVolume,
                canApplyRule = canApplyRule,
            )

            if (newExpectedVolume != -1) {
                commandToEmit = VolumeCommand(
                    targetVolume = newExpectedVolume,
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
                baseVolume = newBaseVolume,
                expectedProgrammaticVolume = newExpectedVolume,
                currentLearnedOffset = event.offsetFromDb,
                maxVolume = event.maxVolume,
                hasLearnedOffsetChanged = false,
                lastManualVolumeChangeTimeMs = 0,
                trackGeneration = event.trackGeneration,
            )
        }

        commandToEmit?.let { command ->
            logDebug("volumeCommand: emit target=${command.targetVolume}, generation=${command.trackGeneration}")
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
                delay(delay)
                logDebug("saveTimer fired after ${delay}ms")
                events.trySend(LearningEvent.SaveIfNeeded)
            }
        }
    }


    override fun onVolumeChanged(newVolume: Int) {
        logDebug("onVolumeChanged: newVolume=$newVolume")
        events.trySend(LearningEvent.VolumeChanged(newVolume))
    }

    /**
     * Вызывается при любом изменении системной громкости.
     * Здесь происходит магия обучения: мы решаем, подстроить ли базу или запомнить смещение для трека.
     */
    private fun handleVolumeChanged(event: LearningEvent.VolumeChanged) {
        var shouldStartTimer = false

        val newVolume = event.newVolume

        updateState("onVolumeChanged", "newVolume=$newVolume") { currentState ->
            // Игнорируем изменения, которые вызвали мы сами программно
            if (isEchoChange(newVolume, currentState)) {
                return@updateState currentState.copy(expectedProgrammaticVolume = -1)
            }

            // Не учимся, если нет наушников или фокуса (например, входящий звонок)
            if (!canLearnVolume(currentState)) return@updateState currentState

            val now = nowMillis()
            val thresholdMs = getThresholdMs()

            // Если трек играет недавно (в окне обучения), значит пользователь правит громкость под ЭТОТ трек
            if (isWithinTrackLearningWindow(currentState, now, thresholdMs)) {
                shouldStartTimer = true
                applyTrackSpecificCorrection(currentState, newVolume, now)
            } else {
                // Если трек играет давно, значит пользователь просто хочет изменить общую громкость (базу)
                applyGlobalBaseCorrection(currentState, newVolume)
            }
        }

        // Запускаем таймер отложенного сохранения, если изменили смещение трека
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

    /**
     * Расчитывает целевую громкость на основе логарифмического коэффициента (отношения).
     * Используем (volume + 1), чтобы избежать деления на ноль и корректно обрабатывать малые уровни.
     */
    private fun calculateExpectedVolume(
        base: Int,
        offsetRatio: Float,
        max: Int,
        canApplyRule: Boolean,
    ): Int {
        if (!canApplyRule || offsetRatio <= 0f || offsetRatio == 1f) return -1

        val result = ((base.toFloat() + 1f) * offsetRatio) - 1f
        return result.roundToInt().coerceIn(0, max)
    }

    private fun isEchoChange(newVolume: Int, state: VolumeState): Boolean {
        return newVolume == state.expectedProgrammaticVolume
    }

    private fun canLearnVolume(state: VolumeState): Boolean {
        return state.isHeadsetConnected &&
            state.hasAudioFocus &&
            state.isPlaying &&
            state.currentTrackTitle != null
    }

    /**
     * Проверяет, находимся ли мы еще в фазе обучения для конкретного трека.
     */
    private fun isWithinTrackLearningWindow(
        state: VolumeState,
        now: Long,
        thresholdMs: Long
    ): Boolean {
        return state.trackStartTimeMs > 0 && state.activePlayingTimeMs(now) <= thresholdMs
    }

    /**
     * Вычисляет логарифмический коэффициент (отношение) для текущего трека относительно базы.
     * Это позволяет изменениям на низкой громкости влиять сильнее, чем на высокой.
     */
    private fun applyTrackSpecificCorrection(
        state: VolumeState,
        newVolume: Int,
        now: Long
    ): VolumeState {
        // Коэффициент = (Новая_Громкость + 1) / (Базовая_Громкость + 1)
        val learnedOffsetRatio = (newVolume.toFloat() + 1f) / (state.baseVolume.toFloat() + 1f)

        return state.copy(
            currentLearnedOffset = learnedOffsetRatio,
            hasLearnedOffsetChanged = true,
            lastManualVolumeChangeTimeMs = now,
            expectedProgrammaticVolume = -1
        )
    }

    /**
     * Обновляет «базовую» громкость, учитывая логарифмическое смещение текущего трека.
     */
    private fun applyGlobalBaseCorrection(state: VolumeState, newVolume: Int): VolumeState {
        // Реверсивный расчет базы: Новая_База = ((Текущая_Громкость + 1) / Коэффициент) - 1
        val ratio = if (state.currentLearnedOffset > 0f) state.currentLearnedOffset else 1f
        val newBaseVolume = (((newVolume.toFloat() + 1f) / ratio) - 1f).roundToInt()

        return state.copy(
            baseVolume = newBaseVolume.coerceIn(0, state.maxVolume),
            expectedProgrammaticVolume = -1
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
        logDebug("Saving learned offset ${currentState.currentLearnedOffset} for $title")
        saveTrackVolumeUseCase(
            title = title,
            artist = currentState.currentTrackArtist,
            offset = currentState.currentLearnedOffset,
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
        if (currentState.baseVolume < 0 || currentState.trackGeneration <= 0) return

        val targetVolume = calculateExpectedVolume(
            base = currentState.baseVolume,
            offsetRatio = currentState.currentLearnedOffset,
            max = currentState.maxVolume,
            canApplyRule = currentState.isHeadsetConnected &&
                currentState.hasAudioFocus &&
                currentState.isPlaying,
        )
        if (targetVolume == -1) return

        val command = VolumeCommand(
            targetVolume = targetVolume,
            trackTitle = title,
            trackArtist = currentState.currentTrackArtist,
            trackGeneration = currentState.trackGeneration,
        )
        updateState("applyCurrentRule", "trigger=$trigger, target=$targetVolume") {
            it.copy(expectedProgrammaticVolume = targetVolume)
        }
        logDebug(
            "volumeCommand: emit target=$targetVolume, " +
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
                expectedProgrammaticVolume = -1,
                currentLearnedOffset = 1f,
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
                "base=${state.baseVolume}, offset=${state.currentLearnedOffset}, " +
                "expectedVol=${state.expectedProgrammaticVolume}, headset=${state.isHeadsetConnected}, " +
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
}
