"""Actual process death/relaunch with the real E2E account's Keystore session."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / "work/screenshots/cold-start"
out.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=20)


adb("shell", "am", "force-stop", "ru.krapiva.app.debug")
adb("shell", "am", "start", "-W", "-n", "ru.krapiva.app.debug/ru.krapiva.app.MainActivity")
deadline = time.monotonic() + 45
accepted = False
opened_profile = False
while time.monotonic() < deadline:
    try:
        adb("shell", "rm", "-f", "/sdcard/p3-cold.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/p3-cold.xml")
        nodes = list(ET.fromstring(adb("shell", "cat", "/sdcard/p3-cold.xml")).iter("node"))
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
        time.sleep(1)
        continue
    assert not any(node.get("resource-id") == "android:id/aerr_close" for node in nodes), "ANR/crash after cold start"
    texts = {node.get("text") for node in nodes}
    if not opened_profile and "Главная" in texts:
        profile_tab = next((node for node in nodes if node.get("text") == "Профиль"), None)
        if profile_tab is not None:
            left, top, right, bottom = map(int, re.findall(r"\d+", profile_tab.attrib["bounds"]))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            opened_profile = True
            continue
    # Offline cached profiles disable editing. An enabled action after startup
    # proves the fresh ViewModel restored the session and loaded the real API.
    editable = any(node.get("text") == "Редактировать профиль" and node.get("enabled") == "true" for node in nodes)
    if any(text and text.startswith("Moscow") for text in texts) and editable:
        accepted = True
        break
    time.sleep(1)

name = "p3-cold-start.png" if accepted else "p3-cold-start-failure.png"
(out / name).write_bytes(adb("exec-out", "screencap", "-p"))
assert accepted, "Cold process did not restore authenticated online profile from Keystore session"
print("PASS actual force-stop/relaunch -> Keystore session -> real API profile, editing enabled")
