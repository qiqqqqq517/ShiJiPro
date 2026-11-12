package com.shiji.pro.data.collector.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.shiji.pro.core.common.Masking
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CellData
import com.shiji.pro.core.model.CellularSnapshot
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.OperatorTable
import com.shiji.pro.core.model.RadioTech
import com.shiji.pro.core.model.SignalGrade
import com.shiji.pro.core.model.SimInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 蜂窝采集器（设计稿 §2.1–§2.3）：双卡 SIM/运营商 + 服务小区 + 邻区。
 *
 * 公开 API 边界：
 * - SubscriptionManager.getActiveSubscriptionInfoList()（API 22+，READ_PHONE_STATE）
 * - TelephonyManager.createForSubscriptionId(subId)（API 24+；API 23 只能默认卡）
 * - getAllCellInfo()（API 17+，ACCESS_FINE_LOCATION；API 29+ 亦需 READ_PHONE_STATE）
 * - requestCellInfoUpdate(Executor, CellInfoCallback)（API 29+，主动刷新）
 *
 * 降级：权限不足 / 无 CellInfo / 机型不返回邻区时，明确产出 N/A 与提示，不造数据。
 * 敏感字段（IMSI/ICCID/号码）默认脱敏；API 29+ 普通应用通常直接 N/A。
 *
 * 缺陷 D5 修复（2026-11-12，小米 22041216UC / API 34 真机取证）：
 * 「5G 显示制式」此前是硬编码 `unavailable("…P7 接入")`，而系统侧
 * `dumpsys telephony.registry` 明确给出 `getRilDataRadioTechnology=20(NR_SA)`，
 * 属于承诺未兑现。现由 [DisplayInfoProvider] 用 `TelephonyCallback.DisplayInfoListener`
 * 取真实 [android.telephony.TelephonyDisplayInfo]（API 核验数值见该文件类头注释）。
 * 回调是异步的：首次刷新尚未取到时走 unavailable 并写明异步语义，
 * **不按 radioTechnology 反推制式**（不伪造）。
 */
class CellularCollector(
    private val context: Context,
    private val gate: CapabilityGate,
    /** 「5G 显示制式」数据源；默认自建于本采集器生命周期上，Koin 无需额外声明。 */
    private val displayInfo: DisplayInfoProvider = DisplayInfoProvider(context),
) : Collector<Map<Int, CellularSnapshot>> {

    override val key: CollectorKey = CollectorKey.CELLULAR

    /** 注册 DisplayInfoListener（幂等，binder 调用在自有后台线程）。装配层可在 init 里显式调用。 */
    fun startDisplayInfo() = displayInfo.start()

    /** 释放路径：反注册全部监听。装配层应在 onCleared() 里调用（对齐 GNSS 常驻订阅口径）。 */
    fun stopDisplayInfo() = displayInfo.stop()

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    private val baseTelephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    override fun capability(): Flow<CapabilityState> = flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<Map<Int, CellularSnapshot>> = flow {
        emit(read())
    }

    /** 同步读一次（供 refreshAll 与 Flow 复用）。所有异常在此吞掉并降级。 */
    @SuppressLint("MissingPermission")
    fun read(): Map<Int, CellularSnapshot> {
        if (!hasPhonePermission()) return emptyMap()
        // D5：首次采集即挂上 DisplayInfoListener（start 幂等，内部把 binder 调用投到自有后台线程，
        // 且本函数已由 AppViewModel 切到 Dispatchers.Default 执行）。回调异步，故本次 read 可能仍是 N/A。
        displayInfo.start()
        val subs = activeSubscriptions()
        if (subs.isEmpty()) {
            // 无 SIM：仍尝试默认卡，避免整页空白
            return mapOf(0 to readSlot(0, subId = null, carrierFromSub = null))
        }
        return subs.associate { info ->
            val slot = info.simSlotIndex
            slot to readSlot(slot, info.subscriptionId, info.carrierName?.toString())
        }
    }

    @SuppressLint("MissingPermission")
    private fun readSlot(slot: Int, subId: Int?, carrierFromSub: String?): CellularSnapshot {
        val now = System.currentTimeMillis()
        val tm = telephonyFor(subId)
        val sim = readSimInfo(slot, subId, carrierFromSub, tm)
        // 研究报告 §11-A1：对每个小区注入「运营商归属」（band + EARFCN 区间 → 运营商，换算值）。
        val cells = readCells(tm).map { withOperatorAttribution(it) }
        val serving = cells.firstOrNull { it.isServing }
        val neighbors = cells.filter { !it.isServing }
        val rsrp = serving?.fields?.get("RSRP") as? FieldValue<Int>
            ?: serving?.fields?.get("SS-RSRP") as? FieldValue<Int>
            ?: FieldValue.unavailable("服务小区无 RSRP 字段")
        val grade = if (rsrp.value != null) {
            FieldValue.estimated(
                gradeOf(rsrp.value!!),
                "按 RSRP 阈值评级（估算值）",
            )
        } else FieldValue.unavailable("无 RSRP，无法评级")
        val neighborNote = if (neighbors.isEmpty()) "当前机型/系统不提供邻区信息" else null
        return CellularSnapshot(
            slotIndex = slot,
            timestampMs = now,
            sim = sim,
            servingCell = serving,
            neighbors = neighbors,
            signalDbm = rsrp,
            qualityGrade = grade,
            neighborNote = neighborNote,
        )
    }

    private fun gradeOf(rsrp: Int): SignalGrade = when {
        rsrp >= -80 -> SignalGrade.EXCELLENT
        rsrp >= -90 -> SignalGrade.GOOD
        rsrp >= -105 -> SignalGrade.FAIR
        rsrp >= -115 -> SignalGrade.POOR
        else -> SignalGrade.BAD
    }

    /**
     * 运营商归属（A1）：把「band + EARFCN 区间」查表结果作为**换算值**注入字段表。
     *
     * - LTE 用 CellParsers 产出的实测 `EARFCN`；NR 用 `NR-ARFCN`；
     * - 未收录（含 NR 本轮未收录）→ N/A + 原因，**不做最近邻猜测**；
     * - 命中 → `FieldValue.derived(...)`，note 写明 band 与命中的 EARFCN 区间（可核对）。
     */
    private fun withOperatorAttribution(cell: CellData): CellData {
        val earfcn = (cell.fields["EARFCN"] as? FieldValue<*>)?.value as? Int
        val nrArfcn = (cell.fields["NR-ARFCN"] as? FieldValue<*>)?.value as? Int
        val attribution: FieldValue<String> = when (cell.tech) {
            RadioTech.LTE -> if (earfcn == null) {
                FieldValue.unavailable("本小区未上报 EARFCN，无法查运营商归属")
            } else {
                val m = OperatorTable.forLteEarfcn(earfcn)
                if (m == null) FieldValue.unavailable("该 EARFCN($earfcn) 不在已收录的运营商子频段内（不猜）")
                else FieldValue.derived(m.operator, "band${m.band} · EARFCN ${m.range.earfcnLow}–${m.range.earfcnHigh}（换算，公开频谱分配）")
            }
            RadioTech.NR -> if (nrArfcn == null) {
                FieldValue.unavailable("本小区未上报 NR-ARFCN，无法查运营商归属")
            } else {
                val m = OperatorTable.forNrArfcn(nrArfcn)
                if (m == null) FieldValue.unavailable("NR-ARFCN($nrArfcn) 归属表尚未收录（NR 子频段待核，不猜）")
                else FieldValue.derived(m.operator, "n${m.band} · NR-ARFCN ${m.range.earfcnLow}–${m.range.earfcnHigh}（换算）")
            }
            else -> FieldValue.unavailable("运营商归属只覆盖 LTE/NR（该制式未收录）")
        }
        return cell.copy(fields = cell.fields + ("运营商归属" to attribution))
    }

    @SuppressLint("MissingPermission")
    private fun activeSubscriptions(): List<SubscriptionInfo> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            subscriptionManager?.activeSubscriptionInfoList.orEmpty()
        } else emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun telephonyFor(subId: Int?): TelephonyManager? {
        val base = baseTelephonyManager ?: return null
        return if (subId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            base.createForSubscriptionId(subId) ?: base
        } else base // API 23：无 createForSubscriptionId，仅默认卡（降级）
    }

    @SuppressLint("MissingPermission")
    private fun readCells(tm: TelephonyManager?): List<CellData> {
        if (tm == null) return emptyList()
        val raw: List<CellInfo> = try {
            tm.allCellInfo.orEmpty()
        } catch (e: SecurityException) {
            return emptyList()
        }
        return raw.mapNotNull { info ->
            try {
                CellParsers.parse(info)?.let { parsed ->
                    // isRegistered 由平台提供；neighborNote 依据未注册项数量判断
                    parsed
                }
            } catch (e: Throwable) {
                null // 单个 CellInfo 解析失败不影响其它项
            }
        }
    }

    @android.annotation.TargetApi(24)
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun readSimInfo(slot: Int, subId: Int?, carrier: String?, tm: TelephonyManager?): SimInfo {
        fun na(reason: String) = FieldValue.unavailable<String>(reason)
        val hasPhone = hasPhonePermission()
        val hasNumbers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED

        // IMSI / ICCID / 号码：API 29+ 普通应用通常返回 null；取不到一律 N/A（不伪造）
        val imsi = try {
            if (hasPhone) tm?.subscriberId.toSensitiveField("IMSI") else na("READ_PHONE_STATE 未授予")
        } catch (e: SecurityException) { na("系统拒绝读取 IMSI") }
        val iccid = try {
            if (hasPhone) tm?.simSerialNumber.toSensitiveField("ICCID") else na("READ_PHONE_STATE 未授予")
        } catch (e: SecurityException) { na("系统拒绝读取 ICCID") }
        val number = try {
            if (hasNumbers || hasPhone) tm?.line1Number.toSensitiveField("电话号码")
            else na("READ_PHONE_NUMBERS 未授予")
        } catch (e: SecurityException) { na("系统拒绝读取号码") }

        val operator = tm?.simOperator
        val networkOperator = tm?.networkOperator
        val mccMnc = splitMccMnc(operator, "SIM MCC/MNC")
        val netMccMnc = splitMccMnc(networkOperator, "注册网络 MCC/MNC")

        return SimInfo(
            slotIndex = slot,
            subscriptionId = subId,
            carrierName = (carrier ?: tm?.simOperatorName).toTextField("运营商名称"),
            mcc = mccMnc.first,
            mnc = mccMnc.second,
            networkMcc = netMccMnc.first,
            networkMnc = netMccMnc.second,
            imsi = imsi,
            iccid = iccid,
            phoneNumber = number,
            dataNetworkType = networkTypeLabel(tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN),
            // D5：真实 TelephonyDisplayInfo（异步回调）。取不到走 unavailable，不按 radioTechnology 猜测。
            displayOverride = displayInfo.fieldFor(subId),
            roaming = if (hasPhone && tm != null) FieldValue.measured(tm.isNetworkRoaming)
                else FieldValue.unavailable("READ_PHONE_STATE 未授予"),
            simState = FieldValue.measured(simStateLabel(slot)),
            isDefaultData = defaultDataSubId() == subId,
        )
    }

    private fun splitMccMnc(raw: String?, label: String): Pair<FieldValue<String>, FieldValue<String>> {
        if (raw.isNullOrBlank() || raw.length < 5) {
            val na = FieldValue.unavailable<String>("$label 取不到")
            return na to na
        }
        return FieldValue.measured(raw.take(3)) to FieldValue.measured(raw.drop(3))
    }

    private fun networkTypeLabel(type: Int): FieldValue<String> {
        val label = when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "NR(5G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "WCDMA/HSPA"
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_EHRPD -> "CDMA/EVDO"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "TYPE_$type"
        }
        return if (label == "UNKNOWN") FieldValue.unavailable("数据网络类型取不到")
        else FieldValue.measured(label)
    }

    private fun simStateLabel(slot: Int): String {
        val tm = baseTelephonyManager ?: return "UNKNOWN"
        return try {
            val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tm.getSimState(slot) else tm.simState
            when (state) {
                TelephonyManager.SIM_STATE_ABSENT -> "ABSENT(无卡)"
                TelephonyManager.SIM_STATE_READY -> "READY"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                else -> "STATE_$state"
            }
        } catch (e: Throwable) { "UNKNOWN" }
    }

    private fun defaultDataSubId(): Int? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SubscriptionManager.getDefaultDataSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } else null
    } catch (e: Throwable) { null }

    private fun String?.toSensitiveField(name: String): FieldValue<String> =
        if (isNullOrBlank()) FieldValue.unavailable("$name 系统未返回（API29+ 普通应用通常取不到）")
        else FieldValue.measured(Masking.mask(this!!))

    private fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
