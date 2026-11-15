"""Real emulator sensor events -> actual screenshot liquid slope. No mock backend."""
from pathlib import Path
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "work/screenshots/sensors"
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=30)


# Run the real loading composable in an instrumentation harness. Production loading
# ends as soon as restoration completes and must never be delayed for screenshots.
log = (OUT / "loading-instrumentation.log").open("wb")
instrumentation = subprocess.Popen(["adb", "shell", "am", "instrument", "-w", "-e", "class",
    "ru.krapiva.app.designsystem.LoadingMotionTest",
    "ru.krapiva.app.debug.test/androidx.test.runner.AndroidJUnitRunner"], stdout=log, stderr=subprocess.STDOUT)
deadline = time.monotonic() + 30
rectangle = None
while time.monotonic() < deadline:
    adb("shell", "uiautomator", "dump", "/sdcard/p3-ui.xml")
    xml = adb("shell", "cat", "/sdcard/p3-ui.xml")
    nodes = list(ET.fromstring(xml).iter("node"))
    for node in nodes:
        if node.get("content-desc") == "Фирменный бокал KraPiva":
            rectangle = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            break
    if rectangle:
        break
    time.sleep(1)
assert rectangle and len(rectangle) == 4, "Visible loading sensor hero not found"


def capture(name, x):
    adb("emu", "sensor", "set", "acceleration", f"{x}:9.2:0")
    time.sleep(2)  # Settle the actual gravity filter; never a product startup delay.
    path = OUT / f"p3-sensor-{name}.png"
    path.write_bytes(adb("exec-out", "screencap", "-p"))
    with Image.open(path) as image:
        crop = image.convert("RGB").crop(rectangle)
        width, height = crop.size

        def surface(fraction):
            values = []
            for column in range(int(width * (fraction - .015)), int(width * (fraction + .015)) + 1):
                for row in range(int(height * .08), int(height * .55)):
                    red, green, blue = crop.getpixel((column, row))
                    if red > 185 and 85 < green < 210 and blue < 120:
                        values.append(row)
                        break
            assert values, "Amber liquid surface not found"
            return statistics.median(values)

        return surface(.66) - surface(.34), width


left, width = capture("left", -4)
right, _ = capture("right", 4)
adb("emu", "sensor", "set", "acceleration", "0:9.81:0")
assert left * right < 0 and abs(left - right) >= max(2, width * .02), (left, right)
print(f"PASS actual accelerometer -> liquid/foam surface: left={left}px, right={right}px; hero={width}px")
instrumentation.wait(timeout=45)
log.close()
result = (OUT / "loading-instrumentation.log").read_text()
assert instrumentation.returncode == 0 and "OK (1 test)" in result, "Loading instrumentation failed; inspect its log"
