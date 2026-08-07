#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验收：连接失败默认不断网 + 设置项默认关；快速失败文案不含「已启用断网保护」。"""
from __future__ import annotations

import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import emulator_fix_verify as m  # noqa: E402


def dump_prefs() -> str:
    r = m.adb(
        "shell",
        "run-as",
        m.PKG,
        "cat",
        f"/data/data/{m.PKG}/shared_prefs/vpn_member_prefs.xml",
    )
    return (r.stdout or "") + (r.stderr or "")


def pref_bool(xml: str, key: str) -> bool | None:
    # <boolean name="..." value="true" />
    pat = rf'name="{re.escape(key)}"\s+value="(true|false)"'
    hit = re.search(pat, xml)
    if not hit:
        return None
    return hit.group(1) == "true"


def open_stability_settings() -> None:
    # 我的 -> 连接与隐私
    m.tap_text("我的")
    time.sleep(0.8)
    if not m.tap_text("连接与隐私", contains=True):
        m.tap_text("设置", contains=True)
        time.sleep(0.5)
        m.tap_text("连接与隐私", contains=True)
    time.sleep(1.0)
    # 「连接失败时阻断网络」在页面偏下，需下滑
    for _ in range(8):
        if any("连接失败时阻断" in t for t in m.all_text()):
            break
        m.adb("shell", "input", "swipe", "540", "1700", "540", "700", "350")
        time.sleep(0.4)


def leave_settings_to_home() -> None:
    """设置页可能多层；用 force-stop + 冷启更稳。登录态保留。"""
    m.adb("shell", "am", "force-stop", m.PKG)
    time.sleep(0.5)
    m.launch_app()
    time.sleep(1.5)
    m.dismiss_system_dialogs()



def switch_checked_near(title: str) -> bool | None:
    """在 UI dump 中找标题附近的 Switch checked 状态。"""
    m.adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = m.SHOT_DIR / "_ui.xml"
    m.adb("pull", "/sdcard/ui.xml", str(local))
    xml = local.read_text(encoding="utf-8", errors="replace")
    # 简化：找包含 title 的 node，再在后续 800 字符内找 CheckBox/Switch checked=
    idx = xml.find(title)
    if idx < 0:
        return None
    window = xml[idx : idx + 1200]
    m_sw = re.search(r'class="[^"]*(?:Switch|CheckBox)[^"]*"[^>]*checked="(true|false)"', window)
    if not m_sw:
        # Switch 可能在 title 之前（Compose 布局）
        window2 = xml[max(0, idx - 1200) : idx + 200]
        m_sw = re.search(r'checked="(true|false)"[^>]*class="[^"]*(?:Switch|CheckBox)', window2)
        if not m_sw:
            m_sw = re.search(r'checked="(true|false)"', window)
    if not m_sw:
        return None
    return m_sw.group(1) == "true"


def network_ok() -> bool:
    # 模拟器 ICMP 常挂死；用短超时 HTTP / 受限 ping。
    h = m.adb(
        "shell",
        "toybox",
        "wget",
        "-qO-",
        "-T",
        "5",
        "http://connectivitycheck.gstatic.com/generate_204",
        timeout=12,
    )
    if h.returncode == 0:
        return True
    p = m.adb("shell", "ping", "-c", "1", "-W", "2", "1.1.1.1", timeout=8)
    out = (p.stdout or "") + (p.stderr or "")
    return "bytes from" in out or "1 received" in out or "1 packets received" in out


def main() -> int:
    results: list[tuple[str, bool, str]] = []

    m.log(f"APK: {m.APK}")
    m.install_apk()
    m.clear_app()
    m.login_via_ui()
    m.shot("simp_01_after_login")

    # --- prefs: block_on_connect_failure 应为 false ---
    xml = dump_prefs()
    block = pref_bool(xml, "block_on_connect_failure")
    # 默认未写入 key 也算 false（代码 default false）；迁移后应显式 false
    baseline_ver = None
    mv = re.search(r'name="privacy_baseline_version"\s+value="(\d+)"', xml)
    if mv:
        baseline_ver = int(mv.group(1))
    ok_pref = block is False or (block is None and (baseline_ver is None or baseline_ver >= 2))
    # 迁移后通常会写入 false
    if block is True:
        ok_pref = False
    results.append(
        (
            "prefs: block_on_connect_failure 默认关",
            ok_pref,
            f"block={block} baseline={baseline_ver}",
        )
    )
    m.log(f"prefs snippet keys: block={block} baseline={baseline_ver}")

    # --- 设置页 Switch ---
    open_stability_settings()
    m.shot("simp_02_settings")
    texts = m.all_text()
    has_row = any("连接失败时阻断" in t for t in texts)
    checked = switch_checked_near("连接失败时阻断")
    # 若找不到 Switch，至少要看到该设置行；checked 必须不是 True
    ok_ui = has_row and checked is not True
    results.append(
        (
            "设置页：连接失败阻断默认关",
            ok_ui,
            f"has_row={has_row} switch_checked={checked} ui含「默认关闭」={any('默认关闭' in t for t in texts)}",
        )
    )

    # 回到主壳（设置页多层返回不可靠，force-stop 保留登录态）
    leave_settings_to_home()
    if not m.wait_text("节点", "连接", "我的", timeout=25):
        results.append(("回到主界面", False, m.joined_text()[:160]))
    else:
        results.append(("回到主界面", True, "ok"))

    # --- 快速失败：文案应是节点不可达，不应「已启用断网保护」---
    try:
        m.open_nodes_tab()
        target = "新加坡5"
        if not m.scroll_find(target, times=16):
            for alt in ("新加坡-5", "新加坡 5", "SG5"):
                if m.scroll_find(alt, times=4):
                    target = alt
                    break
        if not any(target in t for t in m.all_text()):
            results.append(("快速失败文案", False, f"未找到节点 {target}"))
        else:
            m.tap_text(target, contains=True)
            time.sleep(0.4)
            btns = m.connect_buttons()
            if not btns:
                results.append(("快速失败文案", False, "无连接按钮"))
            else:
                m.tap_bounds(btns[-1]["bounds"])
                for _ in range(6):
                    m.dismiss_system_dialogs()
                    if m.tap_text("OK") or m.tap_text("允许") or m.tap_text("Allow"):
                        time.sleep(0.5)
                started = time.time()
                hit = m.wait_text("连接失败", "节点不可达", "已保护", timeout=45, contains=True)
                elapsed = time.time() - started
                joined = m.joined_text()
                path = m.shot("simp_03_fast_fail")
                has_ks_hint = "已启用断网保护" in joined
                has_unreachable = "节点不可达" in joined or "连接失败" in m.all_text()
                ok_fail = (
                    hit in ("连接失败", "节点不可达")
                    and elapsed < 50
                    and has_unreachable
                    and not has_ks_hint
                )
                results.append(
                    (
                        "快速失败：节点不可达且无断网保护文案",
                        ok_fail,
                        f"hit={hit} {elapsed:.1f}s ks_hint={has_ks_hint} shot={path}",
                    )
                )

                time.sleep(1.0)
                net = network_ok()
                results.append(
                    (
                        "连接失败后本机网络仍通",
                        net,
                        "ping 8.8.8.8 " + ("OK" if net else "FAIL"),
                    )
                )
    except Exception as e:  # noqa: BLE001
        results.append(("快速失败文案", False, f"异常: {e}"))
        m.shot("simp_03_fail_exception")

    log = m.adb("logcat", "-d", "-t", "200")
    log_out = (log.stdout or "") + (log.stderr or "")
    has_failover = "health_failover" in log_out
    results.append(
        (
            "本次会话无 health_failover 日志",
            not has_failover,
            "found" if has_failover else "none",
        )
    )

    m.log("\n=== 简化项模拟器验收 ===")
    failed = 0
    for name, ok, detail in results:
        mark = "PASS" if ok else "FAIL"
        m.log(f"{mark} | {name} | {detail}")
        if not ok:
            failed += 1
    m.log(f"\nSummary: {len(results) - failed}/{len(results)} passed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
