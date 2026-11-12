"""P13 真机 UI 取证小工具（2026-11-12）。

用途：用 uiautomator dump 抓当前界面结构，按文本定位可点击节点并点击/滑动，
把每次 dump 落盘到 docs/evidence_p13/，供「装机复验」逐项对拍（不凭观感判缺陷）。

用法：
  python ui_probe.py dump <tag>                  # 抓一份并打印文本节点
  python ui_probe.py tap <tag> <text>            # 抓→按文本中心点点击→再抓同 tag_after
  python ui_probe.py swipe <tag> <x1> <y1> <x2> <y2>
  python ui_probe.py shot <tag>                  # 截图 png
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"D:\Android\SDK\platform-tools\adb.exe"
OUT = Path(r"D:\01项目\ShiJiPro\docs\evidence_p13")


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


def dump(tag: str) -> ET.Element:
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "uiautomator", "dump", "/sdcard/_p13.xml")
    xml = adb("exec-out", "cat", "/sdcard/_p13.xml")
    path = OUT / f"_{tag}.xml"
    path.write_text(xml, encoding="utf-8")
    return ET.fromstring(xml)


def bounds(node) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    return tuple(int(v) for v in m.groups())  # type: ignore[return-value]


def texts(root: ET.Element) -> list[tuple[str, tuple[int, int, int, int] | None, bool]]:
    out = []
    for n in root.iter("node"):
        t = n.get("text") or n.get("content-desc") or ""
        if t.strip():
            out.append((t.replace("\n", " / "), bounds(n), n.get("clickable") == "true"))
    return out


def find(root: ET.Element, needle: str):
    for t, b, clickable in texts(root):
        if needle in t:
            return t, b, clickable
    return None


def main() -> None:
    if len(sys.argv) < 3:
        print(__doc__)
        return
    cmd, tag = sys.argv[1], sys.argv[2]
    if cmd == "dump":
        root = dump(tag)
        for t, b, c in texts(root):
            print(f"{'CLICK ' if c else '      '}{b}  {t[:110]}")
    elif cmd == "tap":
        root = dump(f"{tag}_before")
        hit = find(root, sys.argv[3])
        if not hit or not hit[1]:
            print(f"NOT FOUND/NO BOUNDS: {sys.argv[3]}")
            return
        _, (x1, y1, x2, y2), _ = hit
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1.2)
        root2 = dump(f"{tag}_after")
        print(f"TAPPED: {hit[0][:60]}")
        for t, b, c in texts(root2):
            print(f"{'CLICK ' if c else '      '}{b}  {t[:110]}")
    elif cmd == "swipe":
        x1, y1, x2, y2 = (int(v) for v in sys.argv[3:7])
        adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), "300")
        time.sleep(0.8)
        dump(tag)
        print("SWIPED")
    elif cmd == "shot":
        OUT.mkdir(parents=True, exist_ok=True)
        png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout
        (OUT / f"_{tag}.png").write_bytes(png)
        print(f"SAVED {OUT / f'_{tag}.png'}")


if __name__ == "__main__":
    main()
