package com.redmiklab.app

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEvidenceDispatcherTest {
    @Test
    fun notification_callback_only_queues_work_and_does_not_persist_on_the_caller_thread() {
        val executor = QueueExecutor()
        val persisted = mutableListOf<String>()
        val dispatcher = NotificationEvidenceDispatcher(executor) { evidence ->
            persisted += evidence.packageName
        }

        dispatcher.dispatch { NotificationEvidenceRecord("com.example", "POSTED", "{}", 100) }

        assertEquals(emptyList<String>(), persisted)
        executor.runNext()
        assertEquals(listOf("com.example"), persisted)
    }

    @Test
    fun one_failed_notification_does_not_prevent_later_notifications() {
        val executor = QueueExecutor()
        val persisted = mutableListOf<String>()
        val dispatcher = NotificationEvidenceDispatcher(executor) { evidence ->
            if (evidence.packageName == "com.bad") error("broken notification")
            persisted += evidence.packageName
        }

        dispatcher.dispatch { error("unreadable notification extras") }
        dispatcher.dispatch { NotificationEvidenceRecord("com.bad", "POSTED", "{}", 100) }
        dispatcher.dispatch { NotificationEvidenceRecord("com.good", "POSTED", "{}", 200) }
        executor.runAll()

        assertEquals(listOf("com.good"), persisted)
    }

    @Test
    fun identical_notification_is_deduplicated_but_changed_content_is_persisted() {
        val executor = QueueExecutor()
        val persisted = mutableListOf<NotificationEvidenceRecord>()
        val dispatcher = NotificationEvidenceDispatcher(executor, persisted::add)

        dispatcher.dispatch { NotificationEvidenceRecord("com.video", "NOTIFICATION_POSTED", "{\"text\":\"甲\"}", 100, "key-a") }
        dispatcher.dispatch { NotificationEvidenceRecord("com.video", "NOTIFICATION_POSTED", "{\"text\":\"甲\"}", 101, "key-a") }
        dispatcher.dispatch { NotificationEvidenceRecord("com.video", "NOTIFICATION_POSTED", "{\"text\":\"乙\"}", 102, "key-a") }
        executor.runAll()

        assertEquals(listOf(100L, 102L), persisted.map { it.timestampEpochMs })
    }
}

private class QueueExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks += command
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}
