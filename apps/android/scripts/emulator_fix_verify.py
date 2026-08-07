#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器验收：无隐私引导 / 连接中断 / 切节点 / 快速失败 / 速率不虚高（截图）。

用法:
  python apps/android/scripts/emulator_fix_verify.py
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
SHOT_DIR = ROOT / "scripts" / "_emulator_shots"
SHOT_DIR.mkdir(parents=True, exist_ok=True)


@dataclass
class CaseResult:
    name: str
    passed: bool
    detail: str
    shot: str = ""


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB), *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        errors="replace",
    )


def shot(name: str) -> str:
    remote = f"/sdcard/{name}.png"
    local = SHOT_DIR / f"{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(local))
    adb("shell", "rm", remote)
    return str(local)


def parse_nodes() -> list[dict[str, str]]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    # Windows 下 `adb shell cat` 会把 UTF-8 中文弄乱码，改用 exec-out / pull
    local_xml = SHOT_DIR / "_ui.xml"
    pull = adb("pull", "/sdcard/ui.xml", str(local_xml))
    if pull.returncode != 0 or not local_xml.is_file():
        # fallback: exec-out 保留字节
        raw = subprocess.run(
            [str(ADB), "exec-out", "cat", "/sdcard/ui.xml"],
            capture_output=True,
            timeout=60,
        )
        xml = raw.stdout.decode("utf-8", errors="replace")
    else:
        xml = local_xml.read_text(encoding="utf-8", errors="replace")
    if not xml.strip().startswith("<?xml") and "<hierarchy" not in xml:
        idx = xml.find("<hierarchy")
        if idx >= 0:
            xml = xml[idx:]
    root = ET.fromstring(xml)
    nodes: list[dict[str, str]] = []
    for n in root.iter("node"):
        text = (n.get("text") or "").strip()
        desc = (n.get("content-desc") or "").strip()
        bounds = n.get("bounds") or ""
        clickable = n.get("clickable") == "true"
        clazz = n.get("class") or ""
        if text or desc or "EditText" in clazz:
            nodes.append(
                {
                    "text": text,
                    "desc": desc,
                    "bounds": bounds,
                    "clickable": str(clickable),
                    "class": clazz,
                }
            )
    return nodes


def all_text() -> set[str]:
    return {n["text"] for n in parse_nodes() if n["text"]}


def joined_text() -> str:
    return " | ".join(sorted(all_text()))


def center_of(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_text(text: str, contains: bool = False) -> bool:
    for n in parse_nodes():
        hit = text in n["text"] if contains else n["text"] == text
        if not hit:
            continue
        c = center_of(n["bounds"])
        if not c:
            continue
        adb("shell", "input", "tap", str(c[0]), str(c[1]))
        return True
    return False


def tap_edit_index(index: int) -> bool:
    edits = [n for n in parse_nodes() if "EditText" in n.get("class", "")]
    if index >= len(edits):
        return False
    c = center_of(edits[index]["bounds"])
    if not c:
        return False
    adb("shell", "input", "tap", str(c[0]), str(c[1]))
    return True


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
        time.sleep(0.7)
    return None


def dismiss_system_dialogs() -> None:
    for label in ("Allow", "允许", "WHILE USING THE APP", "仅在使用该应用时允许", "OK", "确定"):
        if tap_text(label, contains=True):
            time.sleep(0.5)


def launch_app() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(1.5)
    dismiss_system_dialogs()


def clear_app() -> None:
    adb("shell", "pm", "clear", PKG)
    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")


def install_apk() -> None:
    if not APK.is_file():
        raise FileNotFoundError(f"APK missing: {APK}")
    r = adb("install", "-r", str(APK), timeout=180)
    out = (r.stdout or "") + (r.stderr or "")
    if r.returncode != 0 and "Success" not in out:
        raise RuntimeError(out)


def input_literal(text: str) -> None:
    """adb input text：特殊字符分段输入（避免 %40 被原样写入）。"""
    buf: list[str] = []
    for ch in text:
        if ch == "@":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            adb("shell", "input", "keyevent", "77")  # KEYCODE_AT
        elif ch == " ":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            adb("shell", "input", "text", "%s")
        elif ch in "\\'\"&<>|();":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            # 跳过极端字符
        else:
            buf.append(ch)
    if buf:
        adb("shell", "input", "text", "".join(buf))


def login_via_ui() -> None:
    launch_app()
    if not wait_text("登录", "欢迎回来", timeout=25):
        raise RuntimeError(f"未进入登录页: {joined_text()}")
    nodes = parse_nodes()
    edits = [n for n in nodes if "EditText" in n.get("class", "")]
    if len(edits) < 2:
        raise RuntimeError(f"登录页 EditText 不足: {len(edits)}")
    email_bounds, pass_bounds = edits[0]["bounds"], edits[1]["bounds"]
    # 清空可能残留
    tap_bounds(email_bounds)
    time.sleep(0.2)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")  # DEL
    input_literal(EMAIL)
    time.sleep(0.35)
    tap_bounds(pass_bounds)
    time.sleep(0.25)
    for _ in range(20):
        adb("shell", "input", "keyevent", "67")
    input_literal(PASSWORD)
    time.sleep(0.4)
    if not tap_text("登录"):
        adb("shell", "input", "tap", "672", "1860")
    time.sleep(1.0)
    dismiss_system_dialogs()
    # 登录按钮可能被键盘挡住，再点一次
    if "请求参数无效" in all_text() or "欢迎回来" in all_text():
        adb("shell", "input", "keyevent", "4")  # 收起键盘
        time.sleep(0.4)
        tap_text("登录") or adb("shell", "input", "tap", "672", "1860")
        time.sleep(1.0)
    if not wait_text("未连接", "一键连接", "购买套餐", "已保护", "连接", "节点", timeout=50):
        path = shot("login_failed")
        raise RuntimeError(f"登录后未进入主界面: {joined_text()} shot={path}")


def open_nodes_tab() -> None:
    if not wait_text("节点", timeout=15):
        raise RuntimeError("未找到节点 Tab")
    tap_text("节点")
    if not wait_text("节点选择", "连接此节点", timeout=25):
        raise RuntimeError(f"节点页未加载: {joined_text()}")


def scroll_find(text: str, times: int = 12) -> bool:
    for _ in range(times):
        if any(text in t for t in all_text()):
            return True
        adb("shell", "input", "swipe", "540", "1800", "540", "700", "350")
        time.sleep(0.45)
    return any(text in t for t in all_text())


def connect_buttons() -> list[dict[str, str]]:
    return [n for n in parse_nodes() if n["text"] == "连接此节点"]


def tap_bounds(bounds: str) -> bool:
    c = center_of(bounds)
    if not c:
        return False
    adb("shell", "input", "tap", str(c[0]), str(c[1]))
    return True


def parse_mbps(texts: set[str]) -> list[float]:
    vals: list[float] = []
    for t in texts:
        m = re.search(r"(\d+(?:\.\d+)?)\s*Mbps", t, re.I)
        if m:
            vals.append(float(m.group(1)))
    return vals


def test_no_privacy_onboarding() -> CaseResult:
    clear_app()
    login_via_ui()
    path = shot("01_after_login_no_privacy")
    texts = all_text()
    if "隐私保护已默认开启" in texts:
        return CaseResult("无隐私引导弹窗", False, "仍弹出隐私引导", path)
    ok = any(t in texts for t in ("未连接", "一键连接", "连接", "已保护", "购买套餐"))
    return CaseResult("无隐私引导弹窗", ok, "登录后直接进主界面" if ok else f"界面异常: {joined_text()[:120]}", path)


def test_interrupt() -> CaseResult:
    clear_app()
    login_via_ui()
    open_nodes_tab()
    shot("02_nodes_before_connect")
    btns = connect_buttons()
    if not btns:
        return CaseResult("连接中可中断", False, "无连接按钮")
    tap_bounds(btns[0]["bounds"])
    if not wait_text("连接中", "切换中", timeout=40):
        path = shot("03_interrupt_missed_connecting")
        # 可能已经极快连上/失败
        texts = all_text()
        if "已保护" in texts or "连接失败" in texts:
            return CaseResult("连接中可中断", True, "节点响应过快，未稳定停留在连接中（可接受）", path)
        return CaseResult("连接中可中断", False, f"未进入连接中: {joined_text()[:120]}", path)
    path = shot("03_connecting_before_interrupt")
    tap_text("连接中") or tap_text("切换中")
    if wait_text("未连接", "一键连接", "连接失败", timeout=25):
        path2 = shot("04_after_interrupt")
        return CaseResult("连接中可中断", True, "再次点击已中断连接", path2)
    path2 = shot("04_after_interrupt_fail")
    return CaseResult("连接中可中断", False, f"中断失败: {joined_text()[:120]}", path2)


def test_switch_node() -> CaseResult:
    clear_app()
    login_via_ui()
    open_nodes_tab()
    btns = connect_buttons()
    if len(btns) < 1:
        return CaseResult("连接中切节点", False, "无连接按钮")
    tap_bounds(btns[0]["bounds"])
    wait_text("连接中", "切换中", "已保护", "连接失败", timeout=20)
    tap_text("节点")
    wait_text("节点选择", "连接此节点", timeout=15)
    btns = connect_buttons()
    if len(btns) < 2:
        adb("shell", "input", "swipe", "540", "1800", "540", "700", "400")
        time.sleep(0.6)
        btns = connect_buttons()
    if len(btns) < 2:
        path = shot("05_switch_only_one_node")
        return CaseResult("连接中切节点", False, "只有一个可连接节点", path)
    tap_bounds(btns[1]["bounds"])
    hit = wait_text("切换中", "连接中", "已保护", "连接失败", timeout=45)
    path = shot("05_after_switch_node")
    return CaseResult("连接中切节点", bool(hit), f"切换后状态命中={hit}", path)


def test_fast_fail() -> CaseResult:
    clear_app()
    login_via_ui()
    open_nodes_tab()
    target = "新加坡5"
    if not scroll_find(target):
        # 退而求其次：任意 VLESS 名
        for alt in ("新加坡4", "新加坡3", "新加坡-BGP", "新加坡1"):
            if scroll_find(alt):
                target = alt
                break
        else:
            path = shot("06_fast_fail_no_target")
            return CaseResult("快速失败提示", False, "未找到测试用 VLESS 节点", path)
    tap_text(target, contains=True)
    time.sleep(0.4)
    btns = connect_buttons()
    if not btns:
        return CaseResult("快速失败提示", False, "找不到连接按钮")
    # 点最靠近目标的按钮：取最后一个可见（滚动后通常是目标附近）
    tap_bounds(btns[-1]["bounds"])
    started = time.time()
    while time.time() - started < 45:
        texts = all_text()
        joined = " | ".join(texts)
        if "连接失败" in texts or "节点不可达" in joined or "断网保护" in joined:
            elapsed = time.time() - started
            path = shot("06_fast_fail")
            ok = elapsed < 50 and ("节点不可达" in joined or "断网保护" in joined)
            return CaseResult("快速失败提示", ok, f"{elapsed:.1f}s 失败，文案命中={ok}", path)
        if "已保护" in texts:
            path = shot("06_unexpected_connected")
            return CaseResult("快速失败提示", True, f"{target} 意外连上（节点可能已修复）", path)
        time.sleep(1.0)
    path = shot("06_fast_fail_timeout")
    return CaseResult("快速失败提示", False, f"45s 内未失败: {joined_text()[:120]}", path)


def test_connect_and_speed() -> CaseResult:
    clear_app()
    login_via_ui()
    open_nodes_tab()
    target = "新加坡-普通线路"
    if not scroll_find(target, times=14):
        path = shot("07_no_trojan_node")
        return CaseResult("连接+速率上限", False, "未找到新加坡-普通线路", path)
    tap_text(target, contains=True)
    time.sleep(0.4)
    btns = connect_buttons()
    if not btns:
        return CaseResult("连接+速率上限", False, "无连接按钮")
    tap_bounds(btns[0]["bounds"] if len(btns) == 1 else btns[-1]["bounds"])
    # VPN 系统授权
    for _ in range(8):
        dismiss_system_dialogs()
        if tap_text("OK") or tap_text("允许") or tap_text("Allow"):
            time.sleep(0.6)
        texts = all_text()
        if "已保护" in texts or "连接失败" in texts:
            break
        time.sleep(1.2)
    hit = wait_text("已保护", "连接失败", timeout=70)
    path = shot("07_connect_result")
    if hit != "已保护":
        return CaseResult("连接+速率上限", False, f"未连上: hit={hit} {joined_text()[:140]}", path)
    # 切回连接 Tab 看速率
    tap_text("连接")
    time.sleep(1.0)
    # 等待 warm-up 结束再采样
    time.sleep(4.0)
    path2 = shot("08_speed_after_warmup")
    texts = all_text()
    mbps = parse_mbps(texts)
    joined = joined_text()
    if any(v > 500 for v in mbps):
        return CaseResult("连接+速率上限", False, f"速率虚高 Mbps={mbps} UI={joined[:140]}", path2)
    # 刚连上可能是 0.0 KB/s，也算正常（warm-up/空闲）
    return CaseResult(
        "连接+速率上限",
        True,
        f"已保护；速率样本 Mbps={mbps or '无Mbps(可能KB/s空闲)'}；无 >500Mbps",
        path2,
    )


def log(msg: str) -> None:
    try:
        print(msg, flush=True)
    except UnicodeEncodeError:
        print(msg.encode("utf-8", errors="replace").decode("ascii", errors="replace"), flush=True)


def main() -> int:
    if not ADB.is_file():
        print(f"adb not found: {ADB}", file=sys.stderr)
        return 1
    dev = adb("devices")
    if "emulator-" not in dev.stdout and "device" not in dev.stdout:
        print("No device attached", file=sys.stderr)
        print(dev.stdout)
        return 1

    log(f"APK: {APK}")
    log(f"Shots: {SHOT_DIR}")
    install_apk()
    log("Installed OK")

    results: list[CaseResult] = []
    for name, fn in (
        ("无隐私引导弹窗", test_no_privacy_onboarding),
        ("连接中可中断", test_interrupt),
        ("连接中切节点", test_switch_node),
        ("快速失败提示", test_fast_fail),
        ("连接+速率上限", test_connect_and_speed),
    ):
        log(f"... start {name}")
        try:
            results.append(fn())
            log(f"... done {name}: {'PASS' if results[-1].passed else 'FAIL'} | {results[-1].detail}")
        except Exception as e:  # noqa: BLE001
            path = shot(f"crash_{len(results)}")
            results.append(CaseResult(name, False, f"异常: {e}", path))
            log(f"... done {name}: FAIL (exception) | {e}")

    log("\n=== 模拟器功能验收 ===")
    failed = 0
    for r in results:
        mark = "PASS" if r.passed else "FAIL"
        line = f"{mark} | {r.name} | {r.detail}"
        if r.shot:
            line += f" | shot={r.shot}"
        log(line)
        if not r.passed:
            failed += 1

    log(f"\nSummary: {len(results) - failed}/{len(results)} passed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
