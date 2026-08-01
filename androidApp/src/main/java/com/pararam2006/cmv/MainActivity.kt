package com.pararam2006.cmv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pararam2006.cmv.core.navigation.RootNavGraph
import com.pararam2006.cmv.utils.logLifecycle

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            RootNavGraph(appVersion = BuildConfig.VERSION_NAME)
        }
    }

    override fun onDestroy() {
        logLifecycle("lifecycle: onDestroy isFinishing=$isFinishing")
        super.onDestroy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}