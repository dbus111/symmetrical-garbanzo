package com.example.tomatogarden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimerLogicTest {
    private val startTime = TimePoint(1_000_000L, 100_000L, 2L)

    @Test fun `focus completion awards once and enters short break`() {
        val running = TimerLogic.start(TimerState(), startTime, "one")
        val end = startTime.copy(wallMs = startTime.wallMs + 1_500_000L, elapsedMs = startTime.elapsedMs + 1_500_000L)
        val result = TimerLogic.reconcile(running, end)
        assertEquals(Phase.SHORT_BREAK, result.state.phase)
        assertEquals(1, result.state.completedFocuses)
        assertEquals(5 * 60_000L, result.state.remainingMs)
        assertNotNull(result.focusCompletion)
        assertNull(TimerLogic.reconcile(result.state, end).focusCompletion)
    }

    @Test fun `fourth focus enters long break`() {
        val running = TimerLogic.start(TimerState(completedFocuses = 3), startTime, "four")
        val end = startTime.copy(wallMs = startTime.wallMs + 1_500_000L, elapsedMs = startTime.elapsedMs + 1_500_000L)
        assertEquals(Phase.LONG_BREAK, TimerLogic.reconcile(running, end).state.phase)
    }

    @Test fun `pause and resume preserve remaining time`() {
        val running = TimerLogic.start(TimerState(), startTime, "one")
        val later = startTime.copy(wallMs = startTime.wallMs + 600_000L, elapsedMs = startTime.elapsedMs + 600_000L)
        val paused = TimerLogic.pause(running, later)
        assertEquals(900_000L, paused.remainingMs)
        val resumed = TimerLogic.start(paused, later, "ignored")
        assertEquals("one", resumed.sessionId)
        assertEquals(900_000L, TimerLogic.remaining(resumed, later))
    }

    @Test fun `reboot uses wall deadline and skip does not award`() {
        val running = TimerLogic.start(TimerState(), startTime, "one")
        val rebooted = TimePoint(startTime.wallMs + 1_500_000L, 5_000L, 3L)
        assertNotNull(TimerLogic.reconcile(running, rebooted).focusCompletion)
        val skipped = TimerLogic.skip(running)
        assertEquals(0, skipped.completedFocuses)
        assertNull(TimerLogic.reconcile(skipped, rebooted).focusCompletion)
    }

    @Test fun `new durations apply to ready phase`() {
        val changed = TimerLogic.changeDurations(TimerState(), Durations(30, 7, 20))
        assertEquals(30 * 60_000L, changed.remainingMs)
        assertEquals(7 * 60_000L, TimerLogic.skip(changed).remainingMs)
    }

    @Test fun `wall clock change does not change elapsed focus or completion instant`() {
        val running = TimerLogic.start(TimerState(), startTime, "clock")
        val later = TimePoint(startTime.wallMs + 5_400_000L, startTime.elapsedMs + 1_800_000L, 2L)
        val result = TimerLogic.reconcile(running, later)
        assertEquals(startTime.wallMs + 5_100_000L, result.focusCompletion?.completedAtMs)
    }
}
