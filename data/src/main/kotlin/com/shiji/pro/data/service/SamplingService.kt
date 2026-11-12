package com.shiji.pro.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 持续采集前台服务（§2.3 / §10.6，P14 落地）。
 *
 * 职责边界（如实）：
 * - 只做**进程保活 + 通知栏可见**，采集循环由 `AppViewModel` 的采样调度器负责；
 *   服务本身不重复采集，避免双份 binder 调用。
 * - `foregroundServiceType="location"`：与 Manifest 声明一致，requires ACCESS_FINE_LOCATION（已申请）。
 * - 通知优先级 LOW、常驻、可点击回到 MainActivity。
 * - API 34+ 从后台启动 FGS 受系统限制：本服务只由用户在设置页显式开启（Activity 在前台时启动）。
 */
class SamplingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            ensureChannel()
            val notification = buildNotification()
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else 0,
            )
            START_STICKY
        } catch (e: Throwable) {
            // 权限被撤/系统拒绝：立即停掉，不留一个没有通知的僵尸服务
            stopSelfResult(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "常驻采集", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持网络诊断采样在后台运行"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("识机 Pro 正在采集")
        .setContentText("按设置的间隔刷新蜂窝/WiFi/GNSS；点击返回应用")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, Class.forName("com.shiji.pro.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        const val CHANNEL_ID = "shiji_sampling"
        const val NOTIF_ID = 1001

        /** 用户显式开启（设置页开关）时调用；Activity 在前台，符合 FGS 启动限制。 */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SamplingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SamplingService::class.java))
        }
    }
}
