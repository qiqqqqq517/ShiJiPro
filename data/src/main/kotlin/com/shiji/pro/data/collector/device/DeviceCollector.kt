package com.shiji.pro.data.collector.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.BatteryInfo
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.DeviceSnapshot
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.HardwareInfo
import com.shiji.pro.core.model.MemoryInfo
import com.shiji.pro.core.model.ScreenInfo
import com.shiji.pro.core.model.SensorInfo
import com.shiji.pro.core.model.SystemInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sqrt

/**
 * 设备信息采集器（设计稿 §4.9 / §7）。
 * 电池：ACTION_BATTERY_CHANGED sticky 广播；屏幕：WindowManager metrics；
 * 内存：ActivityManager.getMemoryInfo；存储：StatFs；
 * 硬件：Build.* + SUPPORTED_ABIS + availableProcessors；系统：Build.VERSION/System.getProperty；
 * 传感器：SensorManager.getSensorList(TYPE_ALL)。
 * CPU 型号/主频/GPU 渲染器依赖本地系统文件或离屏 GL，取不到即 N/A（§13 机型限制）。
 */
class DeviceCollector(
    private val context: Context,
    private val gate: CapabilityGate,
) : Collector<DeviceSnapshot> {

    override val key: CollectorKey = CollectorKey.DEVICE

    override fun capability(): Flow<CapabilityState> = flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<DeviceSnapshot> = flow { emit(read()) }

    fun read(): DeviceSnapshot = DeviceSnapshot(
        timestampMs = System.currentTimeMillis(),
        battery = readBattery(),
        screen = readScreen(),
        memory = readMemory(),
        hardware = readHardware(),
        system = readSystem(),
        sensors = readSensors(),
    )

    private fun readBattery(): BatteryInfo {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun extraInt(name: String): Int? = intent?.takeIf { it.hasExtra(name) }?.getIntExtra(name, -1)
        val level = extraInt(BatteryManager.EXTRA_LEVEL)
        val scale = extraInt(BatteryManager.EXTRA_SCALE)
        val percent = if (level != null && scale != null && scale > 0) {
            FieldValue.measured(level * 100 / scale)
        } else FieldValue.unavailable("电量 EXTRA_LEVEL/SCALE 取不到")
        val statusCode = extraInt(BatteryManager.EXTRA_STATUS)
        val healthCode = extraInt(BatteryManager.EXTRA_HEALTH)
        val temp = extraInt(BatteryManager.EXTRA_TEMPERATURE)
        val volt = extraInt(BatteryManager.EXTRA_VOLTAGE)
        val pluggedCode = extraInt(BatteryManager.EXTRA_PLUGGED)
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        return BatteryInfo(
            percent = percent,
            status = statusCode?.let { FieldValue.measured(batteryStatus(it)) }
                ?: FieldValue.unavailable("充电状态取不到"),
            health = healthCode?.let { FieldValue.measured(batteryHealth(it)) }
                ?: FieldValue.unavailable("电池健康度取不到"),
            temperatureCelsius = temp?.let { FieldValue.measured(it / 10f) }
                ?: FieldValue.unavailable("电池温度取不到"),
            voltageMv = volt?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("电池电压取不到"),
            plugged = pluggedCode?.let { FieldValue.measured(pluggedLabel(it)) }
                ?: FieldValue.unavailable("充电方式取不到"),
            technology = tech?.takeIf { it.isNotBlank() }?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("电池技术取不到"),
        )
    }

    private fun pluggedLabel(c: Int): String = when (c) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
        else -> "未插电/未知"
    }

    private fun batteryStatus(c: Int): String = when (c) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
        else -> "STATUS_$c"
    }

    private fun batteryHealth(c: Int): String = when (c) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> "未知"
        else -> "HEALTH_$c"
    }

    private fun readScreen(): com.shiji.pro.core.model.ScreenInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val sizeInches = if (w > 0 && h > 0 && densityDpi > 0) {
            val diagPx = sqrt((w * w + h * h).toDouble())
            FieldValue.derived(diagPx / densityDpi, "物理尺寸 = 对角像素数 / densityDpi（换算值）")
        } else FieldValue.unavailable("屏幕尺寸无法换算")
        @Suppress("DEPRECATION")
        val refresh = wm?.defaultDisplay?.refreshRate
        return com.shiji.pro.core.model.ScreenInfo(
            widthPx = w,
            heightPx = h,
            densityDpi = densityDpi,
            // xdpi/ypdi 在部分机型上为 0 或 NaN（未标定物理密度），不能当实测值展示。
            xdpi = positiveFloat(metrics.xdpi, "DisplayMetrics.xdpi 无效（0/NaN）"),
            ydpi = positiveFloat(metrics.ydpi, "DisplayMetrics.ydpi 无效（0/NaN）"),
            sizeInches = sizeInches,
            // 真机实测 144.00002f 是有效值，保持 MEASURED；仅剔除 0/NaN/负值。
            refreshRateHz = refresh?.let { positiveFloat(it, "刷新率无效（0/NaN）") }
                ?: FieldValue.unavailable("刷新率取不到"),
        )
    }

    /**
     * 三态守卫：仅当浮点值为正且有限时才标 MEASURED，
     * 否则走 FieldValue.unavailable，绝不把 0 / NaN / 负数伪装成实测值。
     * 返回类型仍是 [FieldValue] 的 Float，不做任何字符串化（格式化属显示层，见 core/common Format）。
     */
    private fun positiveFloat(v: Float, reason: String): FieldValue<Float> =
        if (v > 0f && !v.isNaN() && !v.isInfinite()) FieldValue.measured(v)
        else FieldValue.unavailable(reason)

    private fun readMemory(): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        try { am?.getMemoryInfo(mi) } catch (_: Throwable) {}
        val dataStat = try { StatFs(Environment.getDataDirectory().path) } catch (_: Throwable) { null }
        val totalRom = dataStat?.blockCountLong?.times(dataStat.blockSizeLong)
        val availRom = dataStat?.availableBlocksLong?.times(dataStat.blockSizeLong)
        return MemoryInfo(
            ramTotalBytes = if (mi.totalMem > 0) FieldValue.measured(mi.totalMem)
                else FieldValue.unavailable("RAM 总量取不到"),
            // 原判据用 totalMem > 0，会让 availMem<=0 时仍被标 measured；改为按 availMem 自身有效性判定。
            ramAvailBytes = if (mi.availMem > 0) FieldValue.measured(mi.availMem)
                else FieldValue.unavailable("RAM 可用量取不到或为 0"),
            romTotalBytes = positiveLong(totalRom, "ROM 总量无效（StatFs 取不到或 <=0）"),
            romAvailBytes = positiveLong(availRom, "ROM 可用量无效（StatFs 返回 <=0）"),
        )
    }

    /** 三态守卫：仅正值才算实测，0 / null 一律 unavailable（不输出 0 字节伪实测）。 */
    private fun positiveLong(v: Long?, reason: String): FieldValue<Long> =
        if (v != null && v > 0L) FieldValue.measured(v) else FieldValue.unavailable(reason)

    private fun readHardware(): HardwareInfo = HardwareInfo(
        model = FieldValue.measured(Build.MODEL),
        manufacturer = FieldValue.measured(Build.MANUFACTURER),
        brand = FieldValue.measured(Build.BRAND),
        board = FieldValue.measured(Build.BOARD),
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() }?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("SOC_MODEL 取不到")
        } else FieldValue.unavailable("SOC_MODEL 需 API 31+；低版本 BOARD 常为代号非芯片名"),
        abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        coreCount = Runtime.getRuntime().availableProcessors(),
        gpuRenderer = FieldValue.unavailable("GPU 渲染器需离屏 EGL 上下文，普通应用常取不到（§13 机型限制）"),
    )

    private fun readSystem(): SystemInfo = SystemInfo(
        release = FieldValue.measured(Build.VERSION.RELEASE),
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() }
            ?.let { FieldValue.measured(it) }
            ?: FieldValue.unavailable("安全补丁级别取不到"),
        kernel = System.getProperty("os.version")?.let { FieldValue.measured(it) }
            ?: FieldValue.unavailable("内核版本取不到"),
        fingerprint = FieldValue.measured(Build.FINGERPRINT),
        serial = FieldValue.unavailable("序列号默认隐藏且脱敏；API 29+ 普通应用返回 UNKNOWN"),
    )

    private fun readSensors(): List<SensorInfo> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()
        return try {
            sm.getSensorList(Sensor.TYPE_ALL).map { s ->
                SensorInfo(
                    name = s.name,
                    vendor = s.vendor,
                    type = s.type,
                    version = s.version,
                    maxRange = s.maximumRange,
                    resolution = s.resolution,
                    powerMa = s.power,
                    minDelayUs = s.minDelay,
                )
            }
        } catch (_: Throwable) { emptyList() }
    }
}
