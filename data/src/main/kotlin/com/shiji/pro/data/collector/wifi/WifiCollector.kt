package com.shiji.pro.data.collector.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shiji.pro.core.common.FrequencyConverter
import com.shiji.pro.core.common.WifiValidity
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.WifiAccessPoint
import com.shiji.pro.core.model.WifiConnection
import com.shiji.pro.core.model.WifiSecurity
import com.shiji.pro.core.model.WifiSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * WiFi 采集器（设计稿 §4.5）。
 * 已连接信息：WifiInfo（SSID/BSSID/RSSI/频率/链路速率/安全类型）+ LinkProperties（IP/网关/DNS/掩码/广播）。
 * 周边热点：WifiManager.scanResults；API 33+ 需 NEARBY_WIFI_DEVICES；startScan 自 API 28 起受系统节流。
 * 本机 MAC：API 23+ 随机化，WifiInfo.getMacAddress 恒返回 02:00:00:00:00:00 → N/A。
 *
 * ── 缺陷 D2 的判定口径 ──
 * 真机取证（小米 22041216UC / Android 14 / API 34）：WiFi 并未连接，只开了个人热点（ap0），
 * 默认网络是 5G 蜂窝（ccmni1，10.11.81.176/32）；`dumpsys wifi` 的 mWifiInfo 真值为
 * SSID=<unknown ssid> / BSSID=<none> / MAC=02:00:00:00:00:00 / IP=null / Supplicant=DISCONNECTED /
 * RSSI=-127 / Link speed=-1Mbps / Frequency=-1MHz / Net ID=-1，而 WiFi 页曾显示成「实测 -127 / -1 / -1」，
 * 并把蜂窝的 IP、网关、DNS 当成 WiFi 信息显示。
 *
 * 1. **先判是否真的连着 WiFi**：`SupplicantState == COMPLETED` **且** `networkId != -1`
 *    （AOSP javadoc：无当前连接网络时 getNetworkId() 返回 -1）。判定见 [WifiValidity.disconnectedReason]。
 * 2. **WiFi 未连接时不产出 connection**：[readConnection] 直接返回 null（`WifiSnapshot.connection = null`），
 *    不构造「各字段全 N/A 的非空 WifiConnection」，避免界面渲染出一张标题为「已连接」的空卡片；
 *    未连接的原因写进 [WifiSnapshot.scanNote] 供 UI 出说明卡。**也不借用蜂窝链路信息**：
 *    未连接时不去取 IP/IPv6/网关/DNS/掩码/广播。
 * 3. **取链路属性禁用 activeNetwork**（activeNetwork 常为蜂窝）：遍历 `getAllNetworks()`，用
 *    `getNetworkCapabilities(n)?.hasTransport(TRANSPORT_WIFI) == true` 选出 WiFi 网络再 `getLinkProperties`；
 *    存在多个 WiFi 网络（热点 ap0 与 STA 关联的 wlan0）时优先 station 侧（接口名 wlan*）。
 * 4. **哨兵值不当实测**：RSSI / 频率 / 链路速率 / 安全类型全部经 [WifiValidity] 判定（纯函数、JVM 单测
 *    锁死真机那组哨兵值），本文件不再有裸 `FieldValue.measured(info.rssi / freq / info.linkSpeed)`。
 * 5. 安全类型走 API 31+ `WifiInfo.getCurrentSecurityType()`（设计稿 §4.5 标为**实测**）；低版本保留 N/A 并写明原因。
 */
class WifiCollector(
    private val context: Context,
    private val gate: CapabilityGate,
) : Collector<WifiSnapshot> {

    override val key: CollectorKey = CollectorKey.WIFI

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun capability(): Flow<CapabilityState> = flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<WifiSnapshot> = flow { emit(read()) }

    fun read(): WifiSnapshot {
        val now = System.currentTimeMillis()
        val conn = readConnection()
        val aps = readAccessPoints()
        // 扫描不要求已连接，故未连接时周边热点照常返回。
        val scanHint = if (aps.isEmpty()) {
            "未发现周边热点（可能未授权 NEARBY_WIFI_DEVICES / 系统扫描节流 / 附近无 AP）"
        } else null
        // connection == null 时 UI 不渲染「已连接」卡片；未连接原因借现有 scanNote 字段带出。
        val note = if (conn == null) {
            if (scanHint == null) notConnectedReason else "$notConnectedReason；$scanHint"
        } else scanHint
        return WifiSnapshot(now, conn, aps, note)
    }

    /** 上一次 [readConnection] 判为未连接时的完整原因句，供 [read] 写进 scanNote。 */
    private var notConnectedReason: String =
        notConnected("WifiManager 不可用")

    /** 未连接说明的统一口径：features 侧按「WiFi 未连接」字样出说明卡，故固定前缀。 */
    private fun notConnected(detail: String): String =
        "WiFi 未连接，不显示其它链路的地址信息（判定依据：$detail）"

    /**
     * 已连接才返回 WifiConnection；未连接（或 WifiManager 取不到）一律返回 null。
     * 判定依据留在 [notConnectedReason]，由 [read] 放进 scanNote。
     */
    @SuppressLint("MissingPermission")
    private fun readConnection(): WifiConnection? {
        val info = wifiManager?.connectionInfo ?: run {
            notConnectedReason = notConnected("WifiManager 不可用 / connectionInfo 取不到")
            return null
        }

        // ① 连接态判定（口径 1/2）：未连接时不产卡片、不读任何链路地址
        val disconnected = WifiValidity.disconnectedReason(info.supplicantState?.name, info.networkId)
        if (disconnected != null) {
            notConnectedReason = disconnected
            return null
        }

        // ② 已连接：链路属性只认 TRANSPORT_WIFI 的网络（口径 3）
        val lp = wifiLinkProperties()
        val noLink = "WiFi（TRANSPORT_WIFI）网络取不到链路属性"
        val noAddress = "WiFi 链路上没有 IPv4 地址"

        val ipv4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val ipv4Text = ipv4?.address?.hostAddress
        val prefix = ipv4?.prefixLength
        val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()

        // ③ 三个测量值经有效性判定（口径 4），不裸包 measured
        val rssi = WifiValidity.rssiField(info.rssi)
        val freq = WifiValidity.frequencyField(info.frequency)
        val speed = WifiValidity.linkSpeedField(info.linkSpeed)
        val channel: FieldValue<Int> = if (freq.value != null) {
            FrequencyConverter.wifiFrequencyToChannel(info.frequency)
        } else {
            FieldValue.unavailable("频率无效（${freq.reason}），无法换算信道")
        }
        val mask: FieldValue<String> = if (lp == null) {
            FieldValue.unavailable("$noLink，无法换算子网掩码")
        } else if (prefix == null) {
            FieldValue.unavailable("$noAddress，无法换算子网掩码")
        } else {
            FrequencyConverter.prefixLengthToIpv4Mask(prefix)
        }
        val broadcast: FieldValue<String> = if (lp == null) {
            FieldValue.unavailable("$noLink，无法换算广播地址")
        } else if (ipv4Text == null || prefix == null) {
            FieldValue.unavailable("$noAddress，无法换算广播地址")
        } else {
            FrequencyConverter.ipv4Broadcast(ipv4Text, prefix)
        }

        return WifiConnection(
            ssid = info.ssid.toTextField("SSID"),
            bssid = info.bssid.toTextField("BSSID"),
            rssiDbm = rssi,
            frequencyMhz = freq,
            channel = channel,
            linkSpeedMbps = speed,
            security = readSecurity(info),
            ipV4 = if (ipv4Text != null) FieldValue.measured(ipv4Text)
                else FieldValue.unavailable(if (lp == null) noLink else noAddress),
            ipV6 = lp?.linkAddresses?.firstOrNull { it.address !is Inet4Address }?.address?.hostAddress
                ?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable<String>(if (lp == null) noLink else "WiFi 链路上没有 IPv6 地址"),
            gateway = gateway?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable<String>(if (lp == null) noLink else "WiFi 链路上没有默认路由网关"),
            dns = dns,
            subnetMask = mask,
            broadcast = broadcast,
            localMac = FieldValue.unavailable("Android 6+ MAC 随机化，普通应用取不到真实 MAC"),
        )
    }

    /**
     * 只挑 TRANSPORT_WIFI 的网络取链路属性（口径 3：**禁止使用 activeNetwork**，
     * activeNetwork 在真机取证场景下是蜂窝 ccmni1，会把蜂窝地址串进 WiFi 页）。
     * 多个 WiFi 网络时优先 station 侧（接口名 wlan*），再退回其它 WiFi 链路；
     * 找不到 WiFi 网络就返回 null，由调用方把地址类字段全部置 N/A。
     */
    // getAllNetworks / getNetworkCapabilities / getLinkProperties 需 ACCESS_NETWORK_STATE：
    // 该权限已在 Manifest 声明（普通安装权限，真机 dumpsys 核验 granted=true），
    // 故此处按项目既有口径用守卫标注，与 readConnection / readAccessPoints 一致。
    @SuppressLint("MissingPermission")
    private fun wifiLinkProperties(): LinkProperties? {
        val cm = connectivityManager ?: return null
        val networks: Array<Network> = try {
            // getAllNetworks 在 android-37 已标 @Deprecated，但它是无需回调即可枚举网络的唯一公开入口，
            // 且本口径必须逐网络查 TRANSPORT_WIFI（activeNetwork 常为蜂窝，不可用）。
            @Suppress("DEPRECATION")
            cm.allNetworks
        } catch (e: Throwable) {
            return null
        } ?: emptyArray()
        val picked = ArrayList<LinkProperties>(networks.size)
        for (n in networks) {
            val isWifi = try {
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (e: Throwable) {
                false
            }
            if (!isWifi) continue
            val props = try {
                cm.getLinkProperties(n)
            } catch (e: Throwable) {
                null
            } ?: continue
            picked += props
        }
        if (picked.isEmpty()) return null
        // 热点 ap0 与站点 wlan0 同为 TRANSPORT_WIFI：已连接判定已成立，优先 station 侧链路，
        // 避免把热点侧 NAT 地址当成已连接 WiFi 的地址。
        val (station, rest) = picked.partition { it.interfaceName?.startsWith("wlan") == true }
        val ordered = station + rest
        return ordered.firstOrNull { p -> p.linkAddresses.isNotEmpty() && p.routes.any { it.isDefaultRoute } }
            ?: ordered.firstOrNull { p -> p.linkAddresses.isNotEmpty() }
            ?: ordered.firstOrNull()
    }

    /**
     * 安全类型：API 31+ 用框架实测 `WifiInfo.getCurrentSecurityType()`，数值→枚举与有效性判定
     * 在 [WifiValidity.securityTypeField]（SECURITY_TYPE_UNKNOWN = -1 → N/A）。
     * 低版本框架不提供该接口，保留 N/A 并写明原因，不回退成臆断值。
     */
    private fun readSecurity(info: WifiInfo): FieldValue<WifiSecurity> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            readSecurityApi31(info)
        } else {
            FieldValue.unavailable(
                "安全类型 无效（框架 WifiInfo.getCurrentSecurityType() 需 API 31+，本机 API ${Build.VERSION.SDK_INT} 不提供）",
            )
        }

    @android.annotation.TargetApi(31)
    private fun readSecurityApi31(info: WifiInfo): FieldValue<WifiSecurity> = try {
        WifiValidity.securityTypeField(info.currentSecurityType)
    } catch (e: Throwable) {
        FieldValue.unavailable(
            "安全类型 无效（getCurrentSecurityType() 读取异常：${e.javaClass.simpleName}）",
        )
    }

    @SuppressLint("MissingPermission")
    private fun readAccessPoints(): List<WifiAccessPoint> {
        if (!hasScanPermission()) return emptyList()
        return try {
            wifiManager?.scanResults.orEmpty().map { r ->
                WifiAccessPoint(
                    bssid = r.BSSID ?: "",
                    ssid = r.SSID.toTextField("SSID"),
                    rssiDbm = r.level,
                    frequencyMhz = r.frequency,
                    channel = FrequencyConverter.wifiFrequencyToChannel(r.frequency),
                    bandLabel = FrequencyConverter.wifiBandLabel(r.frequency),
                    security = parseSecurity(r.capabilities),
                )
            }
        } catch (e: SecurityException) { emptyList() }
    }

    /** capabilities 字符串解析为安全类型（换算值）；未知返回 N/A，不臆断。 */
    private fun parseSecurity(caps: String?): FieldValue<WifiSecurity> {
        if (caps.isNullOrBlank()) return FieldValue.unavailable("扫描结果未提供 capabilities")
        val c = caps.uppercase()
        val sec = when {
            c.contains("SAE") || c.contains("WPA3") -> WifiSecurity.WPA3
            c.contains("OWE") -> WifiSecurity.OWE
            c.contains("WAPI") -> WifiSecurity.WAPI
            c.contains("EAP") -> WifiSecurity.EAP
            c.contains("WPA2") -> WifiSecurity.WPA2
            c.contains("WPA") -> WifiSecurity.WPA
            c.contains("WEP") -> WifiSecurity.WEP
            c == "[]" || c.contains("ESS") && !c.contains("WPA") && !c.contains("WEP") -> WifiSecurity.OPEN
            else -> WifiSecurity.UNKNOWN
        }
        return if (sec == WifiSecurity.UNKNOWN) FieldValue.unavailable("无法从 capabilities 判定安全类型")
        else FieldValue.derived(sec, "由 capabilities 字符串解析")
    }

    private fun hasScanPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun String?.toTextField(field: String): FieldValue<String> =
        if (isNullOrBlank() || this == WifiManager.UNKNOWN_SSID ||
            this == "02:00:00:00:00:00" || this == "<unknown ssid>"
        ) FieldValue.unavailable("$field 取不到或系统已脱敏")
        else FieldValue.measured(this!!.trim('"'))
}

/** 本机 wlan0 MAC（尽力而为，取不到即 N/A）。 */
fun readWlan0Mac(): FieldValue<String> = try {
    val ni = NetworkInterface.getByName("wlan0")
    val mac = ni?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
    if (mac.isNullOrBlank()) FieldValue.unavailable("wlan0 MAC 取不到（随机化 / 无 WiFi 接口）")
    else FieldValue.measured(mac)
} catch (e: Throwable) {
    FieldValue.unavailable("wlan0 MAC 读取异常")
}
