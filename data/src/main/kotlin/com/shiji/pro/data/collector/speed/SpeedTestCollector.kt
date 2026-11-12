package com.shiji.pro.data.collector.speed

import com.shiji.pro.core.common.Stats
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.SpeedTestEndpoint
import com.shiji.pro.core.model.SpeedTestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * 网络测速采集器（设计稿 §5）。
 *
 * 不绑定任何商业测速 SDK：用 HttpURLConnection 打可配置端点（自建优先）。
 * - Ping：尝试 `/system/bin/ping -c N <host>`；二进制缺失时降级为 TCP 连接往返，
 *   UI 必须标注口径（ICMP 或 TCP 握手时延）。**弃用 InetAddress.isReachable 作 ICMP**（非 root 多走 port 7）。
 * - Jitter：连续 RTT 序列的 RFC3550 平均偏差（换算值）。
 * - 下行/上行：GET /download?bytes=N、POST /upload 上传随机填充字节（**非用户数据**）。
 * - 出口 IP/运营商/归属地：第三方回显服务**默认关闭**，需 AppSettings.thirdPartyIpLookup 显式开启。
 *
 * 端点默认为占位地址；未配置真实服务器时所有速率字段返回 N/A，不伪造数值。
 */
class SpeedTestCollector(
    private val gate: CapabilityGate,
    /** 测速端点配置（§11-B4）：runOnce() 时读取用户选中端点；未配置走占位端点（速率 N/A）。 */
    private val configRepo: com.shiji.pro.core.domain.SpeedTestConfigRepository,
) : Collector<SpeedTestResult> {

    override val key: CollectorKey = CollectorKey.SPEED_TEST

    private val placeholder = SpeedTestEndpoint(
        id = "self-hosted-placeholder",
        label = "自建端点（未配置）",
        baseUrl = "https://your-speedtest-server.example/",
        isSelfHosted = true,
    )

    /** 解析当前应使用的端点：用户选中端点优先，否则回占位端点（速率字段全部 N/A，不伪造）。 */
    fun resolveEndpoint(): SpeedTestEndpoint {
        // StateFlow 的 first() 立即返回当前值；runOnce() 由 AppViewModel 切到 Dispatchers.Default 调用，
        // 不占主线程。取配置失败时回占位端点，不抛异常炸测速页。
        val cfg = runCatching { runBlocking { configRepo.observeConfig().first() } }.getOrNull()
        val selected = cfg?.selectedOrNull() ?: return placeholder
        return SpeedTestEndpoint(
            id = selected.id, label = selected.label,
            baseUrl = selected.baseUrl, isSelfHosted = true,
        )
    }

    @Volatile var thirdPartyIpLookup: Boolean = false

    override fun capability(): Flow<CapabilityState> =
        flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<SpeedTestResult> = flow { emit(runOnce()) }

    fun runOnce(): SpeedTestResult {
        val now = System.currentTimeMillis()
        val endpoint = resolveEndpoint()
        // 占位判定只看 id（B4 评审缺陷：原实现用 baseUrl 含 "example" 判占位，
        // 用户录入合法域名 example.org 会被误判为「未配置」→ 速率全部 N/A）。
        val isPlaceholder = endpoint.id == placeholder.id
        if (isPlaceholder) {
            return SpeedTestResult(
                timestampMs = now,
                endpoint = endpoint,
                pingMs = FieldValue.unavailable("测速端点未配置"),
                jitterMs = FieldValue.unavailable("测速端点未配置"),
                downMbps = FieldValue.unavailable("测速端点未配置"),
                upMbps = FieldValue.unavailable("测速端点未配置"),
                publicIp = FieldValue.unavailable("第三方出口 IP 查询默认关闭"),
                carrier = FieldValue.unavailable("未查询运营商信息"),
                region = FieldValue.unavailable("未查询归属地"),
            )
        }
        val rtts = measurePing(endpoint, count = 5)
        val ping = Stats.mean(rtts)
        val jitter = Stats.jitter(rtts)
        val down = measureDownload(endpoint, 2 * 1024 * 1024)
        val up = measureUpload(endpoint, 1 * 1024 * 1024)
        return SpeedTestResult(
            timestampMs = now,
            endpoint = endpoint,
            pingMs = ping?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("Ping 无可达样本（ICMP/TCP 均失败）"),
            jitterMs = jitter?.let { FieldValue.derived(it, "RFC3550 相邻 RTT 差均值") }
                ?: FieldValue.unavailable("样本不足，无法计算抖动"),
            downMbps = down?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("下载测速失败（端点不可达）"),
            upMbps = up?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("上传测速失败（端点不可达）"),
            publicIp = if (thirdPartyIpLookup) lookupPublicIp()
                else FieldValue.unavailable("第三方出口 IP 查询默认关闭"),
            carrier = FieldValue.unavailable("未提供运营商查询端点"),
            region = FieldValue.unavailable("未提供归属地查询端点"),
        )
    }

    /** 优先系统 ping 二进制（ICMP），不可用时降级 TCP 握手往返。返回毫秒序列。 */
    private fun measurePing(endpoint: SpeedTestEndpoint, count: Int): List<Double> {
        val host = try { URL(endpoint.baseUrl).host } catch (e: Throwable) { "" }
        if (host.isBlank()) return emptyList()
        val icmp = trySystemPing(host, count)
        if (icmp.isNotEmpty()) return icmp
        return tcpRttSequence(endpoint, host, count)
    }

    private fun trySystemPing(host: String, count: Int): List<Double> = try {
        val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", count.toString(), host))
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        Regex("time=([0-9.]+)\\s*ms").findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()
    } catch (e: Throwable) { emptyList() }

    private fun tcpRttSequence(endpoint: SpeedTestEndpoint, host: String, count: Int): List<Double> {
        // 探测口径跟随端点自身 scheme（原实现固定 https://，http 端点会被探测成 https，口径不一致）。
        val scheme = if (endpoint.baseUrl.startsWith("http://", ignoreCase = true)) "http" else "https"
        val out = mutableListOf<Double>()
        repeat(count) {
            try {
                val s = System.nanoTime()
                val conn = URL("$scheme://$host").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.instanceFollowRedirects = false
                conn.connect()
                conn.disconnect()
                out += (System.nanoTime() - s) / 1_000_000.0
            } catch (e: Throwable) { /* 单次失败不写占位值 */ }
        }
        return out
    }

    private fun measureDownload(endpoint: SpeedTestEndpoint, bytes: Int): Double? {
        val url = endpoint.baseUrl.trimEnd('/') + "/download?bytes=$bytes"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 10_000
            val start = System.nanoTime()
            val buf = ByteArray(8192)
            var total = 0
            BufferedInputStream(conn.inputStream).use { input ->
                while (total < bytes) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
            conn.disconnect()
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            if (total == 0 || seconds <= 0.0) null else (total * 8.0 / 1_000_000.0) / seconds
        } catch (e: Throwable) { null }
    }

    /** 上传随机填充字节；明确不是任何用户数据。 */
    private fun measureUpload(endpoint: SpeedTestEndpoint, bytes: Int): Double? {
        val url = endpoint.baseUrl.trimEnd('/') + "/upload"
        return try {
            val payload = ByteArray(64 * 1024).also { Random.nextBytes(it) }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 10_000
            conn.setFixedLengthStreamingMode(bytes)
            val start = System.nanoTime()
            val out: OutputStream = conn.outputStream
            var sent = 0
            while (sent < bytes) {
                val n = minOf(payload.size, bytes - sent)
                out.write(payload, 0, n)
                sent += n
            }
            out.flush(); out.close()
            conn.responseCode
            conn.disconnect()
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            if (seconds <= 0.0) null else (bytes * 8.0 / 1_000_000.0) / seconds
        } catch (e: Throwable) { null }
    }

    /** 第三方回显（显式开启时才调用），仅返回公网 IP，不上传任何用户数据。 */
    private fun lookupPublicIp(): FieldValue<String> = try {
        val conn = URL("https://api.ipify.org").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        val ip = conn.inputStream.bufferedReader().use { it.readText() }.trim()
        conn.disconnect()
        if (ip.isBlank()) FieldValue.unavailable("回显服务无响应")
        else FieldValue.measured(ip)
    } catch (e: Throwable) {
        FieldValue.unavailable("出口 IP 查询失败（离线或服务不可达）")
    }
}
