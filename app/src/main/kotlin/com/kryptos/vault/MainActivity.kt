package com.kryptos.vault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fmz.kryptos.BuildConfig
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.kryptos.vault.ui.KryptosNavGraph
import com.kryptos.vault.ui.theme.KryptosTheme

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask for notification permission so expiry reminders are visible (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Screen Privacy: blocks screenshots and hides app from recents preview.
        // Disabled in debug builds so we can capture screenshots for testing.
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        enableEdgeToEdge()
        setContent {
            KryptosTheme {
                val unlocked = remember { mutableStateOf(false) }
                KryptosNavGraph(unlocked = unlocked)
            }
        }
    }
}
