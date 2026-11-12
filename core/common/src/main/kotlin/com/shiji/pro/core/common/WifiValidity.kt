package com.shiji.pro.core.common

import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.WifiSecurity

/**
 * WiFi 测量值的有效性判定（缺陷 D2）。设计稿 §3：UNAVAILABLE 时 value 必须为 null，
 * **绝不允许把哨兵值/占位值伪装成 MEASURED**。
 *
 * 本对象刻意**不 import 任何 android.* 类**：判定逻辑全部是纯 Kotlin，可在 JVM 单测里
 * 直接跑真机取证到的那组哨兵值（见 WifiValidityTest），不需要 Robolectric / 装机。
 * Android 侧的类型名（SupplicantState、SECURITY_TYPE_*）都以字符串/整数入参传进来。
 *
 * 真机取证（小米 22041216UC / Android 14 / API 34，WiFi 未连接、只有蜂窝 + 热点）：
 * `dumpsys wifi` 的 mWifiInfo 给出 RSSI=-127、Frequency=-1MHz、LinkSpeed=-1Mbps、
 * Net ID=-1、Security type=-1，而识机 Pro 曾把这些当成「实测」显示 → 本文件补上判定。
 *
 * 判定口径（依据 AOSP android-37 源码/javadoc，均已 javap -constants + android-stubs-src 核验）：
 * - **RSSI**：合法闭区间 `[-120, 0]` dBm；区间外（含 -127）判无效。端点 0 按区间闭合放行，
 *   物理合理性不在此层臆断（上层展示时可再评）。
 * - **频率**：必须 `> 0` MHz；未连接时框架给 -1。
 * - **链路速率**：必须 `> 0` Mbps；框架 `WifiInfo.LINK_SPEED_UNKNOWN = -1`（核验值）。
 * - **安全类型**：框架 `WifiInfo.SECURITY_TYPE_UNKNOWN = -1`，javadoc 明示「未连接时返回该值」→ 判无效。
 * - **是否连着 WiFi**：`SupplicantState == COMPLETED` **且** `networkId != -1`（javadoc：
 *   无当前连接网络时 getNetworkId() 返回 -1）。两者缺一不可。
 */
object WifiValidity {

    /** RSSI 合法下界（dBm）：-127 这类哨兵值必须落在此区间之外。 */
    const val RSSI_MIN_DBM = -120

    /** RSSI 合法上界（dBm）。 */
    const val RSSI_MAX_DBM = 0

    /** WifiManager.INVALID_NETWORK_ID 语义：无当前连接网络时为 -1（AOSP javadoc 已核验）。 */
    const val INVALID_NETWORK_ID = -1

    /** SupplicantState.COMPLETED 的名称；用字符串传，避免本文件依赖 android.* 。 */
    const val SUPPLICANT_COMPLETED = "COMPLETED"

    // ---- WifiInfo.SECURITY_TYPE_* 数值（javap -constants android-37 实测，非推测） ----
    private const val SEC_UNKNOWN = -1
    private const val SEC_OPEN = 0
    private const val SEC_WEP = 1
    private const val SEC_PSK = 2
    private const val SEC_EAP = 3
    private const val SEC_SAE = 4
    private const val SEC_EAP_WPA3_ENT_192 = 5
    private const val SEC_OWE = 6
    private const val SEC_WAPI_PSK = 7
    private const val SEC_WAPI_CERT = 8
    private const val SEC_EAP_WPA3_ENT = 9
    private const val SEC_OSEN = 10
    private const val SEC_PASSPOINT_R1_R2 = 11
    private const val SEC_PASSPOINT_R3 = 12
    private const val SEC_DPP = 13

    /** 统一的无效值文案：写清字段、原因（WiFi 未连接/未上报）、系统返回的哨兵原值。 */
    private fun sentinelReason(field: String, raw: Int, unit: String): String =
        "$field 无效（WiFi 未连接，系统返回哨兵值 $raw$unit）"

    /** RSSI(dBm) → 三态字段；区间外一律 N/A，不把 -127 之类哨兵值当实测。 */
    fun rssiField(rssi: Int): FieldValue<Int> =
        if (rssi in RSSI_MIN_DBM..RSSI_MAX_DBM) {
            FieldValue.measured(rssi)
        } else {
            FieldValue.unavailable(sentinelReason("信号", rssi, " dBm"))
        }

    /** 频率(MHz) → 三态字段；必须 > 0（未连接时框架给 -1）。 */
    fun frequencyField(freqMhz: Int): FieldValue<Int> =
        if (freqMhz > 0) {
            FieldValue.measured(freqMhz)
        } else {
            FieldValue.unavailable(sentinelReason("频率", freqMhz, " MHz"))
        }

    /** 链路速率(Mbps) → 三态字段；必须 > 0（框架 LINK_SPEED_UNKNOWN = -1）。 */
    fun linkSpeedField(mbps: Int): FieldValue<Int> =
        if (mbps > 0) {
            FieldValue.measured(mbps)
        } else {
            FieldValue.unavailable(sentinelReason("链路速率", mbps, " Mbps"))
        }

    /**
     * 框架 `WifiInfo.getCurrentSecurityType()` 的 int → 领域枚举 [WifiSecurity]（API 31+ 才有该 getter）。
     *
     * 值→枚举的映射以 AOSP android-37 的常量与 javadoc 为准（设计稿 §4.5：高版本走框架**实测**，
     * 故 origin = MEASURED）；框架侧比领域枚举更细的类型（WPA3-Enterprise / OSEN / Passpoint / DPP）
     * 统一归入企业级 EAP，并在 note 里保留框架原始类型名，避免 UI 误读成「WPA/WPA2-EAP」。
     * `SECURITY_TYPE_UNKNOWN(-1)`（未连接）与任何未定义值一律 N/A，不臆断成 OPEN。
     */
    fun securityTypeField(securityType: Int): FieldValue<WifiSecurity> {
        val mapped: Pair<WifiSecurity, String>? = when (securityType) {
            SEC_OPEN -> WifiSecurity.OPEN to "开放网络（无认证）"
            SEC_WEP -> WifiSecurity.WEP to "WEP"
            SEC_PSK -> WifiSecurity.WPA2 to "WPA2-PSK/RSN 个人级（框架 PSK 类，WPA/WPA2 混合网络也归此类）"
            SEC_SAE -> WifiSecurity.WPA3 to "WPA3-SAE 个人级"
            SEC_OWE -> WifiSecurity.OWE to "OWE（增强开放）"
            SEC_WAPI_PSK -> WifiSecurity.WAPI to "WAPI-PSK"
            SEC_WAPI_CERT -> WifiSecurity.WAPI to "WAPI 证书"
            SEC_EAP -> WifiSecurity.EAP to "EAP 企业级"
            SEC_EAP_WPA3_ENT -> WifiSecurity.EAP to "WPA3-Enterprise（框架独立类型，此处归企业级 EAP）"
            SEC_EAP_WPA3_ENT_192 -> WifiSecurity.EAP to "WPA3-Enterprise 192-bit（归企业级 EAP）"
            SEC_OSEN -> WifiSecurity.EAP to "OSEN / Hotspot 2.0（归企业级 EAP）"
            SEC_PASSPOINT_R1_R2 -> WifiSecurity.EAP to "Passpoint R1/R2（归企业级 EAP）"
            SEC_PASSPOINT_R3 -> WifiSecurity.EAP to "Passpoint R3（归企业级 EAP）"
            SEC_DPP -> WifiSecurity.EAP to "Easy Connect/DPP（归企业级 EAP）"
            else -> null
        }
        if (mapped == null) {
            return if (securityType == SEC_UNKNOWN) {
                FieldValue.unavailable(sentinelReason("安全类型", securityType, ""))
            } else {
                FieldValue.unavailable(
                    "安全类型 无效（框架返回未定义的安全类型值 $securityType，无对应领域枚举，不臆断）",
                )
            }
        }
        return FieldValue.measured(
            mapped.first,
            "来自 WifiInfo.getCurrentSecurityType()=$securityType；${mapped.second}",
        )
    }

    /**
     * 「是否真的连着 WiFi」判定：`SupplicantState == COMPLETED` 且 `networkId != -1`。
     *
     * 返回 null 表示已连接；返回非 null 即为可直接展示的 N/A 原因（含实测判定依据，
     * 供 UI 说明「为什么没有已连接信息」）。未连接时调用方必须**不产出** WifiConnection，
     * 也**不去取** IP/网关/DNS/掩码/广播 —— 真机上默认网络常是蜂窝，用 activeNetwork
     * 会把蜂窝链路信息串到 WiFi 页（缺陷 D2 根因 2）。
     *
     * @param supplicantStateName 传 `info.supplicantState?.name`（不依赖 android 枚举类型）
     */
    fun disconnectedReason(supplicantStateName: String?, networkId: Int): String? {
        val completed = supplicantStateName == SUPPLICANT_COMPLETED
        val idValid = networkId != INVALID_NETWORK_ID
        if (completed && idValid) return null
        val why = when {
            !completed && !idValid -> "SupplicantState=$supplicantStateName 未握手完成，且 NetworkId=$networkId（未关联）"
            !completed -> "SupplicantState=$supplicantStateName 未握手完成"
            else -> "SupplicantState=$supplicantStateName，但 NetworkId=$networkId（未关联任何已配置网络）"
        }
        return "WiFi 未连接，不显示其它链路的地址信息（判定依据：$why）"
    }
}
