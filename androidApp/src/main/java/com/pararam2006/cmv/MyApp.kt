package com.pararam2006.cmv

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import com.pararam2006.cmv.core.di.appModule
import com.pararam2006.cmv.core.service.MyNotificationListenerService
import com.pararam2006.cmv.utils.logLifecycle
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        logLifecycle("onCreate START")
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                // Переопределяем создание тега, чтобы всегда видеть имя класса
                override fun createStackElementTag(element: StackTraceElement): String {
                    return String.format(
                        "%s:%s",
                        super.createStackElementTag(element),
                        element.lineNumber // Номер строки для удобного клика из logcat
                    )
                }
            })
        }

        try {
            startKoin {
                androidLogger()
                androidContext(this@MyApp)
                modules(appModule)
            }
            logLifecycle("lifecycle: Koin initialized")

        } catch (e: Exception) {
            Timber.e(e, "lifecycle: Koin initialization FAILED")
        }

        // Check service component state
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        val stateStr = when (val state = packageManager.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "DISABLED_USER"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED_UNTIL_USED"
            else -> "UNKNOWN($state)"
        }
        logLifecycle("service component state=$stateStr")

        // Check if notification listener permission is in settings
        val listeners = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        val component = componentName.flattenToString()
        val permissionGranted = listeners?.contains(component) == true
        logLifecycle(
            "notification listener permissionGranted=$permissionGranted, component=$component",
        )
        logLifecycle("onCreate COMPLETE")
    }
}