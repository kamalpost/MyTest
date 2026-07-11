package com.kamalpost.taskmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerState(
    val secondsLeft: Int = 25 * 60,
    val running: Boolean = false,
    val presetMinutes: Int = 25
)

/**
 * Process-wide pomodoro engine, independent of any screen or ViewModel
 * lifecycle so the countdown keeps ticking while TimerService holds the
 * process alive in the background.
 */
object TimerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private var job: Job? = null

    fun toggle() {
        if (_state.value.running) stop() else start()
    }

    private fun start() {
        if (_state.value.secondsLeft <= 0) {
            _state.update { it.copy(secondsLeft = it.presetMinutes * 60) }
        }
        _state.update { it.copy(running = true) }
        job?.cancel()
        job = scope.launch {
            while (_state.value.secondsLeft > 0 && _state.value.running) {
                delay(1000)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            if (_state.value.secondsLeft <= 0) {
                _state.update { it.copy(running = false) }
                _finished.tryEmit(Unit)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun setPreset(minutes: Int) {
        job?.cancel()
        _state.value = TimerState(
            secondsLeft = minutes * 60,
            running = false,
            presetMinutes = minutes
        )
    }
}
