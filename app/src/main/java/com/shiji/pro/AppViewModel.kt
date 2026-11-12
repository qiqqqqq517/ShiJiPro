package com.shiji.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiji.pro.core.common.Sampling
import com.shiji.pro.core.domain.AppStateRepository
import com.shiji.pro.core.domain.SpeedTestConfigRepository
import com.shiji.pro.core.model.AppSettings
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.SpeedTestConfig
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CellularSnapshot
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.DeviceSnapshot
import com.shiji.pro.core.model.SamplingConfig
import com.shiji.pro.core.model.TrafficSnapshot
import com.shiji.pro.core.model.TrendPoint
import com.shiji.pro.core.model.WifiSnapshot
import com.shiji.pro.data.collector.CapabilityGateImpl
import com.shiji.pro.data.collector.cellular.CellularCollector
import com.shiji.pro.data.collector.device.DeviceCollector
import com.shiji.pro.data.collector.gnss.GnssCollector
import com.shiji.pro.data.collector.radio.TrafficCollector
import com.shiji.pro.data.collector.speed.SpeedTestCollector
import com.shiji.pro.data.collector.wifi.WifiCollector
import com.shiji.pro.data.local.InMemoryAppStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 装配层 ViewModel（§2.3 单一数据源）：订阅 AppStateRepository，刷新时触发各 Collector 一次性读取。
 * UI 层不含任何 TelephonyManager / WifiManager / LocationManager。
 *
 * 真机回归修正（2026-11-12，小米 22041216UC / API 34）：
 * - **D1 线程**：getAllCellInfo / scanResults / StatFs / getMemoryInfo 等采集调用是同步 binder
 *   与文件 IO，原先直接在 viewModelScope（Main）里执行，冷启动实测触发
 *   `Choreographer: Skipped 167 frames` 与首帧 doFrame 1187ms。现统一切到 Dispatchers.Default，
 *   仅「发布状态」这一步回主线程语义（StateFlow.update 本身线程安全，无需回切）。
 * - **D3 GNSS**：GnssCollector 的定位与卫星数据只在 observe() 的回调里填充，
 *   而 observe() 此前无任何订阅者，导致 refresh() 调 snapshot() 恒得「0 颗卫星 / fix=null」。
 *   现由本 ViewModel 持有 observe() 的常驻订阅，把回调数据持续合并进单一数据源。
 */
class AppViewModel(
    private val repo: AppStateRepository,
    private val inMemory: InMemoryAppStateRepository,
    private val gate: CapabilityGateImpl,
    private val cellular: CellularCollector,
    private val wifi: WifiCollector,
    private val gnss: GnssCollector,
    private val device: DeviceCollector,
    private val speed: SpeedTestCollector,
    /** 测速端点配置订阅源（§11-B4；把配置并入单一数据源的 speedConfig 字段）。 */
    private val speedConfig: SpeedTestConfigRepository,
    /** 本应用流量（§11-B9：TrafficStats 本 UID）。 */
    private val traffic: TrafficCollector,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState.empty(System.currentTimeMillis()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** GNSS 常驻订阅（D3）。定位/卫星只能从 observe() 的回调里拿到，snapshot() 不是数据源。 */
    private var gnssJob: Job? = null

    /** P14：每个卡槽一根容量 300 的 RSRP 环形缓冲（约 10 分钟@2s，够 20s/1min/5min 三档裁剪）。 */
    private val trendBuffers = mutableMapOf<Int, Sampling.RingBuffer>()

    /** P14：采样调度器（按 SamplingConfig 间隔自动 refresh；此前配置项无消费者）。 */
    private var samplerJob: Job? = null

    @Volatile private var samplingConfig: SamplingConfig = SamplingConfig()

    init {
        viewModelScope.launch { repo.observeState().collect { _state.value = it } }
        // 设置并入单一数据源（CSV 列选择 / KPI 权重等读取入口：state.settings）
        viewModelScope.launch {
            repo.observeSettings().collect { s -> inMemory.publish { it.copy(settings = s) } }
        }
        // 测速端点配置并入单一数据源（§11-B4）
        viewModelScope.launch {
            speedConfig.observeConfig().collect { c -> inMemory.publish { it.copy(speedConfig = c) } }
        }
        // P14：采样配置并入单一数据源，并作为调度器的唯一真源
        viewModelScope.launch {
            repo.observeSamplingConfig().collect { c ->
                samplingConfig = c
                inMemory.publish { it.copy(samplingConfig = c) }
            }
        }
        startScheduler()
        startGnssStream()
        refreshCapabilities()
        refresh()
    }

    /**
     * P14 采样调度器：按 `SamplingConfig.cellularIntervalMs` 自动 refresh。
     *
     * 口径（如实）：这是**进程内**协程循环 —— 只要进程活着（前台或前台服务）就按间隔采集；
     * 进程被杀后停止。常驻能力由 `SamplingService`（前台服务通知）提供，二者配合，
     * 不再声称「间隔配置无消费者」。
     */
    private fun startScheduler() {
        if (samplerJob != null) return
        samplerJob = viewModelScope.launch {
            while (isActive) {
                val c = samplingConfig
                val interval = if (c.cellularIntervalMs <= 0) 2_000L else c.cellularIntervalMs
                delay(interval.coerceIn(500L, 60_000L))
                val enabled = _state.value.settings.autoSamplingEnabled
                if (enabled && !c.paused && !_state.value.samplingPaused) refresh()
            }
        }
    }

    /**
     * 订阅 GnssCollector.observe()：LocationListener 与 GnssStatus.Callback 在其中注册，
     * 每次回调 emit 一份完整快照，直接合并进单一数据源的 gnss 字段。
     * callbackFlow 在 collect 时才开始注册——这就是此前恒「0 颗卫星 / 无定位」的根因。
     */
    private fun startGnssStream() {
        if (gnssJob != null) return
        gnssJob = viewModelScope.launch {
            try {
                gnss.observe().collect { snap ->
                    inMemory.publish { it.copy(gnss = snap) }
                }
            } catch (e: Throwable) {
                // 权限被拒 / 机型不支持：保持 gnss 为当前值（可能为 null），UI 走降级显示
            }
        }
    }

    private fun refreshCapabilities() {
        viewModelScope.launch {
            val caps: Map<CollectorKey, CapabilityState> = withContext(Dispatchers.Default) {
                CollectorKey.entries.associateWith { gate.evaluate(it) }
            }
            inMemory.publish { it.copy(capabilities = caps) }
        }
    }

    /**
     * 顶栏「刷新」：一次性读取所有可用采集器并合并进单一数据源。
     * 采集调用一律离开主线程（D1）；gnss 字段由常驻订阅维护，此处不覆盖它。
     */
    fun refresh() {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.Default) {
                val now = System.currentTimeMillis()
                val caps = CollectorKey.entries.associateWith { gate.evaluate(it) }
                val cellularSlots = if (caps.getValue(CollectorKey.CELLULAR).available) {
                    try { cellular.read() } catch (e: Throwable) { emptyMap() }
                } else emptyMap()
                val wifiSnap = if (caps.getValue(CollectorKey.WIFI).available) {
                    try { wifi.read() } catch (e: Throwable) { null }
                } else null
                val deviceSnap = try { device.read() } catch (e: Throwable) { null }
                val trafficSnap: TrafficSnapshot? = try { traffic.read() } catch (e: Throwable) { null }
                RefreshSnapshot(now, caps, cellularSlots, wifiSnap, deviceSnap, trafficSnap)
            }
            // P14：先把本次快照写入各卡槽的趋势缓冲，再发布（缓冲只在后台线程被写）
            snap.cellularSlots.forEach { (slot, cellSnap) ->
                cellSnap.signalDbm.value?.let { v ->
                    trendBuffers.getOrPut(slot) { Sampling.RingBuffer(300) }
                        .add(Sampling.Point(snap.now, v.toDouble()))
                }
            }
            val trend: Map<Int, List<TrendPoint>> = trendBuffers.mapValues { (_, buf) ->
                buf.snapshot().map { TrendPoint(it.timestampMs, it.value) }
            }
            inMemory.publish { current ->
                // GNSS 尚无订阅数据时兜底一次 snapshot()，避免首屏完全空白
                val gnssSnap = current.gnss ?: runCatching { gnss.snapshot() }.getOrNull()
                current.copy(
                    timestampMs = snap.now,
                    capabilities = snap.caps,
                    cellularSlots = snap.cellularSlots,
                    wifi = snap.wifi,
                    gnss = gnssSnap,
                    device = snap.device,
                    traffic = snap.traffic,
                    cellularTrend = trend,
                    lastSpeedTest = current.lastSpeedTest,
                    driveTestPoints = current.driveTestPoints,
                )
            }
        }
    }

    /** refresh() 在后台线程算出的结果，仅用于把数据带回主线程发布。 */
    private class RefreshSnapshot(
        val now: Long,
        val caps: Map<CollectorKey, CapabilityState>,
        val cellularSlots: Map<Int, CellularSnapshot>,
        val wifi: WifiSnapshot?,
        val device: DeviceSnapshot?,
        val traffic: TrafficSnapshot?,
    )

    fun runSpeedTest() {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { speed.runOnce() }
            } catch (e: Throwable) { return@launch }
            inMemory.publish { it.copy(lastSpeedTest = result, timestampMs = System.currentTimeMillis()) }
        }
    }

    /**
     * 权限结果回来后重启 GNSS 订阅（真机缺陷 2026-11-12）：
     * 首次安装时权限由 `permissionLauncher` 异步授予，而本 ViewModel 在 `onCreate` 里先构造，
     * 此刻 `hasFineLocation()` 为 false，`GnssCollector.observe()` 会立即结束（emit 一份空快照后 awaitClose），
     * 之后即使权限已授予也不会再订阅 —— 实测表现为「卫星 0 颗、无定位、dumpsys location 无本应用监听者」。
     * 由 MainActivity 在权限回调里调用本函数：取消旧协程并重新 collect，即可恢复。
     */
    fun restartGnssStream() {
        gnssJob?.cancel()
        gnssJob = null
        startGnssStream()
        refreshCapabilities()
        refresh()
    }

    /** 设置更新入口（设置页写回；冻结契约，UI 通过本函数写，不直接触碰仓储）。 */
    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch { repo.updateSettings(settings) }
    }

    /** 测速端点配置写入口（§11-B4 端点管理 UI 使用）。 */
    fun updateSpeedConfig(config: SpeedTestConfig) {
        viewModelScope.launch { speedConfig.updateConfig(config) }
    }

    /** P14：常驻前台服务开关状态（服务本身由 MainActivity 启停，此处只同步 UI 状态）。 */
    fun setForegroundSampling(enabled: Boolean) {
        inMemory.publish { it.copy(foregroundSampling = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        // P14：停掉采样调度器
        samplerJob?.cancel()
        samplerJob = null
        // observe() 的 callbackFlow 随协程取消触发 awaitClose → removeUpdates / unregisterGnssStatusCallback
        gnssJob?.cancel()
        gnssJob = null
        // D5：停掉「5G 显示制式」的 TelephonyCallback 监听，避免 ViewModel 销毁后后台监听常驻耗电。
        // stop() 内部把 binder 反注册排到自有线程，主线程调用安全。
        runCatching { cellular.stopDisplayInfo() }
    }
}
