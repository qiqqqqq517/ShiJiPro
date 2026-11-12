package com.shiji.pro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.shiji.pro.core.common.CsvColumns
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.QualityWeights
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiTheme
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens
import com.shiji.pro.features.shell.DualSimCompare
import com.shiji.pro.features.shell.ShiJiTab
import com.shiji.pro.features.shell.TabScreen
import com.shiji.pro.features.shell.TopBarAction
import org.koin.core.context.GlobalContext
import java.util.Locale

/**
 * 单 Activity 装配层：7 标签 + 顶栏动作 + 权限分组申请。
 * 通过 Koin 取得 [AppViewModel]，UI 只读 [AppState]；采集完全解耦在 :data。
 */
class MainActivity : ComponentActivity() {

    private val basePermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // 权限异步授予：重启 GNSS 订阅 + 重新采集（首次安装时 ViewModel 早于授权构造，见 AppViewModel 注释）
            viewModelRef?.restartGnssStream()
        }

    /** 供权限回调引用（权限异步授予后需要重启 GNSS 订阅，见 AppViewModel.restartGnssStream）。 */
    private var viewModelRef: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (basePermissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(basePermissions)
        }
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val koin = GlobalContext.get()
                return AppViewModel(
                    repo = koin.get(), inMemory = koin.get(), gate = koin.get(),
                    cellular = koin.get(), wifi = koin.get(), gnss = koin.get(),
                    device = koin.get(), speed = koin.get(),
                    speedConfig = koin.get(),
                    traffic = koin.get(),
                ) as T
            }
        })[AppViewModel::class.java]
        viewModelRef = vm
        setContent {
            ShiJiTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
                ShiJiApp(vm)
            }
        }
    }
}

@Composable
private fun ShiJiApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    // 前台服务的启停需要 Context；Compose 作用域里 `this` 是 ColumnScope，不能直接用 Activity
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val tabs = ShiJiTab.entries
    var selectedIndex by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = ShiJiThemeTokens.extended.surface) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onAction = { action ->
                when (action) {
                    TopBarAction.REFRESH -> { viewModel.refresh(); overlay = null }
                    TopBarAction.COMPARE_SIMS -> overlay = Overlay.COMPARE
                    TopBarAction.LOG_CENTER -> overlay = Overlay.LOGS
                    TopBarAction.SETTINGS -> overlay = Overlay.SETTINGS
                }
            })
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = Dimen.Space2,
                containerColor = ShiJiThemeTokens.extended.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index; overlay = null },
                        text = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            when (val o = overlay) {
                null -> TabScreen(
                    tabs[selectedIndex], state, Modifier.weight(1f),
                    onSpeedConfig = { cfg -> viewModel.updateSpeedConfig(cfg) },
                )
                Overlay.COMPARE -> DualSimCompare(state, Modifier.weight(1f))
                Overlay.LOGS -> LogCenterOverlay(state, Modifier.weight(1f)) { overlay = null }
                Overlay.SETTINGS -> SettingsOverlay(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onClose = { overlay = null },
                    onTheme = { /* 主题由系统跟随；显式切换在 settings 仓储接入后生效 */ },
                    onSpeedTest = { viewModel.runSpeedTest() },
                    viewModel = viewModel,
                    onForegroundSampling = { enabled ->
                        if (enabled) com.shiji.pro.data.service.SamplingService.start(appContext)
                        else com.shiji.pro.data.service.SamplingService.stop(appContext)
                        viewModel.setForegroundSampling(enabled)
                    },
                )
            }
        }
    }
}

private enum class Overlay { COMPARE, LOGS, SETTINGS }

@Composable
private fun TopBar(onAction: (TopBarAction) -> Unit) {
    val ext = ShiJiThemeTokens.extended
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimen.Space4, vertical = Dimen.Space3)) {
        Text("识机 Pro", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = ext.onSurface)
        Text("蜂窝 / WiFi / GNSS / 路测 / 测速 / 设备",
            style = MaterialTheme.typography.labelSmall, color = ext.onSurfaceMuted)
        Spacer(Modifier.height(Dimen.Space2))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space1)) {
            TopBarAction.entries.forEach { action ->
                TextButton(onClick = { onAction(action) }) {
                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 日志中心（§8）：统一留存、按类型筛选、清空、导出 CSV（导出路径明确告知）。 */
@Composable
private fun LogCenterOverlay(state: AppState, modifier: Modifier, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier.padding(Dimen.Space4)) {
        Text("日志中心", style = MaterialTheme.typography.titleLarge)
        Text("当前为内存存储（进程内），不上传任何用户数据；导出目录由系统文件选择器决定。",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Dimen.Space2))
        Text("蜂窝快照: ${state.cellularSlots.size} 槽 · WiFi: ${if (state.wifi != null) "有" else "无"} · " +
            "GNSS: ${if (state.gnss != null) "有" else "无"} · 设备: ${if (state.device != null) "有" else "无"}",
            style = MaterialTheme.typography.bodyMedium)
        Text("CSV 导出列、清空与按时间筛选由 LogCenterUseCase 提供（内存仓储已实现）。",
            style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Dimen.Space3))
        TextButton(onClick = onClose) { Text("返回") }
    }
}

/** 设置页（§8 / §10）：主题、语言、隐私、刷新间隔说明、关于与导出入口。 */
@Composable
private fun SettingsOverlay(
    state: AppState,
    modifier: Modifier,
    onClose: () -> Unit,
    onTheme: () -> Unit,
    onSpeedTest: () -> Unit,
    /** 设置写回入口（CSV 列选择 / KPI 权重编辑用；冻结契约）。 */
    viewModel: AppViewModel,
    /** 常驻前台服务开关（P14：服务由 Activity 启停，ViewModel 只同步状态）。 */
    onForegroundSampling: (Boolean) -> Unit,
) {
    Column(modifier.padding(Dimen.Space4).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Dimen.Space2))
        Text("主题：跟随系统（深/浅显式切换待设置仓储接入后生效）", style = MaterialTheme.typography.bodyMedium)
        // 真机核验（2026-11-12）：values-en 资源目录确实存在，但全工程 UI 文案 0 处使用 stringResource
        // （52 个 .kt 文件 grep 实测），因此切换系统语言时界面不会变。原句「values / values-en 已就位」
        // 会让用户误读为可切换，故改为如实说明当前状态。
        Text("语言：仅中文（values-en 资源已在，但界面文案尚未接 stringResource，切换系统语言不生效）",
            style = MaterialTheme.typography.bodyMedium)
        Text("隐私：IMSI / ICCID / 号码 / MAC 默认脱敏；不上传用户数据", style = MaterialTheme.typography.bodyMedium)
        Text("本地存储：内存（未持久化）；Room 持久化列为后续增强，不声称已落盘",
            style = MaterialTheme.typography.bodyMedium)
        Text("第三方出口 IP 查询：默认关闭", style = MaterialTheme.typography.bodyMedium)
        Text("悬浮窗 / 状态栏监控：需特殊授权，未授权时开关置灰",
            style = MaterialTheme.typography.bodyMedium)
        // P14（2026-11-12）：采样调度器已接线 —— 进程内按 cellularIntervalMs 自动刷新；
        // 常驻能力由 SamplingService（前台服务通知）提供。以下两行反映真实行为，不再是「无消费者」。
        Text(
            "自动刷新：已启用采样调度器，按 ${state.samplingConfig.cellularIntervalMs / 1000}s 刷新蜂窝/WiFi/GNSS" +
                "（进程被系统杀死后停止；需要后台常驻请打开下方开关）",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动采集", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.settings.autoSamplingEnabled,
                onCheckedChange = { v ->
                    viewModel.updateSettings(state.settings.copy(autoSamplingEnabled = v))
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("常驻前台服务（通知栏可见）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.foregroundSampling,
                onCheckedChange = { v -> onForegroundSampling(v) },
            )
        }
        Spacer(Modifier.height(Dimen.Space3))
        Text("关于：识机 Pro · 零广告 · 自制图标 · 全部 Apache-2.0 依赖",
            style = MaterialTheme.typography.labelSmall)
        // —— P13 A3：路测 CSV 导出列（研究报告 §11-A3，列名真源 CsvColumns） ——
        Spacer(Modifier.height(Dimen.Space3))
        CsvColumnsSection(state, viewModel)
        // —— P13 B6：路测 KPI 权重（研究报告 §11-B6，只影响覆盖测试页聚合评分） ——
        Spacer(Modifier.height(Dimen.Space3))
        KpiWeightsSection(state, viewModel)
        Spacer(Modifier.height(Dimen.Space3))
        Row {
            TextButton(onClick = onSpeedTest) { Text("运行一次测速") }
            TextButton(onClick = onClose) { Text("返回") }
        }
    }
}

/**
 * 「路测 CSV 导出列」节（P13 A3）：13 列各一行 Checkbox，勾选状态由
 * `state.settings.driveTestCsvColumns` 派生（null = 全列，显示为全选）；
 * 必需列（`CsvColumns.REQUIRED_COLUMNS`）强制勾选且不可取消（effective() 也会兜底并入）。
 * 写回统一走 `viewModel.updateSettings`：恢复默认写 `null`，其余写按全量列顺序排列的勾选列表。
 */
@Composable
private fun CsvColumnsSection(state: AppState, viewModel: AppViewModel) {
    val allColumns = CsvColumns.DRIVE_TEST_ALL_COLUMNS
    val requiredColumns = CsvColumns.REQUIRED_COLUMNS
    // 生效列集：null（未自定义）= 全列；勾选显示以 effective 为准（保证必需列恒亮、未知列不显示）。
    val effectiveColumns = CsvColumns
        .effective(state.settings.driveTestCsvColumns ?: allColumns)
        .toSet()

    Text("路测 CSV 导出列", style = MaterialTheme.typography.titleMedium)
    Text("最小必需列 timestampMs / latitude / longitude 不可剔除", style = MaterialTheme.typography.labelSmall)
    allColumns.forEach { column ->
        val required = column in requiredColumns
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = required || column in effectiveColumns,
                enabled = !required,
                onCheckedChange = { checked ->
                    val next = effectiveColumns.toMutableSet()
                    if (checked) next.add(column) else next.remove(column)
                    viewModel.updateSettings(
                        state.settings.copy(driveTestCsvColumns = allColumns.filter { it in next }),
                    )
                },
            )
            Text(column, style = MaterialTheme.typography.bodyMedium)
            if (required) {
                Text("  必需", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space1)) {
        TextButton(onClick = {
            viewModel.updateSettings(state.settings.copy(driveTestCsvColumns = allColumns))
        }) { Text("全选") }
        TextButton(onClick = {
            viewModel.updateSettings(state.settings.copy(driveTestCsvColumns = null))
        }) { Text("恢复默认(全列)") }
        TextButton(onClick = {
            viewModel.updateSettings(
                state.settings.copy(driveTestCsvColumns = allColumns.filter { it in requiredColumns }),
            )
        }) { Text("仅必需列") }
    }
}

/**
 * 「路测 KPI 权重」节（P13 B6）：三条 0..1、步长 0.05 的 Slider 分别绑
 * `qualityWeights.rsrpWeight / rsrqWeight / sinrWeight`。
 * 拖动中只更新本地 `MutableFloatState`（避免拖一半就被设置流回写打断），
 * `onValueChangeFinished` 才 `updateSettings` 提交；下方实时显示 `QualityWeights.normalized()`
 * 后的归一化比例（实际生效口径）。
 */
@Composable
private fun KpiWeightsSection(state: AppState, viewModel: AppViewModel) {
    val weights = state.settings.qualityWeights
    // 本地拖动状态：以当前设置初始化；用 MutableFloatState 而非 by var，
    // 保证 onValueChangeFinished 回调里读到的是其它滑条的最新值（而非旧组合捕获值）。
    val rsrpValue = remember(weights) { mutableFloatStateOf(weights.rsrpWeight.toFloat()) }
    val rsrqValue = remember(weights) { mutableFloatStateOf(weights.rsrqWeight.toFloat()) }
    val sinrValue = remember(weights) { mutableFloatStateOf(weights.sinrWeight.toFloat()) }

    fun commit() {
        viewModel.updateSettings(
            state.settings.copy(
                qualityWeights = QualityWeights(
                    rsrpValue.floatValue.toDouble(),
                    rsrqValue.floatValue.toDouble(),
                    sinrValue.floatValue.toDouble(),
                ),
            ),
        )
    }

    Text("路测 KPI 权重（RSRP / RSRQ / SINR）", style = MaterialTheme.typography.titleMedium)
    Text("相对比例，自动归一化；只影响覆盖测试页的加权评分，不改变蜂窝页实时评级",
        style = MaterialTheme.typography.labelSmall)

    Text("RSRP 权重 ${percentLabel(rsrpValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = rsrpValue.floatValue,
        onValueChange = { rsrpValue.floatValue = it },
        onValueChangeFinished = { commit() },
        valueRange = 0f..1f,
        steps = 19, // 0..1 内步长 0.05 → 19 个中间档
    )

    Text("RSRQ 权重 ${percentLabel(rsrqValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = rsrqValue.floatValue,
        onValueChange = { rsrqValue.floatValue = it },
        onValueChangeFinished = { commit() },
        valueRange = 0f..1f,
        steps = 19,
    )

    Text("SINR 权重 ${percentLabel(sinrValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = sinrValue.floatValue,
        onValueChange = { sinrValue.floatValue = it },
        onValueChangeFinished = { commit() },
        valueRange = 0f..1f,
        steps = 19,
    )

    val preview = QualityWeights(
        rsrpValue.floatValue.toDouble(),
        rsrqValue.floatValue.toDouble(),
        sinrValue.floatValue.toDouble(),
    ).normalized()
    Text(
        "归一化后比例: RSRP ${percentLabel(preview.rsrpWeight)} / " +
            "RSRQ ${percentLabel(preview.rsrqWeight)} / SINR ${percentLabel(preview.sinrWeight)}",
        style = MaterialTheme.typography.labelSmall,
    )
    TextButton(onClick = {
        rsrpValue.floatValue = 0.5f
        rsrqValue.floatValue = 0.3f
        sinrValue.floatValue = 0.2f
        viewModel.updateSettings(state.settings.copy(qualityWeights = QualityWeights.DEFAULT))
    }) { Text("恢复默认权重") }
}

/** 权重滑条旁的百分比标签（一位小数，避免 0.05 步长被整数百分比抹平）。 */
private fun percentLabel(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100.0)
