# P1 · 3GPP 频段常量表独立核验报告

> **文档定位**：ShiJiPro P1 阶段 `core:model` 频段常量表的数值依据（纯研究 + 文档）。
> **维护方**：`band-verify`（本文件的唯一写入方）。如 lead 需追加代码侧修复记录，**请追加到 §8，不要整体覆写本文件**——本轮已发生过一次并行写覆盖，见 §0.4。
> **写入范围**：仅本文件；`band-verify` 未触碰任何 `.kt` / `build.gradle.kts` / `Manifest` / `values`（§7 的代码核对为**只读**）。
> **核验日期**：2026-11-12
> **规范版本基线**：3GPP TS 36.101 / TS 38.101-1 / TS 38.101-2 / TS 38.104 的 Rel-19 逐节转录；ETSI TS 136 101 V17.12.0（= 3GPP TS 36.101 v17.12.0 Rel-17）作为官方 PDF 存档链接。
> **用途**：供 lead 写 Kotlin 常量表时抄录数值。本文所有频率单位均为 **MHz**，所有信道号均为整数。所有由频段表得出的结果一律为**换算值**（`FieldValue.derived`），不是实测值。

---

## §0 来源清单、可信度分级与抓取边界

### 0.1 分级

| 级别 | 性质 | 来源 | 本次是否真实取回正文 | 用途 |
| --- | --- | --- | --- | --- |
| **A** | **规范原文逐节转录**（第三方镜像，逐条对应规范表格；非 3GPP 官网 PDF） | iTecSpec 逐节页面（URL 见 0.2） | ✅ 全部取回 | **本文全部数值的第一来源** |
| **B** | **规范原文 PDF**（ETSI 官方） | ETSI TS 136 101 V17.12.0 | ❌ 1638 页，Firecrawl 超时未逐页取回 | 仅作可核查链接，**未用于抄录数值** |
| **C** | **3GPP 官网目录 / 动态报告入口** | `3gpp.org/dynareport/36101.htm`、`38104.htm`、`ftp/Specs/2023-03/Rel-18/36_series` | ❌ 被 SSRF 代理拦截 | 仅作可核查链接 |
| **D** | **二手整理**（用于交叉对拍） | sqimway、5G/6G Academy、Wikipedia LTE/NR 频段表 | ✅ 全部取回 | 验证 A 级数值，三方一致 |
| **E** | **补充来源（未经 band-verify 复现）** | CafeTele EARFCN/NR-ARFCN 全表、TelecomCalculators LTE 表、HiCellTek | ❌ 本核验通道未访问 | 保留自并行版本，**不作为本文数值依据**，见 0.4 |

> 结论：**本文每个数字均出自 A 级规范转录，并经 D 级至少两方独立对拍一致**。B/C 仅提供官方可核查入口。**如实标注：未宣称「已逐页核对 3GPP 官网 PDF」**。

### 0.2 URL 清单（逐条对应覆盖的数值）

| 编号 | URL | 规范号 / 表号 | 覆盖内容 |
| --- | --- | --- | --- |
| S1 | https://itecspec.com/3gpp/36.101/s/5.7.3 | **3GPP TS 36.101 §5.7.3, Table 5.7.3-1 "E-UTRA channel numbers"** | 表 1 全部 8 列数值（16 个 Band）+ 上下行换算公式 |
| S2 | https://itecspec.com/3gpp/36.101/s/5.5 | **3GPP TS 36.101 §5.5, Table 5.5-1 "E-UTRA operating bands"** | 表 1 双工模式、频段上下界（自洽校验） |
| S3 | https://www.sqimway.com/lte_band.php | 二手整理，自称依据 TS 36.101 Rel-19 (2025-06) | 表 1 全量对拍、表 3 常用名 |
| S4 | https://www.5g6gacademy.com/tools/lte-earfcn-converter | 二手整理，自称依据 TS 36.101 §5.7.3 | 表 1 的 `F_DL low` / `N_Offs-DL` / DL EARFCN 区间对拍 |
| S5 | https://en.wikipedia.org/wiki/LTE_frequency_bands | 二手整理，依据 Tables 5.5-1 / 5.6.1-1 of TS 36.101/36.104/36.106 | 表 1 双工模式、表 3 标称频率与 common name |
| S6 | https://itecspec.com/3gpp/38.104/s/5.4.2.1 | **3GPP TS 38.104 §5.4.2.1, Table 5.4.2.1-1** | 表 2-A 三段完整栅格参数、`NREF` 上限 3279165 |
| S7 | https://itecspec.com/3gpp/38.101-1/s/5.4.2.1 | **3GPP TS 38.101-1 §5.4.2.1, Table 5.4.2.1-1** | 表 2-A 前两段（FR1 UE 视角，`NREF` 上限 2016666） |
| S8 | https://itecspec.com/3gpp/38.101-2/s/5.4.2.1 | **3GPP TS 38.101-2 §5.4.2.1, Table 5.4.2.1-1** | 表 2-A 第三段（FR2 UE 视角） |
| S9 | https://itecspec.com/3gpp/38.101-1/s/5.2 | **3GPP TS 38.101-1 §5.2, Table 5.2-1 "NR operating bands in FR1"** | 表 2-B 全部 13 个 NR 频段上下界与双工模式 |
| S10 | https://en.wikipedia.org/wiki/5G_NR_frequency_bands | 二手整理，依据 Rel-19 TS 38 系列 | 表 2-B 对拍、表 3 common name |
| S11 | https://www.etsi.org/deliver/etsi_ts/136100_136199/136101/17.12.00_60/ts_136101v171200p.pdf | **ETSI TS 136 101 V17.12.0**（= 3GPP TS 36.101 v17.12.0 Rel-17） | 官方规范原文 PDF（B 级存档链接） |
| S12 | https://www.3gpp.org/ftp/Specs/2023-03/Rel-18/36_series | 3GPP 官网 36 系列规范目录 | 官方入口（C 级，本次被拦截） |

**E 级补充来源（来自并行版本，未经本核验通道复现，不作为数值依据）**：
- https://www.3gpp.org/dynareport/36101.htm / https://www.3gpp.org/dynareport/38104.htm（3GPP 动态报告入口）
- https://www.cafetele.com/5g-tools/lte-earfcn/（自称取自 TS 36.101 V20.1.0 / Table 5.7.3-1）
- https://www.cafetele.com/5g-tools/nr-arfcn（自称取自 TS 38.104 V20.0.0 / Table 5.2-1 / 5.4.2.1）
- https://telecomcalculators.com/…/lte-earfcn-frequency-calculator（表头注明 TS 36.101 Table 5.7.3-1）
- HiCellTek / rf PAGE（仅检索命中，未取全文）

### 0.3 抓取通道的诚实记录（影响可复现性）

- `web_search` / `web_fetch`：本会话内**所有**外部域名被 SSRF 代理拦截，DNS 解析到 `198.18.0.x` 私有段，全部失败 → 未作为数值来源。
- `dsh_browser_open`：内置浏览器 sidebar 未连接（desktop bridge 未连），失败 → 未使用。
- `read_page`（modsearch 桥，Firecrawl 云抓取）：**成功**，是本文 A/D 级正文的实际通道。
- 失败页：sharetechnote 两个 URL 均 404；techplayon 落到首页（无目标正文）；5g-tools.com 返回 500（WordPress `create_function()` 未定义）；ETSI PDF 因 1638 页超时。**这些页均未用于抄录任何数值**。

### 0.4 并行写覆盖事件与合并口径（不静默选一个版本）

本文件曾出现两个版本：
- **版本 A（band-verify 产出）**：本文主体，A 级规范转录 + D 级三方对拍，含 3 张主表 / 8 列 LTE 表 / 公式 / 冲突取舍 / 落地建议。
- **版本 B（并行写入方产出）**：100 行简化版，来源改为 CafeTele / TelecomCalculators / 3GPP dynareport，含「Band 28 初版 `9260..9660` 缺陷已修复」「NR FDD 项误用 UL 范围已修复」等**代码侧迭代记录**，以及 WiFi 段与落地约束 6 条。

**取舍**：两版**数值完全一致**（16 个 LTE、栅格三档、13 个 NR 均无冲突），故不做数值取舍；合并规则为——
1. 数值与规范依据以**版本 A**为准（A 级规范转录可逐条回溯 URL）；
2. 版本 B 的「代码侧已修复缺陷」记录保留于 **§4.1**，但明确标注为**代码迭代历史**，不冒充「来源之间的数值冲突」；
3. 版本 B 的来源清单降级为 **E 级**，标注「未经 band-verify 复现」；
4. 版本 B 的 WiFi 段与落地约束整合进 **§5.4 / §5**；
5. 版本 B 的「两个独立二手源逐条一致」结论**不沿用**——该结论所依赖的 CafeTele / TelecomCalculators 本核验通道未访问，改以本文 §1.3 的 16/16 自洽校验 + D 级三方对拍为证据。

---

## §1 表 1 — LTE E-UTRA 频段与 EARFCN 范围

**规范出处（本表全部单元格）**：3GPP **TS 36.101 §5.7.3 Table 5.7.3-1**（[S1]），双工模式与频段上下界对拍 **§5.5 Table 5.5-1**（[S2]）；二手对拍 [S3][S4][S5] 三方一致。

**字段命名对照（见 §4 C2）**：任务书的 `NDL_low` / `NDL_high` 对应规范表格的 **`NOffs-DL`** 与 **`Range of NDL` 上界**；`NUL_low` / `NUL_high` 对应 **`NOffs-UL`** 与 **`Range of NUL` 上界**。在这张表里 `NOffs-DL ≡ Range of NDL 下界`（数值恒等），两种写法等价。

| Band | 双工 | `FDL_low` (MHz) | `NDL_low` (=`NOffs-DL`) | `NDL_high` | `FUL_low` (MHz) | `NUL_low` (=`NOffs-UL`) | `NUL_high` | 规范出处 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | FDD | 2110 | 0 | 599 | 1920 | 18000 | 18599 | TS 36.101 Tab 5.7.3-1 [S1] |
| 2 | FDD | 1930 | 600 | 1199 | 1850 | 18600 | 19199 | 同上 |
| 3 | FDD | 1805 | 1200 | 1949 | 1710 | 19200 | 19949 | 同上 |
| 4 | FDD | 2110 | 1950 | 2399 | 1710 | 19950 | 20399 | 同上 |
| 5 | FDD | 869 | 2400 | 2649 | 824 | 20400 | 20649 | 同上 |
| 7 | FDD | 2620 | 2750 | 3449 | 2500 | 20750 | 21449 | 同上 |
| 8 | FDD | 925 | 3450 | 3799 | 880 | 21450 | 21799 | 同上 |
| 12 | FDD | 729 | 5010 | 5179 | 699 | 23010 | 23179 | 同上 |
| 13 | FDD | 746 | 5180 | 5279 | 777 | 23180 | 23279 | 同上 |
| 17 | FDD | 734 | 5730 | 5849 | 704 | 23730 | 23849 | 同上 |
| 20 | FDD | 791 | 6150 | 6449 | 832 | 24150 | 24449 | 同上 |
| 28 | FDD | 758 | 9210 | 9659 | 703 | 27210 | 27659 | 同上 |
| 38 | TDD | 2570 | 37750 | 38249 | 2570 | 37750 | 38249 | 同上 |
| 39 | TDD | 1880 | 38250 | 38649 | 1880 | 38250 | 38649 | 同上 |
| 40 | TDD | 2300 | 38650 | 39649 | 2300 | 38650 | 39649 | 同上 |
| 41 | TDD | 2496 | 39650 | 41589 | 2496 | 39650 | 41589 | 同上 |

> 目标 16 个 Band 在 [S1] 中均为**无脚注的干净行**；[S1] 中 `292` / `322` / `665` / `672` / `692` / `706` / `752` / `762` / `1067` 等是「Band 号 + 脚注编号」OCR 粘连（如 `665` = Band 65 + 脚注 5），**不在本表范围内**；扩表时不要直接复制粘贴这些行。

### 1.1 下行 / 上行频率换算公式（规范原文 [S1]）

```
FDL [MHz] = FDL_low + 0.1 × (NDL − NOffs-DL)
FUL [MHz] = FUL_low + 0.1 × (NUL − NOffs-UL)
```

- `0.1` MHz = **100 kHz 信道栅格**（LTE channel raster）。
- `NDL` ∈ [`NDL_low`, `NDL_high`]，`NUL` ∈ [`NUL_low`, `NUL_high`]。
- 规范原文用 `NOffs-DL` / `NOffs-UL`；因本表内 `NOffs-DL = NDL_low`、`NOffs-UL = NUL_low`，任务书给出的 `FDL = FDL_low + 0.1*(EARFCN − NDL_low)` **数值上完全成立**。

**反推（由频率求 EARFCN）**：

```
EARFCN_DL = NDL_low + 10 × (FDL − FDL_low)   ⇐ NDL = NOffs-DL + 10 × (FDL − FDL_low)
EARFCN_UL = NUL_low + 10 × (FUL − FUL_low)   ⇐ NUL = NOffs-UL + 10 × (FUL − FUL_low)
```

`10` = `1/0.1`，即每 1 MHz 对应 10 个 EARFCN。结果应为整数；出现非整数说明频率不在 100 kHz 栅格上，须显式处理而非静默取整。

### 1.2 FDD / TDD 的 `NOffs` 偏移规则

| 类型 | 规则 | 依据 |
| --- | --- | --- |
| **FDD** | 下行、上行各有**独立**的 `NOffs-DL` / `NOffs-UL` 与互不相交的 EARFCN 区间 ⇒ **EARFCN 数值本身即可判定方向** | [S1][S4] |
| **FDD（Rel-8~11 频段）** | `NOffs-UL = NOffs-DL + 18000`，本表 12 个 FDD Band 逐一验证成立（0→18000、600→18600、…、9210→27210） | [S1] 逐行计算 |
| **FDD（Rel-13+ 新频段）** | 偏移改为 `+65536`：Band 65 `65536→131072`、66 `66436→131972`、68 `67536→132672`、71 `68586→133122` | [S1][S3] |
| **TDD** | `NOffs-DL = NOffs-UL` 且 `Range of NDL = Range of NUL`，**上下行共用同一 EARFCN 区间，无方向信息**；Band 33 起 `NOffs` 从 36000 起始 | [S1][S5] |
| **SDL / SUL** | 仅单侧有频率，另一侧为 `N/A`（如 LTE Band 29/32/67/75/76；NR n29/n67/n75/n76/n80…）；不可用 0 或 −1 冒充频率 | [S1][S9] |

> ⚠️ **`+18000` 不是普适常量**。Kotlin 常量表必须逐 Band 存 `NOffs-UL`，禁止用 `NOffs-DL + 18000` 推导，否则扩表到 Band 65+ 会全错。

### 1.3 数值自洽校验（证明非凭记忆编造）

对每个 Band 用表 1 数值回算下行频段上界 `FDL_low + 0.1×(NDL_high − NDL_low)`，与 **Table 5.5-1**（[S2]）的 `FDL_high` 对比；差值恒为 **0.1 MHz**（100 kHz 栅格粒度导致最末栅格点比频段上界低一个栅格）：

| Band | 回算值 (MHz) | 5.5-1 `FDL_high` | Band | 回算值 (MHz) | 5.5-1 `FDL_high` |
| --- | --- | --- | --- | --- | --- |
| 1 | 2169.9 | 2170 | 20 | 820.9 | 821 |
| 2 | 1989.9 | 1990 | 28 | 802.9 | 803 |
| 3 | 1879.9 | 1880 | 38 | 2619.9 | 2620 |
| 4 | 2154.9 | 2155 | 39 | 1919.9 | 1920 |
| 5 | 893.9 | 894 | 40 | 2399.9 | 2400 |
| 7 | 2689.9 | 2690 | 41 | 2689.9 | 2690 |
| 8 | 959.9 | 960 | 12 | 745.9 | 746 |
| 13 | 755.9 | 756 | 17 | 745.9 | 746 |

16/16 全部自洽；上表同时说明 `EARFCN` 区间长度 = 频段带宽 × 10 向下取整。

### 1.4 规范 NOTE 1（不是频段归属约束）

[S1] NOTE 1：靠近频段边缘、载波会越出频段边界的信道号不得使用——低端前 `7/15/25/50/75/100` 个、高端后 `6/14/24/49/74/99` 个（对应 1.4/3/5/10/15/20 MHz 带宽）。**这只约束「该 EARFCN 能否作为有效载波」，不改变其频段归属**；查表函数不应据此返回 `null`。

---

## §2 表 2 — NR 频段与 NR-ARFCN 全局栅格

### 2-A 配套参数表：NR-ARFCN 全局栅格

**规范出处（本表全部单元格）**：3GPP **TS 38.104 §5.4.2.1 Table 5.4.2.1-1**（[S6]，三段齐全）；**TS 38.101-1 §5.4.2.1**（[S7]，前两段）；**TS 38.101-2 §5.4.2.1**（[S8]，第三段）。三处数值完全一致。

| 频率范围 (MHz) | `ΔFGlobal` (kHz) | `FREF-Offs` (MHz) | `NREF-Offs` | `Range of NREF` | 归属 | 规范出处 |
| --- | --- | --- | --- | --- | --- | --- |
| 0 – 3000 | 5 | 0 | 0 | 0 – 599999 | FR1 低段 | TS 38.104 / 38.101-1 Tab 5.4.2.1-1 [S6][S7] |
| 3000 – 24250 | 15 | 3000 | 600000 | 600000 – 2016666 | FR1 高段 | 同上 |
| 24250 – 100000 | 60 | 24250.08 | 2016667 | 2016667 – 3279165 | FR2 | TS 38.104 / 38.101-2 Tab 5.4.2.1-1 [S6][S8] |

**全局换算规则（规范原文 [S6][S7][S8]）**：

```
FREF [MHz] = FREF-Offs [MHz] + ΔFGlobal [kHz→MHz] × (NREF − NREF-Offs)
```

实现时必须做单位归一（`ΔFGlobal` 规范以 **kHz** 给出，`FREF` 以 **MHz** 给出）：

```
FREF_MHz = FREF_Offs_MHz + (ΔFGlobal_kHz / 1000.0) × (NREF − NREF_Offs)
NREF     = NREF_Offs + (FREF_MHz − FREF_Offs_MHz) × 1000 / ΔFGlobal_kHz
```

> **这是分段函数，不是单一连续公式**；三段边界（3000 / 24250 MHz）不连续，见 §4 C4。

### 2-B 表 2（主表）：常见 FR1 NR 频段上下界与双工模式

**规范出处（本表全部单元格）**：3GPP **TS 38.101-1 §5.2 Table 5.2-1 "NR operating bands in FR1"**（[S9]）；二手对拍 [S10] 一致。TDD 频段的 UL 与 DL 区间在规范中列为同一组数值；FDD 频段分别列出 UL 与 DL。

| NR 频段 | 双工 | `FUL_low` | `FUL_high` | `FDL_low` | `FDL_high` | 标称 | 规范出处 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| n1 | FDD | 1920 | 1980 | 2110 | 2170 | 2100 | TS 38.101-1 Tab 5.2-1 [S9] |
| n3 | FDD | 1710 | 1785 | 1805 | 1880 | 1800 | 同上 |
| n5 | FDD | 824 | 849 | 869 | 894 | 850 | 同上 |
| n7 | FDD | 2500 | 2570 | 2620 | 2690 | 2600 | 同上 |
| n8 | FDD | 880 | 915 | 925 | 960 | 900 | 同上 |
| n20 | FDD | 832 | 862 | 791 | 821 | 800 | 同上 |
| n28 | FDD | 703 | 748 | 758 | 803 | 700 | 同上 |
| n38 | TDD | 2570 | 2620 | 2570 | 2620 | 2600 | 同上 |
| n40 | TDD | 2300 | 2400 | 2300 | 2400 | 2300 | 同上 |
| n41 | TDD | 2496 | 2690 | 2496 | 2690 | 2500 | 同上 |
| n77 | TDD | 3300 | 4200 | 3300 | 4200 | 3700 | 同上 |
| n78 | TDD | 3300 | 3800 | 3300 | 3800 | 3500 | 同上 |
| n79 | TDD | 4400 | 5000 | 4400 | 5000 | 4500 | 同上 |

**任务书表号纠偏**：任务书写的「TS 38.101 / 38.104 **Table 5.2-1**，NR-ARFCN 全局栅格」把两张表混在一起了——**Table 5.2-1 是「NR operating bands」频段列表**（[S9]），**NR-ARFCN 全局栅格参数在 Table 5.4.2.1-1**（[S6][S7][S8]）。本文按真实表号分别给出。

---

## §3 表 3 — 频段号 → 运营商常用频段名称对照（UI 显示用）

**来源**：标称频率与 common name 取自 [S5]（LTE）/[S10]（NR）的二手整理，频段号与频率范围对拍 [S2][S9] 规范原文。**常用名是行业俗称，不是 3GPP 规范字段**；同一俗称常对应多个 Band，UI 必须同时显示 Band 号。

| 频段号 | 制式 | 标称频率 | 常用名（中文） | 英文 / 行业俗称 | 双工 | 陷阱 |
| --- | --- | --- | --- | --- | --- | --- |
| Band 1 | LTE | 2100 MHz | 2100M / IMT 2100 | IMT, UMTS 2100 | FDD | — |
| Band 3 | LTE | 1800 MHz | 1800M / DCS | DCS 1800 | FDD | — |
| Band 5 | LTE | 850 MHz | 850M / CLR | Cellular 850, CLR | FDD | — |
| Band 8 | LTE | 900 MHz | 900M / E-GSM | Extended GSM 900 | FDD | — |
| Band 20 | LTE | 800 MHz | 800M 数字红利 | Digital Dividend 800 (EU) | FDD | **反向双工**：UL 832–862 高于 DL 791–821，间隔 −41 MHz |
| Band 28 | LTE | 700 MHz | 700M APT | APT 700 | FDD | 「700M」一对多（12/13/14/17/28/29/44/67/68/85） |
| Band 38 | LTE | 2600 MHz | 2600M TDD | IMT-E TDD, TD 2600 | TDD | 与 Band 41 频率部分重叠 |
| Band 40 | LTE | 2300 MHz | 2300M TDD | S-Band 2300, TD 2300 | TDD | 中国移动 TDD 主力 |
| Band 41 | LTE | 2500 MHz（俗称 2.6G） | 2600M TDD / 2.6G TDD | BRS, TD 2600+ | TDD | **标称 2500，俗称常写 2600**；严格「2600」是 Band 38（见 C6） |
| n1 | NR | 2100 MHz | 2100M NR | IMT | FDD | 与 LTE Band 1 同号同频 |
| n3 | NR | 1800 MHz | 1800M NR | DCS | FDD | — |
| n5 | NR | 850 MHz | 850M NR | CLR | FDD | — |
| n8 | NR | 900 MHz | 900M NR | Extended GSM | FDD | — |
| n20 | NR | 800 MHz | 800M NR | Digital Dividend | FDD | 反向双工同 Band 20 |
| n28 | NR | 700 MHz | 700M NR | APT 700 | FDD | 国内 700M 广电/移动共建 |
| n38 | NR | 2600 MHz | 2600M NR TDD | IMT-E TDD | TDD | 与 n41 重叠 |
| n40 | NR | 2300 MHz | 2300M NR TDD | S-Band | TDD | — |
| n41 | NR | 2500 MHz（2.6G） | 2.6G NR / 2600M | BRS, n41 | TDD | **与 LTE Band 41 完全同频 2496–2690**，DSS 场景常见；不可只凭频率区分制式 |
| n77 | NR | 3700 MHz | 3.7G C-Band | C-Band | TDD | 3300–4200，**包含 n78** |
| n78 | NR | 3500 MHz | 3.5G / 3500M | C-Band 3.5 GHz | TDD | 国内 5G 主力；3300–3800 与 n77 重叠 |
| n79 | NR | 4500 MHz | 4.9G | C-Band 4.9 GHz | TDD | 4400–5000 |

---

## §4 冲突、表号错配与实现陷阱（不静默取舍）

| 编号 | 事项 | 冲突 / 差异 | 取舍与理由 |
| --- | --- | --- | --- |
| **C1** | **任务书表号错配（LTE）** | 任务书称「TS 36.101 Table 5.7.3-1 **"E-UTRA operating bands"**」。[S1] 实际标题为 **"E-UTRA channel numbers"**；"**E-UTRA operating bands**" 是 **Table 5.5-1**（[S2] 已取回原文标题）。 | 表 1 数值取 5.7.3-1，双工模式/频段上下界取 5.5-1，两份都标注。不按任务书错误标题去 5.7.3-1 里找「operating bands」。 |
| **C2** | **`NDL_low` vs `NOffs-DL` 术语差异** | 任务书用 `NDL_low`；规范用 `NOffs-DL` 与 `Range of NDL`。二者**在本表内数值恒等**（0/0、600/600、…、39650/39650），但概念不同：前者是「区间下界」，后者是「频率换算偏移」。 | 公式按任务书写法成立；文档与 Kotlin 字段**建议用规范术语 `noffsDl`**（见 §5），并在注释里写明「≡ Range of NDL 下界」。避免后人误以为存在两个独立量。 |
| **C3** | **NR 表号错配 + 栅格表分段归属** | ① 任务书把「Table 5.2-1」与「NR-ARFCN 栅格」绑定，实际栅格在 **Table 5.4.2.1-1**。② [S7] TS 38.101-1 只列前两段、`NREF` 上限 **2016666**；[S8] TS 38.101-2 只列第三段；[S6] TS 38.104 一张表列全三段、`NREF` 上限 **3279165**。 | 这不是数值冲突，是**规范视角差异**（FR1 UE / FR2 UE / 基站全量程）。表 2-A 取 [S6] 全三段；若常量表只服务 FR1 UE，可裁掉第三段但必须显式注释版本边界。 |
| **C4** | **FR2 起点 `24250.08` 的不连续跳变** | 直觉上三段应连续。实测：FR1 第二段末端 `NREF=2016666` → `3000 + 0.015×(2016666−600000) = 24249.99 MHz`；FR2 起点 `NREF=2016667` → `FREF = 24250.08 MHz`。**24250.00–24250.08 MHz 之间存在 80 kHz 无栅格点的空隙**，且 60 kHz 栅格并非 15 kHz 栅格的顺势延续。 | 必须按规范分段函数实现，**禁止写成单一连续公式**，禁止用 `FREF = 0.005 × NREF` 之类简化式跨段计算。 |
| **C5** | **FDD `NOffs-UL` 偏移不是常量** | 常见二手资料写「`NOffs-UL = NOffs-DL + 18000`」。该式对 Band 1–28 成立，但对 **Band 65+ 是 `+65536`**（[S1][S3] 已抽样验证）。 | 逐 Band 存 `NOffs-UL`，不推导、不硬编码。 |
| **C6** | **Band 41 标称频率 2500 vs 俗称 2600** | [S5] Wikipedia 标 `ƒ = 2500`、common name `BRS`；[S3] sqimway 命名 `TD 2600+`；任务书写「Band 41 (2600 TDD)」；而 [S5] 中 **2600 / IMT-E 是 Band 38**。 | 数值以规范为准：**Band 41 = 2496–2690 MHz**（[S2][S1]）；Band 38 = 2570–2620 MHz。UI 可用「2.6 GHz TDD」泛称，但**必须同时显示 `Band 41` / `n41`**，避免与 Band 38/n38 混淆。 |
| **C7** | **「700M / 800M / 2600M」俗称一对多** | 700 MHz 对应 LTE 12/13/14/17/28/29/44/67/68/85 等多个 Band；800 MHz 对应 6/19/20/26/27 等；n77 与 n78、n38 与 n41、n41 与 LTE Band 41 频率重叠。 | UI 显示「常用名 + Band 号」双字段；按频率反查 Band 时**可能返回多个候选**，不得静默取第一个。 |
| **C8** | **FDD 双工间隔可为负** | 直觉认为「上行频率总低于下行」。实测 **Band 13**（`FUL_low=777 > FDL_low=746`，−31 MHz）与 **Band 20**（`FUL_low=832 > FDL_low=791`，−41 MHz）为反向双工（[S1][S5]）。 | 禁止用 `FUL_low < FDL_low` 做数据校验断言或排序假设。 |
| **C9** | **TDD 无方向信息** | [S1] 中 TDD 频段 `NOffs-DL = NOffs-UL`、`Range of NDL = Range of NUL`；[S9] 中 TDD 的 UL/DL 区间完全相同。 | 查表结果不要标注「DL」或「UL」方向；TDD 段不能靠 EARFCN 判方向。 |
| **C10** | **[S1] OCR 脚注粘连** | `292`/`322`/`665`/`672`/`692`/`706`/`752`/`762`/`1067` 等是「Band 号 + 脚注编号」粘在一起（`665` = Band 65 + 脚注 5）。 | 目标 16 个 Band 均为干净行、未受影响；扩表时必须逐行与 [S3] 对拍，禁止整表复制。 |
| **C11** | **抓取通道差异（非数值冲突）** | `web_fetch` 全域 SSRF 拦截、内置浏览器未连接、ETSI PDF 1638 页超时、sharetechnote/5g-tools/techplayon 均失败；`read_page`（Firecrawl 云）成功。 | 数值以 A 级 [S1][S6][S7][S8][S9] 转录为准，D 级 [S3][S4][S5][S10] 三方对拍；**不宣称已逐页核对 3GPP 官网 PDF**，B/C 级仅留可核查链接。 |
| **C12** | **文档并行写覆盖（非数值冲突）** | 本文件存在两个版本，来源清单与结论口径不同（版本 B 称「CafeTele + TelecomCalculators 两个独立二手源逐条一致」）。 | 见 §0.4：数值一致故不取舍；数值依据改以 A+D 级为准，版本 B 来源降 E 级并标注未复现，版本 B 的代码迭代记录保留于 §4.1。 |

### 4.1 代码侧已修复缺陷记录（来自并行版本，属迭代历史，非来源冲突）

- **LTE Band 28 区间**：初版 `9260..9660 / 758.0` → 正确为 **`9210..9659`**（表 1、[S1]）。`9660` 是 **Band 29 (SDL) 的下行起点**（Band 29 `9660..9769`，无上行）。后果：漏掉 9210–9259 共 50 个合法 EARFCN，且把 9660 起的空洞误算成 758 MHz 的 Band 28 频率。已修复并补阴性对照单测（9210→758 MHz、9660→N/A）。
- **NR FDD 项口径**：初版 `NrBand.freqLow/freqHigh` 误用 UL 范围（n1=1920–1980），导致 DL 频率 2110–2170 MHz 查不到 n1。已改为 DL 范围（表 2-B）。**该「NR-ARFCN 换算出的 FREF 必为 DL 载波频率」是代码侧口径假设，不是本文核验结论**，见 §4 N4 与 §6 N4。

---

## §5 给 Kotlin 常量表的落地建议

> 对齐设计稿 `D:\01项目\ShiJiPro_Phase1\00_模块划分与数据字段对照表.md`：频段常量表落 `:core:model`（纯 Kotlin、零 Android 依赖），换算逻辑落 `:core:common`；无 `getBands()` 时用内置频段表换算并标「换算」，DL 频率按 `F_low + 0.1*(EARFCN−N_low)`。

### 5.1 字段名建议（与规范术语对齐）

```kotlin
// :core:model —— 只放不可变数据，不放换算逻辑
enum class Rat { LTE, NR, GSM, WCDMA, TDSCDMA }
enum class DuplexMode { FDD, TDD, SDL, SUL }   // 预留 SDL/SUL，勿只建 FDD/TDD

data class LteBand(
    val band: Int,                 // 1, 3, 41 ...
    val duplex: DuplexMode,
    val dlLowMhz: Double,          // FDL_low
    val noffsDl: Int,              // NOffs-DL ≡ Range of NDL 下界（见 C2）
    val dlArfcnHigh: Int,          // Range of NDL 上界
    val ulLowMhz: Double,          // FUL_low
    val noffsUl: Int,              // NOffs-UL，**逐 Band 存，禁止 +18000 推导**（C5）
    val ulArfcnHigh: Int,
    val hasUplink: Boolean = true, // SDL 频段置 false
    val hasDownlink: Boolean = true,
)

data class NrBand(
    val band: String,              // "n1", "n78" —— 用 String，勿用 Int 去掉 'n'
    val duplex: DuplexMode,
    val ulLowMhz: Double, val ulHighMhz: Double,
    val dlLowMhz: Double, val dlHighMhz: Double,
    val frequencyRange: Int,       // 1 / 2
)

data class NrRasterSegment(        // 表 2-A，分段函数，勿合并成一条公式（C4）
    val fMinMhz: Double, val fMaxMhz: Double,   // 半开区间 [fMin, fMax)
    val deltaFGlobalKhz: Int,      // 5 / 15 / 60 —— **kHz**，换算时 /1000
    val fRefOffsMhz: Double,       // 0 / 3000 / 24250.08
    val nRefOffs: Int,             // 0 / 600000 / 2016667
    val nRefMin: Int, val nRefMax: Int,
)
```

- 频率统一用 `Double` **MHz**；`ΔFGlobal` 单独保留 **kHz** 并命名带单位后缀，防止量纲误用。
- 常量集中在一个 `object LteBands` / `object NrBands` 内，`val` + 不可变集合；单测按 §1.3 的回算公式逐 Band 断言自洽。
- 若维持当前最小充分集设计（`LteBand` 只存 `earfcnLow/earfcnHigh/dlFreqLowMhz`），须在 KDoc 明确「本表只覆盖下行 DL EARFCN，上行 UL 区间未收录」；扩到上行换算时按 §5.1 补 3 列。

### 5.2 奇异值 / 边界处理（硬性）

1. **EARFCN 超出表范围必须返回 `null`，不得猜 Band**：
   - `< 0` 或 `> 262143`（规范 EARFCN 全域 [S1]）→ `null`；
   - 落在**已知区间之间的空洞**（如 DL 段 10360–35999、UL 段 27810–65535，以及 **9660 起 Band 29 之前的空洞**）→ `null`；
   - **禁止**「就近匹配」「取频率最接近的 Band」「线性外推到相邻 Band」。
   - 返回类型建议 `fun lookup(earfcn: Int): BandMatch?`，`null` 语义写进 KDoc；UI 收到 `null` 显示 `N/A`，不显示推测值。
2. **平台奇异值先拦在查表之前**：`CellInfo.UNAVAILABLE` / `Int.MAX_VALUE` / 负数一律视为「无值」，直接走 `N/A`，不进查表函数。
3. **重叠频段按候选返回**：按频率反查（如 3400 MHz 命中 n77 与 n78，见 C7）应返回**候选列表**并标注歧义，禁止静默取第一个；按 EARFCN 查表在 LTE 内是唯一的（DL/UL 区间不重叠），但 TDD 段不能判方向（C9）。
4. **FDD 双工间隔可为负**：不要用 `FUL_low < FDL_low` 做校验断言（C8）；Band 13/20 必须能通过单测。
5. **NR-ARFCN 分段边界**：`NREF` 落在 `0…3279165` 之外 → `null`；三段是半开区间，`3000` / `24250` 处按规范分段取参数；`24250.00–24250.08 MHz` 无栅格点，反推 `NREF` 时须显式处理该空隙（C4），不得四舍五入吞掉。
6. **单位陷阱**：`FREF_MHz = FREF_Offs_MHz + ΔFGlobal_kHz/1000.0 × (NREF − NREF_Offs)`；写成 `+ ΔFGlobal_kHz × (...)` 会放大 1000 倍。单测至少覆盖 `NREF=0 / 600000 / 2016667 / 3279165` 四个边界。
7. **规范 NOTE 1 的边缘不可用信道号**（依带宽低端 7/15/25/50/75/100、高端 6/14/24/49/74/99）只影响「载波有效性」，**不影响频段归属**；不要因此把 EARFCN 判成 `null`（§1.4）。
8. **Android 侧语义另核**：`CellIdentityLte.getEarfcn()` 返回的是 DL 还是 UL EARFCN、`CellIdentityNr.getNrarfcn()` 的取值范围与方向语义，属于平台 API 语义，**不在本文 3GPP 核验范围**，落地时由 lead 结合 Android API 文档确认，本文不下结论。
9. **单例校验**：LTE 各 Band 区间不得重叠；NR 区间允许重叠（已按多候选处理）。未覆盖频段（如 B42/B66/n90/FR2 mmWave）明确返回 `N/A` 并在「已知无法获取字段清单」列出，不伪装成完整表。

### 5.3 「换算值」而非「实测值」的要求（硬性）

- 设计稿已定义 `FieldValue<T>` / `Origin` 三态与「换算」蓝色小标：凡由 `EARFCN` / `NR-ARFCN` 查表得到的 **Band、DL/UL 频率、双工模式、常用频段名**，一律标记 `Origin.DERIVED`（换算），**禁止**标记为实测。
- UI 展示必须同时给出：① 值；② 「换算值」标识（区别于 `实测` 的 `Origin.MEASURED`）；③ 所用公式与规范版本，例如 `Band 3 · 1842.5 MHz（换算：TS 36.101 Tab 5.7.3-1）`。
- 优先使用系统 `getBands()`（API 30+）返回值作为**实测**；只有取不到时才回退到内置表换算，并保留「换算」标识，不要覆盖/伪装成实测（设计稿 §4.2 第 176、181 行）。
- `timingAdvanceToDistanceMeters` 属**估算值**（`FieldValue.estimated`），UI 必须附「估算值，仅供参考」，与换算值区分。
- 换算结果随规范版本演进可能变化：常量表应带**规范版本号字段**（如 `specVersion = "TS 36.101 Rel-19 / TS 38.101-1 Rel-19"`），便于后续升级与审计，避免「数值看起来对但说不清出处」。
- CSV 导出 / 路测点中若写入 Band 与频率，须保留一列 `origin`，否则后续分析会把换算值误当实测样本。

### 5.4 附带：WiFi 信道换算（非 3GPP，IEEE 802.11）

- 2.4 GHz `ch = (freq − 2407)/5`（2412–2472），`2484 MHz → ch14` 特例。
- 5 GHz `ch = (freq − 5000)/5`（5160–5895）。
- 6 GHz `ch = (freq − 5950)/5`（5955–7115）。
- 范围外一律 `N/A`，不猜测。
- 此段来自并行版本，**不在本文 3GPP 核验范围内**，未做独立对拍。

---

## §6 与 `BandTable.kt` 落地值对拍结论（2026-11-12，应答 lead）

> 本节为**只读核验**：读取 `core/model/.../BandTable.kt` 与 `core/common/.../FrequencyConverter.kt`，**未修改任何代码文件**。

### 6.1 数值结论：**全部正确，无需改任何常量**

| 抽查项 | 落地值 | 规范值 | 判定 | 来源 |
| --- | --- | --- | --- | --- |
| LTE Band 3 | `1200..1949` / `1805.0` | `1200–1949` / `FDL_low=1805` | ✅ 一致 | TS 36.101 Tab 5.7.3-1 [S1] |
| LTE Band 1 | `0..599` / `2110.0` | `0–599` / `FDL_low=2110` | ✅ 一致 | 同上 |
| LTE Band 40 | `38650..39649` / `2300.0` | `38650–39649` / `FDL_low=2300` | ✅ 一致 | 同上 |
| LTE Band 41 | `39650..41589` / `2496.0` | `39650–41589` / `FDL_low=2496` | ✅ 一致 | 同上 |

**全量扩展核对**：`LTE_BANDS` 全部 16 行（1/2/3/4/5/7/8/12/13/17/20/28/38/39/40/41）的 `earfcnLow` / `earfcnHigh` / `dlFreqLowMhz` / `duplex` **16/16 与 [S1] 逐行一致**，且 16 个 EARFCN 区间**互不重叠**（FDD 12 段与 TDD 4 段之间亦无交集）⇒ `lteBandForEarfcn()` 用 `firstOrNull` 不产生歧义，安全。
`NR_BANDS` 全部 13 行（n1/3/5/7/8/20/28/38/40/41/77/78/79）的 `freqLowMhz` / `freqHighMhz` / `duplex` **13/13 与 [S9] 一致**（FDD 行取 DL 范围，见 6.4 口径说明）。

### 6.2 NR-ARFCN 栅格三档：`FrequencyConverter.nrArfcnToFrequency()` 全部正确

| 档 | 落地实现 | 规范 Table 5.4.2.1-1 | 判定 | 来源 |
| --- | --- | --- | --- | --- |
| 第 1 档 | `NREF ≤ 599999` → `0.005 × NREF` | 0–3000 MHz，ΔF=5 kHz，`FREF_Offs=0`，`NREF_Offs=0`，`NREF` 0–599999 | ✅ | TS 38.104 / 38.101-1 Tab 5.4.2.1-1 [S6][S7] |
| 第 2 档 | `600000..2016666` → `3000 + 0.015 × (NREF−600000)` | 3000–24250 MHz，ΔF=15 kHz，`FREF_Offs=3000`，`NREF_Offs=600000`，`NREF` 600000–2016666 | ✅ | 同上 |
| 第 3 档 | `else` → `24250.08 + 0.06 × (NREF−2016667)` | 24250–100000 MHz，ΔF=60 kHz，`FREF_Offs=24250.08`，`NREF_Offs=2016667`，`NREF` 2016667–3279165 | ✅ | TS 38.104 / 38.101-2 Tab 5.4.2.1-1 [S6][S8] |
| 上界守卫 | `nrArfcn > 3279165` → `unavailable` | 全局 `NREF` 上限 3279165（TS 38.104 口径） | ✅ | [S6] |

边界 `600000` / `2016666` / `2016667` 与 `ΔF=5/15/60 kHz`、`FREF_Offs=0/3000/24250.08` 全部对拍通过。`24250.08` 造成的 **80 kHz 空隙**（C4）已由分段 `when` 正确表达，未写成单一连续公式。

### 6.3 n77 / n78 重叠：**确认「频率→Band」不唯一**，主路径处理正确

- 规范值：**n77 = 3300–4200 MHz**、**n78 = 3300–3800 MHz**（[S9] TS 38.101-1 Tab 5.2-1；[S10] 明确标 n78 为 n77 的 subset）。
- 重叠区 **3300–3800 MHz** 内任一频率同时命中 n77 与 n78；此外 n38（2570–2620）⊂ n41（2496–2690），2570–2620 同样不唯一。
- ✅ `FrequencyConverter.nrBandsForFrequency()` 用 `filter` 返回**全部候选**并在多候选时标注「非唯一」，与 §5.2 第 3 条一致，正确。
- ⚠️ `BandTable.nrBandForFrequency()`（`BandTable.kt:88-89`）仍是 `NR_BANDS.firstOrNull { it.contains(freq) }`，且 n77 声明在 n78 **之前**（第 80 行 vs 第 81 行）⇒ 对 3300–3800 MHz 会**静默返回 n77**。当前主路径（converter）未调用它，但该函数是 public，存在被误用风险。建议废弃、改为返回 `List<NrBand>`，或在 KDoc 明确「仅供单候选场景，重叠频段请用 `nrBandsForFrequency`」。**这是唯一需要动代码的行为性风险，但不属于数值错误。**

### 6.4 注释 / 字符串层面的 5 处纠正（均为事实标注，不改任何常量值）

| # | 位置 | 现状 | 问题 | 建议 |
| --- | --- | --- | --- | --- |
| N1 | `BandTable.kt:6` | `3GPP TS 36.101 Table 5.7.3-1（E-UTRA operating bands）` | 表号与表标题错配（§4 C1）。**Table 5.7.3-1 标题是 "E-UTRA channel numbers"**（[S1]）；"**E-UTRA operating bands**" 是 **Table 5.5-1**（[S2] 原文标题已核实）。任务书原文即误写，落地注释沿用了该错误。 | 改为 `TS 36.101 Table 5.7.3-1（E-UTRA channel numbers）/ Table 5.5-1（operating bands）` |
| N2 | `BandTable.kt:67` | `独立核验：docs/P1_3GPP_band_reference.md（来源 TS 38.104 V20.0.0 / CafeTele 全表）` | **来源标注与核验文档不符**。核验文档 §0.2 的 NR 频段来源是 **TS 38.101-1 §5.2 Table 5.2-1（Rel-19，[S9]）+ Wikipedia [S10] 对拍**；栅格来源是 TS 38.104/38.101-1/38.101-2 §5.4.2.1-1（[S6][S7][S8]）。文档**没有** "TS 38.104 V20.0.0 / CafeTele" 这一数值依据（CafeTele 已降 E 级并标注未复现）。 | 改为与 §0.2 一致的表号与 URL，保证审计链可回溯 |
| N3 | `BandTable.kt:64` | `NR FR1 频段…（3GPP TS 38.104 Table 5.2-1）` | 表号可接受（38.104 §5.2 亦有 operating bands 表），但**栅格参数在 Table 5.4.2.1-1，不是 5.2-1**（§4 C3）。两表混用易误导后续维护者。 | 频段列表标 `TS 38.101-1/38.104 Table 5.2-1`；栅格公式标 `Table 5.4.2.1-1` |
| N4 | `BandTable.kt:64-67` | `因为 NR-ARFCN 换算得到的 FREF 是下行载波频率，查表口径必须一致` | **该口径假设不是本文的规范核验结论**。FREF 是全局栅格上的 RF 参考频率，可用于 DL 也可用于 UL；Android `CellIdentityNr.getNrarfcn()` 返回的方向语义本文列为未决（§7）。若平台给出 UL ARFCN，FDD 频段用单一 DL 范围查表会漏判（如 n1 UL 1920–1980 不落在 2110–2170）。 | 注释降级为「**本表口径假设**：按 DL 范围建表，待 Android API 方向语义确认」，并补 UL 范围单测或 fallback |
| N5 | `BandTable.kt:57,60,79,82` | Band 38 与 Band 41/n41 均标 `"2600 TDD"`；n79 标 `"4700"` | 显示层口径混用（§4 C6/C7）：规范中 **2600 / IMT-E 指 Band 38（2570–2620）**，**Band 41 标称 ƒ=2500、范围 2496–2690**；n79 规范标称 **ƒ=4500**（[S10]），`"4700"` 是 `(4400+5000)/2` 的算术中心而非标称。两个 Band 共用「2600 TDD」会让 UI 无法区分。 | UI 必须同时显示 Band 号；commonName 建议 Band 41 → `"2.6G TDD / 2500"`，n79 标称统一为 4500 或显式注明「中心频率」 |

> **总结给 lead**：① 四个抽查值 + 全量 16 个 LTE / 13 个 NR 数值**全部正确**；② 栅格三档边界、ΔF、`FREF_Offs`、`3279165` 上界**全部正确**；③ n77/n78 在 3300–3800 重叠确凿，主路径 `nrBandsForFrequency` 的全候选处理正确，但 `BandTable.nrBandForFrequency` 的 `firstOrNull` 会静默取 n77，建议处理；④ 需修的是 **5 处注释/字符串标注**（表号、来源、口径假设、俗称），**不涉及任何数值常量**。

---

## §7 核验边界与未决项

- ✅ 已核验：16 个 LTE Band（1/2/3/4/5/7/8/12/13/17/20/28/38/39/40/41）的 8 列数值 + 双工模式；13 个 NR FR1 频段上下界；NR-ARFCN 三段栅格参数；常用名对照；两处表号错配；12 类冲突/陷阱；`BandTable.kt` 全量对拍。
- ✅ 数值自洽校验 16/16 通过（§1.3），并与 [S3][S4][S5][S10] 交叉对拍一致。
- ❌ 未取回：3GPP 官网 PDF（SSRF 拦截）、ETSI 官方 PDF 逐页正文（1638 页超时）、sharetechnote / 5g-tools / techplayon 目标页、CafeTele / TelecomCalculators（E 级来源未复现）。故**不宣称「已与 3GPP 官网 PDF 逐字对拍」**，只宣称「规范逐节转录 + 三方二手对拍一致」。
- ⏳ 未决（交 lead）：Android `getEarfcn()` / `getNrarfcn()` 的平台侧 DL/UL 语义；是否纳入 SDL/SUL 与 FR2 频段；`getBands()` 与内置表冲突时的优先级策略（设计稿倾向「实测优先」）；`BandTable.nrBandForFrequency()` 的 `firstOrNull` 歧义处理。
- 📌 扩表提示：Band 65+ 的 `NOffs-UL` 偏移规则变化（C5）、[S1] 脚注粘连（C10）、频段重叠一对多（C7）——扩表前必须重跑对拍。

---

## §8 代码侧修复记录追加区

> 供 lead 追加代码侧迭代记录（如后续常量修正、口径变更）。**追加，不要整体覆写本文件**；追加时请注明日期与改动文件。

- 2026-11-12（并行版本记录，已整合进 §4.1）：LTE Band 28 区间由 `9260..9660` 修正为 `9210..9659`，补阴性对照单测；NR FDD 项由 UL 范围改为 DL 范围。
- 2026-11-12（lead 依核验回报收尾，改动 `core/model/BandTable.kt`、`core/common/FrequencyConverter.kt`、`FrequencyConverterTest.kt`）：① 删除 `nrBandForFrequency()`（`firstOrNull` 在 n77/n78 重叠区会静默返回 n77），改为 `nrBandsForFrequency(): List<NrBand>` 全候选；主路径 converter 改调该 API 并补回归单测断言 3500MHz 同时命中 n77 与 n78。② 软化「FREF 必为 DL 载波频率」表述：明确为**本工程取数口径假设、非规范结论**，Android `getNrarfcn()` 方向语义未决，若机型上报 UL ARFCN 则 FDD 项降级 N/A（不伪造），由 P3 采集层二次判定。③ 纠正表号：`Table 5.7.3-1` = E-UTRA channel numbers、`Table 5.5-1` = operating bands、NR 栅格在 `Table 5.4.2.1-1`。④ 展示名修正：LTE B41 `2600 TDD`→`2500 TDD`、NR n79 `4700`→`4500`（仅显示文本，不改频率数值）。⑤ 数值 0 改动：核验确认 16 LTE + 13 NR + 栅格三档全部对拍通过。

---

**本文档为 P1 频段常量表的唯一数值依据；任何代码改动不得引用本文之外的「凭记忆」数值。**
