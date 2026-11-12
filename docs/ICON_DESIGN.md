# 识机 (ShiJi) 应用图标与启动视觉资源设计说明

本文件说明 `app/src/main/res/` 下自制图标资源包的设计语义、配色、密度规格、
生成方式与验证结论。资源包为 P0 阶段独立交付，不依赖 `values/` 下的任何资源
（不引用 `@color/...` / `@string/...` / 主题属性），所有颜色均为 drawable 内字面 hex。

## 1. 设计语义

识机是 Android 网络诊断 / 网优工具，图标用一套统一的构图表达「信号测量 + 定位」：

- **信号塔**：梯形塔身 + 天线杆 + 顶球 + 横撑 / 交叉撑 + 底座，代表蜂窝基站与信号测量。
- **同心信号波纹**：3 条由内向外扩散的同心圆弧，代表信号覆盖、扫描与传播。
- **定位针**：圆头 + 尖底（内圆挖空），代表位置、GNSS 与站点定位。

三层叠加后收在 108dp viewport 的中心安全区（约直径 66dp）内，保证自适应图标
被各种启动器遮罩裁切时主体不被切掉。`about` / `legacy` / `splash` / `dark` 等变体
均复用同一构图，仅改画布、外圈、缩放与配色。

## 2. 配色（全部字面 hex）

| 用途 | Hex | 说明 |
| --- | --- | --- |
| 背景主色 | `#0F766E` | teal-700，自适应图标背景层 / 主色版 PNG 底 |
| 强调色 | `#14B8A6` | teal-500，底座压条 |
| 前景 | `#FFFFFF` | 白色，信号塔 / 波纹 / 定位针 |
| 波纹 | `#33FFFFFF` / `#4DFFFFFF` / `#66FFFFFF` | 半透明白，3 档 alpha |
| 背景纹理 | `#0AFFFFFF` | 极淡同心圆纹理 |
| 暗色版背景 | `#0B1220` | 深色 |
| 暗色版前景 | `#5EEAD4` | 亮青色 |

`launch_background.xml` 采用 `#0B1220` 纯色，供低版本启动屏窗口背景使用。

## 3. 密度与尺寸

5 档密度，每档 3 个 PNG，共 **15 个**：

| 密度 | 边长 | `ic_launcher.png` | `ic_launcher_round.png` | `ic_launcher_dark.png` |
| --- | --- | --- | --- | --- |
| mdpi | 48 px | 圆角方形·主色 | 圆形遮罩·主色 | 圆角方形·暗色 |
| hdpi | 72 px | 圆角方形·主色 | 圆形遮罩·主色 | 圆角方形·暗色 |
| xhdpi | 96 px | 圆角方形·主色 | 圆形遮罩·主色 | 圆角方形·暗色 |
| xxhdpi | 144 px | 圆角方形·主色 | 圆形遮罩·主色 | 圆角方形·暗色 |
| xxxhdpi | 192 px | 圆角方形·主色 | 圆形遮罩·主色 | 圆角方形·暗色 |

PNG 由 Pillow 在 `size × 4` 的画布上手绘后经 `Image.LANCZOS` 缩放得到，带 alpha
通道；圆角方形与圆形遮罩外的像素为透明。

## 4. 生成命令

解释器使用已装 Pillow 12.3.0 的 DSH 发行版 Python，脚本用绝对路径调用：

```
"C:\Users\30458\.dsh\dsh-runtimes\dsh-next-primary-runtime\dependencies\python\python.exe" "D:\01项目\ShiJiPro\tools\generate_launcher_icons.py"
```

脚本内的 `DENSITIES = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}`，
输出目录按脚本自身位置解析为 `../app/src/main/res/mipmap-*/`。每次执行覆盖已有 PNG，
可重复运行且二次运行产物字节一致。

## 5. 零版权素材声明

全部 PNG 与 VectorDrawable 均为本工程自制：图形由 `tools/generate_launcher_icons.py`
用 Pillow `ImageDraw` 的 `rectangle` / `polygon` / `ellipse` / `arc` / `line` /
`rounded_rectangle` 原语手绘，XML 由手写路径数据构成。**未使用任何外部图片、图标库、
字体、AI 生成图或第三方版权素材**，无外部依赖（仅 Pillow 用于离线导出 PNG）。

## 6. 自适应图标分层说明

`mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 均为 `<adaptive-icon>`，
三层的引用关系：

| 层 | 引用 | 作用 |
| --- | --- | --- |
| `<background>` | `@drawable/ic_shiji_background` | 108dp 满幅主色填充 + 极淡同心圆纹理 |
| `<foreground>` | `@drawable/ic_shiji_foreground` | 108dp，主体收在中心 66dp 安全区 |
| `<monochrome>` | `@drawable/ic_shiji_monochrome` | 与前景同构图的单色层，供 Android 13+ 主题图标 |

`<monochrome>` 元素带 `xmlns:tools="http://schemas.android.com/tools"` 与
`tools:targetApi="33"`，避免低版本编译告警；两层 `adaptive-icon` 内容一致，分别供
默认 / 圆形遮罩启动器使用。低版本（API < 26）回落到 5 档 `mipmap-*` PNG。

其余 drawable 用途：

- `ic_shiji_legacy.xml` — 传统方形启动图标 vector（48dp，含圆角背景 + 前景）。
- `ic_shiji_about.xml` — 设置页「关于」图标，同构图 + 白色外圈。
- `ic_shiji_about_dark.xml` — 暗色版关于图标，`#0B1220` 底 + `#5EEAD4` 前景。
- `ic_shiji_splash.xml` — 启动屏占位 vector（居中图标 + 扩散波纹）。
- `launch_background.xml` — 启动屏窗口背景纯色 drawable。

## 7. 验证结论（2026-11-12）

- **PNG**：15/15 存在，像素尺寸逐档核验（48/72/96/144/192 px）全部正确；逐像素
  审计确认背景主色 / 暗色底 / 白色或亮青前景 / 强调色均实际渲染，遮罩四角透明。
- **XML**：10/10 通过 Python `ElementTree` 解析；10/10 通过 `aapt2 compile`
  （build-tools 37.0.0）逐文件编译生成 `.flat`；`aapt2 link`（platform android-37.0）
  rc=0，`@mipmap/ic_launcher` / `@mipmap/ic_launcher_round` 引用解析成功。
- **脚本**：二次运行 rc=0，产物 SHA-256 与首次一致，可重复执行。

> 注：`aapt2 compile --dir` 在含中文的路径（`01项目`）下报「系统找不到指定的文件」，
> 改为逐文件 `aapt2 compile` 即全部通过；这与资源本身合法性无关。
