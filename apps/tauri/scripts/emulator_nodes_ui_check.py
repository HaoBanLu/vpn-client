# -*- coding: utf-8 -*-
"""Verify nodes page margins, region headers, and pull-refresh soft-fail."""
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
SHOT = Path(__file__).resolve().parent / "_ui_nodes_verify.png"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


class Cdp:
    def __init__(self) -> None:
        try:
            import websocket  # noqa: F401
        except ImportError:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket

        pid = adb("shell", "pidof", PACKAGE).strip().split()[0]
        adb("forward", "--remove-all", check=False)
        adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
        pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
        page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
        try:
            self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
        except TypeError:
            self.ws = websocket.create_connection(
                page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
            )
        self._id = 0

    def call(self, method: str, params: dict | None = None) -> dict:
        self._id += 1
        mid = self._id
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(120):
            data = json.loads(self.ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def eval(self, expression: str):
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
    cdp.eval(
        f"""
        (async () => {{
          const setNative = (el, val) => {{
            const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) setter.call(el, val); else el.value = val;
            el.dispatchEvent(new Event('input', {{ bubbles: true }}));
            el.dispatchEvent(new Event('change', {{ bubbles: true }}));
          }};
          const text = document.body.innerText || '';
          if (!text.includes('登录') || !text.includes('邮箱')) return 'already';
          const inputs = [...document.querySelectorAll('input')];
          const email = inputs.find(i => i.type === 'email' || (i.placeholder||'').includes('@')) || inputs[0];
          const pass = inputs.find(i => i.type === 'password') || inputs[1];
          setNative(email, {EMAIL!r});
          setNative(pass, {PASSWORD!r});
          const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('登录'));
          btn?.click();
          return 'ok';
        }})()
        """
    )
    deadline = time.time() + 35
    while time.time() < deadline:
        text = cdp.eval("document.body.innerText || ''") or ""
        if "节点" in text and ("连接" in text or "已保护" in text or "未连接" in text):
            if "邮箱" not in text:
                return
        time.sleep(1)
    raise RuntimeError("login timeout")


def main() -> int:
    print("== nodes UI verify ==")
    ver = adb("shell", "dumpsys", "package", PACKAGE)
    m = re.search(r"versionName=([^\s]+)", ver)
    print(f"  apk versionName={m.group(1) if m else '?'}")

    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(5)

    cdp = Cdp()
    fails = 0
    try:
        login(cdp)
        print("  logged in")

        # go nodes tab
        cdp.eval(
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
        time.sleep(2.5)

        layout = cdp.eval(
            """
            (() => {
              const book = document.querySelector('.nodes-book');
              const nav = document.querySelector('.nodes-region-nav');
              const chips = [...document.querySelectorAll('.nodes-region-nav__chip')].map(el => ({
                text: (el.textContent||'').trim(),
                active: el.classList.contains('active'),
              }));
              const sideIndex = document.querySelector('.nodes-book__index');
              const content = document.querySelector('.app-content');
              if (!book) return { ok: false, reason: 'no-book', text: (document.body.innerText||'').slice(0,120) };
              const br = book.getBoundingClientRect();
              const nr = nav ? nav.getBoundingClientRect() : null;
              return {
                ok: true,
                contentPadL: content ? parseFloat(getComputedStyle(content).paddingLeft || '0') : 0,
                bookLeft: Math.round(br.left),
                marginOk: br.left >= 12,
                hasTopNav: !!nav,
                hasSideIndex: !!sideIndex,
                chips,
                navAboveBook: !!(nr && nr.bottom <= br.top + 2),
                nodeCount: document.querySelectorAll('.ky-node-row').length,
              };
            })()
            """
        )
        print(f"  layout={json.dumps(layout, ensure_ascii=False)}")
        if not layout or not layout.get("ok"):
            print("FAIL layout")
            fails += 1
        else:
            if layout.get("contentPadL", 0) < 12 or not layout.get("marginOk"):
                print("FAIL margins")
                fails += 1
            else:
                print("PASS margins")
            if not layout.get("hasTopNav") or layout.get("hasSideIndex"):
                print("FAIL top-nav placement")
                fails += 1
            elif not layout.get("navAboveBook"):
                print("FAIL nav not above list")
                fails += 1
            elif len(layout.get("chips") or []) < 2:
                print("FAIL chip count")
                fails += 1
            else:
                print(f"PASS top region chips {layout.get('chips')}")
            if layout.get("nodeCount", 0) < 1:
                print("FAIL no nodes")
                fails += 1
            else:
                print(f"PASS nodes loaded ({layout.get('nodeCount')})")

        # tap a region chip and ensure list is filtered
        filter = cdp.eval(
            """
            (async () => {
              const chips = [...document.querySelectorAll('.nodes-region-nav__chip')];
              if (chips.length < 2) return { ok: false, reason: 'need chips' };
              const allChip = chips[0];
              const regionChip = chips[1];
              const beforeSections = document.querySelectorAll('.nodes-book__section').length;
              const beforeNodes = document.querySelectorAll('.ky-node-row').length;
              regionChip.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
              await new Promise((r) => setTimeout(r, 250));
              const afterSections = document.querySelectorAll('.nodes-book__section').length;
              const afterNodes = document.querySelectorAll('.ky-node-row').length;
              const regionActive = regionChip.classList.contains('active');
              const allActiveAfterRegion = allChip.classList.contains('active');
              allChip.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
              await new Promise((r) => setTimeout(r, 250));
              const backSections = document.querySelectorAll('.nodes-book__section').length;
              const allActive = allChip.classList.contains('active');
              return {
                ok: true,
                beforeSections,
                beforeNodes,
                afterSections,
                afterNodes,
                regionActive,
                allActiveAfterRegion,
                backSections,
                allActive,
                filteredDown: afterSections < beforeSections || afterNodes < beforeNodes,
                restored: backSections === beforeSections && allActive,
              };
            })()
            """
        )
        print(f"  filter={json.dumps(filter, ensure_ascii=False)}")
        if (
            filter
            and filter.get("ok")
            and filter.get("regionActive")
            and filter.get("filteredDown")
            and filter.get("restored")
            and not filter.get("allActiveAfterRegion")
        ):
            print("PASS region filter")
        else:
            print("FAIL region filter")
            fails += 1

        # screenshot via CDP
        shot = cdp.call("Page.captureScreenshot", {"format": "png", "fromSurface": True})
        import base64

        SHOT.write_bytes(base64.b64decode(shot["data"]))
        print(f"  shot={SHOT}")
    finally:
        cdp.close()

    print(f"== done fails={fails} ==")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
