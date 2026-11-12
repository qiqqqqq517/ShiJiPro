package com.shiji.pro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.shiji.pro.core.common.CsvColumns
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.QualityWeights
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIcon
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiTheme
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens
import com.shiji.pro.core.ui.theme.WindowLayout
import com.shiji.pro.core.ui.theme.isWide
import com.shiji.pro.core.ui.theme.rememberWindowLayout
import com.shiji.pro.core.ui.theme.twoColumn
import com.shiji.pro.core.ui.theme.useNavigationRail
import com.shiji.pro.features.shell.DualSimCompare
import com.shiji.pro.features.shell.LocalRetryAction
import com.shiji.pro.features.shell.ShiJiTab
import com.shiji.pro.features.shell.TabScreen
import com.shiji.pro.features.shell.TopBarAction
import org.koin.core.context.GlobalContext
import java.util.Locale

/**
 * 单 Activity 装配层：7 标签 + 顶栏动作 + 权限分组申请。
 * 通过 Koin 取得 [AppViewModel]，UI 只读 [AppState]；采集完全解耦在 :data。
 *
 * P15 UI 统一改造（仅装配与呈现，业务链路不变）：
 * - Scaffold + 品牌应用栏；窄屏顶部可滚动标签行、宽屏左侧导航栏；
 * - 三个覆盖层统一标题 / 副标题 / 返回入口，并接管系统返回键；
 * - 首次加载骨架、刷新进度、测速进度；空态与错误态由 core:ui 统一组件呈现；
 * - 图标自绘并带语义描述，交互控件满足最小触控目标与屏幕阅读器可读标签。
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
            ShiJiTheme(darkTheme = isSystemInDarkTheme()) {
                ShiJiApp(vm)
            }
        }
    }
}

@Composable
private fun ShiJiApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    // 前台服务的启停需要 Context；Compose 作用域里 `this` 是 ColumnScope，不能直接用 Activity
    val appContext = LocalContext.current.applicationContext
    var selectedIndex by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var speedTesting by remember { mutableStateOf(false) }
    val layout = rememberWindowLayout()

    // 刷新进度纯 UI 状态：时间戳推进即结束，另有超时兜底避免采集异常时永久转圈。
    // 不向 ViewModel 增加任何字段，也不改动刷新逻辑。
    LaunchedEffect(state.timestampMs) { refreshing = false }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            kotlinx.coroutines.delay(8_000L)
            refreshing = false
        }
    }
    LaunchedEffect(state.lastSpeedTest?.timestampMs) { speedTesting = false }
    LaunchedEffect(speedTesting) {
        if (speedTesting) {
            kotlinx.coroutines.delay(20_000L)
            speedTesting = false
        }
    }

    // 覆盖层打开时，系统返回键先关闭覆盖层而不是退出应用。
    BackHandler(enabled = overlay != null) { overlay = null }

    val onAction: (TopBarAction) -> Unit = { action ->
        when (action) {
            TopBarAction.REFRESH -> {
                viewModel.refresh()
                refreshing = true
                overlay = null
            }
            TopBarAction.COMPARE_SIMS -> overlay = Overlay.COMPARE
            TopBarAction.LOG_CENTER -> overlay = Overlay.LOGS
            TopBarAction.SETTINGS -> overlay = Overlay.SETTINGS
        }
    }

    // 空态 / 降级卡片的「刷新 / 重新检查」与顶栏刷新走同一链路：触发采集并给出进度反馈。
    val retryAction: () -> Unit = {
        viewModel.refresh()
        refreshing = true
    }
    CompositionLocalProvider(LocalRetryAction provides retryAction) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ShiJiThemeTokens.extended.surface,
        topBar = {
            ShiJiTopAppBar(
                onAction = onAction,
                refreshing = refreshing,
                overlay = overlay,
                onBack = { overlay = null },
                layout = layout,
                state = state,
            )
        },
    ) { padding ->
        if (layout.useNavigationRail) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                ShiJiNavigationRail(
                    selectedIndex = selectedIndex,
                    overlay = overlay,
                    onSelect = { index -> selectedIndex = index; overlay = null },
                )
                ShiJiContent(
                    state = state,
                    selectedIndex = selectedIndex,
                    overlay = overlay,
                    viewModel = viewModel,
                    appContext = appContext,
                    speedTesting = speedTesting,
                    onCloseOverlay = { overlay = null },
                    onRunSpeedTest = { viewModel.runSpeedTest(); speedTesting = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ShiJiTabRow(
                    selectedIndex = selectedIndex,
                    overlay = overlay,
                    onSelect = { index -> selectedIndex = index; overlay = null },
                )
                ShiJiContent(
                    state = state,
                    selectedIndex = selectedIndex,
                    overlay = overlay,
                    viewModel = viewModel,
                    appContext = appContext,
                    speedTesting = speedTesting,
                    onCloseOverlay = { overlay = null },
                    onRunSpeedTest = { viewModel.runSpeedTest(); speedTesting = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }
}

private enum class Overlay(val title: String) {
    COMPARE("双卡对比"), LOGS("日志中心"), SETTINGS("设置"),
}

// ---------------------------------------------------------------- 应用栏 / 导航

@Composable
private fun ShiJiTopAppBar(
    onAction: (TopBarAction) -> Unit,
    refreshing: Boolean,
    overlay: Overlay?,
    onBack: () -> Unit,
    layout: WindowLayout,
    state: AppState,
) {
    val ext = ShiJiThemeTokens.extended
    val lastUpdate = remember(state.timestampMs) {
        java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.timestampMs))
    }
    Surface(color = ext.surfaceContainerHigh, contentColor = ext.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimen.Space4, vertical = Dimen.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (overlay != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Dimen.MinTouchTarget),
                    ) {
                        ShiJiIcon(ShiJiIconKind.BACK, Modifier.size(Dimen.IconMedium))
                    }
                    Spacer(Modifier.width(Dimen.Space2))
                }
                Surface(
                    modifier = Modifier.size(Dimen.IconLarge),
                    shape = RoundedCornerShape(Dimen.RadiusCard),
                    color = ext.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ShiJiIcon(ShiJiIconKind.CELL, Modifier.size(Dimen.IconMedium))
                    }
                }
                Spacer(Modifier.width(Dimen.Space3))
                Column(Modifier.weight(1f)) {
                    Text(
                        overlay?.title ?: "识机 Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "蜂窝 / WiFi / GNSS / 路测 / 测速 / 设备",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
                if (layout.isWide) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("数据时间", style = MaterialTheme.typography.labelSmall, color = ext.onSurfaceMuted)
                        Text(lastUpdate, style = MaterialTheme.typography.labelLarge, color = ext.accent)
                    }
                }
            }

            // 动作条：主操作用填充按钮强调，其余为文字按钮；横向可滚动避免极窄屏挤压。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimen.Space3),
                horizontalArrangement = Arrangement.spacedBy(Dimen.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarAction.entries.forEach { action ->
                    if (action == TopBarAction.REFRESH) {
                        FilledTonalButton(
                            onClick = { onAction(action) },
                            enabled = !refreshing,
                            modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
                            contentPadding = PaddingValues(horizontal = Dimen.Space3),
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimen.IconSmall),
                                    strokeWidth = Dimen.StrokeNormal,
                                )
                            } else {
                                ShiJiIcon(ShiJiIconKind.REFRESH, Modifier.size(Dimen.IconSmall))
                            }
                            Spacer(Modifier.width(Dimen.Space2))
                            Text(
                                if (refreshing) "刷新中" else action.label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
                        ) {
                            ShiJiIcon(iconForAction(action), Modifier.size(Dimen.IconSmall))
                            Spacer(Modifier.width(Dimen.Space2))
                            Text(action.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                // 数据时间已在下方状态条内显示（「数据已就绪（HH:mm:ss）」），
                // 这里不再重复追加，避免窄屏动作行末尾出现被裁切的「更新 …」。
            }
            // 固定高度状态条：加载 / 刷新 / 就绪只改变文字与语气，布局高度不变。
            StatusBanner(
                text = when {
                    state.capabilities.isEmpty() ->
                        "正在读取设备能力并首次采集…；页面字段已全部列出，N/A 占位，数据到达后原地更新"
                    refreshing -> "正在刷新采集数据…；字段保持在原位，不做整页替换"
                    else -> "数据已就绪（$lastUpdate）；N/A 字段为权限 / 机型 / 系统限制，不填充占位数值"
                },
                tone = when {
                    state.capabilities.isEmpty() || refreshing -> StatusTone.WARNING
                    else -> StatusTone.SUCCESS
                },
                icon = if (state.capabilities.isEmpty() || refreshing) ShiJiIconKind.REFRESH else ShiJiIconKind.SUCCESS,
                fixedHeight = true,
                modifier = Modifier.padding(horizontal = Dimen.Space3, vertical = Dimen.Space1),
            )
            // 进度槽高度恒定：刷新时显示进度，其余时间显示等高分隔线，不因 3dp 差异产生跳动。
            Box(Modifier.fillMaxWidth().height(Dimen.ProgressHeight)) {
                if (refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(Dimen.ProgressHeight),
                    )
                } else {
                    Divider(color = ext.outlineSoft, thickness = Dimen.DividerThickness)
                }
            }
        }
    }
}

@Composable
private fun ShiJiTabRow(
    selectedIndex: Int,
    overlay: Overlay?,
    onSelect: (Int) -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = Dimen.Space4,
        containerColor = ext.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { Divider(color = ext.outlineSoft, thickness = Dimen.DividerThickness) },
    ) {
        ShiJiTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = selectedIndex == index && overlay == null,
                onClick = { onSelect(index) },
                text = { Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                icon = { ShiJiIcon(tabIcon(tab), Modifier.size(Dimen.IconSmall)) },
            )
        }
    }
}

@Composable
private fun ShiJiNavigationRail(
    selectedIndex: Int,
    overlay: Overlay?,
    onSelect: (Int) -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    NavigationRail(
        modifier = Modifier.width(Dimen.RailWidth).fillMaxHeight(),
        containerColor = ext.surfaceContainer,
        contentColor = ext.onSurface,
        header = {
            Surface(
                modifier = Modifier.size(Dimen.IconLarge),
                shape = RoundedCornerShape(Dimen.RadiusCard),
                color = ext.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShiJiIcon(ShiJiIconKind.CELL, Modifier.size(Dimen.IconMedium))
                }
            }
        },
    ) {
        Spacer(Modifier.height(Dimen.Space3))
        ShiJiTab.entries.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = selectedIndex == index && overlay == null,
                onClick = { onSelect(index) },
                icon = { ShiJiIcon(tabIcon(tab), Modifier.size(Dimen.IconMedium)) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                alwaysShowLabel = true,
            )
            Spacer(Modifier.height(Dimen.Space1))
        }
    }
}

private fun tabIcon(tab: ShiJiTab): ShiJiIconKind = when (tab) {
    ShiJiTab.SLOT1, ShiJiTab.SLOT2 -> ShiJiIconKind.CELL
    ShiJiTab.WIFI -> ShiJiIconKind.WIFI
    ShiJiTab.LOCATION -> ShiJiIconKind.LOCATION
    ShiJiTab.DEVICE -> ShiJiIconKind.DEVICE
    ShiJiTab.DRIVE_TEST -> ShiJiIconKind.DRIVE
    ShiJiTab.SPEED_TEST -> ShiJiIconKind.SPEED
}

private fun iconForAction(action: TopBarAction): ShiJiIconKind = when (action) {
    TopBarAction.REFRESH -> ShiJiIconKind.REFRESH
    TopBarAction.COMPARE_SIMS -> ShiJiIconKind.COMPARE
    TopBarAction.LOG_CENTER -> ShiJiIconKind.LOGS
    TopBarAction.SETTINGS -> ShiJiIconKind.SETTINGS
}

// ---------------------------------------------------------------- 内容区 / 覆盖层

@Composable
private fun ShiJiContent(
    state: AppState,
    selectedIndex: Int,
    overlay: Overlay?,
    viewModel: AppViewModel,
    appContext: Context,
    speedTesting: Boolean,
    onCloseOverlay: () -> Unit,
    onRunSpeedTest: () -> Unit,
    modifier: Modifier,
) {
    // 恒定骨架：首屏不再用整页骨架替换内容，所有页面与字段从第一帧起就全部列出，
    // 采集结果到达后只是原地换值（N/A → 实测），避免整块「突然跳出来」。
    when (val o = overlay) {
        null -> TabScreen(
            ShiJiTab.entries[selectedIndex], state, modifier,
            onSpeedConfig = { cfg -> viewModel.updateSpeedConfig(cfg) },
        )
        Overlay.COMPARE -> OverlayFrame(
            title = o.title,
            subtitle = "两卡槽 RSRP 与评级并排展示",
            onClose = onCloseOverlay,
            modifier = modifier,
        ) { DualSimCompare(state, Modifier.fillMaxSize()) }
        Overlay.LOGS -> OverlayFrame(
            title = o.title,
            subtitle = "统一留存说明与 CSV 导出契约",
            onClose = onCloseOverlay,
            modifier = modifier,
        ) { LogCenterOverlay(state, Modifier.fillMaxSize()) }
        Overlay.SETTINGS -> OverlayFrame(
            title = o.title,
            subtitle = "主题 / 隐私 / 采集 / 路测配置",
            onClose = onCloseOverlay,
            modifier = modifier,
        ) {
            SettingsOverlay(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onClose = onCloseOverlay,
                onTheme = { /* 主题由系统跟随；显式切换在 settings 仓储接入后生效 */ },
                onSpeedTest = onRunSpeedTest,
                speedTesting = speedTesting,
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

/** 覆盖层统一外框：标题 + 副标题 + 返回按钮 + 分隔线，内容区由各页自行滚动。 */
@Composable
private fun OverlayFrame(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimen.Space4, vertical = Dimen.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ext.onSurfaceMuted)
            }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Dimen.MinTouchTarget)) {
                ShiJiIcon(ShiJiIconKind.BACK, Modifier.size(Dimen.IconSmall))
                Spacer(Modifier.width(Dimen.Space1))
                Text("返回")
            }
        }
        Divider(color = ext.outlineSoft, thickness = Dimen.DividerThickness)
        Box(Modifier.weight(1f)) { content() }
    }
}

/** app 模块自己的响应式滚动容器（features 的 ScrollColumn 为 internal，不跨模块引用）。 */
@Composable
private fun ResponsiveScrollColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layout = rememberWindowLayout()
    val horizontal = if (layout.isWide) Dimen.Space6 else Dimen.Space4
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimen.ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = Dimen.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimen.Space3),
        ) { content() }
    }
}

/** 日志中心（§8）：统一留存、按类型筛选、清空、导出 CSV（导出路径明确告知）。 */
@Composable
private fun LogCenterOverlay(state: AppState, modifier: Modifier) {
    ResponsiveScrollColumn(modifier) {
        SectionCard(
            title = "留存说明",
            subtitle = "本地内存，不上传",
            icon = ShiJiIconKind.LOGS,
        ) {
            StatusBanner("当前为内存存储（进程内），不上传任何用户数据；导出目录由系统文件选择器决定。")
        }
        SectionCard(
            title = "当前快照",
            subtitle = "各模块是否有可用数据",
            icon = ShiJiIconKind.INFO,
        ) {
            // 固定展示所有快照项：没有数据时写「0 槽 / 无」，不隐藏整块内容。
            Text(
                "蜂窝快照: ${state.cellularSlots.size} 槽 · WiFi: ${if (state.wifi != null) "有" else "N/A"} · " +
                    "GNSS: ${if (state.gnss != null) "有" else "N/A"} · 设备: ${if (state.device != null) "有" else "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusBanner(
                if (state.cellularSlots.isEmpty() && state.wifi == null && state.gnss == null && state.device == null)
                    "尚未产生任何快照：点顶栏「刷新」后各模块会原地更新，不整页替换"
                else "CSV 导出列、清空与按时间筛选由 LogCenterUseCase 提供（内存仓储已实现）。",
                fixedHeight = true,
            )
        }
    }
}

// ---------------------------------------------------------------- 设置页

/**
 * 设置页（§8 / §10）：主题、语言、隐私、刷新间隔说明、关于与导出入口。
 * 所有写回仍统一走 `viewModel.updateSettings` / `updateSpeedConfig`，开关逻辑不变。
 */
@Composable
private fun SettingsOverlay(
    state: AppState,
    modifier: Modifier,
    onClose: () -> Unit,
    onTheme: () -> Unit,
    onSpeedTest: () -> Unit,
    speedTesting: Boolean,
    /** 设置写回入口（CSV 列选择 / KPI 权重编辑用；冻结契约）。 */
    viewModel: AppViewModel,
    /** 常驻前台服务开关（P14：服务由 Activity 启停，ViewModel 只同步状态）。 */
    onForegroundSampling: (Boolean) -> Unit,
) {
    val layout = rememberWindowLayout()
    ResponsiveScrollColumn(modifier) {
        if (layout.twoColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimen.Space4),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimen.Space3),
                ) {
                    AppearanceSection(onTheme)
                    PrivacySection()
                    SamplingSection(state, viewModel, onForegroundSampling)
                    AboutSection()
                    ActionsSection(onSpeedTest, speedTesting, onClose)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimen.Space3),
                ) {
                    CsvColumnsSection(state, viewModel)
                    KpiWeightsSection(state, viewModel)
                }
            }
        } else {
            AppearanceSection(onTheme)
            PrivacySection()
            SamplingSection(state, viewModel, onForegroundSampling)
            CsvColumnsSection(state, viewModel)
            KpiWeightsSection(state, viewModel)
            AboutSection()
            ActionsSection(onSpeedTest, speedTesting, onClose)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun AppearanceSection(onTheme: () -> Unit) {
    SectionCard(
        title = "外观与语言",
        subtitle = "当前版本的真实能力边界",
        icon = ShiJiIconKind.SETTINGS,
    ) {
        StatusBanner("主题：跟随系统（深/浅显式切换待设置仓储接入后生效）")
        // 真机核验（2026-11-12）：values-en 资源目录确实存在，但全工程 UI 文案 0 处使用 stringResource
        // （52 个 .kt 文件 grep 实测），因此切换系统语言时界面不会变。原句「values / values-en 已就位」
        // 会让用户误读为可切换，故改为如实说明当前状态。
        StatusBanner("语言：仅中文（values-en 资源已在，但界面文案尚未接 stringResource，切换系统语言不生效）")
        // 显式主题切换入口在设置仓储接入前不提供可点击控件（避免「点了没反应」）；
        // onTheme 回调保留在契约中，待 Phase 2 接线后启用。
    }
}

@Composable
private fun PrivacySection() {
    SectionCard(
        title = "隐私与存储",
        subtitle = "默认脱敏，不上传用户数据",
        icon = ShiJiIconKind.PERMISSION,
    ) {
        StatusBanner("隐私：IMSI / ICCID / 号码 / MAC 默认脱敏；不上传用户数据", tone = StatusTone.SUCCESS)
        StatusBanner("本地存储：内存（未持久化）；Room 持久化列为后续增强，不声称已落盘")
        StatusBanner("第三方出口 IP 查询：默认关闭")
        StatusBanner("悬浮窗 / 状态栏监控：需特殊授权，未授权时开关置灰")
    }
}

@Composable
private fun SamplingSection(
    state: AppState,
    viewModel: AppViewModel,
    onForegroundSampling: (Boolean) -> Unit,
) {
    SectionCard(
        title = "采集",
        subtitle = "进程内调度与前台服务",
        icon = ShiJiIconKind.REFRESH,
    ) {
        // P14（2026-11-12）：采样调度器已接线 —— 进程内按 cellularIntervalMs 自动刷新；
        // 常驻能力由 SamplingService（前台服务通知）提供。以下文案反映真实行为，不再是「无消费者」。
        StatusBanner(
            "自动刷新：已启用采样调度器，按 ${state.samplingConfig.cellularIntervalMs / 1000}s 刷新蜂窝/WiFi/GNSS" +
                "（进程被系统杀死后停止；需要后台常驻请打开下方开关）",
        )
        SettingsSwitchRow(
            label = "自动采集",
            checked = state.settings.autoSamplingEnabled,
            onCheckedChange = { v -> viewModel.updateSettings(state.settings.copy(autoSamplingEnabled = v)) },
            description = "自动采集开关",
        )
        SettingsSwitchRow(
            label = "常驻前台服务（通知栏可见）",
            checked = state.foregroundSampling,
            onCheckedChange = { v -> onForegroundSampling(v) },
            description = "常驻前台服务开关",
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimen.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun AboutSection() {
    SectionCard(title = "关于", icon = ShiJiIconKind.INFO) {
        StatusBanner("识机 Pro · 零广告 · 自制图标 · 全部 Apache-2.0 依赖")
    }
}

@Composable
private fun ActionsSection(onSpeedTest: () -> Unit, speedTesting: Boolean, onClose: () -> Unit) {
    SectionCard(title = "操作", icon = ShiJiIconKind.SPEED) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space2)) {
            FilledTonalButton(onClick = onSpeedTest, enabled = !speedTesting) {
                if (speedTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimen.IconSmall),
                        strokeWidth = Dimen.StrokeNormal,
                    )
                } else {
                    ShiJiIcon(ShiJiIconKind.SPEED, Modifier.size(Dimen.IconSmall))
                }
                Spacer(Modifier.width(Dimen.Space2))
                Text(if (speedTesting) "测速中…" else "运行一次测速")
            }
            TextButton(onClick = onClose) { Text("返回") }
        }
        StatusBanner("测速结果写入内存状态；未配置端点时速率字段为 N/A，不伪造数值")
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

    SectionCard(
        title = "路测 CSV 导出列",
        subtitle = "最小必需列不可剔除",
        icon = ShiJiIconKind.LOGS,
    ) {
        allColumns.forEach { column ->
            val required = column in requiredColumns
            Row(
                modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier.semantics { contentDescription = "$column 导出列" },
                )
                Text(column, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (required) Text("必需", style = MaterialTheme.typography.labelSmall)
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

    SectionCard(
        title = "路测 KPI 权重",
        subtitle = "RSRP / RSRQ / SINR 相对比例，自动归一化",
        icon = ShiJiIconKind.COMPARE,
    ) {
        StatusBanner("只影响覆盖测试页的加权评分，不改变蜂窝页实时评级")

        Text("RSRP 权重 ${percentLabel(rsrpValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = rsrpValue.floatValue,
            onValueChange = { rsrpValue.floatValue = it },
            onValueChangeFinished = { commit() },
            valueRange = 0f..1f,
            steps = 19, // 0..1 内步长 0.05 → 19 个中间档
            modifier = Modifier.semantics { contentDescription = "RSRP 权重" },
        )

        Text("RSRQ 权重 ${percentLabel(rsrqValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = rsrqValue.floatValue,
            onValueChange = { rsrqValue.floatValue = it },
            onValueChangeFinished = { commit() },
            valueRange = 0f..1f,
            steps = 19,
            modifier = Modifier.semantics { contentDescription = "RSRQ 权重" },
        )

        Text("SINR 权重 ${percentLabel(sinrValue.floatValue.toDouble())}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sinrValue.floatValue,
            onValueChange = { sinrValue.floatValue = it },
            onValueChangeFinished = { commit() },
            valueRange = 0f..1f,
            steps = 19,
            modifier = Modifier.semantics { contentDescription = "SINR 权重" },
        )

        val preview = QualityWeights(
            rsrpValue.floatValue.toDouble(),
            rsrqValue.floatValue.toDouble(),
            sinrValue.floatValue.toDouble(),
        ).normalized()
        StatusBanner(
            "归一化后比例: RSRP ${percentLabel(preview.rsrpWeight)} / " +
                "RSRQ ${percentLabel(preview.rsrqWeight)} / SINR ${percentLabel(preview.sinrWeight)}",
            tone = StatusTone.SUCCESS,
        )
        TextButton(onClick = {
            rsrpValue.floatValue = 0.5f
            rsrqValue.floatValue = 0.3f
            sinrValue.floatValue = 0.2f
            viewModel.updateSettings(state.settings.copy(qualityWeights = QualityWeights.DEFAULT))
        }) { Text("恢复默认权重") }
    }
}

/** 权重滑条旁的百分比标签（一位小数，避免 0.05 步长被整数百分比抹平）。 */
private fun percentLabel(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100.0)
