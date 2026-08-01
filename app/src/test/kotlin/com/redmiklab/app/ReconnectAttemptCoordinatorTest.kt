package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectAttemptCoordinatorTest {
    @Test
    fun first_and_fourth_attempt_wake_then_wait_10_seconds_before_reconnecting() {
        val scheduler = FakeRetryScheduler()
        val calls = mutableListOf<String>()
        val coordinator = ReconnectAttemptCoordinator(
            scheduler = scheduler,
            isCurrent = { _, _ -> true },
            screenWake = { generation, attempt ->
                calls += "wake:$generation:$attempt"
                ScreenWakeRequestResult(true, "scheduled")
            },
            reconnect = { generation, attempt -> calls += "reconnect:$generation:$attempt" },
            recorder = { event -> calls += "record:${event.type}:${event.attempt}" },
        )

        coordinator.schedule(delayMs = 30_000, generation = 8, attempt = 1)
        scheduler.advanceBy(30_000)
        assertEquals(
            listOf(
                "record:RETRY_SCHEDULED:1",
                "wake:8:1",
                "record:SCREEN_WAKE_REQUESTED:1",
            ),
            calls,
        )

        scheduler.advanceBy(9_999)
        assertEquals(3, calls.size)
        scheduler.advanceBy(1)
        assertEquals("reconnect:8:1", calls.last())

        calls.clear()
        coordinator.schedule(delayMs = 120_000, generation = 8, attempt = 4)
        scheduler.advanceBy(120_000)
        assertEquals("wake:8:4", calls[1])
        scheduler.advanceBy(10_000)
        assertEquals("reconnect:8:4", calls.last())
    }

    @Test
    fun second_third_and_fifth_attempt_reconnect_without_waking() {
        val scheduler = FakeRetryScheduler()
        val calls = mutableListOf<String>()
        val coordinator = coordinator(scheduler, calls) { _, _ -> true }

        listOf(2, 3, 5).forEach { attempt ->
            coordinator.schedule(0, generation = 3, attempt = attempt)
            scheduler.runDue()
        }

        assertEquals(
            listOf(
                "record:RETRY_SCHEDULED:2", "record:RETRY_STARTED:2", "reconnect:3:2",
                "record:RETRY_SCHEDULED:3", "record:RETRY_STARTED:3", "reconnect:3:3",
                "record:RETRY_SCHEDULED:5", "record:RETRY_STARTED:5", "reconnect:3:5",
            ),
            calls,
        )
    }

    @Test
    fun stale_generation_does_not_wake_or_reconnect() {
        val scheduler = FakeRetryScheduler()
        val calls = mutableListOf<String>()
        var current = true
        val coordinator = coordinator(scheduler, calls) { _, _ -> current }

        coordinator.schedule(30_000, generation = 9, attempt = 1)
        current = false
        scheduler.advanceBy(30_000)

        assertEquals(listOf("record:RETRY_SCHEDULED:1"), calls)
    }

    @Test
    fun generation_becoming_stale_during_screen_wait_skips_reconnect() {
        val scheduler = FakeRetryScheduler()
        val calls = mutableListOf<String>()
        var current = true
        val coordinator = coordinator(scheduler, calls) { _, _ -> current }

        coordinator.schedule(0, generation = 5, attempt = 1)
        scheduler.runDue()
        current = false
        scheduler.advanceBy(10_000)

        assertEquals(false, calls.any { it.startsWith("reconnect") })
    }

    private fun coordinator(
        scheduler: FakeRetryScheduler,
        calls: MutableList<String>,
        isCurrent: (Long, Int) -> Boolean,
    ) = ReconnectAttemptCoordinator(
        scheduler = scheduler,
        isCurrent = isCurrent,
        screenWake = { generation, attempt ->
            calls += "wake:$generation:$attempt"
            ScreenWakeRequestResult(true, "scheduled")
        },
        reconnect = { generation, attempt -> calls += "reconnect:$generation:$attempt" },
        recorder = { event -> calls += "record:${event.type}:${event.attempt}" },
    )
}

private class FakeRetryScheduler : RetryScheduler {
    private data class Entry(val atMs: Long, val action: () -> Unit)
    private var nowMs = 0L
    private val entries = mutableListOf<Entry>()

    override fun schedule(delayMs: Long, action: () -> Unit) {
        entries += Entry(nowMs + delayMs, action)
    }

    fun runDue() = advanceBy(0)

    fun advanceBy(deltaMs: Long) {
        nowMs += deltaMs
        while (true) {
            val due = entries.filter { it.atMs <= nowMs }.minByOrNull { it.atMs } ?: return
            entries.remove(due)
            due.action()
        }
    }
}
