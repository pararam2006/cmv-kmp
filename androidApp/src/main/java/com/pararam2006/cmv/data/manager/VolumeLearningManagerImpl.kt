package com.pararam2006.cmv.data.manager

import com.pararam2006.cmv.core.Constants.TINY_DELAY
import com.pararam2006.cmv.domain.manager.VolumeLearningManager
import com.pararam2006.cmv.domain.manager.VolumeState
import com.pararam2006.cmv.domain.manager.activePlayingTimeMs
import com.pararam2006.cmv.domain.manager.timeSinceLastManualChangeMs
import com.pararam2006.cmv.domain.usecase.SaveTrackVolumeUseCase
import com.pararam2006.cmv.ui.changeMode.AppMode
import com.pararam2006.cmv.utils.SettingsPreferences
import com.pararam2006.cmv.utils.logDebug
import com.pararam2006.cmv.utils.logLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

private sealed interface LearningEvent {
    data class TrackChanged(
        val title: String,
        val artist: String?,
        val offsetFromDb: Float,
        val currentVolume: Int,
        val maxVolume: Int
    ) : LearningEvent

    data class VolumeChanged(val newVolume: Int) : LearningEvent
    data class PlaybackChanged(val isPlaying: Boolean) : LearningEvent
    object SaveTimerStart : LearningEvent
    object SaveTimerCancel : LearningEvent
    object SaveIfNeeded : LearningEvent
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
    private val settingsPreferences: SettingsPreferences,
    private val scope: CoroutineScope,
) : VolumeLearningManager {

    private val instanceId = hashCode()
    private val state = MutableStateFlow(VolumeState())
    override val debugState: StateFlow<VolumeState> = state.asStateFlow()

    private val _volumeCommands = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val volumeCommands: SharedFlow<Int> = _volumeCommands.asSharedFlow()

    private var scheduledSaveJob: Job? = null
    private val events = Channel<LearningEvent>(Channel.UNLIMITED)
    private val appModeFlow = settingsPreferences.appModeFlow
    private var appMode = settingsPreferences.getAppMode()

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
            is LearningEvent.PlaybackChanged -> handlePlaybackStateChanged(event)
            LearningEvent.SaveIfNeeded -> handleSavePendingOffset()
            is LearningEvent.TrackChanged -> handleTrackChanged(event)
            is LearningEvent.VolumeChanged -> handleVolumeChanged(event)
            LearningEvent.SaveTimerCancel -> handleSaveTimerCancel(event)
            LearningEvent.SaveTimerStart -> handleSaveTimerStart(event)
            LearningEvent.ServiceStopped -> handleServiceStopped()
            is LearningEvent.AppModeChanged -> handleAppModeChanged(event)
        }
    }

    override fun onActiveSessionPackageNameChanged(newPackageName: String) {
        logDebug(newPackageName)
        state.update {
            it.copy(activeSessionPackageName = newPackageName)
        }
    }

    override fun onTrackChanged(
        title: String,
        artist: String?,
        offsetFromDb: Float,
        currentSystemVolume: Int,
        maxVolume: Int
    ) {
        logDebug("title=$title, artist=$artist, offset=$offsetFromDb, vol=$currentSystemVolume/$maxVolume")
        events.trySend(
            LearningEvent.TrackChanged(
                title = title,
                artist = artist,
                offsetFromDb = offsetFromDb,
                currentVolume = currentSystemVolume,
                maxVolume = maxVolume,
            )
        )
    }

    private suspend fun handleTrackChanged(event: LearningEvent.TrackChanged) {
        val maxVolume = event.maxVolume
        val offsetFromDb = event.offsetFromDb

        // Пытаемся сохранить наработки по предыдущему треку, если они были
        handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
        handleSavePendingOffset()

        var commandToEmit: Int? = null

        updateState(
            "onTrackChanged",
            "title=${event.title}, dbOffset=${offsetFromDb}"
        ) { currentState ->
            // Если базовая громкость еще не установлена (старт сервиса), берем текущую системную
            val newBaseVolume =
                if (currentState.baseVolume == -1) event.currentVolume else currentState.baseVolume

            // Считаем громкость, которую мы должны установить программно
            val newExpectedVolume = calculateExpectedVolume(
                base = newBaseVolume,
                offsetRatio = offsetFromDb,
                max = maxVolume,
                isHeadset = currentState.isHeadsetConnected
            )

            if (newExpectedVolume != -1) commandToEmit = newExpectedVolume

            currentState.copy(
                currentTrackTitle = event.title,
                currentTrackArtist = event.artist,
                trackStartTimeMs = System.currentTimeMillis(),
                accumulatedPlayingTimeMs = 0,
                currentPlayChunkStartMs = if (currentState.isPlaying) System.currentTimeMillis() else 0,
                baseVolume = newBaseVolume,
                expectedProgrammaticVolume = newExpectedVolume,
                currentLearnedOffset = offsetFromDb,
                maxVolume = maxVolume,
                hasLearnedOffsetChanged = false,
                lastManualVolumeChangeTimeMs = 0
            )
        }

        // Отправляем команду на установку громкости (актуально для наушников)
        commandToEmit?.let {
            logDebug("volumeCommand: emit target=$it")
            _volumeCommands.tryEmit(it)
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
        val now = System.currentTimeMillis()

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

            val now = System.currentTimeMillis()
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
        val now = System.currentTimeMillis()
        val isPlaying = event.isPlaying

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
            events.trySend(LearningEvent.SaveTimerStart)
        } else {
            // На паузе таймеры сохранения сбрасываем, но пробуем сохранить накопленное
            handleSaveTimerCancel(LearningEvent.SaveTimerCancel)
            handleSavePendingOffset()
        }
    }

    override fun onHeadsetStateChanged(isConnected: Boolean) {
        logLifecycle("isConnected=$isConnected")
        updateState("onHeadsetStateChanged", "isConnected=$isConnected") {
            it.copy(isHeadsetConnected = isConnected)
        }
    }

    override fun onAudioFocusChanged(hasFocus: Boolean) {
        logLifecycle("$hasFocus")
        updateState("onAudioFocusChanged", "hasFocus=$hasFocus") {
            it.copy(hasAudioFocus = hasFocus)
        }
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
        isHeadset: Boolean
    ): Int {
        // Коэффициент 0 или 1 означает отсутствие специфической настройки
        if (!isHeadset || offsetRatio <= 0f || offsetRatio == 1f) return -1

        // Целевая громкость = ((База + 1) * Коэффициент) - 1
        val result = ((base.toFloat() + 1f) * offsetRatio) - 1f
        return result.roundToInt().coerceIn(0, max)
    }

    private fun isEchoChange(newVolume: Int, state: VolumeState): Boolean {
        return newVolume == state.expectedProgrammaticVolume
    }

    private fun canLearnVolume(state: VolumeState): Boolean {
        return state.isHeadsetConnected && state.hasAudioFocus
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
        (settingsPreferences.getBasicVolumeChangingTime() * 1000).toLong()

    private suspend fun handleSavePendingOffset() {
        val currentState = state.value

        if (appMode == AppMode.LEARNING && state.value.currentLearnedOffset != 1f) {
            val title = currentState.currentTrackTitle ?: return

            logDebug("Saving learned offset ${currentState.currentLearnedOffset} for $title")
            saveTrackVolumeUseCase(
                title = title,
                artist = currentState.currentTrackArtist,
                offset = currentState.currentLearnedOffset,
                id = 0
            )
            // Помечаем как сохраненное, чтобы не писать в БД на каждом чихе
            updateState("saveOffsetSuccess") { it.copy(hasLearnedOffsetChanged = false) }
        } else {
            logDebug("Правило не сохранено - включен режим регулировки")
            updateState("skipSaveInRegulation") {
                it.copy(hasLearnedOffsetChanged = false)
            }
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
    private fun handleAppModeChanged(event: LearningEvent.AppModeChanged) {
        events.trySend(LearningEvent.SaveTimerCancel)

        val newMode = event.newMode

        Timber.d("Попытка измененить режим работы с $appMode на $newMode")
        if (appMode != newMode) {
            logDebug("Изменение режима работы на $newMode")
            appMode = newMode
        }
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