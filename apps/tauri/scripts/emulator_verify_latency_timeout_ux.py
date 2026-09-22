# -*- coding: utf-8 -*-
"""模拟器验证：节点延迟成功显示 ms；失败显示「超时」可点；不再整页假 —。"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = "D:/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.vpn.kuayun"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT = Path(__file__).resolve().parent / "_ui_verify_latency_timeout.png"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(
        [ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def main() -> int:
    try:
        import websocket
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket

    print("== emulator latency UX verify ==")
    ver = adb("shell", "dumpsys", "package", PACKAGE, check=False)
    m = re.search(r"versionName=([^\s]+)", ver)
    print(f"  installed versionName={m.group(1) if m else '?'}")

    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(5)

    pid = ""
    for _ in range(40):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip()
        if pid:
            pid = pid.split()[0]
            break
        time.sleep(1)
    if not pid:
        raise RuntimeError("no pid")
    print(f"  pid={pid}")

    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = None
    for _ in range(25):
        try:
            pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
            if any(p.get("type") == "page" and p.get("webSocketDebuggerUrl") for p in pages):
                break
        except Exception:
            pages = None
        time.sleep(1)
    if not pages:
        raise RuntimeError("cdp unavailable")

    page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
    try:
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=25, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=25, header=["Origin: http://127.0.0.1:9222"]
        )

    nid = 0

    def call(method: str, params=None):
        nonlocal nid
        nid += 1
        mid = nid
        ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(200):
            data = json.loads(ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def ev(expr: str):
        result = call(
            "Runtime.evaluate",
            {"expression": expr, "returnByValue": True, "awaitPromise": True},
        )
        if result.get("exceptionDetails"):
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:500])
        return result.get("result", {}).get("value")

    # login if needed
    body = (ev("document.body.innerText || ''") or "")[:400]
    if "登录" in body or "邮箱" in body:
        print("  logging in…")
        ev(
            f"""
            (async () => {{
              const inputs = [...document.querySelectorAll('input')];
              const email = inputs.find(i => /email|邮箱|mail/i.test(i.type+i.placeholder+i.name));
              const pass = inputs.find(i => i.type === 'password');
              if (email) {{
                email.focus(); email.value = {json.dumps(EMAIL)};
                email.dispatchEvent(new Event('input', {{ bubbles: true }}));
              }}
              if (pass) {{
                pass.focus(); pass.value = {json.dumps(PASSWORD)};
                pass.dispatchEvent(new Event('input', {{ bubbles: true }}));
              }}
              const btn = [...document.querySelectorAll('button')].find(b => /登录|登 录/.test(b.textContent||''));
              if (btn) btn.click();
            }})()
            """
        )
        time.sleep(6)

    # clear latency cache so we exercise fresh probe path
    ev(
        """
        (() => {
          try { localStorage.removeItem('ky_entry_latency_by_node_id'); } catch(e) {}
          try { sessionStorage.removeItem('ky_entry_latency_by_node_id'); } catch(e) {}
        })()
        """
    )

    ev(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '节点');
          if (btn) btn.click(); else location.hash = '#/nodes';
        })()
        """
    )
    # wait for probe wave
    time.sleep(10)

    snapshot = ev(
        """
        (() => {
          const rows = [...document.querySelectorAll('.ky-node-row')];
          const vals = [...document.querySelectorAll('.ky-node-row__latency')].map(el =>
            (el.textContent || '').replace(/\\s+/g, ' ').trim()
          );
          const ms = vals.filter(v => /^\\d+ms/.test(v));
          const timeout = vals.filter(v => v.includes('超时'));
          const dash = vals.filter(v => v === '—' || v === '–' || v === '-');
          const pending = vals.filter(v => v === '…' || v === '...');
          const fastest = vals.filter(v => v.includes('最快'));
          return {
            rowCount: rows.length,
            vals,
            msCount: ms.length,
            timeoutCount: timeout.length,
            dashCount: dash.length,
            pendingCount: pending.length,
            fastestCount: fastest.length,
            sample: vals.slice(0, 12),
          };
        })()
        """
    )
    print("nodes=", json.dumps(snapshot, ensure_ascii=False))

    # if any 超时, click one to ensure retry handler exists
    retry_ok = None
    if (snapshot or {}).get("timeoutCount", 0) > 0:
        retry_ok = ev(
            """
            (() => {
              const btn = [...document.querySelectorAll('.ky-node-row__latency-btn')]
                .find(el => (el.textContent||'').includes('超时') && !el.disabled);
              if (!btn) return { clicked: false };
              btn.click();
              return { clicked: true, text: (btn.textContent||'').trim() };
            })()
            """
        )
        print("retry=", json.dumps(retry_ok, ensure_ascii=False))
        time.sleep(3)

    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {SHOT.name}")
    ws.close()

    fails = []
    s = snapshot or {}
    if s.get("rowCount", 0) < 1:
        fails.append("no node rows")
    if s.get("msCount", 0) < 1 and s.get("timeoutCount", 0) < 1:
        fails.append("neither ms nor 超时 shown (still blank/dash only?)")
    # After our fix: bare dash should be rare; allow a few for missing endpoint
    if s.get("dashCount", 0) > max(2, (s.get("rowCount") or 0) // 2):
        fails.append(f"too many dashes: {s.get('dashCount')}")

    if fails:
        for f in fails:
            print("FAIL", f)
        return 1
    print("PASS latency UX: shows ms and/or 超时; not all dashes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
