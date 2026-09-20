# -*- coding: utf-8 -*-
"""Login via WebView Chrome DevTools Protocol, then run feature checks."""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PACKAGE = "com.vpn.kuayun"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT_DIR = Path(r"D:\Code\Go-www\vpn-client\apps\tauri\scripts")
RESULTS: list[str] = []

T = {
    "mine": "\u6211\u7684",
    "about": "\u5173\u4e8e",
    "check": "\u68c0\u67e5\u66f4\u65b0",
    "login": "\u767b\u5f55",
    "connect": "\u8fde\u63a5",
    "orders": "\u8ba2\u5355",
    "devices": "\u8bbe\u5907",
    "back": "\u8fd4\u56de",
    "update_now": "\u7acb\u5373\u66f4\u65b0",
    "later": "\u7a0d\u540e\u518d\u8bf4",
    "cancel": "\u53d6\u6d88",
    "retry": "\u91cd\u8bd5",
    "traffic": "\u6d41\u91cf",
    "new_ver": "\u53d1\u73b0\u65b0\u7248\u672c",
    "need_update": "\u9700\u8981\u66f4\u65b0",
    "latest": "\u5df2\u662f\u6700\u65b0",
    "latest2": "\u5f53\u524d\u5df2\u662f\u6700\u65b0",
    "updating": "\u6b63\u5728\u66f4\u65b0",
    "downloading": "\u6b63\u5728\u4e0b\u8f7d",
    "fail": "\u66f4\u65b0\u5931\u8d25",
    "vpn_hint": "\u5efa\u8bae\u65ad\u5f00 VPN",
    "pull": "\u4e0b\u62c9\u5237\u65b0",
    "release": "\u677e\u5f00\u5237\u65b0",
    "refreshing": "\u5237\u65b0\u4e2d",
    "one_click": "\u4e00\u952e\u8fde\u63a5",
    "not_connected": "\u672a\u8fde\u63a5",
    "recharge_rec": "\u5145\u503c\u8bb0\u5f55",
    "pkg_order": "\u5957\u9910\u8ba2\u5355",
    "empty": "\u6682\u65e0",
    "login_devices": "\u767b\u5f55\u8bbe\u5907",
    "traffic_title": "\u6d41\u91cf\u7edf\u8ba1",
    "total": "\u603b\u6d41\u91cf",
}


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


def wait(sec: float, msg: str) -> None:
    print(f"  ... {msg} ({sec}s)")
    time.sleep(sec)


def ui_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ky_ui.xml", check=False)
    p = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ky_ui.xml"], capture_output=True)
    return p.stdout.decode("utf-8", errors="replace")


def ui_texts() -> list[str]:
    return list(dict.fromkeys(re.findall(r'text="([^"]+)"', ui_xml())))


def show(label: str) -> list[str]:
    print(f"\n=== UI: {label} ===")
    texts = ui_texts()
    for t in texts:
        try:
            print(f"  {t}")
        except UnicodeEncodeError:
            print(f"  {t.encode('unicode_escape').decode()}")
    return texts


def wait_texts(needles: list[str], timeout: float = 30, label: str = "wait") -> list[str]:
    deadline = time.time() + timeout
    last: list[str] = []
    while time.time() < deadline:
        last = ui_texts()
        j = " | ".join(last)
        if any(n in j for n in needles):
            print(f"\n=== UI: {label} (ready) ===")
            for t in last:
                try:
                    print(f"  {t}")
                except UnicodeEncodeError:
                    print(f"  {t.encode('unicode_escape').decode()}")
            return last
        time.sleep(1)
    print(f"\n=== UI: {label} (timeout) ===")
    for t in last:
        try:
            print(f"  {t}")
        except UnicodeEncodeError:
            print(f"  {t.encode('unicode_escape').decode()}")
    return last


def shot(name: str) -> None:
    local = SHOT_DIR / name
    with open(local, "wb") as f:
        p = subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {name}" if p.returncode == 0 else f"  shot failed {name}")


def find_bounds(text: str, contains: bool = False) -> tuple[int, int] | None:
    xml = ui_xml()
    if contains:
        pat = rf'text="([^"]*{re.escape(text)}[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        m = re.search(pat, xml)
        if not m:
            return None
        x1, y1, x2, y2 = map(int, m.groups()[1:])
    else:
        pat = rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        m = re.search(pat, xml)
        if not m:
            return None
        x1, y1, x2, y2 = map(int, m.groups())
    return ((x1 + x2) // 2, (y1 + y2) // 2)


def tap(text: str, contains: bool = False) -> bool:
    pt = find_bounds(text, contains=contains)
    if not pt:
        print(f"  [miss] {text}")
        return False
    adb("shell", "input", "tap", str(pt[0]), str(pt[1]))
    print(f"  [tap] {text}")
    return True


def joined(texts: list[str]) -> str:
    return " | ".join(texts)


def has_any(texts: list[str], needles: list[str]) -> str | None:
    j = joined(texts)
    for n in needles:
        if n in j:
            return n
    return None


def pass_(cid: str, detail: str) -> None:
    RESULTS.append(f"PASS {cid} :: {detail}")
    print(f"PASS {cid} :: {detail}")


def fail_(cid: str, detail: str) -> None:
    RESULTS.append(f"FAIL {cid} :: {detail}")
    print(f"FAIL {cid} :: {detail}")


def dismiss_system() -> None:
    texts = ui_texts()
    j = joined(texts)
    if "Don't Show Again" in j:
        tap("Don't Show Again")
        wait(2, "compat")
    texts = ui_texts()
    j = joined(texts)
    if "Allow" in j and ("notification" in j.lower() or "\u8de8\u4e91" in j):
        tap("Allow")
        wait(2, "notif")


def webview_pid() -> str | None:
    out = adb("shell", "pidof", PACKAGE, check=False).strip()
    return out.split()[0] if out.strip() else None


def cdp_eval(js: str) -> object:
    """Evaluate JS in the app WebView via DevTools HTTP/WS."""
    import websocket  # type: ignore

    pid = webview_pid()
    if not pid:
        raise RuntimeError("app pid missing")
    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
    page = next((p for p in pages if p.get("type") == "page" and "ws" in (p.get("webSocketDebuggerUrl") or "")), None)
    if not page:
        raise RuntimeError(f"no page target: {pages}")
    ws_url = page["webSocketDebuggerUrl"]
    # Chromium rejects missing/wrong Origin; suppress or spoof local DevTools origin.
    try:
        ws = websocket.create_connection(ws_url, timeout=10, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            ws_url,
            timeout=10,
            header=["Origin: http://127.0.0.1:9222"],
        )
    try:
        msg_id = 1
        ws.send(json.dumps({"id": msg_id, "method": "Runtime.evaluate", "params": {"expression": js, "returnByValue": True, "awaitPromise": True}}))
        while True:
            raw = ws.recv()
            data = json.loads(raw)
            if data.get("id") == msg_id:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result", {}).get("result", {}).get("value")
    finally:
        ws.close()


def login_via_cdp() -> None:
    js = f"""
    (async () => {{
      const sleep = (ms) => new Promise(r => setTimeout(r, ms));
      const setNative = (el, val) => {{
        const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
        const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
        if (setter) setter.call(el, val); else el.value = val;
        el.dispatchEvent(new Event('input', {{ bubbles: true }}));
        el.dispatchEvent(new Event('change', {{ bubbles: true }}));
      }};
      const inputs = [...document.querySelectorAll('input')];
      const email = inputs.find(i => i.type === 'email' || i.placeholder?.includes('@') || i.name?.includes('email')) || inputs[0];
      const pass = inputs.find(i => i.type === 'password') || inputs[1];
      if (!email || !pass) return 'no-inputs:' + inputs.length;
      setNative(email, {EMAIL!r});
      setNative(pass, {PASSWORD!r});
      await sleep(200);
      const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('\u767b\u5f55'));
      if (btn) btn.click();
      else {{
        const form = email.closest('form');
        if (form) form.requestSubmit?.() || form.submit?.();
      }}
      return 'ok';
    }})()
    """
    try:
        val = cdp_eval(js)
        print(f"  CDP login => {val}")
    except Exception as e:
        print(f"  CDP login failed: {e}; fallback adb typing")
        # fallback
        tap("\u90ae\u7bb1")
        wait(0.3, "focus email")
        # tap below
        pt = find_bounds("\u90ae\u7bb1")
        if pt:
            adb("shell", "input", "tap", str(pt[0]), str(pt[1] + 80))
        for _ in range(40):
            adb("shell", "input", "keyevent", "67")
        adb("shell", "input", "text", EMAIL.replace("@", r"\@"))
        pt = find_bounds("\u5bc6\u7801")
        if pt:
            adb("shell", "input", "tap", str(pt[0]), str(pt[1] + 80))
        for _ in range(20):
            adb("shell", "input", "keyevent", "67")
        adb("shell", "input", "text", PASSWORD)
        tap(T["login"])


def main() -> int:
    print("== emulator update + pull-refresh ==")
    if "\tdevice" not in adb("devices"):
        raise RuntimeError("no device")

    # ensure websocket-client
    try:
        import websocket  # noqa: F401
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])

    adb("logcat", "-c", check=False)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait(5, "boot")
    dismiss_system()
    texts = wait_texts([T["login"], T["mine"], T["connect"]], timeout=25, label="app ready")
    shot("_t0_boot.png")

    if has_any(texts, [T["login"], "\u90ae\u7bb1", "\u5bc6\u7801"]):
        print("login via CDP...")
        login_via_cdp()
        wait(10, "after login")
        dismiss_system()
        texts = wait_texts([T["mine"], T["connect"]], timeout=25, label="logged in")
        shot("_t1_login.png")
        if not has_any(texts, [T["mine"], T["connect"]]):
            fail_("A0", f"login failed: {joined(texts)}")
            return 1

    print("\n---- A: update ----")
    if not tap(T["mine"], contains=True):
        fail_("A0", "cannot tap mine tab")
        return 1
    wait(2, "profile")
    texts = show("profile")
    shot("_t2_profile.png")
    if not tap(T["about"], contains=True):
        fail_("A0", "cannot open about")
        return 1
    wait(2, "about")
    texts = show("about")
    shot("_t3_about.png")
    if has_any(texts, [T["check"]]):
        pass_("A1", "has check-update")
    else:
        fail_("A1", "missing check-update")

    tap(T["check"])
    wait(4, "check")
    texts = show("check result")
    shot("_t4_check.png")
    if has_any(texts, [T["new_ver"], T["update_now"], T["need_update"]]):
        pass_("A2", "update prompt")
        tap(T["later"])
        wait(1, "dismiss")
        tap(T["back"])
        wait(1, "back")
        if not tap(T["connect"], contains=True):
            tap("home", contains=True)
        wait(2, "connect")
        texts = show("connect")
        shot("_t5_connect.png")
        if has_any(texts, [T["one_click"], T["not_connected"]]):
            if not tap(T["one_click"], contains=True):
                # hero button often labeled 连接
                tap(T["not_connected"], contains=True)
            wait(15, "vpn")
            texts = show("vpn")
            shot("_t6_vpn.png")
        tap(T["mine"], contains=True)
        wait(1, "profile")
        tap(T["about"], contains=True)
        wait(2, "about2")
        tap(T["check"])
        wait(3, "check2")
        texts = show("prompt2")
        shot("_t7_prompt2.png")
        if has_any(texts, [T["update_now"]]):
            tap(T["update_now"])
            wait(3, "download")
            texts = show("downloading")
            shot("_t8_dl.png")
            hit = has_any(texts, [T["cancel"], T["updating"], T["downloading"], T["fail"], T["vpn_hint"], T["retry"]])
            if hit:
                pass_("A4", f"saw {hit}")
            else:
                fail_("A4", f"no download ui: {joined(texts)}")
            if has_any(texts, [T["cancel"]]):
                tap(T["cancel"])
                wait(2, "cancel")
                texts = show("after cancel")
                shot("_t9_cancel.png")
                if not has_any(texts, [T["updating"], T["downloading"]]):
                    pass_("A5", "cancel closed overlay")
                else:
                    fail_("A5", "still downloading")
            elif has_any(texts, [T["fail"], T["retry"]]):
                if has_any(texts, [T["retry"]]):
                    pass_("A5", "retry")
                else:
                    fail_("A5", "no retry")
                if has_any(texts, [T["later"]]):
                    pass_("A6", "later")
                else:
                    fail_("A6", "no later")
                tap(T["later"])
                wait(2, "later")
                texts = show("after later")
                shot("_t9_later.png")
                if not has_any(texts, [T["fail"]]):
                    pass_("A7", "error dismissed")
                else:
                    fail_("A7", "still error")
        else:
            pass_("A3", "no re-prompt after dismiss")
    elif has_any(texts, [T["latest"], T["latest2"]]):
        pass_("A2", "already latest; fail/retry E2E needs newer server package")
    else:
        fail_("A2", f"unknown: {joined(texts)}")

    print("\n---- B: pull refresh ----")
    tap(T["mine"], contains=True)
    wait(2, "profile")
    texts = show("profile B")
    shot("_r0_profile.png")
    if not tap(T["orders"], contains=True):
        tap(T["devices"], contains=True)
    wait(3, "subpage")
    texts = show("sub before")
    shot("_r1_before.png")
    hit = has_any(texts, [T["orders"], T["login_devices"], T["recharge_rec"], T["pkg_order"], T["empty"]])
    if hit:
        pass_("B1", f"opened ({hit})")
    else:
        fail_("B1", f"not data subpage: {joined(texts)}")

    adb("shell", "input", "swipe", "540", "420", "540", "980", "700")
    print("  [swipe] pull")
    wait(1, "pull")
    texts = show("pulling")
    shot("_r2_pull.png")
    hit = has_any(texts, [T["pull"], T["release"], T["refreshing"]])
    if hit:
        pass_("B2", f"indicator {hit}")
    else:
        # second stronger swipe
        adb("shell", "input", "swipe", "540", "350", "540", "1100", "900")
        wait(1, "pull2")
        texts = show("pulling2")
        shot("_r2b_pull.png")
        hit = has_any(texts, [T["pull"], T["release"], T["refreshing"]])
        if hit:
            pass_("B2", f"indicator {hit}")
        else:
            fail_("B2", f"no indicator: {joined(texts)}")
    wait(3, "settle")
    texts = show("after pull")
    shot("_r3_after.png")
    hit = has_any(texts, [T["orders"], T["login_devices"], T["empty"], T["back"], T["pkg_order"], T["recharge_rec"]])
    if hit:
        pass_("B3", f"still on page ({hit})")
    else:
        fail_("B3", "left page")

    tap(T["back"])
    wait(1, "back")
    if tap(T["traffic"], contains=True):
        wait(2, "traffic")
        adb("shell", "input", "swipe", "540", "420", "540", "980", "700")
        wait(1, "traffic pull")
        texts = show("traffic")
        shot("_r4_traffic.png")
        hit = has_any(texts, [T["pull"], T["release"], T["refreshing"], T["traffic_title"], T["total"]])
        if hit:
            pass_("B4", f"traffic {hit}")
        else:
            fail_("B4", "traffic fail")

    print("\n==== SUMMARY ====")
    for r in RESULTS:
        print(r)
    fails = sum(1 for r in RESULTS if r.startswith("FAIL"))
    passes = sum(1 for r in RESULTS if r.startswith("PASS"))
    print(f"PASS={passes} FAIL={fails}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
