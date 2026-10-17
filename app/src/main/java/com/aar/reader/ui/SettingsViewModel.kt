package com.aar.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aar.reader.data.IUserPreferencesRepository
import com.aar.reader.data.UserPreferences
import com.aar.reader.service.AutoReaderAccessibilityService
import com.aar.reader.service.ReaderStateController
import com.aar.reader.service.model.ReaderState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: IUserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    val readerState: StateFlow<ReaderState> = ReaderStateController.state

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechRate(rate)
        }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechPitch(pitch)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferencesRepository.updateLanguage(lang)
        }
    }

    fun setHighlightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHighlightEnabled(enabled)
        }
    }

    fun setScrollMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.updateScrollMode(mode)
        }
    }

    fun setScrollPercent(percent: Float) {
        viewModelScope.launch {
            preferencesRepository.updateScrollPercent(percent)
        }
    }

    fun setPauseDuration(pauseMs: Long) {
        viewModelScope.launch {
            preferencesRepository.updatePauseDuration(pauseMs)
        }
    }

    fun toggleReading() {
        val currentState = readerState.value
        if (currentState == ReaderState.IDLE || currentState == ReaderState.COMPLETED) {
            val targetPkg = AutoReaderAccessibilityService.resolveCurrentTargetPackage().orEmpty()
            ReaderStateController.startReading(targetPkg)
        } else if (currentState == ReaderState.PAUSED) {
            ReaderStateController.resumeReading()
        } else {
            ReaderStateController.pauseReading()
        }
    }

    fun stopReading() {
        ReaderStateController.stopReading()
    }

    class Factory(
        private val preferencesRepository: IUserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
