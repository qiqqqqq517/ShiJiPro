package com.shiji.pro.data.collector.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.shiji.pro.core.model.FieldValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * 「5G 显示制式」的真实数据源（缺陷 D5 修复）：为默认卡 + 每个已激活 subId 注册
 * `TelephonyCallback.DisplayInfoListener`，缓存最近一次 TelephonyDisplayInfo 的制式数值，
 * 对外提供**同步读取**入口 [fieldFor]（供 CellularCollector.read() 在后台线程直接取）。
 *
 * ── API 核验记录（javap -classpath D:\Android\SDK\platforms\android-37.0\android.jar，2026-11-12）──
 * 1) `TelephonyCallback` 是 **public class + public 无参构造**（不是抽象类），子类化即可：
 *      `public class android.telephony.TelephonyCallback { public android.telephony.TelephonyCallback(); }`
 *    data\api-versions.xml：`<class name="android/telephony/TelephonyCallback" since="31">`
 *    javap -v 确认该类**无类级 @RequiresApi**（RuntimeVisibleAnnotations 只有 @Deprecated 相关项）。
 * 2) `TelephonyCallback$DisplayInfoListener` 是接口，只有一个方法：
 *      `public interface ...$DisplayInfoListener { public abstract void onDisplayInfoChanged(TelephonyDisplayInfo); }`
 *    api-versions.xml：`<class name="...$DisplayInfoListener" since="31">`
 * 3) 注册/释放签名与真实下限（api-versions.xml，**两个重载下限不同**）：
 *      `registerTelephonyCallback(Executor, TelephonyCallback)`                          since=31 ← 本文件用这个
 *      `registerTelephonyCallback(int includeLocationData, Executor, TelephonyCallback)`  since=33（不用）
 *      `unregisterTelephonyCallback(TelephonyCallback)`                                  since=31
 *    javap -v 核验：三个方法的 parameterAnnotations 只有 @NonNull，**没有 @RequiresPermission**。
 * 4) 权限：SDK data\annotations.xml 中 `TelephonyCallback$DisplayInfoListener` **没有** RequiresPermission
 *    条目（同批逐条核对：CellInfoListener=allOf(READ_PHONE_STATE, ACCESS_FINE_LOCATION)、
 *    CallStateListener=READ_PHONE_STATE、BarringInfo/PhysicalChannelConfig/PreciseDataConnectionState=
 *    READ_PRECISE_PHONE_STATE，DisplayInfoListener 一条都没有）。
 *    但旧 API `PhoneStateListener.onDisplayInfoChanged(TelephonyDisplayInfo)` 标了 READ_PHONE_STATE，
 *    DisplayInfo 属电话状态回调族 ⇒ 沿用本采集器既有的 `hasPhonePermission()` 守卫，**不新增权限**；
 *    缺权限时不注册，[fieldFor] 给出对应 N/A。
 * 5) `TelephonyDisplayInfo`（api-versions.xml since=30，**类级同样无 @RequiresApi**）的公开方法只有
 *    `getNetworkType()` / `getOverrideNetworkType()` / `isRoaming()`(API 34+)。
 *    ★ 任务书提到的 `getOverrideNrState()` **在此 SDK 中不存在**（javap 输出里没有该方法），
 *    因此不实现、不臆造其语义。
 * 6) javap -constants 核验到的常量数值：
 *      OVERRIDE_NETWORK_TYPE_NONE             = 0
 *      OVERRIDE_NETWORK_TYPE_LTE_CA           = 1
 *      OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO = 2
 *      OVERRIDE_NETWORK_TYPE_NR_NSA           = 3
 *      OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE    = 4   (API 31 起 deprecated，官方要求改用 5)
 *      OVERRIDE_NETWORK_TYPE_NR_ADVANCED      = 5
 *    getOverrideNetworkType() 的 IntDef 取值集合正是上面这 6 个 ⇒ 其余数值不臆断。
 *      TelephonyManager.NETWORK_TYPE_UNKNOWN=0 GPRS=1 EDGE=2 UMTS=3 CDMA=4 EVDO_0=5 EVDO_A=6
 *      1xRTT=7 HSDPA=8 HSUPA=9 HSPA=10 IDEN=11 EVDO_B=12 LTE=13 EHRPD=14 HSPAP=15 GSM=16
 *      TD_SCDMA=17 IWLAN=18 NR=20   （getNetworkType() 的 IntDef 就是这套取值）
 * 7) 制式文本口径（不自行推断 SA/NSA）：官方对 `OVERRIDE_NETWORK_TYPE_NONE(0)` 的定义是
 *    「No override. getNetworkType() should be used for display network type」⇒ override=0 时
 *    按 getNetworkType() 出文本；NSA 只由 `NR_NSA(3)` 显式表达（定义为 LTE + EN-DC 或辅小区 NR）。
 *    ★ 平台**未定义任何 SA 常量**，所以本机 `dumpsys telephony.registry` 的
 *    `getRilDataRadioTechnology=20(NR_SA)` 在 TelephonyDisplayInfo 侧表现为
 *    `networkType=20 / overrideNetworkType=0` ⇒ 显示 `NR(5G)`；写「5G SA」就是超出平台口径的臆断。
 *
 * 线程与生命周期：
 * - [start] 可在任意线程调用（含主线程）：subId 枚举与 binder 注册都投递到自有后台单线程池，
 *   **UI 线程不做 binder 调用**（对齐真机回归 D1 的线程纪律）；线程池懒建，不调 start 不起线程。
 * - [stop] 释放路径：同步摘下全部注册项并清缓存，binder 反注册排在后台线程执行后 shutdown。
 * - start/stop 由 generation 计数互相隔离：已排队但未执行的注册任务在 stop 之后自行放弃，
 *   抢在同一瞬间的注册会立刻自我反注册 ⇒ 不留「注册了却没人释放」的孤儿监听（防泄漏 / 防后台耗电）。
 * - 监听常驻（与 AppViewModel 的 GNSS 常驻订阅同口径）：SIM 热插拔新增的 subId 本次运行不补注册，
 *   由下次装配层 start() 覆盖。
 */
class DisplayInfoProvider(private val context: Context) {

    companion object {
        /** TelephonyCallback / DisplayInfoListener 的生效下限（javap + api-versions.xml 核验 = 31）。 */
        private const val MIN_CALLBACK_API = 31 // Build.VERSION_CODES.S

        /** 默认卡（无 subId / 取不到 subId）的缓存键，与真实 subscriptionId 不冲突。 */
        private const val DEFAULT_KEY = -1

        /** 回调尚未到达时的统一口径：说明异步语义，绝不回退成按 radioTechnology 猜测的值。 */
        const val PENDING_REASON =
            "TelephonyDisplayInfo 为异步回调，本次刷新尚未取到；已注册监听，下次刷新可得"

        // javap -constants 核验值（类头第 6 条）。用本地常量，避免 minSdk 23 直接引用 API 30 类的静态字段。
        private const val OVERRIDE_NONE = 0
        private const val OVERRIDE_LTE_CA = 1
        private const val OVERRIDE_LTE_ADVANCED_PRO = 2
        private const val OVERRIDE_NR_NSA = 3
        private const val OVERRIDE_NR_NSA_MMWAVE = 4
        private const val OVERRIDE_NR_ADVANCED = 5

        /**
         * 纯函数：把 (getNetworkType, getOverrideNetworkType) 映射成显示文本。
         * 只映射上面核验到的常量；未定义数值与 UNKNOWN 一律返回 null（调用方走 N/A，不臆断）。
         */
        fun displayLabel(networkType: Int, overrideNetworkType: Int): String? =
            when (overrideNetworkType) {
                OVERRIDE_NR_NSA -> "5G NSA"
                OVERRIDE_NR_NSA_MMWAVE -> "5G NSA 毫米波"
                OVERRIDE_NR_ADVANCED -> "5G+"
                OVERRIDE_LTE_CA -> "LTE 载波聚合"
                OVERRIDE_LTE_ADVANCED_PRO -> "LTE Advanced Pro"
                OVERRIDE_NONE -> when (networkType) { // 官方口径：无覆盖时按 getNetworkType() 显示
                    TelephonyManager.NETWORK_TYPE_NR -> "NR(5G)"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
                    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
                    TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EHRPD -> "WCDMA/HSPA"
                    TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA/EVDO"
                    TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
                    else -> null // 含 NETWORK_TYPE_UNKNOWN(0)：没有可显示的制式
                }
                else -> null
            }

        private fun namedFactory(name: String): ThreadFactory = ThreadFactory { r ->
            Thread(r, name).apply { isDaemon = true }
        }
    }

    /**
     * 缓存的原始数值（纯 Int，不把 API 30 类型暴露到 @TargetApi 之外）。
     * 按 subId 分别缓存（双卡各自一条监听）；ConcurrentHashMap 的读写自带 happens-before，
     * 后台线程写入后 UI 侧同步读即可见，整块 Sample 替换不会有半新半旧的读值。
     */
    private data class Sample(val networkType: Int, val overrideNetworkType: Int)

    /**
     * 一条注册记录；manager 保存每个 subId 各自的 TelephonyManager 实例，供原地反注册。
     * callback 的实际类型是 TelephonyCallback（API 31），这里以 Any 承载，
     * 避免把 API 31 类型写进未加版本守卫的字段声明（lint NewApi 按成员判定，见类头核验第 1/3 条）。
     */
    private class Registration(val manager: TelephonyManager, val callback: Any)

    private val cache = ConcurrentHashMap<Int, Sample>()
    private val registrations = ConcurrentHashMap<Int, Registration>()

    @Volatile private var worker: ExecutorService? = null
    @Volatile private var started = false
    @Volatile private var generation = 0
    @Volatile private var lastError: String? = null

    /** 平台是否具备取到该字段的数据源（API 31+）。 */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= MIN_CALLBACK_API

    /**
     * 注册入口（幂等）。权限不足 / 低于 API 31 时不注册，[fieldFor] 给出对应 N/A 原因。
     * 可在主线程调用：subId 枚举与 binder 注册都在自有后台线程执行。
     */
    fun start() {
        if (!isSupported || !hasPhonePermission()) return
        val gen: Int = synchronized(this) {
            if (started) return
            started = true
            ++generation
        }
        val w = ensureWorker() ?: return
        try {
            w.execute {
                if (gen != generation) return@execute // stop 已发生：不留孤儿监听
                try {
                    registerAll(w, gen)
                } catch (e: Throwable) {
                    lastError = "${e::class.java.simpleName}: ${e.message}"
                }
            }
        } catch (e: Throwable) {
            // 线程池已关（与 stop 抢同一瞬间）：回退状态，下次 start 重开
            synchronized(this) { if (gen == generation) started = false }
            lastError = "executor ${e::class.java.simpleName}: ${e.message}"
        }
    }

    /**
     * 释放路径（幂等）：反注册全部监听并清空缓存。
     * 注册表与缓存在锁内同步摘除（保证 UI 侧立刻读到 N/A 而不是脏值），binder 反注册排到后台线程
     * 执行后 shutdown，因此 onCleared（主线程）不执行 binder 调用。
     */
    fun stop() {
        val plan: Pair<List<Registration>, ExecutorService?>? = synchronized(this) {
            if (!started && registrations.isEmpty()) {
                generation++
                null
            } else {
                started = false
                generation++
                val snapshot = registrations.values.toList()
                registrations.clear()
                cache.clear()
                snapshot to worker.also { worker = null }
            }
        }
        // 不用解构：Kotlin 2.4 不允许对 `?: return` 之后仍标注为可空的 Pair 做 component1()/component2()
        if (plan == null) return
        val snapshot = plan.first
        val w = plan.second
        if (snapshot.isEmpty()) {
            // 没有注册项需要反注册：只回收线程池，不新建临时线程
            w?.shutdown()
            return
        }
        val target = w ?: runCatching {
            Executors.newSingleThreadExecutor(namedFactory("ShiJi-DisplayInfo-Stop"))
        }.getOrNull()
        if (target == null) {
            // 建不出线程池：注册表已清空，不会再有数据写入；反注册尽力而为，不留脏值
            lastError = "释放线程池不可用，反注册未执行"
            return
        }
        try {
            target.execute { snapshot.forEach { unregisterOne(it) } }
        } catch (e: Throwable) {
            lastError = "释放任务被拒 ${e::class.java.simpleName}: ${e.message}"
        }
        target.shutdown()
    }

    /**
     * 同步读取入口：给出该 subId 的「5G 显示制式」三态值。
     * 无数据一律 N/A（异步未到 / 版本下限 / 权限 / 注册异常），不做任何猜测。
     */
    fun fieldFor(subId: Int?): FieldValue<String> {
        if (Build.VERSION.SDK_INT < MIN_CALLBACK_API) {
            return FieldValue.unavailable(
                "TelephonyCallback.DisplayInfoListener 需 API $MIN_CALLBACK_API+（当前 ${Build.VERSION.SDK_INT}）"
            )
        }
        val sample = cache[subId ?: DEFAULT_KEY]
            ?: return FieldValue.unavailable(missingReason())
        val label = displayLabel(sample.networkType, sample.overrideNetworkType)
            ?: return FieldValue.unavailable(
                "TelephonyDisplayInfo 给出 networkType=${sample.networkType} / " +
                    "overrideNetworkType=${sample.overrideNetworkType}，无对应显示制式口径，不臆断"
            )
        return FieldValue.measured(
            label,
            "来源 TelephonyCallback.DisplayInfoListener；getNetworkType=${sample.networkType}, " +
                "getOverrideNetworkType=${sample.overrideNetworkType}（javap android-37.0 核验）",
        )
    }

    private fun missingReason(): String = when {
        !started && !hasPhonePermission() ->
            "READ_PHONE_STATE 未授予，无法注册 TelephonyCallback.DisplayInfoListener，无数据源"
        !started -> "监听未注册；$PENDING_REASON"
        lastError != null -> "$PENDING_REASON；注册异常：${lastError}"
        else -> PENDING_REASON
    }

    private fun ensureWorker(): ExecutorService? = synchronized(this) {
        worker ?: newWorker()?.also { worker = it }
    }

    private fun newWorker(): ExecutorService? = runCatching {
        Executors.newSingleThreadExecutor(namedFactory("ShiJi-DisplayInfo"))
    }.getOrElse {
        lastError = "executor ${it::class.java.simpleName}: ${it.message}"
        null
    }

    /**
     * 后台线程执行：枚举默认卡 + 全部已激活 subId，逐个注册 DisplayInfoListener。
     * 回调也投递在这条自有单线程上 ⇒ UI 线程既不发起 binder 也不接收回调。
     */
    @android.annotation.TargetApi(31) // = MIN_CALLBACK_API；注解参数用字面量，避免 lint 解析常量歧义
    @SuppressLint("MissingPermission")
    private fun registerAll(executor: ExecutorService, gen: Int) {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        for (key in subscriptionKeys()) {
            if (gen != generation) return // stop 已发生：放弃后续注册
            if (registrations.containsKey(key)) continue
            val tm = telephonyFor(base, key) ?: continue
            val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                // 参数类型是 API 30 类 TelephonyDisplayInfo；只取 javap 核验到的两个 getter
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    cache[key] = Sample(info.networkType, info.overrideNetworkType)
                }
            }
            try {
                // javap：registerTelephonyCallback(Executor, TelephonyCallback)；api-versions since=31
                tm.registerTelephonyCallback(executor, callback)
                if (gen != generation) {
                    // 与 stop 抢在同一瞬间：立刻自我反注册，不留孤儿监听
                    unregisterQuietly(tm, callback)
                    continue
                }
                registrations[key] = Registration(tm, callback)
                lastError = null
            } catch (e: Throwable) {
                lastError = "subId=$key ${e::class.java.simpleName}: ${e.message}"
            }
        }
    }

    @android.annotation.TargetApi(31)
    @SuppressLint("MissingPermission")
    private fun unregisterOne(reg: Registration) {
        val callback = reg.callback as? TelephonyCallback ?: return
        try {
            // javap：unregisterTelephonyCallback(TelephonyCallback)；api-versions since=31
            reg.manager.unregisterTelephonyCallback(callback)
        } catch (_: Throwable) {
            // 单条反注册失败不影响其它 subId 的释放；registrations 已清空，不会重复反注册
        }
    }

    @android.annotation.TargetApi(31)
    @SuppressLint("MissingPermission")
    private fun unregisterQuietly(manager: TelephonyManager, callback: TelephonyCallback) {
        try {
            // javap：unregisterTelephonyCallback(TelephonyCallback)；api-versions since=31
            manager.unregisterTelephonyCallback(callback)
        } catch (_: Throwable) {
            // 单条反注册失败不影响其它 subId 的释放；registrations 已清空，不会重复反注册
        }
    }

    /** 默认卡（[DEFAULT_KEY]）+ 全部已激活 subscriptionId，覆盖双卡。 */
    @SuppressLint("MissingPermission")
    private fun subscriptionKeys(): List<Int> {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val ids = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                sm?.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
            } else emptyList()
        } catch (e: Throwable) {
            // SecurityException（权限竞态）等：只保留默认卡，不阻塞注册流程
            emptyList()
        }
        return (listOf(DEFAULT_KEY) + ids).distinct()
    }

    /** 多卡分派口径与 CellularCollector.telephonyFor 一致（API 24+ 按 subId 建实例）。 */
    private fun telephonyFor(base: TelephonyManager, key: Int): TelephonyManager? =
        if (key == DEFAULT_KEY || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) base
        else runCatching { base.createForSubscriptionId(key) }.getOrNull() ?: base

    private fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
