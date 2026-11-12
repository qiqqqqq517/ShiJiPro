# 识机 Pro v1.0.0（2026-11-12）

零广告的 Android 网络诊断工具：蜂窝（双卡 6 制式）/ WiFi / GNSS / 路测覆盖 / 测速 / 设备信息。

## 本版新增（P13 研究报告补全 + P14 收尾）

- **GNSS 原始观测与导航电文**：`GnssMeasurementsCallback`（伪距率/载波相位/多径/AGC/ADR，全部按 `hasXxx()` 判三态）+ 导航电文（条数/最近一条）+ 基带 C/N0（API 30+）+ TTFF + 硬件型号/年份（API 28+）+ **DOP 几何解算**（估算值）。
- **运营商归属表**：band + EARFCN 区间 → 运营商（14 条 LTE 子频段，3 个公开来源核对；NR 未收录时显示 N/A + 原因，不猜）。
- **蜂窝页按制式分组**：邻区按 NR→LTE→WCDMA→TDS→GSM→CDMA→UNKNOWN 分组。
- **CSV 导出列可选**：13 列勾选，`timestampMs/latitude/longitude` 强制保留。
- **路测 KPI 加权评分**：RSRP/RSRQ/SINR 权重可配（自动归一化），0–100 估算分。
- **测速端点管理 + 就近选点**：端点由用户录入（不内置第三方节点库），haversine 就近选点。
- **采样调度器 + 前台服务**：按 `cellularIntervalMs` 自动刷新；可选常驻通知保活（进程被杀即停止，如实标注）。
- **RSRP 趋势曲线接真实采样**（环形缓冲 300 点，5 分钟窗口降频到 120 点）。
- **覆盖栅格（IDW）**：纯 Kotlin 反距离加权插值热力图（不使用已废弃的 RenderScript）。
- **本应用流量开销**：`TrafficStats` 本 UID 收发字节（不做逐应用流量）。

## 质量门禁

- `assembleDebug + assembleRelease`、`testDebugUnitTest`（**150 例全绿**）、`lint`（**0 error**）全部 BUILD SUCCESSFUL。
- 真机装机复验：小米 22041216UC / Android 14 / API 34，逐项 `uiautomator dump` 取证（见 `docs/`）。
- Release APK 已用 `apksigner` 验签（V2），SHA-256 见 Release 资产旁的 `SHA256SUMS.txt`。

## 如实边界

- 原始观测依赖机型 GNSS HAL：本机（小米 22041216UC）注册成功但不输出 `GnssMeasurement`，界面如实提示「已注册但尚未收到回调」，不伪造数值。
- NR 运营商子频段（n41/n78/n28）需叠加 38.104 栅格与工信部分配文件逐段核对，本版未收录 → 显示 N/A。
- 存储为内存态（重启不保留），Room 持久化列为后续工作。
- 界面文案目前为中文硬编码（未接 `stringResource`）。
- 不实现：私有高通诊断通道、广告/支付/云 SDK、逐应用流量、百度地图、悬浮窗、POI 报表。

## 安装

```bash
adb install app-release.apk
```

首次启动会请求定位 / 电话状态 / 通知权限；拒绝时对应模块降级显示（不崩溃）。
