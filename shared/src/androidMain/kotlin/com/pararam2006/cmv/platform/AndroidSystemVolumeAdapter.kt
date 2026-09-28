package com.pararam2006.cmv.platform

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Maps Android's device/OEM-specific STREAM_MUSIC indices to their real dB
 * values. The project targets Android 12+, so getStreamVolumeDb is always
 * available. Android 13+ also exposes the device currently selected for media.
 */
class AndroidSystemVolumeAdapter(
    private val audioManager: AudioManager,
) {
    private val samsungFineVolumeApi = SamsungFineVolumeApi(audioManager)
    private var volumeBackendDetails = samsungFineVolumeApi.diagnostic

    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun snapshot(): SystemVolumeSnapshot {
        val scale = readVolumeScale()
        return snapshot(scale)
    }

    fun setVolumeDb(volumeDb: Float, flags: Int): Int {
        val scale = readVolumeScale()
        val targetLevel = snapshot(scale).nativeVolumeForDb(volumeDb)
        val nativeTarget = scale.minVolume + targetLevel

        if (scale.isFine && samsungFineVolumeApi.setVolume(nativeTarget, flags)) {
            return targetLevel
        }

        // The OEM method may disappear or reject a call after an update. Rebuild the
        // target for Android's public scale before falling back to setStreamVolume().
        val publicScale = readPublicVolumeScale()
        val publicTarget = snapshot(publicScale).nativeVolumeForDb(volumeDb)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            publicScale.minVolume + publicTarget,
            flags,
        )
        return publicTarget
    }

    private fun snapshot(scale: VolumeScale): SystemVolumeSnapshot {
        val publicCurve = publicVolumeDbCurve()
        val curve = if (scale.isFine) {
            subdivideVolumeDbCurve(publicCurve, SAMSUNG_FINE_SUBDIVISIONS)
        } else {
            publicCurve
        }
        return SystemVolumeSnapshot(
            currentVolume = (scale.currentVolume - scale.minVolume)
                .coerceIn(0, scale.maxVolume - scale.minVolume),
            maxVolume = scale.maxVolume - scale.minVolume,
            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
            volumeDbByStep = curve,
        )
    }

    private fun readVolumeScale(): VolumeScale {
        val publicScale = readPublicVolumeScale()
        val fineVolume = samsungFineVolumeApi.getVolume()
        if (fineVolume == null) {
            volumeBackendDetails = samsungFineVolumeApi.diagnostic
            return publicScale
        }
        val fineMinVolume = publicScale.minVolume * SAMSUNG_FINE_SUBDIVISIONS
        val fineMaxVolume = publicScale.maxVolume * SAMSUNG_FINE_SUBDIVISIONS
        if (fineVolume !in fineMinVolume..fineMaxVolume) {
            volumeBackendDetails = "fine=$fineVolume вне диапазона $fineMinVolume..$fineMaxVolume"
            return publicScale
        }
        val publicVolumeForFineVolume = fineVolume.toCoarseVolume(
            subdivisionsPerStep = SAMSUNG_FINE_SUBDIVISIONS,
        )
        if (publicVolumeForFineVolume != publicScale.currentVolume) {
            volumeBackendDetails =
                "fine=$fineVolume -> public=$publicVolumeForFineVolume, " +
                    "AudioManager=${publicScale.currentVolume}"
            return publicScale
        }
        volumeBackendDetails =
            "fine=$fineVolume, public=${publicScale.currentVolume}, ${samsungFineVolumeApi.diagnostic}"

        return VolumeScale(
            minVolume = fineMinVolume,
            maxVolume = fineMaxVolume,
            currentVolume = fineVolume,
            isFine = true,
        )
    }

    private fun readPublicVolumeScale(): VolumeScale {
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return VolumeScale(
            minVolume = minVolume,
            maxVolume = maxVolume,
            currentVolume = currentVolume,
            isFine = false,
        )
    }

    private fun publicVolumeDbCurve(): List<Float> {
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val deviceType = activeMediaDeviceType()
        var previousDb = MIN_VOLUME_DB
        return List(maxVolume - minVolume + 1) { index ->
            val nativeIndex = (minVolume + index).coerceAtMost(maxVolume)
            val reportedDb = audioManager.getStreamVolumeDb(
                AudioManager.STREAM_MUSIC,
                nativeIndex,
                deviceType,
            )
            val finiteDb = if (reportedDb.isFinite()) reportedDb else MIN_VOLUME_DB
            finiteDb.coerceAtLeast(previousDb).also { previousDb = it }
        }
    }

    fun routeSnapshot(): AudioRouteSnapshot? {
        val device = activeMediaDevice() ?: return null
        val scale = readVolumeScale()
        return AudioRouteSnapshot(
            id = "android:${device.id}:${device.type}",
            name = device.productName.toString().ifBlank { deviceTypeName(device.type) },
            isHeadphones = isHeadphoneType(device.type),
            backendName = if (scale.isFine) {
                "Samsung AudioManager (fine volume; $volumeBackendDetails)"
            } else {
                "Android AudioManager ($volumeBackendDetails)"
            },
        )
    }

    private fun activeMediaDeviceType(): Int {
        return activeMediaDevice()?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    private fun activeMediaDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
                .firstOrNull()
                ?.let { return it }
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .asSequence()
            .filter(AudioDeviceInfo::isSink)
            .sortedBy { outputPriority(it.type) }
            .firstOrNull()
    }

    private fun isHeadphoneType(type: Int): Boolean = type in setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
        else -> "Android audio device (type=$type)"
    }

    private fun outputPriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        -> 0

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        -> 1

        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 4
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 3
        else -> 2
    }

    private companion object {
        const val MIN_VOLUME_DB = -200f
        const val SAMSUNG_FINE_SUBDIVISIONS = 10
    }

    private data class VolumeScale(
        val minVolume: Int,
        val maxVolume: Int,
        val currentVolume: Int,
        val isFine: Boolean,
    )
}

private class SamsungFineVolumeApi(
    private val audioManager: AudioManager,
) {
    private val intType = Int::class.javaPrimitiveType!!
    private val getFineVolume = findMethod("getFineVolume", intType, intType)
    private val setFineVolume = findMethod("setFineVolume", intType, intType, intType, intType)
    private val semGetFineVolume = findMethod("semGetFineVolume", intType)
    private val semSetFineVolume = findMethod("semSetFineVolume", intType, intType, intType)
    private var lastFailure: String? = null

    val diagnostic: String
        get() {
            val getter = when {
                getFineVolume != null -> "getFineVolume"
                semGetFineVolume != null -> "semGetFineVolume"
                else -> "getter отсутствует"
            }
            val setter = when {
                setFineVolume != null -> "setFineVolume"
                semSetFineVolume != null -> "semSetFineVolume"
                else -> "setter отсутствует"
            }
            return listOfNotNull(getter, setter, lastFailure).joinToString(", ")
        }

    fun getVolume(): Int? {
        if (setFineVolume == null && semSetFineVolume == null) return null
        val result = runCatching {
            when {
                getFineVolume != null -> getFineVolume.invoke(
                    audioManager,
                    AudioManager.STREAM_MUSIC,
                    CURRENT_DEVICE,
                ) as Int

                semGetFineVolume != null -> semGetFineVolume.invoke(
                    audioManager,
                    AudioManager.STREAM_MUSIC,
                ) as Int

                else -> return null
            }
        }
        lastFailure = result.exceptionOrNull()?.toDiagnosticMessage()
        return result.getOrNull()
    }

    fun setVolume(volume: Int, flags: Int): Boolean {
        val result = runCatching {
            when {
                setFineVolume != null -> setFineVolume.invoke(
                    audioManager,
                    AudioManager.STREAM_MUSIC,
                    volume,
                    flags,
                    CURRENT_DEVICE,
                )

                semSetFineVolume != null -> semSetFineVolume.invoke(
                    audioManager,
                    AudioManager.STREAM_MUSIC,
                    volume,
                    flags,
                )

                else -> return false
            }
        }
        lastFailure = result.exceptionOrNull()?.toDiagnosticMessage()
        return result.isSuccess
    }

    private fun findMethod(name: String, vararg parameterTypes: Class<*>) = runCatching {
        audioManager.javaClass.getMethod(name, *parameterTypes)
    }.getOrNull()

    private fun Throwable.toDiagnosticMessage(): String {
        val actual = cause ?: this
        return "${actual::class.simpleName}: ${actual.message ?: "без сообщения"}"
    }

    private companion object {
        // Samsung's fine-volume API uses 0 to address the currently routed device.
        const val CURRENT_DEVICE = 0
    }
}
