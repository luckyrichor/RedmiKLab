package com.redmiklab.app

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode
import com.redmiklab.model.ProjectIdentity
import com.redmiklab.reports.ReportFormat
import com.redmiklab.storage.DiagnosticDatabaseFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class MainActivity : Activity() {
    private var diagnosticWindowText: TextView? = null
    private var runtimeModeStatus: TextView? = null
    private var nightScheduleStatus: TextView? = null
    private var snapshotValue: TextView? = null
    private var endpointsValue: TextView? = null
    private var startValue: TextView? = null
    private var endValue: TextView? = null
    private var outputValue: TextView? = null
    private var connectionCaptureValue: TextView? = null
    private var runtimeModeValue: TextView? = null
    private var exactAlarmValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestEssentialPermissions()
        val preferences = DiagnosticPreferences(this)
        ensureDefaultReportName(preferences)
        Thread {
            DiagnosticDatabaseFactory.get(this).diagnosticDao().backfillMissingDeviceModel(Build.MODEL)
        }.start()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.BACKGROUND)
        }
        val header = LinearLayout(this).apply {
            tag = "scheme_a_page_header"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(HEADER_TOP_PADDING_DP), dp(16), dp(8))
            addView(TextView(context).apply {
                text = getString(R.string.app_title, ProjectIdentity.name)
                textSize = 20f
                setTextColor(AppUi.TEXT)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = "移动网络夜间诊断与连接证据采集"
                textSize = 10f
                setTextColor(AppUi.MUTED)
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
            diagnosticWindowText = TextView(context).apply {
                setTextColor(AppUi.TEXT)
                textSize = 12f
                includeFontPadding = false
                setLineSpacing(0f, 1.08f)
                setPadding(0, dp(7), 0, dp(2))
            }.also(::addView)
            nightScheduleStatus = TextView(context).apply {
                tag = "night_schedule_status"
                setTextColor(AppUi.MUTED)
                textSize = 11f
                includeFontPadding = false
                setPadding(0, dp(1), 0, dp(2))
            }.also(::addView)
            runtimeModeStatus = TextView(context).apply {
                setTextColor(AppUi.MUTED)
                textSize = 11f
                includeFontPadding = false
            }.also(::addView)
        }
        root.addView(header)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(32))
        }
        buildParameterSection(actions, preferences)
        buildModeSection(actions, preferences)
        buildPermissionSection(actions)
        buildImmediateSection(actions, preferences)
        buildScheduleSection(actions, preferences)
        buildReportSection(actions)

        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = false
                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                addView(actions)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                header.setPadding(
                    dp(16),
                    dp(HEADER_TOP_PADDING_DP) + insets.getInsets(WindowInsets.Type.statusBars()).top,
                    dp(16),
                    dp(8),
                )
                actions.setPadding(
                    dp(16),
                    dp(4),
                    dp(16),
                    dp(32) + insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                )
                insets
            }
            root.requestApplyInsets()
        }
        restoreSystemStatusBar()
        refreshAll()
    }

    private fun buildParameterSection(actions: LinearLayout, preferences: DiagnosticPreferences) {
        val section = AppUi.section(this, "诊断参数", AppUi.SectionTone.PARAMETER) {
            snapshotValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "设置快照间隔",
                    "",
                    "按固定间隔记录网络、信号、屏幕、休眠、系统流量和连接采集状态。",
                ) {
                    val current = preferences.load()
                    DiagnosticSettingsDialogs.snapshot(this@MainActivity, current.snapshotMinutes) { minutes ->
                        preferences.save(current.copy(snapshotMinutes = minutes))
                        refreshAll()
                        Toast.makeText(this@MainActivity, "快照间隔已保存；运行中的诊断仍使用启动时配置", Toast.LENGTH_LONG).show()
                    }
                }
            }
            endpointsValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "设置测速地址",
                    "",
                    "主地址失败 5 次后使用备用地址再尝试 5 次；每次失败之间按设置间隔等待。",
                ) {
                    DiagnosticSettingsDialogs.endpoints(this@MainActivity, preferences.load()) { primary, fallback, seconds ->
                        val updated = preferences.load().copy(
                            probeEndpoint = primary,
                            fallbackProbeEndpoint = fallback,
                            probeRetryDelaySeconds = seconds,
                        )
                        preferences.save(updated)
                        refreshAll()
                        Toast.makeText(this@MainActivity, "测速地址和重试间隔已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            startValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "设置开始时间",
                    "",
                    "计划诊断的开始时刻。使用 24 小时制，整个诊断窗口最长为 6 小时。",
                ) { showStartTime(preferences) }
            }
            endValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "设置结束时间",
                    "",
                    "计划诊断的结束时刻；结束时会额外执行最终快照。使用 24 小时制。",
                ) { showEndTime(preferences) }
            }
            outputValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "设置输出文件名",
                    "",
                    "设置本次或下一次诊断默认使用的报告名称与格式；导出时仍可再次修改。",
                ) { showOutputName(preferences) }
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun buildModeSection(actions: LinearLayout, preferences: DiagnosticPreferences) {
        val section = AppUi.section(this, "诊断与采集模式", AppUi.SectionTone.MODE) {
            runtimeModeValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "严格模式",
                    "",
                    "标准模式由系统闹钟调度；严格模式由连接级采集前台服务维持采样节奏，必须先开启连接级采集。",
                    AppUi.SettingStyle.TOGGLE,
                ) {
                    val current = preferences.load()
                    preferences.save(
                        current.copy(
                            runtimeMode = if (current.runtimeMode == DiagnosticRuntimeMode.STANDARD) {
                                DiagnosticRuntimeMode.STRICT
                            } else DiagnosticRuntimeMode.STANDARD,
                        ),
                    )
                    refreshAll()
                }
            }
            connectionCaptureValue = AppUi.run {
                addSetting(
                    this@MainActivity,
                    "连接级采集",
                    "",
                    "通过本地 VPN 观察连接元数据、成功转发和失败尝试；不解密 HTTPS 正文，不能与 Clash 同时使用。",
                    AppUi.SettingStyle.TOGGLE,
                ) { toggleConnectionCapture() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                exactAlarmValue = AppUi.run {
                    addSetting(
                        this@MainActivity,
                        "精确夜间采样闹钟",
                        "",
                        "允许 App 使用系统精确闹钟安排开始、结束、标准模式快照和异常恢复；不会响铃。",
                        AppUi.SettingStyle.TOGGLE,
                    ) {
                        preferences.setExactAlarmEnabled(!preferences.exactAlarmEnabled())
                        refreshAll()
                    }
                }
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun buildPermissionSection(actions: LinearLayout) {
        val section = AppUi.section(this, "系统权限与后台能力", AppUi.SectionTone.PERMISSION) {
            AppUi.run {
                addSetting(this@MainActivity, "应用流量统计", "去授权", "允许读取 Android 提供的 UID 时段级近似移动流量。", AppUi.SettingStyle.BADGE) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                addSetting(this@MainActivity, "媒体与通知详情", "可选", "记录参与本次诊断应用的媒体会话与通知字段，帮助解释连接行为。", AppUi.SettingStyle.BADGE) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                addSetting(this@MainActivity, "后台诊断不受电池限制", "去管理", "进入本 App 的电池策略页；可改为无限制或恢复其他省电策略。", AppUi.SettingStyle.BADGE) {
                    requestBatteryOptimizationExemption()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addSetting(this@MainActivity, "闹钟和提醒权限", "去管理", "进入系统的“闹钟和提醒”权限页。App 内开关和系统授权是两个不同层级。", AppUi.SettingStyle.BADGE) {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:$packageName")),
                        )
                    }
                }
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun buildImmediateSection(actions: LinearLayout, preferences: DiagnosticPreferences) {
        val section = AppUi.section(this, "立即运行", AppUi.SectionTone.RUN) {
            AppUi.run {
                addActionRow(
                    this@MainActivity,
                    AppUi.ActionSpec("立即诊断", AppUi.ActionTone.PRIMARY) {
                        startDiagnosisIfAllowed(preferences.load(), scheduled = false)
                    },
                    AppUi.ActionSpec("停止当前诊断", AppUi.ActionTone.DANGER) {
                        startForegroundService(
                            Intent(this@MainActivity, NightDiagnosticService::class.java)
                                .setAction(NightDiagnosticService.ACTION_END),
                        )
                    },
                )
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun buildScheduleSection(actions: LinearLayout, preferences: DiagnosticPreferences) {
        val section = AppUi.section(this, "夜间计划", AppUi.SectionTone.RUN) {
            AppUi.run {
                addActionRow(
                    this@MainActivity,
                    AppUi.ActionSpec("安排夜间诊断", AppUi.ActionTone.NEUTRAL) {
                        startDiagnosisIfAllowed(preferences.load(), scheduled = true)
                    },
                    AppUi.ActionSpec("取消已计划的夜间诊断", AppUi.ActionTone.NEUTRAL) {
                        DiagnosticScheduler(this@MainActivity).cancel()
                        refreshAll()
                        Toast.makeText(this@MainActivity, "已取消下一次计划诊断", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun buildReportSection(actions: LinearLayout) {
        val section = AppUi.section(this, "报告与数据分析", AppUi.SectionTone.REPORT) {
            AppUi.run {
                addActionRow(
                    this@MainActivity,
                    AppUi.ActionSpec("历史诊断导出") {
                        startActivity(Intent(this@MainActivity, ReportHistoryActivity::class.java))
                    },
                    AppUi.ActionSpec("导入诊断报告") {
                        startActivity(Intent(this@MainActivity, ReportImportActivity::class.java))
                    },
                )
                addActionRow(
                    this@MainActivity,
                    AppUi.ActionSpec("比较诊断报告") {
                        startActivity(Intent(this@MainActivity, ReportCompareActivity::class.java))
                    },
                    AppUi.ActionSpec("分析诊断报告") {
                        startActivity(Intent(this@MainActivity, ReportAnalysisActivity::class.java))
                    },
                )
            }
        }
        actions.addView(section, AppUi.marginParams(this))
    }

    private fun showStartTime(preferences: DiagnosticPreferences) {
        val current = preferences.load()
        DiagnosticSettingsDialogs.time(
            this,
            "设置开始时间",
            current.start,
            onOpened = {
                preferences.markStartTimeOpened()
                refreshTimeDependentFields(preferences)
            },
        ) { selected ->
            val updated = preferences.load().copy(start = selected)
            if (DiagnosticTimeEditPolicy.canSave(preferences.load(), updated)) {
                preferences.saveStartTime(selected)
                refreshTimeDependentFields(preferences)
            } else Toast.makeText(this, "诊断窗口最长为 6 小时", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEndTime(preferences: DiagnosticPreferences) {
        val current = preferences.load()
        DiagnosticSettingsDialogs.time(
            this,
            "设置结束时间",
            current.end,
            onOpened = {
                preferences.markEndTimeOpened()
                refreshTimeDependentFields(preferences)
            },
        ) { selected ->
            val updated = preferences.load().copy(end = selected)
            if (DiagnosticTimeEditPolicy.canSave(preferences.load(), updated)) {
                preferences.saveEndTime(selected)
                refreshTimeDependentFields(preferences)
            } else Toast.makeText(this, "诊断窗口最长为 6 小时", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOutputName(preferences: DiagnosticPreferences) {
        val format = ReportFormat.from(preferences.preferredReportFormat())
        DiagnosticSettingsDialogs.outputName(
            this,
            preferences.savedExportFileName().orEmpty(),
            format,
        ) { base, selected ->
            saveReportIdentity(preferences, base, selected)
            refreshAll()
        }
    }

    private fun saveReportIdentity(preferences: DiagnosticPreferences, base: String, format: ReportFormat) {
        val normalized = ReportFileNamePolicy.withFormat(base, format)
        preferences.saveExportFileName(normalized)
        preferences.savePreferredReportFormat(format.name)
        Thread {
            DiagnosticDatabaseFactory.get(this).diagnosticDao().activeRun()?.let { run ->
                DiagnosticDatabaseFactory.get(this).diagnosticDao()
                    .updateReportIdentity(run.runId, ReportFileNamePolicy.baseName(normalized), format.name)
            }
        }.start()
    }

    private fun startDiagnosisIfAllowed(config: DiagnosticConfig, scheduled: Boolean) {
        if (!config.canStartDiagnosis()) {
            Toast.makeText(this, "当前诊断窗口超过 6 小时；请重新设置开始或结束时间", Toast.LENGTH_LONG).show()
            return
        }
        if (diagnosticStartDecision(config) == DiagnosticRunStartDecision.REJECT) {
            Toast.makeText(this, "严格模式需要先开启连接级采集", Toast.LENGTH_LONG).show()
            return
        }
        if (scheduled) {
            DiagnosticScheduler(this).schedule(config)
            refreshAll()
            Toast.makeText(this, "已按当前窗口安排下一次夜间诊断", Toast.LENGTH_SHORT).show()
        } else {
            startForegroundService(
                Intent(this, NightDiagnosticService::class.java).setAction(NightDiagnosticService.ACTION_START),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeConnectionCaptureIfNeeded()
        refreshAll()
    }

    @Deprecated("Activity result callback is sufficient for VPN permission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != VPN_PERMISSION_REQUEST) return
        if (resultCode == RESULT_OK) startConnectionCapture()
        else {
            setConnectionCaptureEnabled(false)
            Toast.makeText(this, "未授权本地 VPN，连接级采集保持关闭", Toast.LENGTH_LONG).show()
        }
        refreshAll()
    }

    private fun refreshAll() {
        val preferences = DiagnosticPreferences(this)
        val config = preferences.load()
        diagnosticWindowText?.text = diagnosticWindowDescription(config)
        var nextStart = preferences.nightScheduleNextStartEpochMs()
        if (preferences.nightScheduleEnabled() && nextStart == null) {
            nextStart = NightScheduleUiPolicy.nextStart(config)
            preferences.saveNightSchedule(true, nextStart)
        }
        nightScheduleStatus?.apply {
            text = NightScheduleUiPolicy.status(preferences.nightScheduleEnabled(), nextStart)
            setTextColor(if (preferences.nightScheduleEnabled()) AppUi.RUN else AppUi.MUTED)
        }
        AppUi.renderSettingValue(this, snapshotValue, "${config.snapshotMinutes} 分钟", AppUi.SettingStyle.VALUE)
        AppUi.renderSettingValue(this, endpointsValue, "2 个地址", AppUi.SettingStyle.VALUE)
        AppUi.renderSettingValue(this, startValue, config.start.hhmm(), AppUi.SettingStyle.VALUE)
        AppUi.renderSettingValue(this, endValue, config.end.hhmm(), AppUi.SettingStyle.VALUE)
        AppUi.renderSettingValue(this, outputValue, "自动同步", AppUi.SettingStyle.VALUE)
        AppUi.renderSettingValue(
            this,
            runtimeModeValue,
            if (config.runtimeMode == DiagnosticRuntimeMode.STRICT) "严格模式" else "标准模式",
            AppUi.SettingStyle.TOGGLE,
        )
        AppUi.renderSettingValue(this, connectionCaptureValue, when {
            isConnectionCaptureEnabled() && hasConflictingVpnForResume() -> "已暂停"
            isConnectionCaptureEnabled() -> "已开启"
            else -> "已关闭"
        }, AppUi.SettingStyle.TOGGLE)
        AppUi.renderSettingValue(
            this,
            exactAlarmValue,
            if (preferences.exactAlarmEnabled()) "已启用" else "已停用",
            AppUi.SettingStyle.TOGGLE,
        )
        Thread {
            val active = DiagnosticDatabaseFactory.get(this).diagnosticDao().activeRun()
                ?.runtimeMode
                ?.let { runCatching { DiagnosticRuntimeMode.valueOf(it) }.getOrNull() }
            runOnUiThread { runtimeModeStatus?.text = RuntimeModeUiPolicy().status(config.runtimeMode, active) }
        }.start()
    }

    private fun refreshTimeDependentFields(preferences: DiagnosticPreferences) {
        val config = preferences.load()
        val format = ReportFormat.from(preferences.preferredReportFormat())
        val generated = ReportFileNamePolicy.withFormat(
            suggestedExportFileName(preferences, config),
            format,
        )
        saveReportIdentity(preferences, ReportFileNamePolicy.baseName(generated), format)
        refreshAll()
    }

    private fun ensureDefaultReportName(preferences: DiagnosticPreferences) {
        if (!preferences.savedExportFileName().isNullOrBlank()) return
        val format = ReportFormat.from(preferences.preferredReportFormat())
        preferences.saveExportFileName(
            ReportFileNamePolicy.withFormat(suggestedExportFileName(preferences, preferences.load()), format),
        )
    }

    private fun suggestedExportFileName(preferences: DiagnosticPreferences, config: DiagnosticConfig): String {
        val settings = preferences.exportNameSettings()
        return ReportFileNamePolicy.create(
            exportedAt = LocalDateTime.now(),
            deviceModel = Build.MODEL,
            customDescription = "移动网络测试",
            start = config.start,
            end = config.end,
            startWasConfirmed = settings.startWasConfirmed,
            endWasConfirmed = settings.endWasConfirmed,
        )
    }

    private fun diagnosticWindowDescription(config: DiagnosticConfig): String = getString(
        R.string.diagnostic_window,
        config.start,
        config.end,
        config.snapshotMinutes,
        config.nightlyBudgetBytes / 1_000_000,
    )

    private fun requestEssentialPermissions() {
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun toggleConnectionCapture() {
        if (isConnectionCaptureEnabled()) {
            setConnectionCaptureEnabled(false)
            startService(
                Intent(this, ConnectionCaptureVpnService::class.java)
                    .setAction(ConnectionCaptureVpnService.ACTION_STOP),
            )
            Toast.makeText(this, "连接级采集已关闭", Toast.LENGTH_SHORT).show()
            refreshAll()
            return
        }
        if (!NativeForwarderContract.isLoaded()) {
            Toast.makeText(this, "当前安装包缺少透明转发引擎，无法启用", Toast.LENGTH_LONG).show()
            return
        }
        if (hasActiveVpn()) {
            Toast.makeText(this, "检测到 Clash 或其他 VPN；请先关闭它，再启用连接级采集", Toast.LENGTH_LONG).show()
            return
        }
        VpnService.prepare(this)?.let { startActivityForResult(it, VPN_PERMISSION_REQUEST) }
            ?: startConnectionCapture()
    }

    private fun startConnectionCapture(showFeedback: Boolean = true) {
        setConnectionCaptureEnabled(true)
        val intent = Intent(this, ConnectionCaptureVpnService::class.java)
            .setAction(ConnectionCaptureVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        if (showFeedback && feedbackGate.take()) {
            Toast.makeText(this, "正在启动连接级采集；通知栏可随时停止", Toast.LENGTH_LONG).show()
        }
        refreshAll()
    }

    private fun resumeConnectionCaptureIfNeeded() {
        val userEnabled = isConnectionCaptureEnabled()
        val permissionAlreadyGranted = VpnService.prepare(this) == null
        val conflicting = hasConflictingVpnForResume()
        if (ConnectionCaptureLifecyclePolicy.shouldResume(userEnabled, permissionAlreadyGranted, conflicting) &&
            !ConnectionCaptureVpnService.isActiveInProcess()
        ) {
            startConnectionCapture(showFeedback = false)
        } else if (ConnectionCaptureLifecyclePolicy.shouldDisableUserIntent(userEnabled, permissionAlreadyGranted)) {
            setConnectionCaptureEnabled(false)
        }
    }

    private fun hasConflictingVpnForResume(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        val ownServiceRunning = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            isConnectionCaptureServiceRunning()
        } else false
        @Suppress("DEPRECATION")
        return connectivity.allNetworks.any { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@any false
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@any false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) capabilities.ownerUid != applicationInfo.uid
            else !ownServiceRunning
        }
    }

    @Suppress("DEPRECATION")
    private fun isConnectionCaptureServiceRunning(): Boolean =
        (getSystemService(ActivityManager::class.java)
            ?.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == ConnectionCaptureVpnService::class.java.name }) == true

    private fun hasActiveVpn(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun isConnectionCaptureEnabled(): Boolean =
        getSharedPreferences(ConnectionCaptureVpnService.PREFERENCES, MODE_PRIVATE)
            .getBoolean(ConnectionCaptureVpnService.KEY_ENABLED, false)

    private fun isConnectionCaptureRunning(): Boolean =
        getSharedPreferences(ConnectionCaptureVpnService.PREFERENCES, MODE_PRIVATE)
            .getBoolean(ConnectionCaptureVpnService.KEY_RUNNING, false)

    private fun setConnectionCaptureEnabled(enabled: Boolean) {
        getSharedPreferences(ConnectionCaptureVpnService.PREFERENCES, MODE_PRIVATE)
            .edit().putBoolean(ConnectionCaptureVpnService.KEY_ENABLED, enabled).apply()
    }

    private fun diagnosticStartDecision(config: DiagnosticConfig): DiagnosticRunStartDecision =
        DiagnosticRunStartPolicy().decide(config, isConnectionCaptureEnabled(), isConnectionCaptureRunning())

    @Suppress("DEPRECATION")
    private fun restoreSystemStatusBar() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars())
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        } else window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    private fun LocalTime.hhmm() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PERMISSION_REQUEST = 1003
        const val VPN_PERMISSION_REQUEST = 1005
        const val HEADER_TOP_PADDING_DP = 10
        val feedbackGate = ProcessFeedbackGate()
    }
}
