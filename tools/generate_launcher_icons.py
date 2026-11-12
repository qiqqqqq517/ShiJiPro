#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_launcher_icons.py —— 识机 (ShiJi) 启动图标 PNG 生成器

零外部素材、零版权图片：所有图形均由 Pillow 的 ImageDraw 手绘。
构图 = 信号塔（梯形塔身 + 天线）+ 3 条同心圆弧波纹 + 定位针（圆头 + 尖底）。

处理流程：
  1. 在 size * 4 的高分辨率画布上绘制（4x 超采样抗锯齿）；
  2. 叠加圆角方形 / 圆形遮罩；
  3. 用 Image.LANCZOS 缩放到目标边长 size。

输出（相对脚本自身位置）：
  ../app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ 下
  每档 3 个文件：
    ic_launcher.png        —— 圆角方形，主色背景
    ic_launcher_round.png  —— 圆形遮罩，主色背景
    ic_launcher_dark.png   —— 圆角方形，暗色背景 (#0B1220) + 亮青前景

脚本可重复执行：每次覆盖已有 PNG（Image.save 默认覆盖）。
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw

# 五档密度 -> 目标边长(px)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

SUPERSAMPLE = 4          # 4x 超采样抗锯齿
DESIGN_SPACE = 108.0     # 逻辑设计坐标系，与 VectorDrawable viewport 108dp 对齐
CENTER = 54.0

# 配色（全部字面 hex 转 RGBA，不依赖任何资源引用）
BG_MAIN = (0x0F, 0x76, 0x6E, 255)   # #0F766E teal-700，背景主色
BG_DARK = (0x0B, 0x12, 0x20, 255)   # #0B1220，暗色版背景
ACCENT = (0x14, 0xB8, 0xA6, 255)    # #14B8A6 teal-500，强调色
FG_LIGHT = (0xFF, 0xFF, 0xFF, 255)  # #FFFFFF，前景
FG_DARK = (0x5E, 0xEA, 0xD4, 255)   # #5EEAD4，暗色版前景

RIPPLE_RADII = (30.0, 24.0, 18.0)   # 3 条同心圆弧波纹半径
RIPPLE_ALPHAS = (0x33, 0x4D, 0x66)  # 半透明白（AARRGGBB 的 alpha 分量）
TEXTURE_RADII = (26.0, 36.0, 46.0)  # 极淡背景同心圆纹理
TEXTURE_ALPHA = 0x0A


def _unit(N):
    """设计坐标 -> 像素的缩放系数。"""
    return N / DESIGN_SPACE


def _i(value, u):
    return int(round(value * u))


def _box(cx, cy, r, u):
    """圆心(cx,cy)、半径 r 的外接矩形（设计坐标 -> 像素）。"""
    return (_i(cx - r, u), _i(cy - r, u), _i(cx + r, u), _i(cy + r, u))


def _pts(points, u):
    return [(_i(x, u), _i(y, u)) for x, y in points]


def _w(width, u):
    return max(1, int(round(width * u)))


def render_master(size, dark=False, shape="rounded"):
    """绘制并返回缩放后的 size x size RGBA 图标。"""
    N = size * SUPERSAMPLE
    u = _unit(N)
    bg = BG_DARK if dark else BG_MAIN
    fg = FG_DARK if dark else FG_LIGHT

    # 纯色背景
    base = Image.new("RGBA", (N, N), bg)

    # --- 极淡背景纹理 + 3 条同心圆弧波纹（半透明，单独图层 alpha 合成）---
    overlay = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for r in TEXTURE_RADII:
        od.arc(_box(CENTER, CENTER, r, u), start=0, end=360,
               fill=(255, 255, 255, TEXTURE_ALPHA), width=_w(1.2, u))
    for r, a in zip(RIPPLE_RADII, RIPPLE_ALPHAS):
        od.arc(_box(CENTER, CENTER, r, u), start=0, end=360,
               fill=(255, 255, 255, a), width=_w(1.6, u))
    base = Image.alpha_composite(base, overlay)

    # --- 信号塔：天线杆 + 顶球 + 梯形塔身 + 横撑 + 底座 + 强调色压条 ---
    d = ImageDraw.Draw(base)
    d.rectangle((_i(53, u), _i(33, u), _i(55, u), _i(46, u)), fill=fg)       # 天线杆
    d.ellipse(_box(CENTER, 29, 2.6, u), fill=fg)                             # 天线顶球
    d.polygon(_pts([(50, 46), (58, 46), (63, 62), (45, 62)], u), fill=fg)    # 梯形塔身
    for y in (53.0, 58.0):                                                     # 横撑
        d.line(_pts([(47.5, y), (60.5, y)], u), fill=fg, width=_w(1.4, u))
    d.rectangle((_i(41, u), _i(62, u), _i(67, u), _i(65, u)), fill=fg)       # 底座
    d.rectangle((_i(41, u), _i(65.4, u), _i(67, u), _i(67, u)), fill=ACCENT) # 强调压条

    # --- 交叉撑（半透明前景，叠在塔身之上）---
    cross = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cross)
    fg_t = (fg[0], fg[1], fg[2], 0x99)
    for a, b in (((50, 46), (63, 62)), ((58, 46), (45, 62))):
        cd.line(_pts([a, b], u), fill=fg_t, width=_w(1.0, u))
    base = Image.alpha_composite(base, cross)

    # --- 定位针：圆头 + 尖底，evenOdd 等效地挖空内圆 ---
    pin_mask = Image.new("L", (N, N), 0)
    pd = ImageDraw.Draw(pin_mask)
    pd.ellipse(_box(CENTER, 70, 6, u), fill=255)                              # 圆头
    pd.polygon(_pts([(48.5, 71), (54, 81), (59.5, 71)], u), fill=255)        # 尖底
    pd.ellipse(_box(CENTER, 70, 2.4, u), fill=0)                              # 挖空内圆
    base = Image.composite(Image.new("RGBA", (N, N), fg), base, pin_mask)

    # --- 遮罩：圆角方形 / 圆形 ---
    mask = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(mask)
    if shape == "circle":
        md.ellipse((0, 0, N - 1, N - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, N - 1, N - 1),
                             radius=int(round(N * 0.22)), fill=255)
    base.putalpha(mask)

    # --- LANCZOS 缩放到目标边长 ---
    return base.resize((size, size), Image.LANCZOS)


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    res_root = os.path.normpath(
        os.path.join(script_dir, "..", "app", "src", "main", "res")
    )

    specs = (
        ("ic_launcher.png", False, "rounded"),
        ("ic_launcher_round.png", False, "circle"),
        ("ic_launcher_dark.png", True, "rounded"),
    )

    total = 0
    for dens, size in DENSITIES.items():
        out_dir = os.path.join(res_root, "mipmap-" + dens)
        os.makedirs(out_dir, exist_ok=True)
        for filename, dark, shape in specs:
            icon = render_master(size, dark=dark, shape=shape)
            path = os.path.join(out_dir, filename)
            icon.save(path, "PNG")          # 覆盖已有文件，保证可重复执行
            total += 1
            print("[OK] {:>4} {:>3}x{:<3} -> {}".format(dens, size, size, path))

    print("Generated {} PNG files across {} densities.".format(total, len(DENSITIES)))


if __name__ == "__main__":
    main()
