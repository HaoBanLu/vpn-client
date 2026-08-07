#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器安装 3.12 debug 包并验证回国节点连接（芜湖）。"""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = Path(r"C:\Users\luban\AppData\Local\Android\Sdk\platform-tools\adb.exe")
ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-x86_64-debug.apk"
PKG = "com.vpn.member"
EMAIL = "zc16@qq.com"
PASSWORD = "123456"
SHOT = ROOT / "scripts" / "_emulator_shots"
SHOT.mkdir(parents=True, exist_ok=True)
OUT = SHOT / "v312_connect_result.txt"


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB), *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        errors="replace",
    )


def log(msg: str) -> None:
    print(msg, flush=True)
    with OUT.open("a", encoding="utf-8") as f:
        f.write(msg + "\n")


def dump_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = SHOT / "_ui_live.xml"
    p = adb("pull", "/sdcard/ui.xml", str(local))
    if p.returncode != 0 or not local.is_file():
        raise RuntimeError(f"ui dump pull failed: {p.stderr}")
    raw = local.read_text(encoding="utf-8", errors="replace")
    i = raw.find("<hierarchy")
    return raw[i:] if i >= 0 else raw


def nodes() -> list[dict[str, str]]:
    root = ET.fromstring(dump_xml())
    out: list[dict[str, str]] = []
    for n in root.iter("node"):
        out.append(
            {
                "text": (n.get("text") or "").strip(),
                "desc": (n.get("content-desc") or "").strip(),
                "class": n.get("class") or "",
                "bounds": n.get("bounds") or "",
                "clickable": n.get("clickable") or "",
            }
        )
    return out


def all_text() -> set[str]:
    s: set[str] = set()
    for n in nodes():
        if n["text"]:
            s.add(n["text"])
        if n["desc"]:
            s.add(n["desc"])
    return s


def center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_xy(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap_bounds(bounds: str) -> bool:
    c = center(bounds)
    if not c:
        return False
    tap_xy(*c)
    return True


def tap_text(*texts: str) -> bool:
    for n in nodes():
        blob = n["text"] or n["desc"]
        if any(t == blob or t in blob for t in texts):
            if tap_bounds(n["bounds"]):
                return True
    return False


def input_text(s: str) -> None:
    parts = s.split("@")
    for i, p in enumerate(parts):
        if p:
            adb("shell", "input", "text", p)
        if i < len(parts) - 1:
            adb("shell", "input", "keyevent", "77")


def dismiss() -> bool:
    t = all_text()
    joined = " ".join(t)
    if "稍后" in t and tap_text("稍后"):
        time.sleep(0.7)
        return True
    if "发现新版本" in joined:
        if tap_text("稍后"):
            time.sleep(0.7)
            return True
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.5)
        return True
    for x in ("允许", "Allow", "确定", "OK", "同意", "继续"):
        if x in t and tap_text(x):
            time.sleep(0.6)
            return True
    return False


def launch() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/com.vpn.member.MainActivity")
    time.sleep(2.5)


def on_home() -> bool:
    t = all_text()
    return "Play Store" in t or "At a glance" in t


def on_main() -> bool:
    t = all_text()
    return any(x in t for x in ("未连接", "一键连接", "已保护", "节点")) or any(
        "套餐" in x for x in t
    )


def on_login() -> bool:
    t = all_text()
    return "欢迎回来" in t or ("登录" in t and "邮箱" in t)


def shot(name: str) -> None:
    remote = f"/sdcard/{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(SHOT / f"{name}.png"))


def login() -> None:
    for _ in range(8):
        if not dismiss():
            break
    if on_home():
        launch()
        for _ in range(6):
            if not dismiss():
                break
    if on_main():
        log("already main")
        return
    if not on_login():
        end = time.time() + 25
        while time.time() < end and not on_login() and not on_main():
            dismiss()
            time.sleep(0.7)
    if on_main():
        log("already main")
        return
    if not on_login():
        raise RuntimeError(f"not login page: {sorted(all_text())[:40]}")

    edits = [n for n in nodes() if "EditText" in n["class"]]
    if len(edits) < 2:
        raise RuntimeError("no edittexts")
    tap_bounds(edits[0]["bounds"])
    time.sleep(0.25)
    for _ in range(80):
        adb("shell", "input", "keyevent", "67")
    input_text(EMAIL)
    time.sleep(0.35)
    tap_bounds(edits[1]["bounds"])
    time.sleep(0.25)
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")
    input_text(PASSWORD)
    time.sleep(0.4)

    # Compose 登录文字本身常不可点：点整行中心 x=540
    login_y = None
    for n in nodes():
        if n["text"] == "登录":
            c = center(n["bounds"])
            if c:
                login_y = c[1]
                break
    if login_y is None:
        login_y = 1535
    log(f"tap login at 540,{login_y}")
    tap_xy(540, login_y)
    time.sleep(3)
    for _ in range(20):
        if on_home():
            log("home after login, relaunch")
            launch()
        if dismiss():
            continue
        if on_main():
            log("login ok")
            return
        time.sleep(0.8)
    raise RuntimeError(f"login stuck: {sorted(all_text())[:50]}")


def open_nodes_page() -> None:
    # 连接页空状态优先点「去连接节点/选择节点」；否则点底栏「节点」
    if tap_text("去连接节点", "选择节点"):
        time.sleep(1.8)
        return
    if tap_text("节点"):
        time.sleep(1.8)
        return
    # 底部第二 tab 粗略坐标（Pixel）
    tap_xy(270, 2280)
    time.sleep(1.8)


def connect_wuhu() -> None:
    open_nodes_page()
    for _ in range(5):
        if not dismiss():
            break
    log(f"nodes_entry sample={sorted(all_text())[:45]}")
    found = False
    for _ in range(26):
        if any("芜湖" in t for t in all_text()):
            found = True
            break
        # 可能还在连接页
        if any(x in all_text() for x in ("去连接节点", "选择节点", "一键连接")):
            open_nodes_page()
        adb("shell", "input", "swipe", "540", "1600", "540", "700", "300")
        time.sleep(0.35)
    log(f"found_wuhu={found} sample={sorted(all_text())[:45]}")
    if not found:
        shot("v312_no_node")
        raise RuntimeError("no wuhu node")
    for n in nodes():
        if "芜湖" in n["text"]:
            tap_bounds(n["bounds"])
            break
    time.sleep(1.2)
    if not tap_text("连接此节点"):
        # 部分 UI：点节点即连
        tap_text("一键连接")
    log("connect tapped")
    time.sleep(1.5)
    for _ in range(18):
        joined = " ".join(all_text())
        if any(x in joined for x in ("已保护", "连接失败", "不可达", "无响应")):
            break
        if tap_text("确定", "OK", "允许", "Allow"):
            time.sleep(1)
            continue
        if "Connection request" in joined or "VPN" in joined or "要设置" in joined:
            tap_xy(780, 1400)
            time.sleep(1)
            continue
        if dismiss():
            continue
        if "连接中" in joined:
            time.sleep(2)
            continue
        time.sleep(1)


def main() -> int:
    OUT.write_text("", encoding="utf-8")
    log("=== 3.12 emulator connect verify ===")
    if "device" not in (adb("devices").stdout or ""):
        log("no emulator")
        return 2
    tz = (adb("shell", "getprop", "persist.sys.timezone").stdout or "").strip()
    abi = (adb("shell", "getprop", "ro.product.cpu.abi").stdout or "").strip()
    log(f"tz={tz} abi={abi} apk={APK.name}")
    if not APK.is_file():
        raise FileNotFoundError(APK)

    # 已装过则跳过重装，加快重试；需要干净环境时再 uninstall
    path = adb("shell", "pm", "path", PKG).stdout or ""
    if "package:" not in path:
        p = adb("install", "-r", str(APK), timeout=180)
        log(f"install rc={p.returncode}")
        if p.returncode != 0:
            log(p.stdout + p.stderr)
            return 2
    else:
        log("app already installed, skip reinstall")
    adb("logcat", "-c")
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    launch()
    login()
    connect_wuhu()

    end = time.time() + 100
    while time.time() < end:
        joined = " ".join(all_text())
        if any(x in joined for x in ("已保护", "已连接", "连接失败", "不可达", "无响应")):
            break
        time.sleep(1.2)

    texts = sorted(all_text())
    joined = " ".join(texts)
    shot("v312_result")
    log(f"final_ui={texts[:80]}")

    raw = adb("logcat", "-d", "-t", "12000", timeout=60).stdout or ""
    keys = [
        "post_connect_policy",
        "overseas_tz",
        "隧道探测通过",
        "回国隧道基础连通性失败",
        "隧道基础连通性失败",
        "dataplane_check",
        "start_mihomo",
        "open_tun",
    ]
    hits = {k: (k in raw) for k in keys}
    samples = [
        ln
        for ln in raw.splitlines()
        if any(x in ln for x in keys + ["开始连接", "VpnDiag", "config.yaml"])
    ][-50:]
    log(f"hits={[k for k, v in hits.items() if v]}")
    for ln in samples:
        log("  " + ln[:300])

    connected = (("已保护" in joined) or ("已连接" in joined)) and ("连接失败" not in joined)
    failed = ("连接失败" in joined) or ("不可达" in joined) or hits.get("回国隧道基础连通性失败")
    probe = hits.get("隧道探测通过")
    log(f"RESULT connected={connected} failed={failed} probe_ok={probe}")
    return 0 if connected or probe else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        log(f"ERROR {e}")
        raise
