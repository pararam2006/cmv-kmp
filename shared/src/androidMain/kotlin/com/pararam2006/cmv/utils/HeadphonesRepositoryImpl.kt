package com.pararam2006.cmv.utils

import android.app.Application
import android.content.Context.AUDIO_SERVICE
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.pararam2006.cmv.domain.repository.HeadphonesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import java.util.Collections.addAll

class HeadphonesRepositoryImpl(
    context: Application,
    scope: CoroutineScope
) : HeadphonesRepository {
    private val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
    override val isHeadsetFlow = callbackFlow {
//        lifecycle("isHeadsetFlow", "subscribed, registering AudioDeviceCallback")
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val connected = computeIsHeadsetConnected()
//                lifecycle("audioDevicesAdded", "count=${addedDevices?.size ?: 0}, isHeadset=$connected")
                trySend(connected)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val connected = computeIsHeadsetConnected()
//                lifecycle("audioDevicesRemoved", "count=${removedDevices?.size ?: 0}, isHeadset=$connected")
                trySend(connected)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose {
//            lifecycle("isHeadsetFlow", "unsubscribed, unregistering AudioDeviceCallback")
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }
        .onStart { emit(computeIsHeadsetConnected()) }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun computeIsHeadsetConnected(): Boolean {
        val headsetTypes = mutableListOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

        if (Build.VERSION.SDK_INT > 28) {
            headsetTypes.add(AudioDeviceInfo.TYPE_HEARING_AID)
        }

        if (Build.VERSION.SDK_INT > 31) {
            addAll(
                headsetTypes,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
            )
        }

        if (Build.VERSION.SDK_INT > 33) {
            headsetTypes.add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in headsetTypes
        }
    }
}