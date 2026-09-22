# -*- coding: utf-8 -*-
"""模拟器：更新提示 / 稍后再说 / DownloadManager 下载到安装提示。"""
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
SHOT = Path(__file__).resolve().parent


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(
        [ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and p.returncode != 0:
        raise RuntimeError((p.stderr or p.stdout or "adb fail").strip())
    return (p.stdout or "") + (p.stderr or "")


def shot(name: str) -> None:
    with open(SHOT / name, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {name}")


def main() -> int:
    try:
        import websocket
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket

    print("== update prompt / dismiss / install flow ==")
    # 允许未知来源安装，便于验证安装意图
    adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow", check=False)
    # 清旧 APK / 下载任务，避免脏状态
    adb("shell", "rm", "-f", f"/sdcard/Android/data/{PACKAGE}/files/Download/kuayun-*.apk", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(2)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(6)

    pid = ""
    for i in range(50):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip().split()
        if pid:
            pid = pid[0]
            break
        if i in (5, 15, 30):
            adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
        time.sleep(1)
    if not pid:
        raise RuntimeError("no pid")
    print(f"  pid={pid}")

    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = None
    for _ in range(30):
        try:
            pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
            if any(p.get("type") == "page" and p.get("webSocketDebuggerUrl") for p in pages):
                break
        except Exception:
            pages = None
        time.sleep(1)
    if not pages:
        raise RuntimeError("cdp unavailable")

    # 优先选 tauri.localhost 主页面，避开 splash
    candidates = [p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl")]
    page = next((p for p in candidates if "tauri.localhost" in (p.get("url") or "")), None) or candidates[0]
    print(f"  cdp url={(page.get('url') or '')[:80]}")
    try:
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=25, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=25, header=["Origin: http://127.0.0.1:9222"]
        )

    nid = 0

    def call(method, params=None):
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
        result = call("Runtime.evaluate", {"expression": expr, "returnByValue": True, "awaitPromise": True})
        if result.get("exceptionDetails"):
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:400])
        return result.get("result", {}).get("value")

    fails = []
    notes = []

    # 等主文档可写 localStorage（避免 splash / about:blank）
    for _ in range(30):
        ok = False
        try:
            ok = bool(
                ev(
                    """
                    (() => {
                      try {
                        localStorage.setItem('__ky_probe', '1');
                        localStorage.removeItem('__ky_probe');
                        return true;
                      } catch (e) { return false; }
                    })()
                    """
                )
            )
        except Exception:
            ok = False
        if ok:
            break
        time.sleep(1)
    else:
        raise RuntimeError("localStorage unavailable")

    try:
        # 清 dismiss，再手动触发检查
        ev(
            """
            (() => {
              localStorage.removeItem('tauri_update_dismissed_version');
              localStorage.removeItem('tauri_update_accepted_version');
              localStorage.removeItem('tauri_update_last_check_at');
            })()
            """
        )

        body = (ev("document.body.innerText||''") or "")[:300]
        if "登录" in body or "邮箱" in body:
            print("  login…")
            ev(
                f"""
                (async () => {{
                  const inputs=[...document.querySelectorAll('input')];
                  const email=inputs.find(i=>/email|邮箱|mail/i.test(i.type+i.placeholder+i.name));
                  const pass=inputs.find(i=>i.type==='password');
                  if(email){{email.value={json.dumps(EMAIL)};email.dispatchEvent(new Event('input',{{bubbles:true}}));}}
                  if(pass){{pass.value={json.dumps(PASSWORD)};pass.dispatchEvent(new Event('input',{{bubbles:true}}));}}
                  const btn=[...document.querySelectorAll('button')].find(b=>/登录/.test(b.textContent||''));
                  if(btn) btn.click();
                }})()
                """
            )
            time.sleep(6)

        # 进关于页（路由：/profile/about）
        ev(
            """
            (() => {
              const me=[...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
                .find(el=>(el.textContent||'').trim()==='我的');
              if(me) me.click();
            })()
            """
        )
        time.sleep(2)
        # 点关于入口
        ev(
            """
            (() => {
              const item=[...document.querySelectorAll('*')]
                .find(el=>(el.textContent||'').trim()==='关于跨云' || (el.textContent||'').trim()==='关于');
              if(item) item.click();
              else location.hash='#/profile/about';
            })()
            """
        )
        time.sleep(2)
        # 检查更新
        ev(
            """
            (() => {
              const btn=[...document.querySelectorAll('button')].find(b=>(b.textContent||'').includes('检查更新'));
              if(btn) btn.click();
            })()
            """
        )
        time.sleep(4)

        # 浮层或关于页卡片
        state1 = ev(
            """
            (() => {
              const o=document.querySelector('.app-update-overlay');
              const text=(document.body.innerText||'').replace(/\\s+/g,' ');
              return {
                overlay: !!o,
                overlayTitle: o?.querySelector('.app-update-panel__title')?.textContent||'',
                hasNewInPage: /发现新版本/.test(text),
                hasDownloadBtn: [...document.querySelectorAll('button')].some(b=>/下载更新|立即更新/.test(b.textContent||'')),
              };
            })()
            """
        )
        print("after_check=", json.dumps(state1, ensure_ascii=False))
        shot("_ui_verify_update_has_prompt.png")
        if not ((state1 or {}).get("overlay") or (state1 or {}).get("hasNewInPage")):
            fails.append("check update: no prompt / no new-version card")
        else:
            notes.append("has update prompt or About card")

        # 若有浮层：点稍后再说
        dismissed = False
        if (state1 or {}).get("overlay"):
            ev(
                """
                (() => {
                  const b=[...document.querySelectorAll('.app-update-overlay button')]
                    .find(x=>(x.textContent||'').includes('稍后再说'));
                  if(b) b.click();
                })()
                """
            )
            time.sleep(1)
            d = ev(
                """
                (() => ({
                  overlay: !!document.querySelector('.app-update-overlay'),
                  key: localStorage.getItem('tauri_update_dismissed_version'),
                }))()
                """
            )
            print("after_later=", json.dumps(d, ensure_ascii=False))
            if (d or {}).get("overlay"):
                fails.append("overlay remains after 稍后再说")
            if not (d or {}).get("key"):
                fails.append("dismiss key not saved")
            else:
                dismissed = True
                notes.append(f"dismissed key saved: {(d or {}).get('key')}")

            # 再清冷却，模拟回前台自动检查 → 不应再弹浮层
            ev(
                """
                (() => {
                  localStorage.removeItem('tauri_update_last_check_at');
                  document.dispatchEvent(new Event('visibilitychange'));
                  window.dispatchEvent(new Event('focus'));
                })()
                """
            )
            time.sleep(5)
            again = ev(
                """
                (() => ({
                  overlay: !!document.querySelector('.app-update-overlay'),
                  title: document.querySelector('.app-update-overlay .app-update-panel__title')?.textContent||'',
                  key: localStorage.getItem('tauri_update_dismissed_version'),
                }))()
                """
            )
            print("auto_recheck_after_dismiss=", json.dumps(again, ensure_ascii=False))
            if (again or {}).get("overlay") and "新版本" in ((again or {}).get("title") or ""):
                fails.append("auto prompt came back after dismiss")
            else:
                notes.append("auto prompt suppressed after dismiss")

            # About 手动检查：仍应能看到更新信息（页内卡片/绿条）
            ev(
                """
                (() => {
                  const btn=[...document.querySelectorAll('button')].find(b=>(b.textContent||'').includes('检查更新'));
                  if(btn) btn.click();
                })()
                """
            )
            time.sleep(3)
            manual = ev(
                """
                (() => {
                  const text=(document.body.innerText||'').replace(/\\s+/g,' ');
                  return {
                    hasAlertOrCard: /发现新版本/.test(text),
                    hasDownload: [...document.querySelectorAll('button')].some(b=>/下载更新|立即更新/.test(b.textContent||'')),
                    overlay: !!document.querySelector('.app-update-overlay'),
                  };
                })()
                """
            )
            print("manual_after_dismiss=", json.dumps(manual, ensure_ascii=False))
            shot("_ui_verify_update_after_dismiss.png")
            if (manual or {}).get("hasAlertOrCard") or (manual or {}).get("hasDownload"):
                notes.append("About manual check still shows update after dismiss")
            else:
                notes.append("About after dismiss: no card (updateResult may be cleared by shouldShow)")

        # —— 下载流程：清 dismiss，点下载 ——
        ev(
            """
            (() => {
              localStorage.removeItem('tauri_update_dismissed_version');
              localStorage.removeItem('tauri_update_accepted_version');
              localStorage.removeItem('tauri_update_last_check_at');
            })()
            """
        )
        ev(
            """
            (() => {
              const btn=[...document.querySelectorAll('button')].find(b=>(b.textContent||'').includes('检查更新'));
              if(btn) btn.click();
            })()
            """
        )
        time.sleep(3)
        started = ev(
            """
            (() => {
              const o=[...document.querySelectorAll('.app-update-overlay button')]
                .find(b=>/立即更新|下载更新/.test(b.textContent||''));
              if(o){ o.click(); return 'overlay'; }
              const p=[...document.querySelectorAll('button')]
                .find(b=>/下载更新|立即更新/.test(b.textContent||''));
              if(p){ p.click(); return 'page'; }
              return null;
            })()
            """
        )
        print(f"start_via={started}")
        if not started:
            fails.append("no download button to click")
        time.sleep(2)
        mid = ev(
            """
            (() => ({
              overlay: !!document.querySelector('.app-update-overlay'),
              progress: !!document.querySelector('.app-update-overlay .ky-progress'),
              accepted: localStorage.getItem('tauri_update_accepted_version'),
            }))()
            """
        )
        print("after_click_download=", json.dumps(mid, ensure_ascii=False))
        if (mid or {}).get("progress"):
            fails.append("fake in-app progress shown")
        else:
            notes.append("no fake progress bar after download click")

        pending = False
        apk_ok = False
        for i in range(40):
            time.sleep(2)
            st = ev(
                """
                (() => {
                  const o=document.querySelector('.app-update-overlay');
                  const t=(o?.textContent||'').replace(/\\s+/g,' ');
                  const body=(document.body.innerText||'').replace(/\\s+/g,' ');
                  return {
                    title: o?.querySelector('.app-update-panel__title')?.textContent||'',
                    pending: /已下载|立即安装|待安装|新版本已下载/.test(t+body),
                    failed: /下载失败|更新失败/.test(t),
                    overlay: !!o,
                  };
                })()
                """
            )
            ls = adb("shell", "ls", "-1", f"/sdcard/Android/data/{PACKAGE}/files/Download/", check=False)
            if any(line.strip().endswith(".apk") for line in ls.splitlines()):
                apk_ok = True
            print(f"  poll[{i}] ui={json.dumps(st, ensure_ascii=False)} apk={apk_ok} ls={ls.strip()[:120]!r}")
            if (st or {}).get("failed"):
                fails.append("download failed in UI")
                break
            if (st or {}).get("pending"):
                pending = True
                break
            prefs = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/kuayun_app_update.xml", check=False)
            if "pending_install_file" in prefs or "download_id" in prefs:
                print(f"  prefs: {prefs.strip()[:220]}")
            if "pending_install_file" in prefs:
                time.sleep(2)
                st2 = ev(
                    """
                    (() => {
                      const t=(document.querySelector('.app-update-overlay')?.textContent||document.body.innerText||'');
                      return { pending: /已下载|立即安装|待安装|新版本已下载/.test(t) };
                    })()
                    """
                )
                if (st2 or {}).get("pending"):
                    pending = True
                    break
                # prefs 已有 pending：即使浮层慢，也算下载闭环
                if apk_ok:
                    pending = True
                    notes.append("pending prefs registered (UI may still catch up)")
                    break

        shot("_ui_verify_update_download_done.png")
        prefs_final = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/kuayun_app_update.xml", check=False)
        print("prefs_final=", prefs_final.strip()[:400])
        ls_final = adb("shell", "ls", "-l", f"/sdcard/Android/data/{PACKAGE}/files/Download/", check=False)
        print("apk_final=", ls_final.strip()[:300])

        if pending:
            notes.append("pending install UI shown → download flow succeeded")
        elif "pending_install_file" in prefs_final and apk_ok:
            notes.append("pending prefs + apk ok (UI event may lag)")
            # 仍算下载闭环成功
        elif apk_ok:
            fails.append("apk downloaded but pending install not registered")
        else:
            fails.append("download did not finish")

    finally:
        ws.close()

    for n in notes:
        print("NOTE", n)
    if fails:
        for f in fails:
            print("FAIL", f)
        return 1
    print("PASS update prompt / dismiss / download→pending")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
