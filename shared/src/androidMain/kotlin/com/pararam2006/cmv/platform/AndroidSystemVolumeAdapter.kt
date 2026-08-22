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
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun snapshot(): SystemVolumeSnapshot {
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val nativeRange = maxVolume - minVolume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val deviceType = activeMediaDeviceType()
        var previousDb = MIN_VOLUME_DB
        val curve = List(nativeRange + 1) { index ->
            val nativeIndex = (minVolume + index).coerceAtMost(maxVolume)
            val reportedDb = audioManager.getStreamVolumeDb(
                AudioManager.STREAM_MUSIC,
                nativeIndex,
                deviceType,
            )
            val finiteDb = if (reportedDb.isFinite()) reportedDb else MIN_VOLUME_DB
            finiteDb.coerceAtLeast(previousDb).also { previousDb = it }
        }
        return SystemVolumeSnapshot(
            currentVolume = (currentVolume - minVolume).coerceIn(0, maxVolume - minVolume),
            maxVolume = nativeRange,
            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
            volumeDbByStep = curve,
        )
    }

    fun nativeVolumeForDb(volumeDb: Float): Int {
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        return minVolume + snapshot().nativeVolumeForDb(volumeDb)
    }

    fun routeSnapshot(): AudioRouteSnapshot? {
        val device = activeMediaDevice() ?: return null
        return AudioRouteSnapshot(
            id = "android:${device.id}:${device.type}",
            name = device.productName.toString().ifBlank { deviceTypeName(device.type) },
            isHeadphones = isHeadphoneType(device.type),
            backendName = "Android AudioManager",
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
    }
}
