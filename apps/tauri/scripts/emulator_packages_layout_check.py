# -*- coding: utf-8 -*-
"""Emulator CDP: verify packages page layout aligned with member-center cards."""
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
SHOT = Path(__file__).resolve().parent / "_ui_packages_layout.png"


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
        adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity", check=False)
        time.sleep(4)
        parts = adb("shell", "pidof", PACKAGE, check=False).strip().split()
        pid = parts[0] if parts else ""
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

    # login if needed
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
        if "套餐" in text and "邮箱" not in text:
            break
        time.sleep(1)

    # open packages tab
    nav = ev(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '套餐');
          if (btn) { btn.click(); return 'click'; }
          try {
            const app = document.querySelector('#app')?.__vue_app__;
            const router = app?.config?.globalProperties?.$router;
            if (router) { router.push({ name: 'Packages' }); return 'router'; }
          } catch (e) {}
          location.hash = '#/packages';
          return 'hash';
        })()
        """
    )
    print(f"nav={nav}")
    for _ in range(25):
        html = ev("document.body.innerHTML || ''") or ""
        if "ky-package-card" in html or "ky-subscription-summary" in html:
            break
        time.sleep(0.6)
    time.sleep(1.2)

    layout = ev(
        """
        (() => {
          const text = document.body.innerText || '';
          const cards = [...document.querySelectorAll('.ky-package-card')];
          const first = cards[0];
          const action = first?.querySelector('.ky-package-card__action, button');
          const features = first ? first.querySelectorAll('.ky-package-card__feature').length : 0;
          const amount = first?.querySelector('.ky-package-card__amount');
          const title = first?.querySelector('.ky-package-card__title');
          const oldChips = document.querySelectorAll('.ky-package-card__chips, .ky-package-card__stat').length;
          const priceRowSideBtn = first ? !!first.querySelector('.ky-package-card__price-row') : false;
          const actionRect = action ? action.getBoundingClientRect() : null;
          const cardRect = first ? first.getBoundingClientRect() : null;
          const fullWidth =
            actionRect && cardRect
              ? actionRect.width / Math.max(cardRect.width, 1) > 0.7
              : false;
          return {
            noSummaryBar: !document.querySelector('.ky-subscription-summary'),
            noIntroTitle: !text.includes('选择套餐') && !document.querySelector('.packages-intro'),
            brandHeader: !!document.querySelector('.brand-header--tab, .ky-tab-brand'),
            cardCount: cards.length,
            features,
            hasAmount: !!amount,
            titleSample: title ? (title.textContent || '').trim() : null,
            firstIsCurrent: !!first?.classList.contains('ky-package-card--current'),
            firstBadge: first?.querySelector('.ky-status-badge')?.textContent?.trim() || null,
            oldChipLayout: oldChips > 0,
            priceRowSideBtn,
            fullWidthCta: fullWidth,
            actionTag: action ? action.tagName : null,
            actionWidth: actionRect ? Math.round(actionRect.width) : null,
            cardWidth: cardRect ? Math.round(cardRect.width) : null,
            hasCurrent: !!document.querySelector('.ky-package-card--current'),
            snippet: text.replace(/\\s+/g, ' ').slice(0, 220),
          };
        })()
        """
    )
    print("layout", json.dumps(layout, ensure_ascii=True))

    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print("shot", SHOT)

    # also CDP screenshot for sharper UI
    try:
        shot = call("Page.captureScreenshot", {"format": "png", "fromSurface": True})
        import base64

        cdp_shot = Path(__file__).resolve().parent / "_ui_packages_layout_cdp.png"
        cdp_shot.write_bytes(base64.b64decode(shot.get("data") or ""))
        print("cdp_shot", cdp_shot)
    except Exception as e:
        print(f"cdp shot skip: {e}")

    ws.close()

    fails = []
    if not layout:
        fails.append("no layout")
    else:
        if not layout.get("noSummaryBar"):
            fails.append("top summary bar still present")
        if not layout.get("noIntroTitle"):
            fails.append("intro title 选择套餐 still present")
        if layout.get("hasCurrent") and not layout.get("firstIsCurrent"):
            fails.append("current package not pinned to top")
        if layout.get("firstIsCurrent") and layout.get("firstBadge") != "当前套餐":
            fails.append(f"top card badge not 当前套餐: {layout.get('firstBadge')!r}")
        if layout.get("brandHeader"):
            fails.append("brand header still visible")
        if (layout.get("cardCount") or 0) < 1:
            fails.append("no package cards")
        if (layout.get("features") or 0) < 1:
            fails.append("no feature checklist")
        if not layout.get("hasAmount"):
            fails.append("missing large price amount")
        title = layout.get("titleSample") or ""
        if not (title.startswith("【") and title.endswith("】")):
            fails.append(f"title not 【name】 style: {title!r}")
        if layout.get("oldChipLayout"):
            fails.append("old duration/traffic chips still present")
        if layout.get("priceRowSideBtn"):
            fails.append("old side-by-side price+button row still present")
        if not layout.get("fullWidthCta"):
            fails.append("CTA not full-width")
        if layout.get("actionTag") != "BUTTON":
            fails.append(f"action not button: {layout.get('actionTag')}")

    if fails:
        for f in fails:
            print("FAIL", f)
        return 1
    print("PASS packages member-style layout")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
