package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReportFileNamePolicyTest {
    private val exportedAt = LocalDateTime.of(2026, 7, 14, 16, 40)

    @Test
    fun uses_confirmed_end_time_before_start_time() {
        val name = ReportFileNamePolicy.create(
            exportedAt = exportedAt,
            deviceModel = "K80",
            customDescription = "抖音锁屏测试",
            start = LocalTime.of(0, 0),
            end = LocalTime.of(7, 0),
            startWasConfirmed = true,
            endWasConfirmed = true,
        )

        assertEquals("20260714-0700-RedmiKLab-K80-抖音锁屏测试.zip", name)
    }

    @Test
    fun uses_confirmed_start_time_even_when_it_matches_the_default() {
        val name = ReportFileNamePolicy.create(
            exportedAt = exportedAt,
            deviceModel = "K60",
            customDescription = "",
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(7, 0),
            startWasConfirmed = true,
            endWasConfirmed = false,
        )

        assertEquals("20260714-0000-RedmiKLab-K60-移动网络测试.zip", name)
    }

    @Test
    fun uses_export_time_when_neither_time_has_been_confirmed() {
        val name = ReportFileNamePolicy.create(
            exportedAt = exportedAt,
            deviceModel = "K80",
            customDescription = "  夜间/复测  ",
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(7, 0),
            startWasConfirmed = false,
            endWasConfirmed = false,
        )

        assertEquals("20260714-1640-RedmiKLab-K80-夜间_复测.zip", name)
    }

    @Test
    fun keeps_the_filename_typed_in_the_input_and_normalizes_it_for_zip() {
        assertEquals(
            "20260714-0700-RedmiKLab-K80-抖音_复测.zip",
            ReportFileNamePolicy.normalizeForZip(" 20260714-0700-RedmiKLab-K80-抖音/复测 "),
        )
    }
}
