package com.example.tomatogarden

enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class TimerStatus { READY, RUNNING, PAUSED }

data class Durations(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
) {
    fun minutesFor(phase: Phase): Int = when (phase) {
        Phase.FOCUS -> focusMinutes
        Phase.SHORT_BREAK -> shortBreakMinutes
        Phase.LONG_BREAK -> longBreakMinutes
    }

    fun isValid(): Boolean = focusMinutes in 1..180 &&
        shortBreakMinutes in 1..60 && longBreakMinutes in 1..120
}

data class TimePoint(val wallMs: Long, val elapsedMs: Long, val bootId: Long)

data class TimerState(
    val phase: Phase = Phase.FOCUS,
    val status: TimerStatus = TimerStatus.READY,
    val durations: Durations = Durations(),
    val remainingMs: Long = 25 * 60_000L,
    val deadlineWallMs: Long = 0L,
    val deadlineElapsedMs: Long = 0L,
    val bootId: Long = -1L,
    val completedFocuses: Int = 0,
    val sessionId: String = "",
    val focusStartedAtMs: Long = 0L,
    val plannedFocusMinutes: Int = 25,
)

data class FocusCompletion(
    val sessionId: String,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val durationMinutes: Int,
)

data class TimerTransition(
    val state: TimerState,
    val completedPhase: Phase? = null,
    val focusCompletion: FocusCompletion? = null,
)

object TimerLogic {
    fun remaining(state: TimerState, now: TimePoint): Long {
        if (state.status != TimerStatus.RUNNING) return state.remainingMs
        val deadline = if (state.bootId == now.bootId) {
            state.deadlineElapsedMs - now.elapsedMs
        } else {
            state.deadlineWallMs - now.wallMs
        }
        return deadline.coerceAtLeast(0L)
    }

    fun start(state: TimerState, now: TimePoint, newSessionId: String): TimerState {
        if (state.status == TimerStatus.RUNNING) return state
        val timeLeft = state.remainingMs.coerceAtLeast(1L)
        val newFocus = state.phase == Phase.FOCUS && state.sessionId.isEmpty()
        return state.copy(
            status = TimerStatus.RUNNING,
            deadlineWallMs = now.wallMs + timeLeft,
            deadlineElapsedMs = now.elapsedMs + timeLeft,
            bootId = now.bootId,
            sessionId = if (newFocus) newSessionId else state.sessionId,
            focusStartedAtMs = if (newFocus) now.wallMs else state.focusStartedAtMs,
            plannedFocusMinutes = if (newFocus) state.durations.focusMinutes else state.plannedFocusMinutes,
        )
    }

    fun pause(state: TimerState, now: TimePoint): TimerState {
        if (state.status != TimerStatus.RUNNING) return state
        return state.copy(status = TimerStatus.PAUSED, remainingMs = remaining(state, now))
    }

    fun reset(state: TimerState): TimerState = state.copy(
        status = TimerStatus.READY,
        remainingMs = state.durations.minutesFor(state.phase) * 60_000L,
        sessionId = "",
        focusStartedAtMs = 0L,
    )

    fun skip(state: TimerState): TimerState {
        val next = nextPhase(state.phase, state.completedFocuses)
        return state.copy(
            phase = next,
            status = TimerStatus.READY,
            remainingMs = state.durations.minutesFor(next) * 60_000L,
            sessionId = "",
            focusStartedAtMs = 0L,
        )
    }

    fun changeDurations(state: TimerState, durations: Durations): TimerState {
        require(durations.isValid())
        return state.copy(
            durations = durations,
            remainingMs = if (state.status == TimerStatus.READY) {
                durations.minutesFor(state.phase) * 60_000L
            } else state.remainingMs,
        )
    }

    fun reconcile(state: TimerState, now: TimePoint): TimerTransition {
        if (state.status != TimerStatus.RUNNING || remaining(state, now) > 0L) {
            return TimerTransition(state)
        }
        val completed = state.phase
        val focusCount = state.completedFocuses + if (completed == Phase.FOCUS) 1 else 0
        val next = nextPhase(completed, focusCount)
        val completedAt = if (state.bootId == now.bootId) {
            now.wallMs - (now.elapsedMs - state.deadlineElapsedMs)
        } else {
            state.deadlineWallMs.coerceAtMost(now.wallMs)
        }
        val record = if (completed == Phase.FOCUS) {
            FocusCompletion(
                sessionId = state.sessionId,
                startedAtMs = state.focusStartedAtMs,
                completedAtMs = completedAt,
                durationMinutes = state.plannedFocusMinutes,
            )
        } else null
        return TimerTransition(
            state = state.copy(
                phase = next,
                status = TimerStatus.READY,
                remainingMs = state.durations.minutesFor(next) * 60_000L,
                completedFocuses = focusCount,
                sessionId = "",
                focusStartedAtMs = 0L,
            ),
            completedPhase = completed,
            focusCompletion = record,
        )
    }

    private fun nextPhase(phase: Phase, completedFocuses: Int): Phase = when (phase) {
        Phase.FOCUS -> if (completedFocuses > 0 && completedFocuses % 4 == 0) {
            Phase.LONG_BREAK
        } else Phase.SHORT_BREAK
        Phase.SHORT_BREAK, Phase.LONG_BREAK -> Phase.FOCUS
    }
}
