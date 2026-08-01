package com.redmiklab.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Insets
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.ReportFormat
import com.redmiklab.reports.ReportNumericSummary
import com.redmiklab.reports.ReportRun
import com.redmiklab.storage.DiagnosticRunEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.jvm.functions.Function0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReportActivitiesSmokeTest {
    @Test
    fun every_report_entry_page_can_be_created() {
        listOf<Class<out Activity>>(
            ReportHistoryActivity::class.java,
            ReportImportActivity::class.java,
            ReportCompareActivity::class.java,
            ReportAnalysisActivity::class.java,
        ).forEach { activity ->
            Robolectric.buildActivity(activity).setup().get()
        }
    }

    @Test
    fun settings_dialog_uses_custom_scheme_a_panel_instead_of_native_miui_controls() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        DiagnosticSettingsDialogs.snapshot(activity, current = 5) {}
        assertCustomSchemeADialog(ShadowAlertDialog.getLatestAlertDialog() as AlertDialog)

        DiagnosticSettingsDialogs.endpoints(activity, DiagnosticConfig.default()) { _, _, _ -> }
        assertCustomSchemeADialog(ShadowAlertDialog.getLatestAlertDialog() as AlertDialog)

        DiagnosticSettingsDialogs.time(activity, "设置开始时间", LocalTime.of(1, 0), {}, {})
        assertCustomSchemeADialog(ShadowAlertDialog.getLatestAlertDialog() as AlertDialog)

        DiagnosticSettingsDialogs.outputName(activity, "夜间测试", ReportFormat.ZIP) { _, _ -> }
        assertCustomSchemeADialog(ShadowAlertDialog.getLatestAlertDialog() as AlertDialog)
    }

    @Test
    fun help_uses_an_anchored_non_modal_popup_instead_of_an_alert_dialog() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val anchor = TextView(activity)
        activity.setContentView(anchor)
        ShadowAlertDialog.reset()

        val result = AppUi.showHelp(anchor, "严格模式", "说明")

        assertTrue(result.isShowing)
        assertTrue(result.isOutsideTouchable)
        assertFalse(result.isFocusable)
        assertEquals(null, ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun help_popup_uses_the_anchor_window_instead_of_the_main_activity_window() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val anchor = TextView(activity)
        val dialog = AlertDialog.Builder(activity).setView(anchor).create().apply { show() }

        assertEquals(dialog.window!!.decorView, AppUi.helpPopupParent(anchor))
    }

    @Test
    fun scheme_a_dialog_preserves_an_explicit_content_height_for_scrollable_lists() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val expectedHeight = AppUi.run { activity.dp(320) }
        val list = ListView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                expectedHeight,
            )
        }

        AppUi.showSchemeADialog(
            activity,
            "选择历史诊断",
            "选择一条记录。",
            list,
            saveLabel = null,
        ) { false }

        assertEquals(expectedHeight, list.layoutParams.height)
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        assertNotNull(allViews(dialog.window!!.decorView as ViewGroup).firstOrNull {
            it.tag == "scheme_a_dialog_cancel"
        })
    }

    @Test
    fun endpoint_and_output_dropdowns_use_single_border_choice_fields_with_arrows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        DiagnosticSettingsDialogs.endpoints(activity, DiagnosticConfig.default()) { _, _, _ -> }
        val endpointDialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val endpointViews = allViews(endpointDialog.window!!.decorView as ViewGroup)
        assertTrue(endpointViews.none { it is android.widget.Spinner })
        assertEquals(1, endpointViews.count { it.tag == "scheme_a_choice_field" })
        assertTrue(endpointViews.filterIsInstance<TextView>().any { it.text.toString() == "▾" })

        DiagnosticSettingsDialogs.outputName(activity, "夜间测试", ReportFormat.ZIP) { _, _ -> }
        val outputDialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val outputViews = allViews(outputDialog.window!!.decorView as ViewGroup)
        assertTrue(outputViews.none { it is android.widget.Spinner })
        assertEquals(1, outputViews.count { it.tag == "scheme_a_choice_field" })
        assertTrue(outputViews.filterIsInstance<TextView>().any { it.text.toString() == "▾" })
    }

    @Test
    fun endpoint_retry_help_expands_inline_below_its_row_without_opening_another_dialog() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        DiagnosticSettingsDialogs.endpoints(activity, DiagnosticConfig.default()) { _, _, _ -> }
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val views = allViews(dialog.window!!.decorView as ViewGroup)
        val icon = views.first { it.tag == "endpoint_retry_help_icon" }
        val card = views.first { it.tag == "scheme_a_inline_help_card" }
        val close = views.first { it.tag == "scheme_a_inline_help_close" }
        val retryRow = icon.parent as ViewGroup
        val form = retryRow.parent as ViewGroup

        assertEquals(View.GONE, card.visibility)
        assertEquals(form.indexOfChild(retryRow) + 1, form.indexOfChild(card))

        icon.performClick()

        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(dialog, ShadowAlertDialog.getLatestAlertDialog())

        close.performClick()

        assertEquals(View.GONE, card.visibility)
    }

    @Test
    fun time_dialog_uses_separate_hour_and_minute_inputs_without_requiring_a_colon() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var saved: LocalTime? = null

        DiagnosticSettingsDialogs.time(activity, "设置开始时间", LocalTime.of(1, 0), {}) { saved = it }
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val views = allViews(dialog.window!!.decorView as ViewGroup)
        val hour = views.firstOrNull { it.tag == "scheme_a_time_hour" } as? EditText
        val minute = views.firstOrNull { it.tag == "scheme_a_time_minute" } as? EditText

        assertNotNull(hour)
        assertNotNull(minute)
        assertEquals("01", hour!!.text.toString())
        assertEquals("00", minute!!.text.toString())
        assertFalse(views.filterIsInstance<EditText>().any { it.hint?.toString() == "手动输入（HH:mm）" })

        hour.setText("12")
        minute.setText("34")
        views.first { it.tag == "scheme_a_dialog_save" }.performClick()

        assertEquals(LocalTime.of(12, 34), saved)
    }

    @Test
    fun shared_choice_adapter_gives_history_rows_a_tonal_selected_state() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val adapter = AppUi.SchemeAChoiceAdapter(activity, listOf("第一条", "第二条"), true)

        val initiallyUnselected = adapter.getView(0, null, FrameLayout(activity)) as TextView
        assertEquals(AppUi.TEXT, initiallyUnselected.currentTextColor)
        assertEquals(AppUi.SURFACE, (initiallyUnselected.background as GradientDrawable).color!!.defaultColor)

        adapter.select(1)

        val row = adapter.getView(1, null, FrameLayout(activity)) as TextView

        assertEquals(AppUi.PRIMARY, row.currentTextColor)
        assertEquals(AppUi.TONAL_SURFACE, (row.background as GradientDrawable).color!!.defaultColor)
    }

    @Test
    fun report_workspace_scroll_fills_the_available_viewport() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val page = AppUi.page(activity, "测试页面")

        val scroll = ReportWorkspaceUi.scroll(activity, page)

        assertTrue(scroll.isFillViewport)
        assertEquals(page, scroll.getChildAt(0))
        assertEquals(AppUi.BACKGROUND, (page.background as ColorDrawable).color)
    }

    @Test
    fun report_workspace_empty_state_groups_guidance_formats_and_equal_actions() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val card = ReportWorkspaceUi.emptyState(
            activity = activity,
            tag = "report_workspace_empty_state",
            icon = ReportWorkspaceUi.WorkspaceIcon.ANALYSIS,
            title = "选择一份诊断报告",
            description = "选择后将生成关键指标和趋势图。",
            formatLabels = listOf("ZIP", "JSON"),
            actions = listOf(
                AppUi.ActionSpec("从历史记录选择", AppUi.ActionTone.NEUTRAL) {},
                AppUi.ActionSpec("从本地文件选择", AppUi.ActionTone.NEUTRAL) {},
            ),
        )

        val views = allViews(card)
        val texts = views.filterIsInstance<TextView>()

        assertEquals("report_workspace_empty_state", card.tag)
        assertTrue(texts.any { it.text.toString() == "选择一份诊断报告" })
        assertTrue(texts.any { it.text.toString() == "ZIP" })
        assertTrue(texts.any { it.text.toString() == "JSON" })
        assertEquals(
            2,
            texts.count {
                it.text.toString() in setOf("从历史记录选择", "从本地文件选择") &&
                    it.tag == AppUi.ActionTone.NEUTRAL
            },
        )
    }

    @Test
    fun report_pages_keep_their_scheme_a_page_background() {
        listOf<Class<out Activity>>(
            ReportHistoryActivity::class.java,
            ReportImportActivity::class.java,
            ReportCompareActivity::class.java,
            ReportAnalysisActivity::class.java,
        ).forEach { type ->
            val activity = Robolectric.buildActivity(type).setup().get()
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val page = (content.getChildAt(0) as ScrollView).getChildAt(0)
            assertEquals(AppUi.BACKGROUND, (page.background as ColorDrawable).color)
        }
    }

    @Test
    fun report_pages_add_status_and_navigation_insets_without_accumulating_them() {
        listOf<Class<out Activity>>(
            ReportHistoryActivity::class.java,
            ReportImportActivity::class.java,
            ReportCompareActivity::class.java,
            ReportAnalysisActivity::class.java,
        ).forEach { type ->
            val activity = Robolectric.buildActivity(type).setup().get()
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val page = allViews(content).first { it.tag == "scheme_a_report_page" }
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 48, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 72))
                .build()

            content.dispatchApplyWindowInsets(insets)
            content.dispatchApplyWindowInsets(insets)

            assertEquals(AppUi.run { activity.dp(14) } + 48, page.paddingTop)
            assertEquals(AppUi.run { activity.dp(24) } + 72, page.paddingBottom)
        }
    }

    @Test
    fun report_source_actions_are_not_implicitly_primary() {
        val analysis = Robolectric.buildActivity(ReportAnalysisActivity::class.java).setup().get()
        val compare = Robolectric.buildActivity(ReportCompareActivity::class.java).setup().get()
        val import = Robolectric.buildActivity(ReportImportActivity::class.java).setup().get()

        assertEquals(AppUi.ActionTone.NEUTRAL, button(analysis, "从历史记录选择").tag)
        assertEquals(AppUi.ActionTone.NEUTRAL, button(analysis, "从本地文件选择（最近 / 浏览）").tag)
        assertEquals(
            2,
            buttons(compare, "从历史记录选择").count { it.tag == AppUi.ActionTone.NEUTRAL },
        )
        assertEquals(
            2,
            buttons(compare, "从本地选择（最近 / 浏览）").count { it.tag == AppUi.ActionTone.NEUTRAL },
        )
        assertEquals(AppUi.ActionTone.NEUTRAL, button(import, "选择本地报告").tag)
    }

    @Test
    fun import_and_compare_pages_use_full_height_neutral_report_surfaces() {
        val import = Robolectric.buildActivity(ReportImportActivity::class.java).setup().get()
        val compare = Robolectric.buildActivity(ReportCompareActivity::class.java).setup().get()
        val importViews = allViews(import.window.decorView as ViewGroup)
        val compareRoot = compare.findViewById<ViewGroup>(android.R.id.content)
        val compareViews = allViews(compareRoot)

        assertNotNull(importViews.firstOrNull { it.tag == "import_workspace_empty" })
        assertEquals(AppUi.ActionTone.NEUTRAL, button(import, "选择本地报告").tag)
        assertTrue((compareRoot.getChildAt(0) as ScrollView).isFillViewport)
        listOf("compare_source_a", "compare_source_b").forEach { tag ->
            val card = compareViews.first { it.tag == tag }
            assertEquals(AppUi.SURFACE, (card.background as GradientDrawable).color!!.defaultColor)
        }
    }

    @Test
    fun analysis_page_starts_with_a_balanced_report_workspace() {
        val activity = Robolectric.buildActivity(ReportAnalysisActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val views = allViews(root)

        assertTrue((root.getChildAt(0) as ScrollView).isFillViewport)
        assertNotNull(views.firstOrNull { it.tag == "analysis_workspace_empty" })
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "ZIP" })
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "JSON" })
        assertEquals(AppUi.ActionTone.NEUTRAL, button(activity, "从历史记录选择").tag)
        assertEquals(AppUi.ActionTone.NEUTRAL, button(activity, "从本地文件选择（最近 / 浏览）").tag)
    }

    @Test
    fun analysis_result_replaces_the_empty_state_with_summary_and_metric_grid() {
        val activity = Robolectric.buildActivity(ReportAnalysisActivity::class.java).setup().get()
        val render = ReportAnalysisActivity::class.java.getDeclaredMethod(
            "render",
            CompleteReport::class.java,
        ).apply { isAccessible = true }

        render.invoke(activity, completeReportFixture())

        val views = allViews(activity.window.decorView as ViewGroup)
        assertEquals(null, views.firstOrNull { it.tag == "analysis_workspace_empty" })
        assertNotNull(views.firstOrNull { it.tag == "analysis_report_summary" })
        assertNotNull(views.firstOrNull { it.tag == "analysis_metric_grid" })
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "12.00 MiB" })
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "8.00 Mbps" })
    }

    @Test
    fun analysis_result_keeps_both_report_reselection_actions_available() {
        val activity = Robolectric.buildActivity(ReportAnalysisActivity::class.java).setup().get()
        val render = ReportAnalysisActivity::class.java.getDeclaredMethod(
            "render",
            CompleteReport::class.java,
        ).apply { isAccessible = true }

        render.invoke(activity, completeReportFixture())

        val texts = allViews(activity.window.decorView as ViewGroup)
            .filterIsInstance<TextView>()
            .map { it.text.toString() }
        assertTrue("更换分析报告" in texts)
        assertEquals(
            AppUi.ActionTone.NEUTRAL,
            button(activity, "从历史记录重新选择").tag,
        )
        assertEquals(
            AppUi.ActionTone.NEUTRAL,
            button(activity, "从本地文件重新选择").tag,
        )
    }

    @Test
    fun history_card_actions_default_to_neutral_instead_of_first_primary() {
        val activity = Robolectric.buildActivity(ReportHistoryActivity::class.java).setup().get()
        val method = ReportHistoryActivity::class.java.getDeclaredMethod(
            "smallAction",
            String::class.java,
            Function0::class.java,
        ).apply { isAccessible = true }
        val callback = object : Function0<Unit> {
            override fun invoke() = Unit
        }

        val action = method.invoke(activity, "分析报告", callback) as TextView

        assertEquals(AppUi.ActionTone.NEUTRAL, action.tag)
        assertFalse(action is android.widget.Button)
    }

    @Test
    fun history_names_prefer_the_saved_report_name_and_use_readable_choice_labels() {
        val named = historyRun(reportBaseName = "七月夜间测试")
        val unnamed = historyRun(reportBaseName = "")

        assertEquals("七月夜间测试", ReportActivitySupport.historyDisplayName(named))
        assertTrue(ReportActivitySupport.historyDisplayName(unnamed).startsWith("RedmiKLab-"))
        assertEquals(
            "七月夜间测试\n2026-07-25 01:00 · 严格模式 · 已完成",
            ReportActivitySupport.historyChoiceLabel(named),
        )
    }

    @Test
    fun history_choice_rows_are_spacious_structured_cards_and_recycle_cleanly() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val first = historyRun(reportBaseName = "七月夜间测试")
        val second = historyRun(reportBaseName = "第二份诊断报告")
        val adapter = ReportHistoryChoiceAdapter(activity, listOf(first, second))

        val firstRow = adapter.getView(0, null, FrameLayout(activity)) as ViewGroup
        val firstTexts = allViews(firstRow).filterIsInstance<TextView>().map { it.text.toString() }

        assertTrue(firstRow.minimumHeight >= AppUi.run { activity.dp(76) })
        assertTrue("七月夜间测试" in firstTexts)
        assertTrue(firstTexts.any { "2026-07-25 01:00" in it })
        assertTrue(firstTexts.any { "严格模式" in it && "已完成" in it })
        assertTrue(allViews(firstRow).any { it.tag == "history_choice_chevron" })
        assertEquals(first, adapter.getItem(0))

        val recycled = adapter.getView(1, firstRow, FrameLayout(activity)) as ViewGroup
        val recycledTexts = allViews(recycled).filterIsInstance<TextView>().map { it.text.toString() }

        assertTrue("第二份诊断报告" in recycledTexts)
        assertFalse("七月夜间测试" in recycledTexts)
        assertEquals(second, adapter.getItem(1))
    }

    @Test
    fun history_card_keeps_actions_visible_and_toggles_only_its_details() {
        val activity = Robolectric.buildActivity(ReportHistoryActivity::class.java).setup().get()
        val method = ReportHistoryActivity::class.java.getDeclaredMethod(
            "runCard",
            DiagnosticRunEntity::class.java,
        ).apply { isAccessible = true }

        val card = method.invoke(activity, historyRun(reportBaseName = "七月夜间测试")) as ViewGroup
        val views = allViews(card)
        val actions = views.first { it.tag == "history_actions" } as ViewGroup
        val details = views.first { it.tag == "history_details_content" } as ViewGroup
        val toggle = views.first { it.tag == "history_details_toggle" } as TextView

        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().lineSequence().first() == "七月夜间测试" })
        assertEquals(View.VISIBLE, actions.visibility)
        listOf("分析报告", "导出报告", "修改文件名", "与其他报告比较").forEach { label ->
            assertTrue(allViews(actions).filterIsInstance<TextView>().any { it.text.toString() == label })
        }
        assertEquals(View.GONE, details.visibility)
        assertFalse(card.hasOnClickListeners())
        assertEquals("查看详情", toggle.text.toString())

        details.addView(View(activity))
        toggle.performClick()
        assertEquals(View.VISIBLE, details.visibility)
        assertEquals("收起详情", toggle.text.toString())

        toggle.performClick()
        assertEquals(View.GONE, details.visibility)
        assertEquals("查看详情", toggle.text.toString())
    }

    private fun button(activity: Activity, label: String): TextView =
        buttons(activity, label).first()

    private fun buttons(activity: Activity, label: String): List<TextView> =
        allViews(activity.window.decorView as ViewGroup)
            .filterIsInstance<TextView>()
            .filter { it.text.toString() == label }

    private fun historyRun(reportBaseName: String): DiagnosticRunEntity {
        val zone = ZoneId.systemDefault()
        val started = LocalDateTime.of(2026, 7, 25, 1, 0).atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 7, 25, 7, 0).atZone(zone).toInstant().toEpochMilli()
        return DiagnosticRunEntity(
            "run-history",
            started,
            ended,
            started,
            ended,
            "COMPLETED",
            "STRICT",
            5,
            10,
            30,
            true,
            null,
            ended,
            reportBaseName,
            "ZIP",
            "23013RK75C",
        )
    }

    private fun completeReportFixture(): CompleteReport {
        val zone = ZoneId.systemDefault()
        val started = LocalDateTime.of(2026, 7, 25, 1, 0).atZone(zone).toInstant().toEpochMilli()
        val ended = LocalDateTime.of(2026, 7, 25, 7, 0).atZone(zone).toInstant().toEpochMilli()
        return CompleteReport(
            run = ReportRun(
                runId = "run-analysis",
                status = "COMPLETED",
                runtimeMode = "STRICT",
                plannedStart = started,
                plannedEnd = ended,
                actualStart = started,
                actualEnd = ended,
                snapshotMinutes = 5,
                connectionCaptureEnabled = true,
                reportBaseName = "七月夜间测试",
                preferredFormat = "ZIP",
                deviceModel = "23013RK75C",
            ),
            summary = ReportNumericSummary(
                averageDownloadMbps = 8.0,
                snapshotCount = 73,
                systemMobileBytes = 12L * 1024 * 1024,
                tunnelObservedBytes = 9L * 1024 * 1024,
            ),
        )
    }

    private fun allViews(root: ViewGroup): List<android.view.View> = buildList {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            add(child)
            if (child is ViewGroup) addAll(allViews(child))
        }
    }

    private fun assertCustomSchemeADialog(dialog: AlertDialog) {
        val views = allViews(dialog.window!!.decorView as ViewGroup)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.isNullOrBlank())
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.isNullOrBlank())
        assertEquals(1, views.count { it.tag == "scheme_a_dialog_panel" })
        assertEquals(1, views.count { it.tag == "scheme_a_dialog_save" })
        assertEquals(1, views.count { it.tag == "scheme_a_dialog_cancel" })
        assertFalse(views.first { it.tag == "scheme_a_dialog_save" } is android.widget.Button)
    }

}
