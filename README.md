# 识机 Pro (ShiJiPro) · 说明文档

专业网优工具：蜂窝网络诊断 / WiFi 分析 / GNSS 定位 / 路测覆盖 / 设备信息 / 网络测速。
对标并集合 Cellular-Z、CellScan PRO、Signal Detector、Network Cell Info Lite、WiFi Analyzer 等同类工具的通用能力。

> 版本 0.1.0（P0–P12 全阶段）· 日期 2026-11-12
> 工程：`D:\01项目\ShiJiPro`　包名 `com.shiji.pro`
> minSdk **23** / targetSdk **37** / compileSdk **37**　语言 Kotlin 2.4.0　UI Jetpack Compose (Material 3, BOM 2024.01.00)

---

## 0. 编译运行步骤

```bat
cd /d D:\01项目\ShiJiPro
gradlew.bat assembleDebug          :: 产物 app\build\outputs\apk\debug\app-debug.apk
gradlew.bat testDebugUnitTest      :: 57 个 JVM 单测
gradlew.bat lint                   :: 0 errors
gradlew.bat installDebug           :: 需 adb 设备（本机当前无设备）
```

环境：JDK 21+（本机 JAVA_HOME = Android Studio JBR / JDK 25）、`ANDROID_HOME=D:\Android\SDK`（platform android-37.0、build-tools 37.0.0）、Gradle wrapper 9.7.1（Tencent 镜像，已缓存）。默认走阿里云 maven 镜像（可 `gradle.properties` 设 `useMirror=false` 回退）。

**签名发版**：本工程为全新包名，未配置签名。若日后作为 v1.0.0（`com.shiji.network`）升级包发布，需复用 `D:\ShiJi_v2\shiji.keystore` 并从 `keystore.properties` 读取凭据（不入库）。

---

## 1. 功能清单（7 标签 + 顶栏动作）

| 标签 | 已实现 | 数据来源 |
|---|---|---|
| 卡槽1 / 卡槽2 | 双卡 SIM/运营商、服务小区、邻区、信号评级、趋势图占位 | SubscriptionManager / TelephonyManager / getAllCellInfo |
| WiFi | 已连接信息、网络详情(IP/网关/DNS/掩码/广播)、周边热点、信道占用图 | WifiManager / ConnectivityManager.LinkProperties |
| 位置 | 定位数据(WGS-84)、卫星天空图、卫星列表、NMEA 占位 | LocationManager / GnssStatus.Callback |
| 设备 | 电池/屏幕/内存存储/硬件/系统/传感器 | BatteryManager sticky / WindowManager / ActivityManager / StatFs / Build / SensorManager |
| 覆盖测试 | 数据契约、CSV 列定义、室内三模式说明、路测点展示 | GNSS + 蜂窝/WiFi 快照 |
| 测速 | Ping/Jitter/下行/上行、服务器选择契约、历史对比契约、说明 | 自建端点(HttpURLConnection) / 系统 ping 二进制 |
| 顶栏：双卡对比 | 两槽 RSRP/评级并排 | AppState |
| 顶栏：日志中心 | 统一留存说明、CSV 导出契约 | LogRepository |
| 顶栏：设置 | 主题/语言/隐私/刷新间隔/关于/测速入口 | AppSettings |

### 1.1 P13 补全（2026-11-12，依据第三方研究报告 §11）

| 新增 | 内容 | 验收 |
|---|---|---|
| A0 GNSS 原始观测 | `GnssMeasurementsCallback`（伪距率/载波相位/多径/AGC/ADR）+ `GnssNavigationMessageCallback`（电文条数/最近一条）+ 基带 C/N0（API30+）+ TTFF + 硬件型号/年份（API28+）+ **DOP 几何解算**（`DopCalculator`，估算值） | 位置页新增「原始观测与导航电文」卡；`DopTest` 5 例 |
| A1 运营商归属 | `OperatorTable`：band + EARFCN 区间 → 运营商（14 条 LTE 子频段，3 源核对；NR 未收录→N/A） | 服务小区/邻区新增「运营商归属（换算）」；`OperatorTableTest` 8 例；`docs/P13_operator_earfcn_reference.md` |
| A2 制式分组 | 邻区按 NR→LTE→WCDMA→TDS→GSM→CDMA→UNKNOWN 分组，组内保持字段排序 | 真机 `邻区 · 5G NR（8）` |
| A3 CSV 字段可选 | 设置页 13 列 Checkbox（必需 3 列锁定）；`CsvColumns` + `exportCsvWith(columns)` | `DriveCsvColumnSelectionTest` 8 例 |
| B6 KPI 权重 | `QualityWeights`（自动归一化）+ `DriveKpi` 加权评分 0–100（估算） | `DriveKpiTest` 14 例；设置页滑条 |
| B4 测速端点 | 端点录入/选用/删除 + `GeoMath` haversine 就近选点（不内置第三方节点库） | `GeoMathTest` 6 例；测速页「测速端点」卡 |

结构拆分：`features` 由单文件 `FeatureScreens.kt`（409 行）拆为 8 个按标签文件（CellularTab/WifiTab/LocationTab/DeviceTab/DriveTestTab/SpeedTestTab/ScreenCommon/DualSimCompare + 分派器）。契约、所有权与验收见 `docs/P13_研究报告功能补全_契约与分工.md`；真机证据见 `docs/P13_真机复验记录_20261112.md`。

**P13 后门禁**：`assembleDebug + testDebugUnitTest（136 例）+ lint（0 error）` 全 `BUILD SUCCESSFUL`；APK 14,271,018 B。


**三态标注**：所有字段带 `实测/换算/估算/N/A` 徽标；估算值（信号评级、TA 距离、屏幕尺寸等）附「估算值，仅供参考」；取不到的字段一律 `N/A` + 原因，不编造数值。

---

## 2. 各字段数据来源 API 对照

| 分组 | 字段 | 公开 API | 标注 | 门槛/降级 |
|---|---|---|---|---|
| SIM | 卡槽/subId/运营商/MCC-MNC | `SubscriptionManager.getActiveSubscriptionInfoList()` + `SubscriptionInfo` | 实测 | API22 / READ_PHONE_STATE |
| SIM | IMSI / ICCID / 号码 | `getSubscriberId()` / `getSimSerialNumber()` / `getLine1Number()` | 实测→**通常 N/A** | API29+ 普通应用返回 null |
| SIM | 数据制式 / 漫游 / SIM 状态 | `getDataNetworkType()` / `isNetworkRoaming()` / `getSimState(slot)` | 实测 | API24/26 |
| LTE | TAC/CI/PCI/EARFCN/Bands | `CellIdentityLte` | 实测 | `getBands()` 需 API30 |
| LTE | RSRP/RSRQ/SINR/TA/CQI | `CellSignalStrengthLte` | 实测 | 奇异值→N/A |
| LTE | **ECI / Band / 频率 / eNB** | `FrequencyConverter`（查 3GPP 表 / 公式） | **换算** | 表未覆盖→N/A |
| NR | NR-PCI/NCI/TAC/NRARFCN | `CellIdentityNr` | 实测→常 N/A | API29，NSA 机型常 UNKNOWN |
| NR | SS/CSI RSRP/RSRQ/SINR | `CellSignalStrengthNr` | 实测 | 需机型上报 |
| GSM/WCDMA/TDS/CDMA | LAC/CI/ARFCN/BSIC/UARFCN 等 | 对应 `CellInfo*` 子类 | 实测 | TDS 需 API28 |
| 邻区 | 未注册 `CellInfo` 项 | `getAllCellInfo()` | 实测 | 空则「当前机型/系统不提供邻区信息」 |
| WiFi | SSID/BSSID/RSSI/频率/速率 | `WifiInfo` / `ScanResult` | 实测 | API33 需 NEARBY_WIFI_DEVICES |
| WiFi | 信道 / 频段 / 安全类型 | `FrequencyConverter` / capabilities 解析 | 换算 | 范围外→N/A |
| WiFi | IP/网关/DNS/掩码/广播 | `LinkProperties` | 实测 + 换算 | 掩码/广播由前缀长度换算 |
| 位置 | 经纬度/精度/高度/方位/速度 | `Location` | 实测 | 无分量→N/A |
| 位置 | 定位方式 | `Location.getProvider()` | 换算映射 | network 源 CELL/WIFI 不可区分 |
| GNSS | 卫星 7 项 | `GnssStatus.Callback` | 实测 | API24+ |
| 电池 | 电量/状态/健康/温度/电压 | `ACTION_BATTERY_CHANGED` | 实测 | 无 EXTRA→N/A |
| 屏幕 | 分辨率/密度/尺寸/刷新率 | `WindowManager` / `DisplayMetrics` | 实测 + 换算 | 尺寸为换算值 |
| 内存/存储 | RAM/ROM | `ActivityManager.MemoryInfo` / `StatFs` | 实测 | — |
| 硬件 | 型号/厂商/芯片/ABI/核心数 | `Build.*` / `Runtime` | 实测 | SOC_MODEL 需 API31 |
| 系统 | 版本/SDK/补丁/内核 | `Build.VERSION` / `System.getProperty` | 实测 | — |
| 传感器 | 全量列表 | `SensorManager.getSensorList(TYPE_ALL)` | 实测 | 无需权限 |
| 测速 | Ping/Jitter/上下行 | 系统 ping 二进制 / HttpURLConnection | 实测 + 换算 | 端点未配置→全部 N/A |

频段常量表（16 LTE + 13 NR）与 NR-ARFCN 栅格公式经**双源独立核验**，见 [`docs/P1_3GPP_band_reference.md`](docs/P1_3GPP_band_reference.md)。

---

## 3. 权限用途

| 权限 | 用途 | 拒绝后 |
|---|---|---|
| READ_PHONE_STATE / READ_PHONE_NUMBERS | SIM、CellInfo、号码 | 蜂窝页 PermissionGate 降级 |
| ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION | getAllCellInfo、GNSS、WiFi 扫描(≤32) | 对应模块降级 |
| ACCESS_WIFI_STATE / CHANGE_WIFI_STATE / NEARBY_WIFI_DEVICES | 连接信息 / 扫描 / API33 热点 | WiFi 页降级 |
| ACCESS_NETWORK_STATE / INTERNET | LinkProperties / 测速 | 网络详情 N/A |
| BLUETOOTH / BLUETOOTH_ADMIN(≤30) / BLUETOOTH_SCAN / BLUETOOTH_CONNECT | 蓝牙扫描 | 蓝牙卡片隐藏 |
| POST_NOTIFICATIONS / FOREGROUND_SERVICE(_LOCATION) | 前台采集通知 | 后台采集不可用 |
| SYSTEM_ALERT_WINDOW / PACKAGE_USAGE_STATS | 悬浮窗 / 逐应用流量 | 开关置灰或隐藏 |
| ACCESS_BACKGROUND_LOCATION / WAKE_LOCK | 后台路测 | 仅前台 |

按模块分组动态申请（MainActivity），拒绝不崩溃。

---

## 4. 机型 / 系统限制与已知无法获取字段

- **IMSI / ICCID / 电话号码**：Android 10(API29)+ 普通应用通常返回 null；SIM 常不写号码 → N/A。
- **邻区**：多数 OEM 仅返回服务小区 → 明确提示，不造数据。
- **5G NR 参数**：`CellInfoNr` 在大量 NSA 机型返回 UNKNOWN；NR 邻区几乎不提供。
- **NR-ARFCN 方向**：Android `getNrarfcn()` 未明示 DL/UL，本工程按 DL 口径查频段表（**工程假设，非规范结论**）；若机型上报 UL ARFCN，FDD 频段会降级 N/A。见参考文档 §7 未决项。
- **CDMA / TDSCDMA**：新机型普遍不返回；TDS 需 API28。
- **设备 MAC / 序列号**：MAC 随机化、序列号 Q+ 返回 UNKNOWN → N/A。
- **CPU 型号 / 主频 / GPU 渲染器**：依赖本地系统文件或离屏 EGL，非稳定公开 API → N/A。
- **逆地理编码**：依赖设备内置后端，离线/无 GMS → N/A。
- **国内网络定位源坐标偏移**：可能非 WGS-84，仅提示不静默转换。
- **WiFi 扫描节流**：`startScan()` 自 API28 起受系统限频，配置间隔不保证生效。
- **API 23–24 双卡**：可枚举但无法按 subId 建独立 TelephonyManager → slot1 降级。
- **频段表覆盖**：仅 16 LTE + 13 NR 常用频段，未覆盖频段返回 N/A，不伪装完整表。
- **测速端点**：默认占位（自建），未配置时所有速率字段 N/A，不伪造数值。
- **本地存储**：当前为**内存 StateFlow**（进程内），重启不保留；Room 持久化列为后续增强，设置页如实显示「内存（未持久化）」。
- **中英文切换**：`values/` 与 `values-en/` 两套 strings.xml 已就位，但 MainActivity/各 Screen 的文案目前为中文硬编码（未走 `stringResource`），因此「跟随系统语言切换 UI」尚未真正生效，列为 Phase 2 增强（不在本版宣称已完成）。
- **后台持续采集**：`SamplingService` 仅为 Manifest 占位、启动即自停，`SamplingScheduler`/前台服务通知未接线；曲线实时刷新与趋势窗口切换当前为空图（不画伪造曲线）。

---

## 5. 「未实现 / 需 Root 方案」清单（§13 硬性约束）

1. 底层信令抓取（Qualcomm DIAG/QMI、`/dev/diag`、私有 `ITelephony` 隐藏接口）→ 需 Root + 私有高通接口，**不实现**。
2. 完整测量报告(MR)、锁频/锁网/PCI 锁定、强制切换 → 隐藏/系统 API，不实现。
3. 基站真实经纬度数据库 → 公开 API 不提供；基站地图仅为估算布点，标「估算值，仅供参考」。
4. 毫秒级物理层原始测量序列 → 公开 API 仅秒级回调，不伪造高频数据。
5. 读取 WiFi 密码 / WPS / 802.1X 配置 → 不实现。
6. 其它 App 逐应用流量明细（无 PACKAGE_USAGE_STATS 特殊授权）→ 隐藏。
7. Room 持久化 / 在线地图瓦片(osmdroid) / 悬浮窗与状态栏常驻 / 逐应用流量 / 逐点信号趋势实时曲线 / 室内平面图叠加 / 传感器自动打点 → 列为 Phase 2 增强，当前未实现。

---

## 6. 架构与分层（§10.2）

```
:core:model   纯 Kotlin 领域模型 + FieldValue 三态 + 频段表        (零 Android 依赖)
:core:common  频段/信道/ECI 换算、CSV(RFC4180)、脱敏、采样、AppError (零 Android 依赖)
:core:domain  Collector / Repository 接口、UseCase、SamplingPolicy    (零 Android 依赖，架构单测守护)
:core:ui      主题色板/字号/间距/圆角 + 复用组件 + 3 种自绘图表
:data         8 个采集器 + CapabilityGate + 5 个仓储实现 + Koin dataModule
:features     7 标签 Screen（无状态，只读 AppState）+ ShiJiTab 契约
:app          MainActivity / AppViewModel / Koin appModule / Manifest / res
```

单一数据源：`8 Collector → AppStateRepository(StateFlow) → AppViewModel → Compose Screen`。UI 层不含任何 `TelephonyManager`/`WifiManager`/`LocationManager`。敏感字段默认脱敏；不上传用户数据。

---

## 7. 第三方组件与开源协议

| 组件 | 协议 |
|---|---|
| AndroidX (core-ktx / activity-compose / lifecycle / compose BOM / material3 / datastore) | Apache-2.0 |
| Kotlin stdlib / kotlinx-coroutines | Apache-2.0 |
| Koin (koin-android / koin-core / koin-compose) | Apache-2.0 |
| JUnit 4（仅测试） | EPL-1.0 |

未内置任何付费商业 SDK；测速用 JDK `HttpURLConnection` + 可替换自建端点；图表/图标全部自绘，零版权素材（图标设计见 `docs/ICON_DESIGN.md`）。

---

## 8. 阶段交付与自检

| 阶段 | 报告 |
|---|---|
| P0 骨架 | `docs/P0_骨架交付与自检报告.md` |
| P1 领域模型/通用工具 | `docs/P1_领域模型与通用工具交付报告.md` |
| P1 频段核验 | `docs/P1_3GPP_band_reference.md` |
| 图标包 | `docs/ICON_DESIGN.md` + `tools/generate_launcher_icons.py` |

**§11 界面代码自动优化**：组件拆分（`SectionCard`/`ValueRow`/`OriginBadge`/`EmptyState`/`EstimateDisclaimer`）、主题 token（颜色/字号/间距/圆角集中在 `core/ui/theme/`）、`LazyColumn` 稳定 key、状态提升到 ViewModel、无硬编码颜色/尺寸（自检修复 1 处 `1.dp`）、Parser 顶部注明制式→字段→奇异值判定。

**验证**：`assembleDebug` + `testDebugUnitTest`(57 例全绿) + `lint`(0 errors) 全部通过；`app-debug.apk` 14,078,755 B。无 adb 设备，未做装机回归。
