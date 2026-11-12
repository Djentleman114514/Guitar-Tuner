package com.xianzhun.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xianzhun.tuner.ui.TunerScreen
import com.xianzhun.tuner.ui.TunerTheme

class MainActivity : ComponentActivity() {
    private val tuner: TunerViewModel by viewModels()

    private fun hasMicrophonePermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by tuner.state.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) tuner.start() else tuner.permissionDenied(
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
                )
            }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> tuner.onBackground()
                        Lifecycle.Event.ON_START -> tuner.onForeground(hasMicrophonePermission())
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            TunerTheme {
                TunerScreen(
                    state = state,
                    onAutoChanged = tuner::setAuto,
                    onStringSelected = tuner::selectString,
                    onToggleListening = {
                        when {
                            state.isListening -> tuner.pause()
                            hasMicrophonePermission() -> tuner.start()
                            state.permissionBlocked -> startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                            )
                            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }
    }
}
