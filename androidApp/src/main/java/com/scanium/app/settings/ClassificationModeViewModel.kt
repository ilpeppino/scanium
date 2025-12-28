package com.scanium.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.BuildConfig
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassifierDebugFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for classification mode settings.
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 */
@HiltViewModel
class ClassificationModeViewModel @Inject constructor(
    private val preferences: ClassificationPreferences
) : ViewModel() {

    val classificationMode: StateFlow<ClassificationMode> = preferences.mode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClassificationMode.CLOUD)

    val saveCloudCrops: StateFlow<Boolean> = preferences.saveCloudCrops
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClassifierDebugFlags.saveCloudCropsEnabled)

    val lowDataMode: StateFlow<Boolean> = preferences.lowDataMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val verboseLogging: StateFlow<Boolean> = preferences.verboseLogging
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuildConfig.DEBUG)

    init {
        viewModelScope.launch {
            saveCloudCrops.collect { enabled ->
                ClassifierDebugFlags.saveCloudCropsEnabled = BuildConfig.DEBUG && enabled
            }
        }
        viewModelScope.launch {
            lowDataMode.collect { enabled ->
                ClassifierDebugFlags.lowDataModeEnabled = enabled
            }
        }
        viewModelScope.launch {
            verboseLogging.collect { enabled ->
                ScaniumLog.verboseEnabled = BuildConfig.DEBUG && enabled
            }
        }
    }

    fun updateMode(mode: ClassificationMode) {
        viewModelScope.launch {
            preferences.setMode(mode)
        }
    }

    fun updateSaveCloudCrops(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setSaveCloudCrops(enabled)
        }
    }

    fun updateLowDataMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setLowDataMode(enabled)
        }
    }

    fun updateVerboseLogging(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setVerboseLogging(enabled)
        }
    }

}
