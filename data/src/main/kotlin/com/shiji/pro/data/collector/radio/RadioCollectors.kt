package com.shiji.pro.data.collector.radio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.BluetoothDeviceInfo
import com.shiji.pro.core.model.BluetoothSnapshot
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 蓝牙扫描采集器（设计稿 §8 可选增强，本轮做骨架）。
 * BLE：BluetoothLeScanner.startScan（API 21+），API 31+ 需 BLUETOOTH_SCAN；
 * 远端 MAC 需 BLUETOOTH_CONNECT（API 31+），取不到则脱敏/N/A。
 * 经典发现（startDiscovery）需定位权限且为异步广播，本骨架只做 BLE 扫描。
 */
class BluetoothCollector(
    private val context: Context,
    private val gate: CapabilityGate,
) : Collector<BluetoothSnapshot> {

    override val key: CollectorKey = CollectorKey.BLUETOOTH

    override fun capability(): Flow<CapabilityState> =
        kotlinx.coroutines.flow.flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<BluetoothSnapshot> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !hasScanPermission()) {
            trySend(BluetoothSnapshot(System.currentTimeMillis(), emptyList(), "蓝牙不可用或权限未授予"))
            awaitClose { }
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            trySend(BluetoothSnapshot(System.currentTimeMillis(), emptyList(), "BLE 扫描器不可用"))
            awaitClose { }
            return@callbackFlow
        }
        val found = LinkedHashMap<String, BluetoothDeviceInfo>()
        val cb = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device
                val mac = try { dev.address } catch (e: SecurityException) { null }
                val name = try { dev.name } catch (e: SecurityException) { null }
                val key = mac ?: name ?: "unknown-${result.hashCode()}"
                found[key] = BluetoothDeviceInfo(
                    name = name?.let { FieldValue.measured(it) }
                        ?: FieldValue.unavailable("设备名未广播"),
                    mac = mac?.let { FieldValue.measured(it) }
                        ?: FieldValue.unavailable("远端 MAC 需 BLUETOOTH_CONNECT"),
                    rssiDbm = FieldValue.measured(result.rssi),
                    type = FieldValue.measured(deviceTypeLabel(dev.type)),
                )
                trySend(BluetoothSnapshot(System.currentTimeMillis(), found.values.toList(), null))
            }
            override fun onScanFailed(errorCode: Int) {
                trySend(BluetoothSnapshot(System.currentTimeMillis(), found.values.toList(), "BLE 扫描失败 code=$errorCode"))
            }
        }
        try {
            @Suppress("MissingPermission")
            scanner.startScan(cb)
        } catch (e: SecurityException) {
            trySend(BluetoothSnapshot(System.currentTimeMillis(), emptyList(), "蓝牙扫描权限被拒绝"))
        }
        awaitClose {
            try { @Suppress("MissingPermission") scanner.stopScan(cb) } catch (_: Throwable) {}
        }
    }

    private fun deviceTypeLabel(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
        else -> "未知"
    }

    private fun hasScanPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

/** 应用网络占用概览（§8）：本 UID 流量，不读取其它 App 数据（需特殊授权，取不到则隐藏）。 */
class TrafficCollector(
    private val context: Context,
    private val gate: CapabilityGate,
) : Collector<Pair<Long, Long>> { // (TxBytes, RxBytes) 本应用累计

    override val key: CollectorKey = CollectorKey.TRAFFIC

    override fun capability(): Flow<CapabilityState> =
        kotlinx.coroutines.flow.flow { emit(gate.evaluate(key)) }

    override fun observe(): Flow<Pair<Long, Long>> =
        kotlinx.coroutines.flow.flow {
            val snap = read()
            emit(
                (snap.txBytes.value ?: -1L) to (snap.rxBytes.value ?: -1L),
            )
        }

    /**
     * 读一次本应用收发字节（P14 / 报告 §11-B9）。
     * `TrafficStats` 在部分机型返回 `UNSUPPORTED`(-1) 或抛 `NoSuchMethodError` ——
     * 取不到时返回 N/A + 原因，不填 0。
     */
    fun read(): com.shiji.pro.core.model.TrafficSnapshot {
        val now = System.currentTimeMillis()
        return try {
            val uid = android.os.Process.myUid()
            val tx = android.net.TrafficStats.getUidTxBytes(uid)
            val rx = android.net.TrafficStats.getUidRxBytes(uid)
            com.shiji.pro.core.model.TrafficSnapshot(
                timestampMs = now,
                txBytes = if (tx >= 0) com.shiji.pro.core.model.FieldValue.measured(tx)
                else com.shiji.pro.core.model.FieldValue.unavailable("TrafficStats 返回 UNSUPPORTED(-1)"),
                rxBytes = if (rx >= 0) com.shiji.pro.core.model.FieldValue.measured(rx)
                else com.shiji.pro.core.model.FieldValue.unavailable("TrafficStats 返回 UNSUPPORTED(-1)"),
            )
        } catch (e: Throwable) {
            com.shiji.pro.core.model.TrafficSnapshot(
                timestampMs = now,
                txBytes = com.shiji.pro.core.model.FieldValue.unavailable("读取本应用流量失败：${e.javaClass.simpleName}"),
                rxBytes = com.shiji.pro.core.model.FieldValue.unavailable("读取本应用流量失败：${e.javaClass.simpleName}"),
                note = "本机型可能不支持 TrafficStats.getUidTxBytes/getUidRxBytes",
            )
        }
    }
}
