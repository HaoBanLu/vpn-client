# -*- coding: utf-8 -*-
"""Restart emulator app and verify connect latency copy + nodes button-only."""
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
SHOT_DIR = Path(__file__).resolve().parent
RESULTS: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def safe_print(msg: str) -> None:
    try:
        print(msg)
    except UnicodeEncodeError:
        print(msg.encode("unicode_escape").decode())


def ok(msg: str) -> None:
    safe_print(f"PASS {msg}")
    RESULTS.append(f"PASS {msg}")


def fail(msg: str) -> None:
    safe_print(f"FAIL {msg}")
    RESULTS.append(f"FAIL {msg}")


def shot(name: str) -> None:
    path = SHOT_DIR / name
    with open(path, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {path.name}")


class Cdp:
    def __init__(self) -> None:
        try:
            import websocket
        except ImportError:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
            import websocket

        pid = ""
        for _ in range(30):
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
        last_err: Exception | None = None
        for attempt in range(20):
            try:
                pages = json.loads(
                    urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode()
                )
                if any(p.get("type") == "page" and p.get("webSocketDebuggerUrl") for p in pages):
                    break
            except Exception as e:  # noqa: BLE001
                last_err = e
                pages = None
            time.sleep(1)
        if not pages:
            raise RuntimeError(f"cdp json unavailable: {last_err}")
        page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
        try:
            self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
        except TypeError:
            self.ws = websocket.create_connection(
                page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
            )
        self._id = 0

    def call(self, method: str, params=None) -> dict:
        self._id += 1
        mid = self._id
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(160):
            data = json.loads(self.ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def ev(self, expression: str):
        result = self.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True, "awaitPromise": True},
        )
        if result.get("exceptionDetails"):
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:400])
        return result.get("result", {}).get("value")

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass


def login(cdp: Cdp) -> None:
    cdp.ev(
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
        text = cdp.ev("document.body.innerText || ''") or ""
        if "节点" in text and "邮箱" not in text:
            return
        time.sleep(1)
    raise RuntimeError("login timeout")


def tap_tab(cdp: Cdp, name: str) -> None:
    cdp.ev(
        f"""
        (() => {{
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === {name!r});
          if (btn) {{ btn.click(); return 'click'; }}
          const map = {{ '连接': '#/connect', '节点': '#/nodes', '套餐': '#/packages', '我的': '#/profile' }};
          location.hash = map[{name!r}] || '#/';
          return 'hash';
        }})()
        """
    )
    time.sleep(2)


def main() -> int:
    print("== restart + verify ==")
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(5)

    cdp = Cdp()
    try:
        login(cdp)
        ok("login")

        # ---- Connect page: no 入口, may show 延迟 ----
        tap_tab(cdp, "连接")
        connect = cdp.ev(
            """
            (() => {
              const text = document.body.innerText || '';
              return {
                hasEntry: text.includes('入口'),
                hasTunnelWord: text.includes('隧道 ') && /隧道\\s*\\d+ms/.test(text),
                hasDelay: /延迟\\s*\\d+ms/.test(text),
                hasBrandHeader: !!document.querySelector('.brand-header--tab, .ky-tab-brand'),
                snippet: text.slice(0, 280),
              };
            })()
            """
        )
        print(f"  connect={json.dumps(connect, ensure_ascii=True)}")
        if connect.get("hasEntry"):
            fail("connect page still shows 入口")
        else:
            ok("connect page no 入口")
        if connect.get("hasTunnelWord"):
            fail("connect page still shows 隧道 xxms wording")
        else:
            ok("connect page no 隧道 xxms wording")
        # delay may be absent if not connected / probe not ready
        if connect.get("hasDelay"):
            ok("connect page shows 延迟 xxms")
        else:
            ok("connect page delay absent (ok if disconnected/no probe yet)")
        if connect.get("hasBrandHeader"):
            # connect page still has brand header by design
            ok("connect keeps brand header")
        shot("_ui_verify_connect.png")

        # ---- Nodes: no brand, button-only, flat ----
        tap_tab(cdp, "节点")
        nodes = cdp.ev(
            """
            (() => {
              const row = document.querySelector('.ky-node-row');
              const btn = document.querySelector('.ky-node-row__action');
              const cs = row ? getComputedStyle(row) : null;
              return {
                brandHeader: !!document.querySelector('.brand-header--tab, .ky-tab-brand'),
                nodeCount: document.querySelectorAll('.ky-node-row').length,
                radius: cs ? cs.borderRadius : null,
                rowRoleButton: row ? row.getAttribute('role') === 'button' : null,
                actionIsButton: btn ? btn.tagName === 'BUTTON' : false,
                chips: [...document.querySelectorAll('.nodes-region-nav__chip')].map(el => (el.textContent||'').trim()),
              };
            })()
            """
        )
        print(f"  nodes={json.dumps(nodes, ensure_ascii=True)}")
        if nodes.get("brandHeader"):
            fail("nodes still has brand header")
        else:
            ok("nodes no brand header")
        if (nodes.get("nodeCount") or 0) < 1:
            fail("nodes empty")
        else:
            ok(f"nodes loaded ({nodes.get('nodeCount')})")
        radius = float(str(nodes.get("radius") or "0").split()[0].replace("px", "") or 0)
        if radius > 1:
            fail(f"nodes row still rounded ({radius})")
        else:
            ok("nodes flat rows")
        if nodes.get("rowRoleButton"):
            fail("nodes row still whole-row clickable")
        else:
            ok("nodes row not whole-click connect")
        if not nodes.get("actionIsButton"):
            fail("nodes action not a button")
        else:
            ok("nodes connect is button")
        shot("_ui_verify_nodes.png")

        # ---- Packages: no brand ----
        tap_tab(cdp, "套餐")
        pkgs = cdp.ev(
            """
            (() => {
              const text = document.body.innerText || '';
              return {
                brandHeader: !!document.querySelector('.brand-header--tab, .ky-tab-brand'),
                hasTitleBlock: text.includes('跨云') && text.includes('加速套餐') && !!document.querySelector('.brand-title'),
                snippet: text.slice(0, 160),
              };
            })()
            """
        )
        print(f"  packages={json.dumps(pkgs, ensure_ascii=True)}")
        if pkgs.get("brandHeader"):
            fail("packages still has brand header")
        else:
            ok("packages no brand header")
        shot("_ui_verify_packages.png")
    finally:
        cdp.close()

    print("\n== summary ==")
    for line in RESULTS:
        print(line)
    return 1 if any(r.startswith("FAIL") for r in RESULTS) else 0


if __name__ == "__main__":
    raise SystemExit(main())
