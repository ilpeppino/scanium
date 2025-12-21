package com.scanium.app

import android.os.Bundle
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
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.ThemeMode
import com.scanium.app.ui.theme.ScaniumTheme

/**
 * Main (and only) Activity for the Scanium app.
 *
 * Sets up Compose UI with Material 3 theme and Scanium branding.
 * The app opens directly into the camera screen via navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Domain Pack provider (config-driven category taxonomy)
        // This loads the home_resale_domain_pack.json and sets up the CategoryEngine
        // for future CLIP/cloud classification integration (Track A foundation)
        DomainPackProvider.initialize(this)

        setContent {
            val settingsRepository = remember { SettingsRepository(this@MainActivity) }
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
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
}
