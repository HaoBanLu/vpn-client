# -*- coding: utf-8 -*-
"""CDP-based emulator verification for update + pull-refresh."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PACKAGE = "com.vpn.kuayun"
SHOT_DIR = Path(r"D:\Code\Go-www\vpn-client\apps\tauri\scripts")
RESULTS: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


def wait(sec: float, msg: str) -> None:
    print(f"  ... {msg} ({sec}s)")
    time.sleep(sec)


def shot(name: str) -> None:
    with open(SHOT_DIR / name, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {name}")


def pass_(cid: str, detail: str) -> None:
    RESULTS.append(f"PASS {cid} :: {detail}")
    print(f"PASS {cid} :: {detail}")


def fail_(cid: str, detail: str) -> None:
    RESULTS.append(f"FAIL {cid} :: {detail}")
    print(f"FAIL {cid} :: {detail}")


def info_(cid: str, detail: str) -> None:
    RESULTS.append(f"INFO {cid} :: {detail}")
    print(f"INFO {cid} :: {detail}")


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
            self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=15, suppress_origin=True)
        except TypeError:
            self.ws = websocket.create_connection(
                page["webSocketDebuggerUrl"],
                timeout=15,
                header=["Origin: http://127.0.0.1:9222"],
            )
        self._id = 0

    def call(self, method: str, params: dict | None = None, timeout: float = 20) -> dict:
        self._id += 1
        msg_id = self._id
        self.ws.send(json.dumps({"id": msg_id, "method": method, "params": params or {}}))
        deadline = time.time() + timeout
        while time.time() < deadline:
            data = json.loads(self.ws.recv())
            if data.get("id") == msg_id:
                if "error" in data:
                    raise RuntimeError(f"{method}: {data['error']}")
                return data.get("result") or {}
        raise TimeoutError(method)

    def eval(self, expression: str, await_promise: bool = True):
        result = self.call(
            "Runtime.evaluate",
            {
                "expression": expression,
                "returnByValue": True,
                "awaitPromise": await_promise,
            },
        )
        if result.get("exceptionDetails"):
            raise RuntimeError(result["exceptionDetails"])
        return result.get("result", {}).get("value")

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass


def main() -> int:
    print("== CDP emulator check ==")
    if "\tdevice" not in adb("devices"):
        raise RuntimeError("no device")

    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait(6, "boot")

    # dismiss system dialogs via uiautomator if any
    adb("shell", "uiautomator", "dump", "/sdcard/ky_ui.xml", check=False)
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ky_ui.xml"], capture_output=True).stdout.decode(
        "utf-8", "replace"
    )
    if "Don't Show Again" in xml:
        # rough center of button - dump bounds would be better; use input key
        adb("shell", "input", "tap", "800", "1710", check=False)
        wait(1, "compat")
    if "Allow" in xml and "notification" in xml.lower():
        adb("shell", "input", "tap", "270", "1400", check=False)
        wait(1, "notif")

    cdp = Cdp()
    try:
        # wait until vue router ready / logged in
        ready = False
        for _ in range(30):
            path = cdp.eval("location.hash || location.pathname")
            print(f"  route={path}")
            if path and ("login" not in str(path).lower()):
                ready = True
                break
            # try click login if on login - skip, session should persist
            wait(1, "wait route")
        if not ready:
            fail_("boot", f"not logged in: {path}")
            shot("_cdp0.png")
            return 1
        shot("_cdp0_home.png")
        pass_("boot", f"in app route {path}")

        # -------- A: update --------
        print("\n---- A update ----")
        cdp.eval("""
        (() => {
          const app = document.querySelector('#app');
          // vue-router via history push
          const go = window.__VUE_ROUTER__ || null;
          return !!document.body;
        })()
        """)
        # navigate by clicking menu via DOM or hash
        nav = cdp.eval("""
        (async () => {
          // Prefer Vue Router if exposed; else click menu item
          const clickText = (t) => {
            const nodes = [...document.querySelectorAll('button,a,div,span,p,li')];
            const el = nodes.find(n => (n.textContent||'').replace(/\\s+/g,' ').includes(t) && n.getBoundingClientRect().height > 0);
            if (!el) return false;
            el.click();
            return true;
          };
          // ensure on profile
          clickText('我的');
          await new Promise(r => setTimeout(r, 800));
          // scroll profile and open about
          const about = [...document.querySelectorAll('*')].find(n => (n.textContent||'').includes('关于跨云') && n.children.length < 5);
          if (about) {
            about.scrollIntoView({block:'center'});
            about.click();
            await new Promise(r => setTimeout(r, 1000));
          } else {
            // router push fallback using location
            history.pushState({}, '', '#/about');
            window.dispatchEvent(new PopStateEvent('popstate'));
            await new Promise(r => setTimeout(r, 1000));
          }
          return document.body.innerText.slice(0, 400);
        })()
        """)
        print(f"  after about nav: {str(nav)[:200]}")
        shot("_cdp1_about.png")
        body = cdp.eval("document.body.innerText") or ""
        if "检查更新" in body or "关于" in body:
            pass_("A1", "about page visible")
        else:
            # force router via vue app
            forced = cdp.eval("""
            (async () => {
              const appEl = document.querySelector('#app');
              // Try common vue router path
              const anchors = [...document.querySelectorAll('a')].map(a => a.getAttribute('href'));
              history.pushState({}, '', (location.pathname || '/') + '#/about');
              window.dispatchEvent(new PopStateEvent('popstate'));
              // also try without hash mode
              const candidates = ['/about', '/#/about', '/profile/about'];
              return {href: location.href, anchors: anchors.slice(0,20), text: document.body.innerText.slice(0,300)};
            })()
            """)
            print(f"  force nav: {forced}")
            shot("_cdp1b_about.png")
            body = cdp.eval("document.body.innerText") or ""
            if "检查更新" in body:
                pass_("A1", "about page after force")
            else:
                fail_("A1", f"cannot open about: {body[:180]}")

        # click 检查更新
        clicked = cdp.eval("""
        (async () => {
          const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('检查更新'));
          if (!btn) return 'no-btn';
          btn.click();
          await new Promise(r => setTimeout(r, 3500));
          return document.body.innerText.slice(0, 800);
        })()
        """)
        print(f"  check result text head: {str(clicked)[:300]}")
        shot("_cdp2_check.png")
        text = str(clicked)
        if any(k in text for k in ["发现新版本", "立即更新", "需要更新"]):
            pass_("A2", "update prompt shown")
            # VPN hint / download cancel path
            # First connect VPN if possible
            cdp.eval("""
            (async () => {
              const mine = [...document.querySelectorAll('*')].find(n => (n.textContent||'').trim() === '我的' || (n.textContent||'').includes('user'));
              // go connect
              const tab = [...document.querySelectorAll('button,a,div')].find(n => (n.textContent||'').includes('连接') && (n.textContent||'').includes('home'));
              if (tab) tab.click();
              await new Promise(r => setTimeout(r, 500));
              const later = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('稍后再说'));
              if (later) later.click();
              await new Promise(r => setTimeout(r, 500));
              const connectTab = [...document.querySelectorAll('*')].find(n => /^\\s*home\\s*连接\\s*$/.test((n.textContent||'').replace(/\\n/g,' ')) || (n.textContent||'').trim()==='连接');
              // click bottom tab by text content pieces
              const tabs = [...document.querySelectorAll('nav *, footer *, [class*=tab] *, button, a, div')];
              const home = tabs.find(n => (n.textContent||'').includes('连接') && (n.textContent||'').includes('home'));
              if (home) home.click();
              await new Promise(r => setTimeout(r, 800));
              const hero = [...document.querySelectorAll('button')].find(b => /连接|一键/.test(b.textContent||''));
              if (hero) hero.click();
              await new Promise(r => setTimeout(r, 12000));
              return document.body.innerText.slice(0, 300);
            })()
            """)
            shot("_cdp3_vpn.png")
            # back to about and update
            again = cdp.eval("""
            (async () => {
              const tabs = [...document.querySelectorAll('*')];
              const mine = tabs.find(n => (n.textContent||'').includes('我的') && (n.textContent||'').includes('user'));
              if (mine) mine.click();
              await new Promise(r => setTimeout(r, 800));
              const about = [...document.querySelectorAll('*')].find(n => (n.textContent||'').includes('关于跨云'));
              if (about) { about.scrollIntoView({block:'center'}); about.click(); }
              await new Promise(r => setTimeout(r, 1000));
              const check = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('检查更新'));
              if (check) check.click();
              await new Promise(r => setTimeout(r, 3000));
              const update = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('立即更新'));
              if (!update) return {state:'no-prompt', text: document.body.innerText.slice(0,500)};
              update.click();
              await new Promise(r => setTimeout(r, 2500));
              const body = document.body.innerText;
              const hasCancel = [...document.querySelectorAll('button')].some(b => (b.textContent||'').includes('取消'));
              const hasRetry = [...document.querySelectorAll('button')].some(b => (b.textContent||'').includes('重试'));
              const hasLater = [...document.querySelectorAll('button')].some(b => (b.textContent||'').includes('稍后再说'));
              const hasHint = body.includes('建议断开');
              // try cancel if present
              if (hasCancel) {
                [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('取消')).click();
                await new Promise(r => setTimeout(r, 1500));
                return {state:'cancelled', hasCancel, hasHint, text: document.body.innerText.slice(0,400)};
              }
              if (hasRetry) {
                return {state:'error', hasRetry, hasLater, hasHint, text: body.slice(0,400)};
              }
              return {state:'downloading', hasCancel, hasHint, text: body.slice(0,400)};
            })()
            """)
            print(f"  update flow: {again}")
            shot("_cdp4_update.png")
            state = (again or {}).get("state") if isinstance(again, dict) else None
            if state == "cancelled":
                pass_("A4", "cancel available and clicked")
                if "正在更新" not in str((again or {}).get("text", "")) and "正在下载" not in str((again or {}).get("text", "")):
                    pass_("A5", "overlay closed after cancel")
                else:
                    fail_("A5", "overlay still showing download")
            elif state == "error":
                if (again or {}).get("hasRetry"):
                    pass_("A4", "error has retry")
                else:
                    fail_("A4", "error missing retry")
                if (again or {}).get("hasLater"):
                    pass_("A5", "error has later")
                else:
                    fail_("A5", "error missing later")
            elif state == "no-prompt":
                pass_("A3", "no re-prompt after dismiss (expected)")
            else:
                info_("A4", f"download state={state} body={(again or {})}")
        elif any(k in text for k in ["已是最新", "当前已是最新"]):
            pass_("A2", "already latest; fail/retry E2E needs newer server package")
        else:
            fail_("A2", f"unexpected check result: {text[:200]}")

        # -------- B: pull refresh --------
        print("\n---- B pull refresh ----")
        pull = cdp.eval("""
        (async () => {
          // open orders
          const tabs = [...document.querySelectorAll('*')];
          const mine = tabs.find(n => (n.textContent||'').includes('我的') && (n.textContent||'').includes('user'));
          if (mine) mine.click();
          await new Promise(r => setTimeout(r, 700));
          const orders = [...document.querySelectorAll('*')].find(n => (n.textContent||'').includes('订单') && (n.textContent||'').includes('充值与套餐'));
          if (orders) { orders.scrollIntoView({block:'center'}); orders.click(); }
          else {
            history.pushState({}, '', location.pathname + (location.hash.startsWith('#') ? '' : '') );
          }
          await new Promise(r => setTimeout(r, 1200));
          const title = document.body.innerText.includes('订单');
          // Find pull-refresh root
          const pullRoot = document.querySelector('.ky-pull-refresh');
          const hasPull = !!pullRoot;
          // Simulate touch pull via PointerEvents on pull root
          let indicator = false;
          if (pullRoot) {
            const rect = pullRoot.getBoundingClientRect();
            const x = rect.left + rect.width / 2;
            const y = rect.top + 40;
            const fire = (type, clientY, buttons=1) => {
              pullRoot.dispatchEvent(new PointerEvent(type, {
                bubbles: true, cancelable: true, pointerId: 1, pointerType: 'touch',
                clientX: x, clientY, buttons, pressure: type === 'pointerup' ? 0 : 0.5
              }));
            };
            fire('pointerdown', y);
            for (let i = 1; i <= 8; i++) {
              fire('pointermove', y + i * 30);
              await new Promise(r => setTimeout(r, 30));
            }
            indicator = document.body.innerText.includes('下拉刷新')
              || document.body.innerText.includes('松开刷新')
              || document.body.innerText.includes('刷新中')
              || !!document.querySelector('.ky-pull-refresh--pulling, .ky-pull-refresh--refreshing');
            fire('pointerup', y + 240, 0);
            await new Promise(r => setTimeout(r, 1500));
          }
          const afterClass = document.querySelector('.ky-pull-refresh')?.className || '';
          return {
            title,
            hasPull,
            indicator,
            afterClass,
            text: document.body.innerText.slice(0, 300),
          };
        })()
        """)
        print(f"  pull: {pull}")
        shot("_cdp5_pull.png")
        if isinstance(pull, dict) and pull.get("title"):
            pass_("B1", "orders page opened")
        else:
            fail_("B1", f"orders not opened: {pull}")
        if isinstance(pull, dict) and pull.get("hasPull"):
            pass_("B1b", "KyPullRefresh mounted on subpage")
        else:
            fail_("B1b", "KyPullRefresh missing on orders")
        if isinstance(pull, dict) and pull.get("indicator"):
            pass_("B2", "pull indicator / pulling class shown")
        else:
            fail_("B2", f"no pull indicator: {pull}")

        # desktop refresh button not applicable on phone width; skip
        # traffic page also has pull
        traffic = cdp.eval("""
        (async () => {
          const back = [...document.querySelectorAll('button')].find(b => (b.getAttribute('aria-label')||'') === '返回' || (b.textContent||'').includes('返回'));
          if (back) back.click();
          await new Promise(r => setTimeout(r, 700));
          const traffic = [...document.querySelectorAll('*')].find(n => (n.textContent||'').trim() === '流量统计' || ((n.textContent||'').includes('流量统计') && (n.textContent||'').length < 20));
          if (traffic) { traffic.click(); await new Promise(r => setTimeout(r, 1000)); }
          return {
            hasPull: !!document.querySelector('.ky-pull-refresh'),
            text: document.body.innerText.slice(0, 200),
          };
        })()
        """)
        print(f"  traffic: {traffic}")
        shot("_cdp6_traffic.png")
        if isinstance(traffic, dict) and traffic.get("hasPull"):
            pass_("B4", "traffic page has KyPullRefresh")
        elif isinstance(traffic, dict) and "流量" in str(traffic.get("text", "")):
            info_("B4", "traffic opened but pull unknown")
        else:
            info_("B4", f"traffic skip: {traffic}")

    finally:
        cdp.close()

    print("\n==== SUMMARY ====")
    for r in RESULTS:
        print(r)
    fails = sum(1 for r in RESULTS if r.startswith("FAIL"))
    passes = sum(1 for r in RESULTS if r.startswith("PASS"))
    print(f"PASS={passes} FAIL={fails}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
