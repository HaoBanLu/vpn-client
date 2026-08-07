#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器 UX 四项功能验收：隐私引导 / 连接中中断 / 连接中切节点 / 快速失败提示。

用法:
  python apps/android/scripts/emulator_ux_four_features_test.py
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-x86_64-debug.apk"
ADB = Path(os.environ.get("ANDROID_HOME", r"C:\Users\luban\AppData\Local\Android\Sdk")) / "platform-tools" / "adb.exe"
PKG = "com.vpn.member"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"


@dataclass
class CaseResult:
    name: str
    passed: bool
    detail: str


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB), *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        errors="replace",
    )


def parse_nodes() -> list[dict[str, str]]:
    r = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    if r.returncode != 0:
        raise RuntimeError(r.stderr or r.stdout)
    xml = adb("shell", "cat", "/sdcard/ui.xml").stdout
    root = ET.fromstring(xml)
    nodes: list[dict[str, str]] = []
    for n in root.iter("node"):
        text = (n.get("text") or "").strip()
        desc = (n.get("content-desc") or "").strip()
        bounds = n.get("bounds") or ""
        clickable = n.get("clickable") == "true"
        if text or desc:
            nodes.append(
                {
                    "text": text,
                    "desc": desc,
                    "bounds": bounds,
                    "clickable": str(clickable),
                }
            )
    return nodes


def all_text() -> set[str]:
    return {n["text"] for n in parse_nodes() if n["text"]}


def tap_text(text: str, contains: bool = False) -> bool:
    for n in parse_nodes():
        hit = text in n["text"] if contains else n["text"] == text
        if not hit:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n["bounds"])
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        return True
    return False


def wait_text(*candidates: str, timeout: float = 30.0, contains: bool = False) -> str | None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        texts = all_text()
        for c in candidates:
            if contains:
                if any(c in t for t in texts):
                    return c
            elif c in texts:
                return c
        time.sleep(0.8)
    return None


def launch_app() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")


def clear_app() -> None:
    adb("shell", "pm", "clear", PKG)
    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")


def install_apk() -> None:
    if not APK.is_file():
        raise FileNotFoundError(f"APK missing: {APK}")
    r = adb("install", "-r", str(APK))
    if r.returncode != 0 and "Success" not in (r.stdout + r.stderr):
        raise RuntimeError(r.stderr or r.stdout)


def login_via_ui() -> None:
    launch_app()
    if not wait_text("登录", "欢迎回来", timeout=20):
        raise RuntimeError("未进入登录页")
    # 邮箱 / 密码输入框
    edits = [n for n in parse_nodes() if n["text"] in ("邮箱", "密码")]
    if len(edits) < 2:
        raise RuntimeError("未找到邮箱/密码输入框")
    for label, value in (("邮箱", EMAIL), ("密码", PASSWORD)):
        if not tap_text(label):
            raise RuntimeError(f"无法点击 {label}")
        time.sleep(0.4)
        escaped = value.replace("@", "%40").replace(" ", "%s")
        adb("shell", "input", "text", escaped)
        time.sleep(0.4)
    if not tap_text("登录"):
        raise RuntimeError("无法点击登录按钮")
    if not wait_text("未连接", "一键连接", "购买套餐", "已保护", "隐私保护已默认开启", timeout=45):
        raise RuntimeError(f"登录后未进入主界面，当前: {sorted(all_text())[:12]}")


def complete_privacy_if_needed() -> None:
    if "隐私保护已默认开启" in all_text():
        tap_text("跳过引导，直接连接") or tap_text("下一步")
        wait_text("未连接", "一键连接", "购买套餐", timeout=20)


def open_nodes_tab() -> None:
    if not wait_text("节点", timeout=15):
        raise RuntimeError("未找到节点 Tab")
    tap_text("节点")
    if not wait_text("节点选择", "连接此节点", timeout=20):
        raise RuntimeError("节点页未加载")


def first_connect_button() -> bool:
    return tap_text("连接此节点")


def test_privacy_onboarding() -> CaseResult:
    clear_app()
    launch_app()
    if not wait_text("登录", timeout=15):
        return CaseResult("隐私引导", False, "未进入登录页")
    login_via_ui()
    if "隐私保护已默认开启" in all_text():
        ok = "跳过引导，直接连接" in all_text()
        return CaseResult("隐私引导", ok, "登录后进主界面展示隐私引导" if ok else "引导页缺少跳过按钮")
    return CaseResult("隐私引导", False, f"未展示引导，当前: {sorted(all_text())[:8]}")


def ensure_logged_in() -> None:
    clear_app()
    login_via_ui()
    complete_privacy_if_needed()
    if not wait_text("未连接", "一键连接", "购买套餐", timeout=20):
        raise RuntimeError("登录后主界面异常")


def test_interrupt_while_connecting() -> CaseResult:
    ensure_logged_in()
    open_nodes_tab()
    if not first_connect_button():
        return CaseResult("连接中中断", False, "无可用节点")
    if not wait_text("连接中", "切换中", timeout=45):
        return CaseResult("连接中中断", False, f"未进入连接中: {sorted(all_text())[:8]}")
    started = time.time()
    tap_text("连接中")
    if wait_text("未连接", "一键连接", timeout=25):
        return CaseResult("连接中中断", True, f"约 {time.time() - started:.1f}s 内回到未连接")
    return CaseResult("连接中中断", False, f"点击后未断开: {sorted(all_text())[:8]}")


def test_switch_while_connecting() -> CaseResult:
    ensure_logged_in()
    open_nodes_tab()
    if not first_connect_button():
        return CaseResult("连接中切节点", False, "无可用节点")
    if not wait_text("连接中", "切换中", timeout=45):
        return CaseResult("连接中切节点", False, "未进入连接中")
    tap_text("节点")
    if not wait_text("节点选择", "连接此节点", timeout=15):
        return CaseResult("连接中切节点", False, "无法回到节点页")
    nodes = [n for n in parse_nodes() if n["text"] == "连接此节点"]
    if len(nodes) < 2:
        adb("shell", "input", "swipe", "700", "2200", "700", "900", "400")
        time.sleep(1)
        nodes = [n for n in parse_nodes() if n["text"] == "连接此节点"]
    if len(nodes) < 2:
        return CaseResult("连接中切节点", False, "只有一个可连接节点")
    # 点第二个连接按钮
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", nodes[1]["bounds"])
    if not m:
        return CaseResult("连接中切节点", False, "无法解析按钮坐标")
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    if wait_text("切换中", "连接中", "已保护", "连接失败", timeout=45):
        return CaseResult("连接中切节点", True, "连接中可切换并继续流程")
    return CaseResult("连接中切节点", False, f"切换后无响应: {sorted(all_text())[:8]}")


def test_fast_fail_kill_switch() -> CaseResult:
    ensure_logged_in()
    open_nodes_tab()
    if not wait_text("新加坡5", contains=True, timeout=2):
        pass
    # 滚动找新加坡5
    found = False
    for _ in range(12):
        if any("新加坡5" in t for t in all_text()):
            found = True
            break
        adb("shell", "input", "swipe", "700", "2200", "700", "900", "350")
        time.sleep(0.5)
    if not found:
        return CaseResult("快速失败+断网保护", False, "未找到新加坡5节点")
    tap_text("新加坡5", contains=True)
    time.sleep(0.5)
    if not tap_text("连接此节点"):
        return CaseResult("快速失败+断网保护", False, "无法点击新加坡5连接")
    started = time.time()
    deadline = started + 40
    while time.time() < deadline:
        texts = all_text()
        joined = " | ".join(sorted(texts))
        if "连接失败" in texts or "节点不可达" in joined or "断网保护" in joined:
            elapsed = time.time() - started
            ok = elapsed < 50 and ("节点不可达" in joined or "断网保护" in joined)
            return CaseResult(
                "快速失败+断网保护",
                ok,
                f"{elapsed:.1f}s 失败，文案含节点不可达/断网保护={ok}",
            )
        time.sleep(1)
    return CaseResult("快速失败+断网保护", False, f"40s 内未失败: {sorted(all_text())[:8]}")


def main() -> int:
    if not ADB.is_file():
        print(f"adb not found: {ADB}", file=sys.stderr)
        return 1
    dev = adb("devices")
    if dev.stdout.count("device") < 2:
        print("No device attached", file=sys.stderr)
        print(dev.stdout)
        return 1

    print(f"APK: {APK}")
    install_apk()

    results = [
        test_privacy_onboarding(),
        test_interrupt_while_connecting(),
        test_switch_while_connecting(),
        test_fast_fail_kill_switch(),
    ]

    print("\n=== UX 四项验收 ===")
    failed = 0
    for r in results:
        mark = "PASS" if r.passed else "FAIL"
        line = f"{mark} | {r.name} | {r.detail}"
        try:
            print(line)
        except UnicodeEncodeError:
            print(line.encode("utf-8", errors="replace").decode("utf-8"))
        if not r.passed:
            failed += 1

    print(f"\nSummary: {len(results) - failed}/{len(results)} passed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
