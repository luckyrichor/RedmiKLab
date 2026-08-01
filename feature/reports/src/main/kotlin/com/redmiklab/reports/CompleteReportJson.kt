package com.redmiklab.reports

import org.json.JSONObject
import android.util.JsonWriter
import android.util.JsonReader
import android.util.JsonToken
import java.io.Reader
import java.io.Writer
import java.io.StringWriter

object CompleteReportJson {
    fun encode(report: CompleteReport): String = StringWriter().also { write(report, it) }.toString()

    fun write(report: CompleteReport, output: Writer) {
        JsonWriter(output).apply {
            beginObject()
            name("schemaVersion").value(report.schemaVersion.toLong())
            name("run")
            writeRun(report.run)
            name("summary")
            writeSummary(report.summary)
            name("tables").beginObject()
            report.tables.toSortedMap().forEach { (name, rows) ->
                name(name).beginArray()
                rows.forEach { row ->
                    beginObject()
                    row.toSortedMap().forEach { (key, value) ->
                        name(key)
                        if (value == null) nullValue() else value(value)
                    }
                    endObject()
                }
                endArray()
            }
            endObject()
            endObject()
            flush()
        }
    }

    fun decode(json: String): CompleteReport {
        val root = JSONObject(json)
        return if (root.has("run")) decodeV4(root) else decodeLegacy(root)
    }

    fun decode(input: Reader): CompleteReport {
        val reader = JsonReader(input)
        var schemaVersion = 1
        var run: ReportRun? = null
        var summary = ReportNumericSummary()
        var tables: Map<String, List<Map<String, String?>>> = emptyMap()
        val legacy = mutableMapOf<String, Any?>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "run" -> run = reader.readRun()
                "summary" -> summary = reader.readSummary()
                "tables" -> tables = reader.readTables()
                else -> legacy[name] = reader.readScalar()
            }
        }
        reader.endObject()
        if (run != null) return CompleteReport(schemaVersion, run, summary, tables)
        return legacyReport(schemaVersion, legacy)
    }

    private fun decodeV4(root: JSONObject): CompleteReport {
        val run = root.getJSONObject("run")
        val summary = root.optJSONObject("summary") ?: JSONObject()
        val tables = root.optJSONObject("tables")?.let(::tables) ?: emptyMap()
        return CompleteReport(
            schemaVersion = root.optInt("schemaVersion", 4),
            run = ReportRun(
                runId = run.getString("runId"),
                status = run.optString("status", "UNKNOWN"),
                runtimeMode = run.optString("runtimeMode", "STANDARD"),
                plannedStart = run.optLong("plannedStart"),
                plannedEnd = run.optLong("plannedEnd"),
                actualStart = run.optLong("actualStart"),
                actualEnd = run.optLong("actualEnd"),
                snapshotMinutes = run.optInt("snapshotMinutes", 5),
                connectionCaptureEnabled = run.optBoolean("connectionCaptureEnabled"),
                reportBaseName = run.optString("reportBaseName"),
                preferredFormat = run.optString("preferredFormat", "ZIP"),
                deviceModel = run.optString("deviceModel"),
                connectivityMinutes = run.optInt("connectivityMinutes", 10),
                throughputMinutes = run.optInt("throughputMinutes", 30),
                terminalReason = run.nullableString("terminalReason"),
                lastActionAt = run.optLong("lastActionAt"),
            ),
            summary = decodeSummary(summary),
            tables = tables,
        )
    }

    private fun decodeLegacy(root: JSONObject): CompleteReport = CompleteReport(
        schemaVersion = root.optInt("schemaVersion", 1),
        run = ReportRun(
            runId = root.getString("runId"),
            status = root.optString("status", "UNKNOWN"),
            runtimeMode = root.optString("runtimeMode", "STANDARD"),
            plannedStart = root.optLong("plannedStart"),
            plannedEnd = root.optLong("plannedEnd"),
            actualStart = root.optLong("actualStart"),
            actualEnd = root.optLong("actualEnd"),
            snapshotMinutes = root.optInt("snapshotMinutes", 5),
            connectionCaptureEnabled = root.optBoolean("connectionCaptureEnabled"),
            reportBaseName = root.optString("reportBaseName"),
            preferredFormat = root.optString("preferredReportFormat", "ZIP"),
            deviceModel = root.optString("deviceModel"),
            connectivityMinutes = root.optInt("connectivityMinutes", 10),
            throughputMinutes = root.optInt("throughputMinutes", 30),
            terminalReason = root.nullableString("terminalReason"),
            lastActionAt = root.optLong("lastActionAt"),
        ),
        summary = ReportNumericSummary(
            averageDownloadMbps = root.nullableDouble("averageDownloadMbps"),
            snapshotCount = root.optInt("snapshotCount"),
            systemMobileBytes = root.optLong("systemMobileBytes"),
            tunnelObservedBytes = root.optLong("tunnelObservedBytes"),
            probeBytes = root.optLong("probeBytes"),
            heartbeatBytes = root.optLong("heartbeatBytes"),
            screenWakeRequestCount = root.optInt("screenWakeRequestCount"),
            reconnectStartedCount = root.optInt("reconnectStartedCount"),
            reconnectExhaustedCount = root.optInt("reconnectExhaustedCount"),
        ),
    )

    private fun legacyReport(schemaVersion: Int, values: Map<String, Any?>): CompleteReport =
        CompleteReport(
            schemaVersion = schemaVersion,
            run = ReportRun(
                runId = values.string("runId"),
                status = values.string("status", "UNKNOWN"),
                runtimeMode = values.string("runtimeMode", "STANDARD"),
                plannedStart = values.long("plannedStart"),
                plannedEnd = values.long("plannedEnd"),
                actualStart = values.long("actualStart"),
                actualEnd = values.long("actualEnd"),
                snapshotMinutes = values.int("snapshotMinutes", 5),
                connectionCaptureEnabled = values.boolean("connectionCaptureEnabled"),
                reportBaseName = values.string("reportBaseName"),
                preferredFormat = values.string("preferredReportFormat", "ZIP"),
                deviceModel = values.string("deviceModel"),
                connectivityMinutes = values.int("connectivityMinutes", 10),
                throughputMinutes = values.int("throughputMinutes", 30),
                terminalReason = values["terminalReason"]?.toString(),
                lastActionAt = values.long("lastActionAt"),
            ),
            summary = ReportNumericSummary(
                averageDownloadMbps = values.doubleOrNull("averageDownloadMbps"),
                snapshotCount = values.int("snapshotCount"),
                systemMobileBytes = values.long("systemMobileBytes"),
                tunnelObservedBytes = values.long("tunnelObservedBytes"),
                probeBytes = values.long("probeBytes"),
                heartbeatBytes = values.long("heartbeatBytes"),
                screenWakeRequestCount = values.int("screenWakeRequestCount"),
                reconnectStartedCount = values.int("reconnectStartedCount"),
                reconnectExhaustedCount = values.int("reconnectExhaustedCount"),
            ),
        )

    private fun JsonWriter.writeRun(run: ReportRun) {
        beginObject()
        name("runId").value(run.runId)
        name("status").value(run.status)
        name("runtimeMode").value(run.runtimeMode)
        name("plannedStart").value(run.plannedStart)
        name("plannedEnd").value(run.plannedEnd)
        name("actualStart").value(run.actualStart)
        name("actualEnd").value(run.actualEnd)
        name("snapshotMinutes").value(run.snapshotMinutes.toLong())
        name("connectionCaptureEnabled").value(run.connectionCaptureEnabled)
        name("reportBaseName").value(run.reportBaseName)
        name("preferredFormat").value(run.preferredFormat)
        name("deviceModel").value(run.deviceModel)
        name("connectivityMinutes").value(run.connectivityMinutes.toLong())
        name("throughputMinutes").value(run.throughputMinutes.toLong())
        name("terminalReason")
        if (run.terminalReason == null) nullValue() else value(run.terminalReason)
        name("lastActionAt").value(run.lastActionAt)
        endObject()
    }

    private fun JsonWriter.writeSummary(summary: ReportNumericSummary) {
        beginObject()
        name("averageDownloadMbps")
        if (summary.averageDownloadMbps == null) nullValue() else value(summary.averageDownloadMbps)
        name("snapshotCount").value(summary.snapshotCount.toLong())
        name("probeCount").value(summary.probeCount.toLong())
        name("probeAttemptCount").value(summary.probeAttemptCount.toLong())
        name("appTrafficCount").value(summary.appTrafficCount.toLong())
        name("connectionFlowCount").value(summary.connectionFlowCount.toLong())
        name("evidenceCount").value(summary.evidenceCount.toLong())
        name("systemMobileBytes").value(summary.systemMobileBytes)
        name("tunnelObservedBytes").value(summary.tunnelObservedBytes)
        name("probeBytes").value(summary.probeBytes)
        name("heartbeatBytes").value(summary.heartbeatBytes)
        name("screenWakeRequestCount").value(summary.screenWakeRequestCount.toLong())
        name("reconnectStartedCount").value(summary.reconnectStartedCount.toLong())
        name("reconnectExhaustedCount").value(summary.reconnectExhaustedCount.toLong())
        endObject()
    }

    private fun JsonReader.readRun(): ReportRun {
        val values = mutableMapOf<String, Any?>()
        beginObject()
        while (hasNext()) values[nextName()] = readScalar()
        endObject()
        return ReportRun(
            runId = values.string("runId"),
            status = values.string("status", "UNKNOWN"),
            runtimeMode = values.string("runtimeMode", "STANDARD"),
            plannedStart = values.long("plannedStart"),
            plannedEnd = values.long("plannedEnd"),
            actualStart = values.long("actualStart"),
            actualEnd = values.long("actualEnd"),
            snapshotMinutes = values.int("snapshotMinutes", 5),
            connectionCaptureEnabled = values.boolean("connectionCaptureEnabled"),
            reportBaseName = values.string("reportBaseName"),
            preferredFormat = values.string("preferredFormat", "ZIP"),
            deviceModel = values.string("deviceModel"),
            connectivityMinutes = values.int("connectivityMinutes", 10),
            throughputMinutes = values.int("throughputMinutes", 30),
            terminalReason = values["terminalReason"]?.toString(),
            lastActionAt = values.long("lastActionAt"),
        )
    }

    private fun JsonReader.readSummary(): ReportNumericSummary {
        val values = mutableMapOf<String, Any?>()
        beginObject()
        while (hasNext()) values[nextName()] = readScalar()
        endObject()
        return ReportNumericSummary(
            averageDownloadMbps = values.doubleOrNull("averageDownloadMbps"),
            snapshotCount = values.int("snapshotCount"),
            probeCount = values.int("probeCount"),
            probeAttemptCount = values.int("probeAttemptCount"),
            appTrafficCount = values.int("appTrafficCount"),
            connectionFlowCount = values.int("connectionFlowCount"),
            evidenceCount = values.int("evidenceCount"),
            systemMobileBytes = values.long("systemMobileBytes"),
            tunnelObservedBytes = values.long("tunnelObservedBytes"),
            probeBytes = values.long("probeBytes"),
            heartbeatBytes = values.long("heartbeatBytes"),
            screenWakeRequestCount = values.int("screenWakeRequestCount"),
            reconnectStartedCount = values.int("reconnectStartedCount"),
            reconnectExhaustedCount = values.int("reconnectExhaustedCount"),
        )
    }

    private fun JsonReader.readTables(): Map<String, List<Map<String, String?>>> {
        val result = linkedMapOf<String, List<Map<String, String?>>>()
        beginObject()
        while (hasNext()) {
            val tableName = nextName()
            val rows = mutableListOf<Map<String, String?>>()
            beginArray()
            while (hasNext()) {
                val row = linkedMapOf<String, String?>()
                beginObject()
                while (hasNext()) row[nextName()] = readScalar()?.toString()
                endObject()
                rows += row
            }
            endArray()
            result[tableName] = rows
        }
        endObject()
        return result
    }

    private fun JsonReader.readScalar(): Any? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NUMBER -> nextString()
        JsonToken.STRING -> nextString()
        else -> { skipValue(); null }
    }

    private fun Map<String, Any?>.string(name: String, default: String = "") =
        this[name]?.toString() ?: default

    private fun Map<String, Any?>.long(name: String, default: Long = 0L) =
        this[name]?.toString()?.toLongOrNull() ?: default

    private fun Map<String, Any?>.int(name: String, default: Int = 0) =
        this[name]?.toString()?.toIntOrNull() ?: default

    private fun Map<String, Any?>.boolean(name: String, default: Boolean = false) =
        this[name]?.toString()?.toBooleanStrictOrNull() ?: default

    private fun Map<String, Any?>.doubleOrNull(name: String) =
        this[name]?.toString()?.toDoubleOrNull()

    private fun decodeSummary(json: JSONObject) = ReportNumericSummary(
        averageDownloadMbps = json.nullableDouble("averageDownloadMbps"),
        snapshotCount = json.optInt("snapshotCount"),
        probeCount = json.optInt("probeCount"),
        probeAttemptCount = json.optInt("probeAttemptCount"),
        appTrafficCount = json.optInt("appTrafficCount"),
        connectionFlowCount = json.optInt("connectionFlowCount"),
        evidenceCount = json.optInt("evidenceCount"),
        systemMobileBytes = json.optLong("systemMobileBytes"),
        tunnelObservedBytes = json.optLong("tunnelObservedBytes"),
        probeBytes = json.optLong("probeBytes"),
        heartbeatBytes = json.optLong("heartbeatBytes"),
        screenWakeRequestCount = json.optInt("screenWakeRequestCount"),
        reconnectStartedCount = json.optInt("reconnectStartedCount"),
        reconnectExhaustedCount = json.optInt("reconnectExhaustedCount"),
    )

    private fun tables(json: JSONObject): Map<String, List<Map<String, String?>>> =
        json.keys().asSequence().associateWith { name ->
            val rows = json.getJSONArray(name)
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                row.keys().asSequence().associateWith { key ->
                    if (row.isNull(key)) null else row.get(key).toString()
                }
            }
        }

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name)

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name)
}
