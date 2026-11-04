"""Render original vectors; requires CairoSVG 2.8.2 and Pillow 11.3.0."""
from pathlib import Path
import cairosvg
ROOT = Path(__file__).resolve().parents[1]
for name, width, height in [("mark",512,512),("logo",940,240),("avatar",256,256)]:
    cairosvg.svg2png(url=str(ROOT / f"assets/brand/{name}.svg"),
                    write_to=str(ROOT / f"assets/brand/{name}.png"),
                    output_width=width, output_height=height)
mark = (ROOT / "assets/brand/mark.svg").read_text()
for density, size in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
    folder = ROOT / f"android/app/src/main/res/mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    for filename, radius in [("ic_launcher",24),("ic_launcher_round",54)]:
        # Standalone PNG fallback; adaptive icons use the platform mask.
        inner = mark[mark.index(">")+1:mark.rindex("</svg>")]
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="{radius}" fill="#171512"/><g transform="translate(15.12 15.12) scale(.72)">{inner}</g></svg>'
        cairosvg.svg2png(bytestring=svg.encode(),write_to=str(folder/f"{filename}.png"),output_width=size,output_height=size)
print("Exported brand PNGs and five launcher densities")
