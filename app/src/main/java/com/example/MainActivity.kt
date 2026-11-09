package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.IntentFileResolver
import com.example.ui.MainViewModel
import com.example.ui.QuickShareApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load persisted profile or show first-time profile creation
        val prefs = getSharedPreferences("cute_share_prefs", Context.MODE_PRIVATE)
        val isCreated = prefs.getBoolean("profile_created", false)
        val name = prefs.getString("profile_name", "Cute Explorer") ?: "Cute Explorer"
        val avatarRes = prefs.getInt("profile_avatar", R.drawable.avatar_1_bunny)
        viewModel.attachContext(this)
        viewModel.initProfile(name = name, avatarRes = avatarRes, isCreated = isCreated)

        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Persist profile when user saves it
            LaunchedEffect(userProfile) {
                if (userProfile.isCreated) {
                    prefs.edit()
                        .putBoolean("profile_created", true)
                        .putString("profile_name", userProfile.name)
                        .putInt("profile_avatar", userProfile.avatarRes)
                        .apply()
                }
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                val window = (LocalContext.current as Activity).window
                DisposableEffect(isDark) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                    onDispose {}
                }
            }

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickShareApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val files = IntentFileResolver.resolveFilesFromIntent(this, intent)
            if (files.isNotEmpty()) {
                viewModel.handleExternalSharedFiles(files)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // If not actively transferring files, shut down all sockets and discovery
        viewModel.onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanupAllBackgroundWork()
    }
}
