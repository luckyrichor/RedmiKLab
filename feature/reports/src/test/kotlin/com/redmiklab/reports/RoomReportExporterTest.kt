package com.redmiklab.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.redmiklab.storage.DiagnosticDatabase
import com.redmiklab.storage.DiagnosticEventEntity
import com.redmiklab.storage.DiagnosticRunEntity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class RoomReportExporterTest {
    private lateinit var database: DiagnosticDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DiagnosticDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.diagnosticDao().insertRun(DiagnosticRunEntity("run", 1, 2, "COMPLETED"))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun schema_v3_archive_contains_every_required_layer_and_evidence_entry() {
        val bytes = ByteArrayOutputStream().also { RoomReportExporter(database).export("run", it) }.toByteArray()
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertTrue(RoomReportExporter.requiredSchemaV3Entries.all(entries::containsKey))
        assertTrue(entries.getValue("forwarding_outcomes.csv").contains("stage,direction,bytes,outcome,reason"))
        assertTrue(entries.getValue("media_events.csv").contains("event_source,event_key,content_fingerprint"))
        assertTrue(entries.getValue("report.json").contains("\"schemaVersion\":4"))
        assertTrue(entries.getValue("report.json").contains("\"tables\""))
    }

    @Test
    fun report_explains_screen_assisted_reconnect_actions_and_keeps_the_timeline() {
        database.diagnosticDao().insertEvent(
            DiagnosticEventEntity(
                "run",
                1_000,
                "NETWORK_SCREEN_WAKE_REQUESTED",
                "generation=8;attempt=1;accepted=true",
            ),
        )
        database.diagnosticDao().insertEvent(
            DiagnosticEventEntity(
                "run",
                11_000,
                "NETWORK_RETRY_STARTED",
                "generation=8;attempt=1;actual_request",
            ),
        )

        val entries = unzip(
            ByteArrayOutputStream().also { RoomReportExporter(database).export("run", it) }.toByteArray(),
        )

        assertTrue(entries.getValue("report.html").contains("亮屏辅助重连"))
        assertTrue(entries.getValue("report.html").contains("亮屏请求 1 次"))
        assertTrue(entries.getValue("diagnostic_events.csv").contains("NETWORK_SCREEN_WAKE_REQUESTED"))
        assertTrue(entries.getValue("diagnostic_events.csv").contains("NETWORK_RETRY_STARTED"))
        assertTrue(entries.getValue("report.json").contains("\"screenWakeRequestCount\":1"))
    }

    @Test
    fun standalone_json_and_html_are_complete_offline_exports() {
        val json = ByteArrayOutputStream().also {
            StandaloneReportExporter(database).export("run", ReportFormat.JSON, it)
        }.toString(Charsets.UTF_8.name())
        val html = ByteArrayOutputStream().also {
            StandaloneReportExporter(database).export("run", ReportFormat.HTML, it)
        }.toString(Charsets.UTF_8.name())

        val imported = CompleteReportJson.decode(json)
        assertEquals("run", imported.run.runId)
        assertTrue(json.contains("\"tables\""))
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("摘要指标"))
        assertTrue(html.contains("重要明细预览"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }
}
