# -*- coding: utf-8 -*-
"""Quick CDP check: compact nodes row padding after redesign."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = "D:/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.vpn.kuayun"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT = Path(__file__).resolve().parent / "_ui_nodes_compact.png"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def main() -> int:
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(5)
    pid = ""
    for _ in range(30):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip()
        if pid:
            pid = pid.split()[0]
            break
        time.sleep(1)
    if not pid:
        # one more cold start
        adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity", check=False)
        time.sleep(4)
        pid = adb("shell", "pidof", PACKAGE, check=False).strip().split()
        pid = pid[0] if pid else ""
    if not pid:
        raise RuntimeError("no pid")
    print(f"pid={pid}")
    try:
        import websocket
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket

    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
    page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
    try:
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
        )
    nid = 0

    def call(method: str, params=None):
        nonlocal nid
        nid += 1
        mid = nid
        ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(120):
            data = json.loads(ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def ev(expr: str):
        result = call("Runtime.evaluate", {"expression": expr, "returnByValue": True, "awaitPromise": True})
        if result.get("exceptionDetails"):
            raise RuntimeError(result["exceptionDetails"])
        return result.get("result", {}).get("value")

    ev(
        f"""
        (async () => {{
          const text = document.body.innerText || '';
          if (!text.includes('登录') || !text.includes('邮箱')) return 'already';
          const set = (el, v) => {{
            const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) setter.call(el, v); else el.value = v;
            el.dispatchEvent(new Event('input', {{ bubbles: true }}));
          }};
          const inputs = [...document.querySelectorAll('input')];
          const email = inputs.find(i => i.type === 'email' || (i.placeholder||'').includes('@')) || inputs[0];
          const pass = inputs.find(i => i.type === 'password') || inputs[1];
          set(email, {EMAIL!r});
          set(pass, {PASSWORD!r});
          [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('登录'))?.click();
          return 'ok';
        }})()
        """
    )
    for _ in range(40):
        text = ev("document.body.innerText || ''") or ""
        if "节点" in text and "邮箱" not in text:
            break
        time.sleep(1)

    ev(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '节点');
          if (btn) { btn.click(); return 'click'; }
          location.hash = '#/nodes';
          return 'hash';
        })()
        """
    )
    time.sleep(2.2)

    layout = ev(
        """
        (() => {
          const active = document.querySelector('.ky-node-row--active') || document.querySelector('.ky-node-row');
          const btn = document.querySelector('.ky-node-row__action');
          if (!active) return { ok: false };
          const cs = getComputedStyle(active);
          const br = active.getBoundingClientRect();
          const name = active.querySelector('.ky-node-row__name');
          const nr = name ? name.getBoundingClientRect() : null;
          const rowClickable = active.getAttribute('role') === 'button';
          return {
            ok: true,
            padL: cs.paddingLeft,
            padR: cs.paddingRight,
            marginL: cs.marginLeft,
            radius: cs.borderRadius,
            rowH: Math.round(br.height),
            nameLeftInset: nr ? Math.round(nr.left - br.left) : null,
            hasActionBtn: !!btn,
            actionTag: btn ? btn.tagName : null,
            rowClickable,
            nodeCount: document.querySelectorAll('.ky-node-row').length,
            brandHeader: !!document.querySelector('.brand-header--tab, .ky-tab-brand'),
          };
        })()
        """
    )
    print("layout", json.dumps(layout, ensure_ascii=False))
    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print("shot", SHOT)
    ws.close()

    if not layout or not layout.get("ok"):
        print("FAIL no layout")
        return 1
    radius = float(str(layout.get("radius", "0")).split()[0].replace("px", "") or 0)
    if radius > 1:
        print(f"FAIL still rounded row radius={radius}")
        return 1
    if layout.get("rowClickable"):
        print("FAIL row still clickable as button")
        return 1
    if not layout.get("hasActionBtn") or layout.get("actionTag") != "BUTTON":
        print(f"FAIL action should be button, got {layout.get('actionTag')}")
        return 1
    if layout.get("brandHeader"):
        print("FAIL brand header still visible")
        return 1
    print("PASS button-only connect + flat list")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
