package com.roadplay.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roadplay.player.ui.LibraryViewModel
import com.roadplay.player.ui.RoadPlayNav
import com.roadplay.player.ui.theme.RoadPlayTheme

/** Single activity; every screen is a Compose destination (see ui/AppNav.kt). */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as RoadPlayApp).container

    private val libraryViewModel: LibraryViewModel by viewModels {
        viewModelFactory { initializer { LibraryViewModel(container.library, container.settings) } }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        container.carMonitor // start observing Android Auto connection state (main thread)
        requestNotificationPermissionIfNeeded()

        setContent {
            val settings by libraryViewModel.settings.collectAsStateWithLifecycle()
            RoadPlayTheme(settings.themeMode) {
                RoadPlayNav(container, libraryViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.playbackConnection.connect()
        libraryViewModel.refreshAvailability() // flag videos that were moved/deleted/revoked
    }

    override fun onStop() {
        container.playbackConnection.disconnect()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
