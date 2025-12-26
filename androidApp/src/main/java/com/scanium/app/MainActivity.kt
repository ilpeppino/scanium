package com.scanium.app

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.ThemeMode
import com.scanium.app.ui.theme.ScaniumTheme
import kotlinx.coroutines.launch

/**
 * Main (and only) Activity for the Scanium app.
 *
 * Sets up Compose UI with Material 3 theme and Scanium branding.
 * The app opens directly into the camera screen via navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(this)

        // Initialize Domain Pack provider (config-driven category taxonomy)
        // This loads the home_resale_domain_pack.json and sets up the CategoryEngine
        // for future CLIP/cloud classification integration (Track A foundation)
        try {
            DomainPackProvider.initialize(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Domain Pack provider", e)
        }

        var lastAllowScreenshots: Boolean? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.devAllowScreenshotsFlow.collect { allow ->
                    if (lastAllowScreenshots == allow) return@collect
                    if (allow) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    lastAllowScreenshots = allow
                }
            }
        }

        setContent {
            val repo = remember { settingsRepository }
            val themeMode by repo.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ScaniumTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScaniumApp()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
