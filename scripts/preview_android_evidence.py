"""Compact visual review of real, synthetic-only emulator screenshots.

Original PNGs and their hashes remain in the CI artifact. The review JPEG is
also emitted through authenticated GitHub logs for clients unable to open ZIPs.
"""
from pathlib import Path
import base64
import hashlib
import io
import json
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont, ImageOps

root = Path(__file__).resolve().parents[1] / "work/screenshots"
project = root.parents[1]
for report in (project / "android/app/build/outputs/androidTest-results").rglob("TEST-*.xml"):
    for testcase in ET.parse(report).getroot().iter("testcase"):
        for failure in testcase.findall("failure"):
            lines = (failure.text or "").splitlines()
            print("P3_TEST_FAILURE=" + testcase.get("classname", "") + "." + testcase.get("name", ""))
            if lines:
                print(lines[0])
            for line in lines:
                if "P4_" in line or "P5_" in line or "at ru.krapiva." in line or "WakeLockRefCount" in line or "status:" in line or "Connection Number:" in line:
                    print(line)
paths = sorted(root.rglob("p[345]-*.png"))
if not paths:
    print("No emulator screenshots available; visual acceptance is not claimed.")
    raise SystemExit(0)

cell_width, cell_height, columns = 360, 680, 3
sheet = Image.new("RGB", (cell_width * columns, cell_height * ((len(paths) + columns - 1) // columns)), "#171A1D")
draw = ImageDraw.Draw(sheet)
try:
    font = ImageFont.truetype("DejaVuSans.ttf", 14)
except OSError:
    font = ImageFont.load_default(size=14)
manifest = []
for index, path in enumerate(paths):
    x, y = (index % columns) * cell_width, (index // columns) * cell_height
    draw.text((x + 8, y + 6), path.name, fill="#F5EBD8", font=font)
    with Image.open(path) as image:
        preview = ImageOps.contain(image.convert("RGB"), (cell_width - 8, cell_height - 30))
        sheet.paste(preview, (x + (cell_width - preview.width) // 2, y + 28))
        dimensions = list(image.size)
    manifest.append({"path": str(path.relative_to(root)), "dimensions": dimensions, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

(root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
stream = io.BytesIO()
sheet.save(stream, "JPEG", quality=78, optimize=True)
(root / "p3-review.jpg").write_bytes(stream.getvalue())
print("P3_SCREENSHOT_MANIFEST=" + json.dumps(manifest))
print("P3_SCREENSHOTS_JPEG=" + base64.b64encode(stream.getvalue()).decode("ascii"))
