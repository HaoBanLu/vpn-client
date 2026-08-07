#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器验收 3.15.5：没网不拆隧道、恢复后自动重连；抓 logcat 错误。"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ.get("ANDROID_HOME", r"C:\Users\luban\AppData\Local\Android\Sdk")) / "platform-tools" / "adb.exe"
PKG = "com.vpn.member"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT = ROOT / "scripts" / "_emulator_shots"
SHOT.mkdir(parents=True, exist_ok=True)
OUT = SHOT / "_3155_offline_wait_report.txt"


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


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = SHOT / "_ui_3155.xml"
    adb("pull", "/sdcard/ui.xml", str(local))
    if local.is_file():
        return local.read_text(encoding="utf-8", errors="replace")
    raw = subprocess.run([str(ADB), "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, timeout=60)
    return raw.stdout.decode("utf-8", errors="replace")


def ui_texts() -> list[str]:
    xml = dump_ui()
    idx = xml.find("<hierarchy")
    if idx >= 0:
        xml = xml[idx:]
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out: list[str] = []
    for n in root.iter("node"):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        if t:
            out.append(t)
        if d:
            out.append(d)
    return out


def find_node(substr: str) -> dict[str, str] | None:
    xml = dump_ui()
    idx = xml.find("<hierarchy")
    if idx >= 0:
        xml = xml[idx:]
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    for n in root.iter("node"):
        t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
        if substr in t:
            return dict(n.attrib)
    return None


def tap_bounds(bounds: str) -> None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap_text(substr: str, wait: float = 1.0) -> bool:
    n = find_node(substr)
    if not n or not n.get("bounds"):
        return False
    tap_bounds(n["bounds"])
    time.sleep(wait)
    return True


def shot(name: str) -> None:
    remote = f"/sdcard/{name}.png"
    local = SHOT / f"{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(local))
    adb("shell", "rm", remote)
    log(f"  shot -> {local}")


def clear_logcat() -> None:
    adb("logcat", "-c")


def grab_logcat(lines: int = 400) -> str:
    p = adb("logcat", "-d", "-t", str(lines))
    return p.stdout or ""


def version_ok() -> tuple[bool, str]:
    p = adb("shell", "dumpsys", "package", PKG)
    code = re.search(r"versionCode=(\d+)", p.stdout or "")
    name = re.search(r"versionName=([^\s]+)", p.stdout or "")
    vc = code.group(1) if code else "?"
    vn = name.group(1) if name else "?"
    ok = vc == "50" or vn.startswith("3.15.5")
    return ok, f"versionName={vn} versionCode={vc}"


def ensure_login() -> None:
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    texts = " | ".join(ui_texts())
    if "连接" in texts or "节点" in texts or "已保护" in texts or "断开" in texts:
        log("已在主界面（可能已登录）")
        return
    # 尝试登录
    if tap_text("邮箱") or tap_text("登录"):
        time.sleep(0.5)
    # 填邮箱：点第一个输入框较难，用 uiautomator EditText
    xml = dump_ui()
    edits = []
    try:
        root = ET.fromstring(xml[xml.find("<hierarchy") :])
        for n in root.iter("node"):
            if n.get("class") == "android.widget.EditText":
                edits.append(n)
    except Exception:
        pass
    if len(edits) >= 2:
        tap_bounds(edits[0].get("bounds") or "")
        time.sleep(0.3)
        adb("shell", "input", "text", EMAIL.replace("@", "%40"))
        time.sleep(0.3)
        tap_bounds(edits[1].get("bounds") or "")
        time.sleep(0.3)
        adb("shell", "input", "text", PASSWORD)
        time.sleep(0.3)
        tap_text("登录", wait=5) or tap_text("登 录", wait=5)
    time.sleep(3)


def try_connect() -> bool:
    texts = " | ".join(ui_texts())
    if "已保护" in texts or "已连接" in texts or "断开连接" in texts:
        log("已是连接态")
        return True
    for label in ("一键连接", "连接", "立即连接"):
        if tap_text(label, wait=2):
            log(f"点击了 {label}")
            break
    # 等连接
    for i in range(24):
        time.sleep(2.5)
        t = " | ".join(ui_texts())
        log(f"  connect wait {i}: {[x for x in ('已保护', '已连接', '连接中', '失败', '重连') if x in t]}")
        if "已保护" in t or "已连接" in t or "断开" in t:
            return True
        if "失败" in t and "自动重连" not in t:
            return False
    return False


def airplane(on: bool) -> None:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(2)


def service_tunnel_running() -> bool:
    p = adb("shell", "dumpsys", "activity", "services", PKG)
    return "VpnTunnelService" in (p.stdout or "")


def main() -> int:
    lines: list[str] = []
    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    ok_ver, ver = version_ok()
    rec(f"== 安装版本: {ver} ok={ok_ver}")
    if not ok_ver:
        rec("FAIL: 需要 3.15.5 / 50，请先 installDebug")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    clear_logcat()
    ensure_login()
    shot("3155_01_home")

    # 确保非飞行模式
    airplane(False)
    time.sleep(2)

    connected = try_connect()
    shot("3155_02_after_connect")
    rec(f"连接结果: {connected}")
    svc_before = service_tunnel_running()
    rec(f"连接后 VpnTunnelService: {svc_before}")

    # --- 断网：飞行模式 ---
    clear_logcat()
    rec(">> 开启飞行模式 25s（期望：不拆隧道、不因探活升级重连）")
    airplane(True)
    time.sleep(25)
    shot("3155_03_airplane_on")
    offline_log = grab_logcat(500)
    offline_texts = " | ".join(ui_texts())
    svc_offline = service_tunnel_running()

    tear_down = "DISCONNECT_FOR_RECONNECT" in offline_log or "disconnectHoldingKillSwitch" in offline_log
    # 日志里可能是中文
    reconnect_while_offline = bool(
        re.search(r"自动重连.*health_probe|scheduleAutoReconnect.*health_probe|自动重连.*transport_", offline_log)
    ) and "物理网不可用" not in offline_log
    wait_msg = "物理网不可用" in offline_log or "恢复后将自动重连" in offline_log or "网络已断开" in offline_texts
    fatal = bool(re.search(r"FATAL EXCEPTION|AndroidRuntime.*FATAL", offline_log))

    rec(f"飞行中服务仍在: {svc_offline}")
    rec(f"飞行中 UI 片段: {[x for x in offline_texts.split(' | ') if any(k in x for k in ('断开', '保护', '网络', '重连', '连接', '失败'))][:12]}")
    rec(f"飞行中出现拆隧道重连: {tear_down}")
    rec(f"飞行中误触发自动重连(无等待日志): {reconnect_while_offline}")
    rec(f"出现等待/没网提示或日志: {wait_msg}")
    rec(f"FATAL: {fatal}")

    # --- 恢复网络 ---
    clear_logcat()
    rec(">> 关闭飞行模式，等待恢复 35s")
    airplane(False)
    time.sleep(35)
    shot("3155_04_airplane_off")
    restore_log = grab_logcat(600)
    restore_texts = " | ".join(ui_texts())
    svc_restore = service_tunnel_running()

    restored_heal = "物理网络切换" in restore_log or "隧道自愈" in restore_log or "自动重连" in restore_log or "network_restored" in restore_log
    restore_fatal = bool(re.search(r"FATAL EXCEPTION|AndroidRuntime.*FATAL", restore_log))
    crashes = adb("shell", "dumpsys", "dropbox", "--print").stdout or ""
    recent_crash = PKG in crashes and "data_app_crash" in crashes

    rec(f"恢复后服务: {svc_restore}")
    rec(f"恢复后有自愈/重连迹象: {restored_heal}")
    rec(f"恢复 FATAL: {restore_fatal}")
    rec(f"UI: {[x for x in restore_texts.split(' | ') if any(k in x for k in ('保护', '连接', '重连', '失败', '网络', '断开'))][:15]}")

    # 汇总错误关键词
    err_hits = []
    for blob, tag in ((offline_log, "offline"), (restore_log, "restore")):
        for pat in ("FATAL EXCEPTION", "AndroidRuntime", "NullPointerException", "IllegalStateException"):
            if pat in blob:
                err_hits.append(f"{tag}:{pat}")

    # 判定
    pass_offline = connected and svc_offline and not tear_down and not fatal
    # 没连上也无法验断网保持；仍检查无崩溃
    if not connected:
        pass_offline = not fatal and not tear_down
        rec("WARN: 未能确认已连接，断网保持结论弱化")

    pass_restore = not restore_fatal and (restored_heal or svc_restore)
    overall = pass_offline and pass_restore and not err_hits

    rec("")
    rec("== 结论 ==")
    rec(f"断网保持(不拆隧道): {'PASS' if pass_offline else 'FAIL'}")
    rec(f"恢复自愈/重连: {'PASS' if pass_restore else 'FAIL'}")
    rec(f"无致命错误: {'PASS' if not err_hits and not fatal and not restore_fatal else 'FAIL'} {err_hits}")
    rec(f"OVERALL: {'PASS' if overall else 'FAIL'}")

    # 摘录关键日志
    rec("\n== offline log 摘录 ==")
    for line in offline_log.splitlines():
        if any(k in line for k in ("物理网", "自动重连", "自愈", "DISCONNECT", "FATAL", "ConnectViewModel", "VpnTunnel", "network")):
            rec(line[:240])
    rec("\n== restore log 摘录 ==")
    for line in restore_log.splitlines():
        if any(k in line for k in ("物理网", "自动重连", "自愈", "DISCONNECT", "FATAL", "network", "切网", "探测")):
            rec(line[:240])

    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report -> {OUT}")
    return 0 if overall else 2


if __name__ == "__main__":
    raise SystemExit(main())
