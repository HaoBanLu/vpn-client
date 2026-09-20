# -*- coding: utf-8 -*-
import re
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SHOT = Path(r"D:\Code\Go-www\vpn-client\apps\tauri\scripts\_login_try.png")


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True)


def xml():
    adb("shell", "uiautomator", "dump", "/sdcard/ky_ui.xml")
    return subprocess.run([ADB, "exec-out", "cat", "/sdcard/ky_ui.xml"], capture_output=True).stdout.decode(
        "utf-8", "replace"
    )


def texts():
    return list(dict.fromkeys(re.findall(r'text="([^"]+)"', xml())))


def tap_text(t: str) -> bool:
    x = xml()
    m = re.search(rf'text="{re.escape(t)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        print("miss", t)
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    adb("shell", "input", "tap", str(cx), str(cy))
    print("tap", t, cx, cy)
    return True


def tap_xy(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    print("tap_xy", x, y)


adb("shell", "am", "force-stop", "com.vpn.kuayun")
adb("shell", "am", "start", "-n", "com.vpn.kuayun/.MainActivity")
time.sleep(5)
print("before", texts())

# Focus email input via placeholder
if not tap_text("you@example.com"):
    # fallback: below email label
    x = xml()
    m = re.search(r'text="\u90ae\u7bb1"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        tap_xy((x1 + x2) // 2, y2 + 70)

time.sleep(0.4)
for _ in range(40):
    adb("shell", "input", "keyevent", "67")
# @ must be escaped for input text
adb("shell", "input", "text", r"luban7733\@gmail.com")
time.sleep(0.4)

# Password field: tap below password label
x = xml()
m = re.search(r'text="\u5bc6\u7801"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    tap_xy((x1 + x2) // 2, y2 + 70)
time.sleep(0.3)
for _ in range(20):
    adb("shell", "input", "keyevent", "67")
adb("shell", "input", "text", "123456")
time.sleep(0.3)

tap_text("\u767b\u5f55")
time.sleep(10)
print("after", texts())
with open(SHOT, "wb") as f:
    subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
print("shot", SHOT)
