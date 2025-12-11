package com.scanium.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.scanium.app.domain.DomainPackProvider
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
            ScaniumTheme {
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
