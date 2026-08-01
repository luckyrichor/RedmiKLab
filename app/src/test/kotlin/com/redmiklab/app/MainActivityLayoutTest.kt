package com.redmiklab.app

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class MainActivityLayoutTest {
    @Test
    fun main_screen_exposes_the_confirmed_sections_and_report_actions() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup
        val text = collectText(root)

        assertTrue("诊断参数" in text)
        assertTrue("诊断与采集模式" in text)
        assertTrue("系统权限与后台能力" in text)
        assertTrue("立即运行" in text)
        assertTrue("夜间计划" in text)
        assertTrue("报告与数据分析" in text)
        assertTrue("历史诊断导出" in text)
        assertTrue("导入诊断报告" in text)
        assertTrue("比较诊断报告" in text)
        assertTrue("分析诊断报告" in text)
    }

    @Test
    fun app_ui_exposes_scheme_a_visual_roles_and_action_styles() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = LinearLayout(activity)
        val section = AppUi.section(activity, "模式", AppUi.SectionTone.MODE) {}
        val primary = AppUi.run {
            container.addAction(activity, "主操作", tone = AppUi.ActionTone.PRIMARY) {}
        }
        val tonal = AppUi.run {
            container.addAction(activity, "浅色操作", tone = AppUi.ActionTone.TONAL) {}
        }
        val neutral = AppUi.run {
            container.addAction(activity, "普通操作", tone = AppUi.ActionTone.NEUTRAL) {}
        }
        val danger = AppUi.run {
            container.addAction(activity, "危险操作", tone = AppUi.ActionTone.DANGER) {}
        }

        assertEquals(0xFFEDF2F7.toInt(), AppUi.BACKGROUND)
        assertEquals(0xFF172131.toInt(), AppUi.TEXT)
        assertEquals(AppUi.SectionTone.MODE, section.tag)
        assertEquals(0xFF7854C4.toInt(), sectionAccentColorOf(section))
        assertEquals(AppUi.ActionTone.PRIMARY, primary.tag)
        assertEquals(AppUi.ActionTone.TONAL, tonal.tag)
        assertEquals(AppUi.ActionTone.NEUTRAL, neutral.tag)
        assertEquals(AppUi.ActionTone.DANGER, danger.tag)
        assertEquals(0xFFFFFFFF.toInt(), primary.currentTextColor)
        assertEquals(0xFF2864DC.toInt(), tonal.currentTextColor)
        assertEquals(0xFF172131.toInt(), neutral.currentTextColor)
        assertEquals(0xFFA33D3D.toInt(), danger.currentTextColor)
        assertEquals(0xFF2864DC.toInt(), backgroundColorOf(primary))
        assertEquals(0xFFE9F0FC.toInt(), backgroundColorOf(tonal))
        assertEquals(0xFFFFFFFF.toInt(), backgroundColorOf(neutral))
        assertEquals(0xFFFFFFFF.toInt(), backgroundColorOf(danger))
    }

    @Test
    fun main_screen_applies_scheme_a_roles_to_sections_and_actions() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup

        assertEquals("自动同步", textForSetting(root, "设置输出文件名"))
        assertEquals(1, viewsWithTag(root, AppUi.SectionTone.PARAMETER).size)
        assertEquals(1, viewsWithTag(root, AppUi.SectionTone.MODE).size)
        assertEquals(1, viewsWithTag(root, AppUi.SectionTone.PERMISSION).size)
        assertEquals(2, viewsWithTag(root, AppUi.SectionTone.RUN).size)
        assertEquals(1, viewsWithTag(root, AppUi.SectionTone.REPORT).size)

        assertEquals(AppUi.ActionTone.PRIMARY, action(root, "立即诊断").tag)
        assertEquals(AppUi.ActionTone.DANGER, action(root, "停止当前诊断").tag)
        assertEquals(AppUi.ActionTone.NEUTRAL, action(root, "安排夜间诊断").tag)
        assertEquals(AppUi.ActionTone.NEUTRAL, action(root, "取消已计划的夜间诊断").tag)
        listOf("历史诊断导出", "导入诊断报告", "比较诊断报告", "分析诊断报告").forEach { label ->
            assertEquals(AppUi.ActionTone.NEUTRAL, action(root, label).tag)
        }
        assertEquals(6, viewsWithTag(root, AppUi.ActionTone.NEUTRAL).size)
    }

    @Test
    fun main_screen_keeps_run_schedule_and_report_actions_in_two_column_rows() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup

        assertEquals(2, (action(root, "立即诊断").parent as ViewGroup).childCount)
        assertEquals(2, (action(root, "安排夜间诊断").parent as ViewGroup).childCount)
        assertEquals(2, (action(root, "历史诊断导出").parent as ViewGroup).childCount)
        assertEquals(2, (action(root, "比较诊断报告").parent as ViewGroup).childCount)
    }

    @Test
    fun scheme_a_actions_are_compact_flat_views_instead_of_miui_buttons() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup
        val action = action(root, "历史诊断导出")
        val expectedHeight = AppUi.run { activity.dp(36) }

        assertFalse("系统 Button 会在 MIUI 注入阴影和默认留白", action is Button)
        assertEquals(expectedHeight, action.layoutParams.height)
        assertEquals(0f, action.elevation)
        assertEquals(12f, spOf(activity, action))
    }

    @Test
    fun settings_use_chevrons_toggles_and_badges_from_the_browser_reference() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup
        val text = collectText(root)

        assertTrue("参数和权限行应显示右箭头", "›" in text)
        assertEquals(3, viewsWithTag(root, "scheme_a_toggle").size)
        assertEquals(4, viewsWithTag(root, "scheme_a_badge").size)
        assertEquals(13f, spOf(activity, sectionTitle(root, "诊断参数")))
    }

    @Test
    fun section_accent_and_title_share_one_vertically_centered_header_row() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup
        val headers = viewsWithTag(root, "scheme_a_section_header").filterIsInstance<LinearLayout>()

        assertEquals(6, headers.size)
        headers.forEach { header ->
            assertEquals(
                android.view.Gravity.CENTER_VERTICAL,
                header.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK,
            )
            assertEquals(2, header.childCount)
            val title = header.getChildAt(1) as TextView
            assertEquals(0, (title.layoutParams as LinearLayout.LayoutParams).bottomMargin)
            assertTrue((header.layoutParams as LinearLayout.LayoutParams).bottomMargin > 0)
        }
    }

    @Test
    fun status_bar_inset_is_added_to_the_page_header_top_padding() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val header = viewsWithTag(root, "scheme_a_page_header").single()
        val insetTop = 48
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, insetTop, 0, 0))
            .build()

        root.dispatchApplyWindowInsets(insets)

        assertEquals(AppUi.run { activity.dp(10) } + insetTop, header.paddingTop)
    }

    @Test
    fun header_always_shows_an_explicit_night_schedule_status() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("diagnostic_config", 0)
            .edit()
            .clear()
            .commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView as ViewGroup
        val scheduleStatus = viewsWithTag(root, "night_schedule_status").single() as TextView

        assertEquals("夜间计划：尚未安排", scheduleStatus.text.toString())
    }

    private fun collectText(group: ViewGroup): String = buildString {
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is TextView -> appendLine(child.text)
                is ViewGroup -> appendLine(collectText(child))
            }
        }
    }

    private fun backgroundColorOf(view: android.view.View): Int =
        (view.background as GradientDrawable).color!!.defaultColor

    private fun sectionAccentColorOf(section: ViewGroup): Int =
        ((section.getChildAt(0) as ViewGroup).getChildAt(0).background as ColorDrawable).color

    private fun textForSetting(root: ViewGroup, label: String): String? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is ViewGroup) {
                if (child.childCount >= 2 && containsText(child.getChildAt(0), label) && child.getChildAt(1) is TextView) {
                    return (child.getChildAt(1) as TextView).text.toString()
                }
                textForSetting(child, label)?.let { return it }
            }
        }
        return null
    }

    private fun action(root: ViewGroup, label: String): TextView =
        allTextViews(root).first { it.text.toString() == label }

    private fun sectionTitle(root: ViewGroup, label: String): TextView =
        allTextViews(root).first { it.text.toString() == label }

    private fun viewsWithTag(root: ViewGroup, tag: Any): List<View> = buildList {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.tag == tag) add(child)
            if (child is ViewGroup) addAll(viewsWithTag(child, tag))
        }
    }

    private fun allTextViews(root: ViewGroup): List<TextView> = buildList {
        for (index in 0 until root.childCount) {
            when (val child = root.getChildAt(index)) {
                is TextView -> add(child)
                is ViewGroup -> addAll(allTextViews(child))
            }
        }
    }

    private fun containsText(view: View, label: String): Boolean = when (view) {
        is TextView -> view.text.toString() == label
        is ViewGroup -> (0 until view.childCount).any { containsText(view.getChildAt(it), label) }
        else -> false
    }

    private fun spOf(activity: MainActivity, view: TextView): Float =
        view.textSize / (activity.resources.displayMetrics.density * activity.resources.configuration.fontScale)
}
