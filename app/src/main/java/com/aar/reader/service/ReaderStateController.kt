package com.aar.reader.service

import com.aar.reader.service.model.ReaderState
import com.aar.reader.service.model.SentenceUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unidirectional Data Flow (UDF) controller coordinating the 7-state finite state machine.
 */
interface IReaderStateController {
    val state: StateFlow<ReaderState>
    val currentSentence: StateFlow<SentenceUnit?>
    val targetPackage: StateFlow<String?>

    fun startReading(packageName: String = "")
    fun pauseReading()
    fun resumeReading()
    fun stopReading()
    fun skipForward()
    fun skipBackward()
    fun onUtteranceStart(utteranceId: String)
    fun onUtteranceDone(utteranceId: String)
    fun onScrollSettled()
    fun updateState(newState: ReaderState)
    fun setTargetPackage(packageName: String)
    fun setCurrentSentence(sentence: SentenceUnit?)
}

/**
 * Thread-safe singleton implementation of IReaderStateController.
 */
object ReaderStateController : IReaderStateController {
    internal val lock = Any()

    private val _state = MutableStateFlow<ReaderState>(ReaderState.IDLE)
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _currentSentence = MutableStateFlow<SentenceUnit?>(null)
    override val currentSentence: StateFlow<SentenceUnit?> = _currentSentence.asStateFlow()

    private val _targetPackage = MutableStateFlow<String?>(null)
    override val targetPackage: StateFlow<String?> = _targetPackage.asStateFlow()

    /**
     * Atomically returns the current state and target package snapshot.
     */
    fun getSnapshot(): Pair<ReaderState, String?> = synchronized(lock) {
        _state.value to _targetPackage.value
    }

    /**
     * Atomically returns the complete state, target package, and current sentence snapshot.
     */
    fun getFullSnapshot(): Triple<ReaderState, String?, SentenceUnit?> = synchronized(lock) {
        Triple(_state.value, _targetPackage.value, _currentSentence.value)
    }

    override fun startReading(packageName: String) {
        synchronized(lock) {
            if (_state.value == ReaderState.IDLE || _state.value == ReaderState.COMPLETED) {
                // Ensure state is marked active before targetPackage is populated to preserve atomic invariant
                _state.value = ReaderState.SCANNING
                _targetPackage.value = packageName.ifBlank { null }
            }
        }
    }

    override fun pauseReading() {
        synchronized(lock) {
            when (_state.value) {
                ReaderState.SPEAKING,
                ReaderState.SCANNING,
                ReaderState.SCROLLING,
                ReaderState.SETTLING -> {
                    _state.value = ReaderState.PAUSED
                }
                ReaderState.IDLE,
                ReaderState.PAUSED,
                ReaderState.COMPLETED -> {
                    // No-op: cannot pause inactive or already paused reader
                }
            }
        }
    }

    override fun resumeReading() {
        synchronized(lock) {
            if (_state.value == ReaderState.PAUSED) {
                _state.value = ReaderState.SCANNING
            }
        }
    }

    override fun stopReading() {
        synchronized(lock) {
            // Nullify target and sentence before transitioning to IDLE to preserve atomic invariant
            _targetPackage.value = null
            _currentSentence.value = null
            _state.value = ReaderState.IDLE
        }
    }

    override fun skipForward() {
        synchronized(lock) {
            // Implemented in M2/M3 sentence queue
        }
    }

    override fun skipBackward() {
        synchronized(lock) {
            // Implemented in M2/M3 sentence queue
        }
    }

    override fun onUtteranceStart(utteranceId: String) {
        synchronized(lock) {
            // Only transition to SPEAKING if actively reading; drop delayed callbacks if IDLE, PAUSED, SCROLLING, or COMPLETED
            if (_state.value == ReaderState.SCANNING || _state.value == ReaderState.SPEAKING) {
                _state.value = ReaderState.SPEAKING
            }
        }
    }

    override fun onUtteranceDone(utteranceId: String) {
        synchronized(lock) {
            // Lifecycle managed by TTS controller in M3
        }
    }

    override fun onScrollSettled() {
        synchronized(lock) {
            if (_state.value == ReaderState.SETTLING) {
                _state.value = ReaderState.SCANNING
            }
        }
    }

    override fun updateState(newState: ReaderState) {
        synchronized(lock) {
            // Prevent arbitrary external transitions from overriding IDLE or PAUSED
            if (_state.value == ReaderState.IDLE && newState != ReaderState.IDLE) {
                return
            }
            if (_state.value == ReaderState.PAUSED && newState != ReaderState.PAUSED && newState != ReaderState.IDLE) {
                return
            }
            if (newState == ReaderState.IDLE) {
                _targetPackage.value = null
                _currentSentence.value = null
            }
            _state.value = newState
        }
    }

    override fun setTargetPackage(packageName: String) {
        synchronized(lock) {
            if (_state.value != ReaderState.IDLE) {
                _targetPackage.value = packageName.ifBlank { null }
            }
        }
    }

    override fun setCurrentSentence(sentence: SentenceUnit?) {
        synchronized(lock) {
            if (_state.value != ReaderState.IDLE) {
                _currentSentence.value = sentence
            } else {
                _currentSentence.value = null
            }
        }
    }
}
