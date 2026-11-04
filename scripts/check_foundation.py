"""Validate P1 original assets, contrast, wrapper and repository hygiene."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
colors = json.loads((ROOT / "assets/brand/tokens.json").read_text())["colors"]


def luminance(hex_color):
    values = [int(hex_color[i:i+2], 16) / 255 for i in (1, 3, 5)]
    channels = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in values]
    return sum(a * b for a, b in zip(channels, (0.2126, 0.7152, 0.0722)))


for foreground, background in [("cream", "ink"), ("muted", "ink"), ("amber", "ink"),
                               ("cream", "surface"), ("muted", "surface"), ("error", "surface")]:
    light, dark = sorted([luminance(colors[foreground]), luminance(colors[background])], reverse=True)
    ratio = (light + 0.05) / (dark + 0.05)
    assert ratio >= 4.5, (foreground, background, ratio)
    print(f"PASS contrast {foreground}/{background}: {ratio:.2f}:1")

for name, dimensions in [("mark", (512, 512)), ("logo", (940, 240)), ("avatar", (256, 256))]:
    ET.parse(ROOT / f"assets/brand/{name}.svg")
    with Image.open(ROOT / f"assets/brand/{name}.png") as image:
        image.load()
        assert image.size == dimensions and image.getbbox() is not None

for density, size in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
    for filename in ("ic_launcher", "ic_launcher_round"):
        with Image.open(ROOT / f"android/app/src/main/res/mipmap-{density}/{filename}.png") as image:
            image.load()
            assert image.size == (size, size)
for path in (ROOT / "android/app/src/main").rglob("*.xml"):
    ET.parse(path)
print("PASS SVG, PNG dimensions and Android XML")

wrapper = ROOT / "android/gradle/wrapper/gradle-wrapper.jar"
assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
print("PASS Gradle wrapper JAR matches official 8.11.1 SHA-256")

paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT).decode().split("\0")
patterns = [rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----",
            rb"gh[pousr]_[A-Za-z0-9]{30,}", rb"github_pat_[A-Za-z0-9_]{60,}", rb"AKIA[0-9A-Z]{16}"]
for name in filter(None, paths):
    path = ROOT / name
    assert path.suffix.lower() not in {".apk", ".aab", ".dex", ".smali", ".sqlite", ".db", ".keystore", ".jks", ".p12", ".key"}, name
    assert not (path.name == ".env" or path.name.startswith(".env.")) or path.name == ".env.example", name
    data = path.read_bytes()
    if b"\0" not in data:
        assert not any(re.search(pattern, data) for pattern in patterns), f"Potential credential: {name}"
print("PASS baseline credential/artifact guard (not a complete security audit)")
