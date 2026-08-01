package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameAutoSaverTest {
    @Test
    fun persists_each_user_edited_filename_without_a_save_button() {
        val saved = mutableListOf<String>()
        val saver = ExportFileNameAutoSaver { saved += it }

        saver.onUserEdited("第一次测试")
        saver.onUserEdited("第二次测试")

        assertEquals(listOf("第一次测试", "第二次测试"), saved)
    }
}
