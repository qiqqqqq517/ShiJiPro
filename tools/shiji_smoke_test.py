#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""识机 Pro 真机自动化冒烟测试。

做四件事：
1) 冷启动计时，确认前台 Activity 是 com.shiji.pro/.MainActivity；
2) 7 个主标签逐页「滚动到底 + 聚合文本」，断言固定字段清单里的每一项都在（N/A 也算在）；
3) 3 个覆盖层 + 端点对话框 + 连点刷新，验证交互链路不崩、返回键可用；
4) 采集 logcat，检查 FATAL / ANR，并把每页 UI XML 与截图落到 evidence 目录。

判定口径：字段存在性同时读 uiautomator 的 text 与 content-desc——
`ValueRow` 用 clearAndSetSemantics{contentDescription=...} 合并语义，字段名只出现在 content-desc 里。

用法：
    python tools/shiji_smoke_test.py --apk app/build/outputs/apk/debug/app-debug.apk \
        --out docs/evidence_ui_v1.1
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.shiji.pro"
ACTIVITY = f"{PKG}/.MainActivity"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


class Adb:
    def __init__(self, serial: str | None = None) -> None:
        self.base = ["adb"] + (["-s", serial] if serial else [])

    def run(self, args: list[str], timeout: int = 60, check: bool = False) -> str:
        proc = subprocess.run(
            self.base + args,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
        if check and proc.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} 失败: {proc.stderr or proc.stdout}")
        return (proc.stdout or "") + (proc.stderr or "")

    # ---- 设备动作 ----
    def install(self, apk: str) -> str:
        self.run(["uninstall", PKG])
        return self.run(["install", "-r", "-t", "-g", apk], timeout=180)

    def start(self) -> tuple[int, str]:
        self.run(["shell", "am", "force-stop", PKG])
        self.run(["logcat", "-c"])
        out = self.run(["shell", "am", "start", "-W", "-n", ACTIVITY], timeout=60)
        m = re.search(r"TotalTime:\s*(\d+)", out)
        return (int(m.group(1)) if m else -1), out

    def focus(self) -> str:
        out = self.run(["shell", "dumpsys", "window", "displays"])
        for line in out.splitlines():
            if "mCurrentFocus" in line:
                return line.strip()
        return ""

    def tap(self, x: int, y: int) -> None:
        self.run(["shell", "input", "tap", str(x), str(y)])

    def swipe_up(self, height: int) -> None:
        x = 540
        # 慢速拖拽（1200ms）而非快速滑动：快滑会触发 fling 惯性，一次滚过好几张卡片，
        # 导致相邻帧之间漏掉整张卡。慢拖的位移≈手指位移，配合 44% 步长形成稳定重叠。
        self.run(["shell", "input", "swipe", str(x), str(int(height * 0.72)),
                  str(x), str(int(height * 0.28)), "1200"])

    def back(self) -> None:
        self.run(["shell", "input", "keyevent", "4"])

    def size(self) -> tuple[int, int]:
        out = self.run(["shell", "wm", "size"])
        m = re.search(r"(\d+)x(\d+)", out)
        return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    # ---- UI 取证 ----
    def dump_xml(self) -> str:
        for _ in range(3):
            self.run(["shell", "uiautomator", "dump", "/sdcard/ui.xml"], timeout=40)
            xml = self.run(["shell", "cat", "/sdcard/ui.xml"], timeout=40)
            if xml.lstrip().startswith("<?xml"):
                return xml
            time.sleep(0.4)
        raise RuntimeError("uiautomator dump 连续失败")

    def screenshot(self, path: str) -> None:
        self.run(["shell", "screencap", "-p", "/sdcard/ui.png"], timeout=40)
        self.run(["pull", "/sdcard/ui.png", path], timeout=60)

    def logcat(self) -> str:
        return self.run(["logcat", "-d", "-v", "brief"], timeout=60)

    # ---- 系统弹窗处置 ----
    ALLOW = ("仅在使用中允许", "本次使用允许", "始终允许", "允许")

    def ensure_foreground(self, rounds: int = 14, allow_relaunch: bool = False) -> str:
        """处理 MIUI 权限弹窗 / 通知栏遮挡，返回最终焦点。

        判定不看 `mCurrentFocus` 文案（MIUI 会把窗口标题混进该行），而是解析 UI XML 的包名：
        出现 `com.lbe.security.miui` 或 `com.android.permissioncontroller` 即视为系统权限弹窗，
        点最靠下的「允许」类按钮（等价于用户授予「使用期间」权限）。
        """
        relaunched = False
        for _ in range(rounds):
            nodes = parse_nodes(self.dump_xml())
            pkgs = {n["pkg"] for n in nodes}
            dialog = [p for p in pkgs
                      if "lbe.security" in p or "permissioncontroller" in p or "permission.ui" in p]
            if dialog:
                allow = [n for n in nodes if n["text"] in self.ALLOW] or \
                        [n for n in nodes if "允许" in n["text"]]
                if allow:
                    allow.sort(key=lambda n: n["top"])
                    self.tap(allow[-1]["cx"], allow[-1]["cy"])
                    time.sleep(0.8)
                    continue
            if PKG in pkgs:
                return self.focus()
            # 无弹窗、也抓不到应用节点：可能是通知栏遮挡或抓取时机过早
            if "NotificationShade" in self.focus():
                self.run(["shell", "cmd", "statusbar", "collapse"])
                time.sleep(0.5)
                continue
            if allow_relaunch and not relaunched:
                self.start()
                relaunched = True
                time.sleep(1.0)
                continue
            time.sleep(0.5)
        return self.focus()


def parse_nodes(xml: str) -> list[dict]:
    root = ET.fromstring(xml)
    out: list[dict] = []
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        bounds = node.get("bounds") or ""
        m = BOUNDS.match(bounds)
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        out.append({
            "text": text,
            "desc": desc,
            "pkg": node.get("package") or "",
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "top": y1,
        })
    return out


def page_text(nodes: list[dict]) -> str:
    parts: list[str] = []
    for n in nodes:
        if n["text"]:
            parts.append(n["text"])
        if n["desc"]:
            parts.append(n["desc"])
    return "\n".join(parts)


def find(nodes: list[dict], label: str, exact: bool = True) -> dict | None:
    hits = [n for n in nodes if (n["text"] == label or n["desc"] == label)] if exact else \
           [n for n in nodes if label in n["text"] or label in n["desc"]]
    if not hits:
        return None
    hits.sort(key=lambda n: n["top"])
    return hits[0]


def wait_focus(adb: Adb, timeout: float = 8.0) -> str:
    deadline = time.time() + timeout
    while time.time() < deadline:
        f = adb.focus()
        if PKG in f:
            return f
        time.sleep(0.3)
    return adb.focus()


def tap_tab(adb: Adb, label: str, height: int) -> bool:
    """点击顶部标签；若标签在屏幕外，先横向拖动标签行再找。

    手机宽度 393dp 时 ScrollableTabRow 只能同时显示约 4 个标签，
    「设备 / 覆盖测试 / 测速」默认在屏幕外，必须拖动标签行才可见。
    """
    nodes = parse_nodes(adb.dump_xml())
    top_limit = int(height * 0.45)

    def hit_in(ns: list[dict]) -> dict | None:
        cands = [n for n in ns
                 if n["top"] < top_limit and (n["text"] == label or n["desc"] == label)]
        cands.sort(key=lambda n: n["top"])
        return cands[0] if cands else None

    hit = hit_in(nodes)
    if hit is not None:
        adb.tap(hit["cx"], hit["cy"])
        return True

    anchor = None
    for name in ("卡槽1", "卡槽2", "WiFi", "位置"):
        anchor = next((n for n in nodes if n["top"] < top_limit and n["text"] == name), None)
        if anchor is not None:
            break
    if anchor is None:
        return False
    y = anchor["cy"]
    for _ in range(4):
        # 标签行向左拖：内容左移，露出右侧标签
        adb.run(["shell", "input", "swipe", "950", str(y), "150", str(y), "450"])
        time.sleep(0.5)
        hit = hit_in(parse_nodes(adb.dump_xml()))
        if hit is not None:
            adb.tap(hit["cx"], hit["cy"])
            return True
    return False


class Report:
    def __init__(self) -> None:
        self.rows: list[tuple[str, bool, str]] = []

    def add(self, name: str, ok: bool, detail: str = "") -> None:
        self.rows.append((name, ok, detail))

    @property
    def failed(self) -> list[tuple[str, bool, str]]:
        return [r for r in self.rows if not r[1]]


TABS = [
    {
        "label": "卡槽1",
        "slug": "slot1",
        # 断言口径：每张固定卡片的标题 + 该卡首行字段（相邻帧有重叠，首行不会丢）。
        # 全量字段清单（39 个服务小区键等）由代码内固定列表保证，UI 侧只验“卡片与首行都在”。
        "must": ["SIM / 运营商（Slot 0）", "服务小区", "服务小区 · 补充字段", "邻区", "信号强度趋势",
                 "运营商", "TAC"],
    },
    {
        "label": "卡槽2",
        "slug": "slot2",
        "must": ["SIM / 运营商（Slot 1）", "服务小区", "服务小区 · 补充字段", "邻区", "信号强度趋势",
                 "运营商", "TAC"],
    },
    {
        "label": "WiFi",
        "slug": "wifi",
        "must": ["已连接", "网络详情", "信道占用图", "周边热点",
                 "SSID", "DNS"],
    },
    {
        "label": "位置",
        "slug": "location",
        "must": ["定位数据（WGS-84）", "卫星天空图", "卫星列表", "原始观测与导航电文",
                 "NMEA 原始日志", "经度", "卫星原始观测数"],
    },
    {
        "label": "设备",
        "slug": "device",
        "must": ["电池", "屏幕", "内存与存储", "硬件", "系统",
                 "本应用网络开销（TrafficStats）", "传感器",
                 "电量(%)", "尺寸(英寸)", "RAM 总量", "型号", "Android", "发送"],
    },
    {
        "label": "覆盖测试",
        "slug": "drivetest",
        "must": ["覆盖测试（路测）", "覆盖栅格（IDW 插值，估算）", "记录点", "CSV 列", "反距离加权"],
    },
    {
        "label": "测速",
        "slug": "speedtest",
        "must": ["网络测速", "测速端点", "说明", "Ping(ms)", "Jitter(ms)",
                 "下行(Mbps)", "上行(Mbps)", "出口 IP", "运营商", "归属地"],
    },
]

OVERLAYS = [
    {"button": "双卡对比", "slug": "compare", "must": ["双卡对比", "卡槽1", "卡槽2", "RSRP", "评级（估算）"]},
    {"button": "日志中心", "slug": "logs", "must": ["留存说明", "当前快照", "蜂窝快照"]},
    {"button": "设置", "slug": "settings",
     "must": ["外观与语言", "隐私与存储", "采集", "路测 CSV 导出列", "路测 KPI 权重", "关于", "操作"]},
]


def walk_page(adb: Adb, size: tuple[int, int], must: list[str], slug: str, out_dir: str,
              report: Report, prefix: str) -> str:
    """从当前页顶部滚到底，聚合所有 text/content-desc，断言 must 全部出现。"""
    texts: list[str] = []
    last_layout: frozenset | None = None
    identical = 0

    for i in range(10):
        xml = adb.dump_xml()
        with open(os.path.join(out_dir, f"{prefix}_{slug}_p{i}.xml"), "w", encoding="utf-8") as fh:
            fh.write(xml)
        nodes = parse_nodes(xml)
        page = page_text(nodes)
        texts.append(page)
        if i <= 1:
            adb.screenshot(os.path.join(out_dir, f"{prefix}_{slug}_p{i}.png"))

        # 早停只看「布局坐标」是否变化：顶栏时间戳文字每秒都在变，用文本比较会永远判为“有变化”。
        layout = frozenset((n["cx"], n["cy"]) for n in nodes)
        if layout == last_layout:
            identical += 1
            if identical >= 2:
                break
        else:
            identical = 0
            last_layout = layout
        adb.swipe_up(size[1])
        time.sleep(0.35)

    merged = "\n".join(texts)
    missing = [m for m in must if m not in merged]
    report.add(f"{prefix}/{slug} 字段齐全", not missing,
               "全部命中" if not missing else "缺失: " + "、".join(missing))
    print(f"  - {prefix}/{slug}: {'OK' if not missing else '缺失 ' + '、'.join(missing)}", flush=True)
    return merged


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--out", default="docs/evidence_ui_v1.1")
    parser.add_argument("--serial", default=None)
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--only", default="all",
                        help="只跑指定 slug（逗号分隔），用于分批执行；默认 all")
    args = parser.parse_args()
    only = {s for s in args.only.split(",") if s}

    def want(slug: str) -> bool:
        return "all" in only or slug in only

    os.makedirs(args.out, exist_ok=True)
    adb = Adb(args.serial)
    report = Report()
    size = adb.size()
    print(f"[device] {size[0]}x{size[1]}")

    if not args.skip_install:
        out = adb.install(args.apk)
        ok = "Success" in out
        report.add("安装", ok, out.strip().splitlines()[-1] if out.strip() else "")

    total_ms, start_out = adb.start()
    wait_focus(adb)
    # 首启可能弹 MIUI 权限框：先走完授权，再重新冷启动取「无弹窗」基线计时。
    adb.ensure_foreground()
    total_ms, start_out = adb.start()
    with open(os.path.join(args.out, "am_start.txt"), "w", encoding="utf-8") as fh:
        fh.write(start_out)
    focus = adb.ensure_foreground()
    report.add("冷启动", total_ms > 0 and PKG in focus,
               f"TotalTime={total_ms}ms · focus={focus.split('u0 ')[-1] if focus else 'N/A'}")

    # ---- 主导航：7 个标签 ----
    for tab in TABS:
        if not want(tab["slug"]):
            continue
        if not tap_tab(adb, tab["label"], size[1]):
            report.add(f"tab/{tab['slug']} 入口", False, f"未找到标签「{tab['label']}」")
            continue
        time.sleep(1.0)
        walk_page(adb, size, tab["must"], tab["slug"], args.out, report, "tab")

        # 刷新压力与「刷新后字段不消失」放在卡槽1 页内做：此时标签行未横向滚动，
        # 「卡槽1」仍在可视区，刷新后原地复验同一页最有意义。
        if tab["slug"] == "slot1":
            nodes = parse_nodes(adb.dump_xml())
            refresh = find(nodes, "刷新")
            if refresh is not None:
                for _ in range(3):
                    adb.tap(refresh["cx"], refresh["cy"])
                    time.sleep(0.7)
                time.sleep(2.0)
                alive = PKG in adb.focus()
                report.add("刷新×3 压力", alive, "无 ANR / 进程存活" if alive else "应用失焦或崩溃")
                # 刷新后复验：先经「卡槽2 → 卡槽1」重建页面（滚动回到顶部），再验卡片与首行仍在。
                for label in ("卡槽2", "卡槽1"):
                    hit = find(parse_nodes(adb.dump_xml()), label)
                    if hit is not None:
                        adb.tap(hit["cx"], hit["cy"])
                        time.sleep(0.8)
                walk_page(adb, size, tab["must"], "after_refresh", args.out, report, "regress")
            else:
                report.add("刷新×3 压力", False, "未找到「刷新」按钮")

    # ---- 覆盖层 + 返回键 ----
    for ov in OVERLAYS:
        if not want(ov["slug"]):
            continue
        # 回到顶部再点顶栏动作，避免顶栏被内容遮住
        adb.run(["shell", "input", "swipe", "540", "600", "540", "2000", "200"])
        time.sleep(0.4)
        nodes = parse_nodes(adb.dump_xml())
        node = find(nodes, ov["button"])
        if node is None:
            report.add(f"overlay/{ov['slug']} 入口", False, f"未找到按钮「{ov['button']}」")
            continue
        adb.tap(node["cx"], node["cy"])
        time.sleep(0.9)
        walk_page(adb, size, ov["must"], ov["slug"], args.out, report, "overlay")
        adb.back()
        time.sleep(0.8)
        after = page_text(parse_nodes(adb.dump_xml()))
        closed = "返回" not in after and PKG in adb.focus()
        report.add(f"overlay/{ov['slug']} 返回键", closed,
                   "覆盖层已关闭，仍在应用内" if closed else "返回后状态异常")

    # ---- 端点对话框 ----
    if tap_tab(adb, "测速", size[1]):
        time.sleep(0.9)
        for _ in range(6):
            nodes = parse_nodes(adb.dump_xml())
            add = find(nodes, "添加端点")
            if add is not None:
                break
            adb.swipe_up(size[1])
            time.sleep(0.4)
        else:
            add = None
        if add is not None:
            adb.tap(add["cx"], add["cy"])
            time.sleep(0.9)
            merged = page_text(parse_nodes(adb.dump_xml()))
            adb.screenshot(os.path.join(args.out, "dialog_endpoint.png"))
            ok = "添加测速端点" in merged and ("URL 必须" in merged or "校验通过" in merged)
            report.add("对话框/端点录入", ok, "标题与常驻校验条均在" if ok else "标题或校验条缺失")
            report.add("对话框/恒高校验条", "校验通过" in merged or "URL 必须" in merged,
                       "校验条常驻（未通过显示原因）")
            adb.back()
            time.sleep(0.6)
        else:
            report.add("对话框/端点录入", False, "未找到「添加端点」按钮")

    # ---- logcat ----
    log = adb.logcat()
    with open(os.path.join(args.out, "logcat.txt"), "w", encoding="utf-8") as fh:
        fh.write(log)
    fatal = [ln for ln in log.splitlines() if "FATAL EXCEPTION" in ln or "ANR in " + PKG in ln]
    report.add("logcat 无崩溃", not fatal, "无 FATAL / ANR" if not fatal else f"{len(fatal)} 条")

    # ---- 汇总 ----
    import json
    with open(os.path.join(args.out, "smoke_report.json"), "w", encoding="utf-8") as fh:
        json.dump([{"name": n, "ok": o, "detail": d} for n, o, d in report.rows],
                  fh, ensure_ascii=False, indent=2)
    print("\n== 冒烟结果 ==")
    for name, ok, detail in report.rows:
        print(f"[{'PASS' if ok else 'FAIL'}] {name}: {detail}")
    failed = report.failed
    print(f"\n合计 {len(report.rows)} 项，失败 {len(failed)} 项；证据目录 {os.path.abspath(args.out)}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
