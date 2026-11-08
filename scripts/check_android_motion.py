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


adb("shell", "am", "start", "-W", "-n", "ru.krapiva.app.debug/ru.krapiva.app.MainActivity")
deadline = time.monotonic() + 30
rectangle = None
while time.monotonic() < deadline:
    adb("shell", "uiautomator", "dump", "/sdcard/p3-ui.xml")
    xml = adb("shell", "cat", "/sdcard/p3-ui.xml")
    for node in ET.fromstring(xml).iter("node"):
        if node.get("content-desc") == "Фирменный бокал KraPiva с пеной и хмелем":
            rectangle = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            break
    if rectangle:
        break
    time.sleep(1)
assert rectangle and len(rectangle) == 4, "Visible welcome hero not found"


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
                for row in range(int(height * .25), int(height * .65)):
                    red, green, blue = crop.getpixel((column, row))
                    if abs(red - 245) < 25 and abs(green - 166) < 25 and abs(blue - 35) < 25:
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
