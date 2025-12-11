package com.scanium.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.ml.classification.ClassificationMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassificationModeViewModel(
    private val preferences: ClassificationPreferences
) : ViewModel() {

    val classificationMode: StateFlow<ClassificationMode> = preferences.mode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClassificationMode.ON_DEVICE)

    fun updateMode(mode: ClassificationMode) {
        viewModelScope.launch {
            preferences.setMode(mode)
        }
    }

    companion object {
        fun factory(preferences: ClassificationPreferences): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ClassificationModeViewModel(preferences) as T
                }
            }
        }
    }
}
