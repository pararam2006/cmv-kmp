package com.pararam2006.cmv.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.pararam2006.cmv.domain.model.AppInfo
import com.pararam2006.cmv.domain.model.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

actual class SettingsPreferences(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_SHOW_SYSTEM_VOLUME_UI = "show_system_volume_ui"
        private const val KEY_LEARNING_TIME_SECONDS = "learning_time_seconds"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_USER_STOPPED = "user_stopped"
    }

    // Basic settings
    actual var showSystemVolumeUi: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_VOLUME_UI, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_SYSTEM_VOLUME_UI, value) }

    actual var learningTimeSeconds: Int
        get() = prefs.getInt(KEY_LEARNING_TIME_SECONDS, 15)
        set(value) = prefs.edit { putInt(KEY_LEARNING_TIME_SECONDS, value) }

    actual var appMode: AppMode
        get() = when (prefs.getString(KEY_APP_MODE, AppMode.LEARNING.name)) {
            "LEARNING" -> AppMode.LEARNING
            "JUST_CHANGING" -> AppMode.JUST_CHANGING
            else -> AppMode.LEARNING
        }
        set(value) {
            prefs.edit { putString(KEY_APP_MODE, value.name) }
            _appMode.value = value
        }

    // Flow-based settings
    private val _appMode = MutableStateFlow(
        when (prefs.getString(KEY_APP_MODE, AppMode.LEARNING.name)) {
            "LEARNING" -> AppMode.LEARNING
            "JUST_CHANGING" -> AppMode.JUST_CHANGING
            else -> AppMode.LEARNING
        }
    )
    actual val appModeFlow: StateFlow<AppMode> = _appMode

    private val _userStopped = MutableStateFlow(getUserStopped())
    actual val userStoppedFlow: StateFlow<Boolean> = _userStopped

    actual fun getUserStopped(): Boolean {
        return prefs.getBoolean(KEY_USER_STOPPED, false)
    }

    actual fun setUserStopped(new: Boolean) {
        prefs.edit { putBoolean(KEY_USER_STOPPED, new) }
        _userStopped.value = new
    }

    actual fun isSystemVolumeUiEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_VOLUME_UI, true)
    }
}

actual fun isDynamicColorAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

@Composable
actual fun dynamicLightColorScheme(): ColorScheme {
    val context = androidx.compose.ui.platform.LocalContext.current
    return dynamicLightColorScheme(context)
}

@Composable
actual fun dynamicDarkColorScheme(): ColorScheme {
    val context = androidx.compose.ui.platform.LocalContext.current
    return dynamicDarkColorScheme(context)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

class AndroidAppDiscoveryService(
    context: Context,
) : AppDiscoveryService {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    override suspend fun discoverApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val mediaApps = packageManager
            .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0)
            .map { it.activityInfo.packageName }

        val getContentApps = packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                0,
            )
            .map { it.activityInfo.packageName }

        val documentTreeApps = packageManager
            .queryIntentActivities(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 0)
            .map { it.activityInfo.packageName }

        val knownFileManagers = listOf(
            "com.google.android.apps.nbu.files",
            "com.google.android.documentsui",
            "com.android.providers.downloads",
            "com.mi.android.globalFileexplorer",
            "com.sec.android.app.myfiles",
        )

        (mediaApps + getContentApps + documentTreeApps + knownFileManagers)
            .distinct()
            .filter { packageName ->
                if (packageName == context.packageName) {
                    false
                } else {
                    val isSystemDocumentsApp =
                        packageName == "com.google.android.documentsui" ||
                            packageName == "com.android.providers.downloads"
                    packageManager.getLaunchIntentForPackage(packageName) != null ||
                        isSystemDocumentsApp
                }
            }
            .mapNotNull { packageName ->
                runCatching {
                    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                    AppInfo(
                        label = packageManager.getApplicationLabel(applicationInfo).toString(),
                        iconUri = "android.resource://" + packageName + "/" + applicationInfo.icon,
                        packageName = packageName,
                        name = applicationInfo.name.orEmpty(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}

actual class SystemService private constructor(
    context: Context,
    private val notificationListenerServiceClass: Class<out NotificationListenerService>,
    private val startAction: String,
    private val stopAction: String,
) {
    private val context = context.applicationContext
    private val notificationListenerComponent =
        ComponentName(this.context, notificationListenerServiceClass)

    companion object {
        fun create(
            context: Context,
            notificationListenerServiceClass: Class<out NotificationListenerService>,
            startAction: String,
            stopAction: String,
        ) = SystemService(
            context = context,
            notificationListenerServiceClass = notificationListenerServiceClass,
            startAction = startAction,
            stopAction = stopAction,
        )
    }

    actual fun isNotificationServiceSupported(): Boolean = true

    actual fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false

        return enabledListeners
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == notificationListenerComponent }
    }

    actual fun toggleService(isOn: Boolean): Boolean {
        return runCatching {
            if (isOn) {
                val stopIntent = Intent(context, notificationListenerServiceClass).apply {
                    action = stopAction
                }
                ContextCompat.startForegroundService(context, stopIntent)
            } else {
                val startIntent = Intent(context, notificationListenerServiceClass).apply {
                    action = startAction
                }
                ContextCompat.startForegroundService(context, startIntent)
            }
        }.isSuccess
    }

    actual fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    actual fun searchWeb(query: String) {
        val encodedQuery = Uri.encode(query)
        val searchIntent =
            Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$encodedQuery".toUri())
        searchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(searchIntent)
    }
}
