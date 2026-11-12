# 识机 Pro v1.1.0（2026-11-12）

零广告的 Android 网络诊断工具：蜂窝（双卡 6 制式）/ WiFi / GNSS / 路测覆盖 / 测速 / 设备信息。
本版为**纯界面层发版**：业务逻辑、数据契约、采集器与写回链路与 v1.0.0 完全一致。

## 本版内容

### 1. 统一 UI 体系（P15）

- 新增 `ShiJiExtendedColors`（表面分层 / 状态容器 / 徽标前景），补齐 Typography 全档位（`labelSmall` 11→12sp），新增自绘图标集与响应式断点工具。
- 装配层改为 `Scaffold` + 品牌应用栏：窄屏（<600dp）顶部标签行，宽屏（≥600dp）左侧导航栏；内容限宽 900dp 居中，≥840dp 字段两列、设置页左右分栏。
- 统一「加载 / 空数据 / 错误」三态组件；`ValueRow` 合并语义一次朗读，三种图表补 `contentDescription`，交互控件 ≥48dp，覆盖层接管系统返回键。

### 2. 恒定骨架：所有项均展示、消除布局跳变

- 7 个主标签 + 双卡对比页改为**固定卡片集 + 固定字段清单**：SIM 15 项、服务小区 39 项（LTE/NR/GSM/WCDMA/TDS/CDMA 全键）、WiFi 13 项 + DNS、定位 7 项 + 原始观测 8 项、设备 7/2/4/5/4/2 项。**取不到就写 N/A + 原因，不再隐藏字段或整张卡片。**
- 首屏不再用骨架屏顶掉内容：第一帧即完整骨架，采集结果到达后原地换值。
- 高度稳定：`ValueRow` 注释位恒定占一行（N/A 原因与估算声明共用）、`StatusBanner` 固定 2 行 + 最小 48dp、`ChartFrame`/`ListFrame` 固定 160dp、端点徽标常驻「当前 / 备选」、对话框校验条常驻。
- 修正：位置页经纬度标签与数值互换（原 `latitude` 填入「经度」），现按 `GnssFix` 字段正确对应。

## 质量门禁

| 项 | 结果 |
|---|---|
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **150 例，0 失败 / 0 错误 / 0 跳过** |
| `lint` | **0 error**（`features` 7 条 `ComposableNaming` 为既有小写 `render*` 约定；无可访问性告警） |
| 签名 | `apksigner` 验签通过：V1 (JAR) + V2；证书 `CN=ShiJi`，SHA-256 `8f94e91d…b036` |
| 真机冒烟 | 小米 22041216UC / Android 14 / API 34：**23 项全通过**（7 标签 + 3 覆盖层 + 端点对话框 + 刷新×3 + 刷新后字段回归） |
| 冷启动 | Release **345ms 均值**（`am start -W TotalTime`，n=3：362 / 335 / 339ms；同机 Debug 1643ms） |

真机取证：`docs/evidence_ui_v1.1/`（逐页 UI XML + 截图 + logcat），无 `FATAL EXCEPTION` / `ANR`。

## 如实边界（沿用 v1.0.0）

- 存储仍为内存态（进程重启不保留）；Room 持久化未实现。
- 界面文案为中文硬编码，未接 `stringResource`（`values-en` 存在但切换系统语言不生效）。
- 原始观测 / 导航电文依赖机型 GNSS HAL：本机注册成功但不输出 `GnssMeasurement`，界面如实显示 N/A。
- NR 运营商子频段未收录 → N/A；不猜测。
- 悬浮窗、逐应用流量、百度地图、POI 报表等仍未实现。

## 安装

```bash
adb install app-release.apk
# 校验
certutil -hashfile app-release.apk SHA256
```

APK：10,234,029 B
SHA-256：`38DE639EBD00E9DF0562B7B1FA99B04F961CD29C3E5FF30A661484DDEB7E4F12`

首次启动会请求定位 / 电话状态 / WiFi / 通知权限；拒绝时对应模块降级为 N/A，不崩溃。
