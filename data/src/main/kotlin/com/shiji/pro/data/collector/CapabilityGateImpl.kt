package com.shiji.pro.data.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey

/**
 * 能力闸门实现（设计稿 §10.3）：权限 / API 版本 / 机型能力的唯一判定出口。
 * 任何采集器在 observe() 之前先问这里；不可用时返回 available=false + reason，
 * 由 UI 的 PermissionGate 降级，不抛异常、不崩溃。
 */
class CapabilityGateImpl(private val context: Context) : CapabilityGate {

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun evaluate(key: CollectorKey): CapabilityState = when (key) {
        CollectorKey.CELLULAR -> {
            val perm = Manifest.permission.READ_PHONE_STATE
            val fine = Manifest.permission.ACCESS_FINE_LOCATION
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                    unavailable(key, "蜂窝采集需 API 23+", minApi = 23)
                !granted(perm) -> unavailable(key, "READ_PHONE_STATE 未授予", perm)
                !granted(fine) -> unavailable(key, "getAllCellInfo 需 ACCESS_FINE_LOCATION", fine)
                else -> available(key)
            }
        }
        CollectorKey.WIFI -> {
            val fine = Manifest.permission.ACCESS_FINE_LOCATION
            if (!granted(fine)) unavailable(key, "扫描周边热点需定位权限", fine) else available(key)
        }
        CollectorKey.GNSS -> {
            val fine = Manifest.permission.ACCESS_FINE_LOCATION
            if (!granted(fine)) unavailable(key, "定位需 ACCESS_FINE_LOCATION", fine) else available(key)
        }
        CollectorKey.DEVICE, CollectorKey.BATTERY -> available(key) // 无需权限（只读 Build / sticky 广播）
        CollectorKey.BLUETOOTH -> {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else Manifest.permission.BLUETOOTH
            if (!granted(perm)) unavailable(key, "蓝牙扫描权限未授予", perm) else available(key)
        }
        CollectorKey.TRAFFIC -> available(key) // TrafficStats 本 UID 无需权限；逐应用明细另需特殊授权
        CollectorKey.SPEED_TEST -> available(key) // 仅 INTERNET（manifest 已声明）
    }

    private fun available(key: CollectorKey) = CapabilityState(key, available = true)

    private fun unavailable(
        key: CollectorKey,
        reason: String,
        permission: String? = null,
        minApi: Int? = null,
    ) = CapabilityState(
        key = key,
        available = false,
        reason = reason,
        requiredPermission = permission,
        minApi = minApi,
    )
}
