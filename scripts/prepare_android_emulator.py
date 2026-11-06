"""Wait for AOSP launcher readiness; never dismiss an app-under-test ANR."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=20)


adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
deadline = time.monotonic() + 75
stable = 0
while time.monotonic() < deadline:
    adb("shell", "uiautomator", "dump", "/sdcard/p3-boot.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/p3-boot.xml"))
    nodes = list(root.iter("node"))
    close = next((node for node in nodes if node.get("resource-id") == "android:id/aerr_close"), None)
    if close is not None:
        # AOSP15 can show a boot-only Quickstep ANR. It is an emulator fixture
        # problem, never evidence of a KraPiva failure or a usable baseline.
        assert any("Quickstep" in node.get("text", "") for node in nodes), "Unexpected crash/ANR during emulator boot"
        bounds = list(map(int, re.findall(r"\d+", close.get("bounds", ""))))
        assert len(bounds) == 4
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        print("Closed stock Quickstep boot ANR; waiting for clean launcher")
        stable = 0
    elif any(node.get("package") == "com.android.launcher3" for node in nodes):
        stable += 1
        if stable >= 3:
            print("PASS emulator launcher ready, three clean UI observations")
            break
    else:
        stable = 0
    time.sleep(2)
else:
    raise AssertionError("AOSP launcher did not become ready; no UI acceptance claimed")
