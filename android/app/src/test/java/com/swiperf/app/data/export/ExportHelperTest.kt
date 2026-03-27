package com.swiperf.app.data.export

import com.swiperf.app.data.model.TraceEntry
import com.swiperf.app.data.model.Verdict
import org.junit.Assert.*
import org.junit.Test

class ExportHelperTest {

    @Test
    fun buildTraceLink_constructsUrl() {
        val link = ExportHelper.buildTraceLink("abc-123", "com.test")
        assertTrue(link.contains("uuid=abc-123"))
        assertTrue(link.contains("com.android.AndroidStartup.packageName"))
    }

    @Test
    fun buildTraceLink_emptyUuid_returnsEmpty() {
        assertEquals("", ExportHelper.buildTraceLink(""))
    }

    @Test
    fun traceExportRow_includesVerdict() {
        val trace = TraceEntry("uuid1", "com.test", 1000000, emptyList())
        val verdicts = mapOf("uuid1|com.test||1000000" to Verdict.LIKE)
        val row = ExportHelper.traceExportRow(trace, "uuid1|com.test||1000000", "tab1", verdicts)
        assertEquals("positive", row.verdict)
    }

    @Test
    fun rowsToTsv_hasHeaderAndData() {
        val trace = TraceEntry("uuid1", "com.test", 1000000, emptyList())
        val row = ExportHelper.traceExportRow(trace, "key1", "tab1", emptyMap())
        val tsv = ExportHelper.rowsToTsv(listOf(row))
        val lines = tsv.split("\n")
        assertTrue(lines[0].contains("trace_uuid"))
        assertTrue(lines[1].contains("uuid1"))
    }

    @Test
    fun rowsToTsv_includesBrushFormula() {
        val trace = TraceEntry("uuid1", "com.test", 1000000, emptyList())
        val row = ExportHelper.traceExportRow(trace, "key1", "tab1", emptyMap())
        val tsv = ExportHelper.rowsToTsv(listOf(row))
        assertTrue(tsv.contains("brush.corp.google.com"))
    }

    @Test
    fun rowsToJson_validFormat() {
        val trace = TraceEntry("uuid1", "com.test", 1000000, emptyList())
        val row = ExportHelper.traceExportRow(trace, "key1", "tab1", emptyMap())
        val json = ExportHelper.rowsToJson(listOf(row))
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"trace_uuid\": \"uuid1\""))
    }

    @Test
    fun rowsToTsv_empty_returnsEmpty() {
        assertEquals("", ExportHelper.rowsToTsv(emptyList()))
    }
}
