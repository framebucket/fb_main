"""Synthetic sample photos for the end-to-end suites (no client photos in the repository)."""
from __future__ import annotations

import datetime as dt
import io
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter


def photo(seed: int, size=(1800, 1200)) -> Image.Image:
    rng = random.Random(seed)
    width, height = size
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    top = tuple(rng.randint(40, 220) for _ in range(3))
    bottom = tuple(rng.randint(20, 200) for _ in range(3))
    for y in range(height):
        t = y / height
        draw.line([(0, y), (width, y)], fill=tuple(int(a * (1 - t) + b * t) for a, b in zip(top, bottom)))
    for _ in range(6):
        x, y = rng.randint(0, width), rng.randint(0, height)
        r = rng.randint(60, 260)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=tuple(rng.randint(0, 255) for _ in range(3)))
    return image.filter(ImageFilter.GaussianBlur(1.2))


def jpeg(image: Image.Image, quality: int = 88, taken: dt.datetime | None = None) -> bytes:
    buffer = io.BytesIO()
    exif = Image.Exif()
    if taken:
        exif.get_ifd(0x8769)[0x9003] = taken.strftime("%Y:%m:%d %H:%M:%S")
    image.save(buffer, "JPEG", quality=quality, exif=exif.tobytes())
    return buffer.getvalue()


def write_photos(folder: Path, count: int) -> list[Path]:
    folder.mkdir(parents=True, exist_ok=True)
    start = dt.datetime(2026, 1, 25, 9, 0)
    paths = []
    for index in range(count):
        size = (1200, 1800) if index % 3 == 1 else (1800, 1200)
        path = folder / f"IMG_{index:04d}.jpg"
        path.write_bytes(jpeg(photo(index, size), taken=start + dt.timedelta(minutes=3 * index)))
        paths.append(path)
    return paths


def graded(seed: int, side: int = 1400) -> bytes:
    """A 'finished' photo in a recognisable studio look (muted colour, more contrast, warm highlights)."""
    image = photo(seed, (side, int(side * 2 / 3)))
    image = ImageEnhance.Color(image).enhance(0.6)
    image = ImageEnhance.Contrast(image).enhance(1.25)
    r, g, b = image.split()
    image = Image.merge("RGB", (r.point(lambda v: min(255, v + 12)), g, b.point(lambda v: max(0, v - 10))))
    return jpeg(image, 90)


def logo_png() -> bytes:
    image = Image.new("RGBA", (900, 260), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((20, 40, 880, 220), radius=40, outline=(255, 255, 255, 255), width=14)
    draw.ellipse((60, 80, 200, 180), fill=(255, 255, 255, 255))
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return buffer.getvalue()


# ---- editing presets and reference album designs ----

XMP_PRESET = b"""<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
<rdf:Description rdf:about="" xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
 crs:IncrementalTemperature="+18" crs:IncrementalTint="+4" crs:Exposure2012="+0.35" crs:Contrast2012="-12"
 crs:Highlights2012="-40" crs:Shadows2012="+30" crs:Clarity2012="+10" crs:Vibrance="+12" crs:Saturation="-6"
 crs:SplitToningShadowHue="210" crs:SplitToningShadowSaturation="12" crs:SplitToningHighlightHue="40" crs:SplitToningHighlightSaturation="18">
 <crs:Name><rdf:Alt><rdf:li xml:lang="x-default">Golden Hour Bride</rdf:li></rdf:Alt></crs:Name>
 <crs:ToneCurvePV2012><rdf:Seq><rdf:li>0, 22</rdf:li><rdf:li>64, 60</rdf:li><rdf:li>192, 200</rdf:li><rdf:li>255, 250</rdf:li></rdf:Seq></crs:ToneCurvePV2012>
</rdf:Description></rdf:RDF></x:xmpmeta>"""

LRTEMPLATE_PRESET = b"""s = { title = "Moody Film", value = { settings = { Exposure2012 = -0.3, Contrast2012 = 25, Blacks2012 = 12,
 IncrementalTemperature = -10, Saturation = -25, ToneCurvePV2012 = { 0, 30, 128, 120, 255, 235, }, }, }, version = 0, }"""


def cube_preset(size: int = 17) -> bytes:
    """A teal-and-orange 3D LUT in .cube format (red changes fastest)."""
    lines = ['TITLE "Teal Orange"', f"LUT_3D_SIZE {size}"]
    axis = [i / (size - 1) for i in range(size)]
    for b in axis:
        for g in axis:
            for r in axis:
                lum = 0.3 * r + 0.6 * g + 0.1 * b
                lines.append(f"{min(1, r * 1.05 + 0.02):.6f} {g:.6f} {max(0, b * 0.9 + 0.05 * (1 - lum)):.6f}")
    return "\n".join(lines).encode()


def spread(boxes, seed: int = 0, size=(2400, 1200), background=(246, 238, 220), frame: int = 8) -> bytes:
    """A finished album spread: photos (with white frames) at the given normalised boxes on a cream page."""
    rng = random.Random(seed)
    image = Image.new("RGB", size, background)
    draw = ImageDraw.Draw(image)
    for index, (x, y, w, h) in enumerate(boxes):
        px = [int(v) for v in (x * size[0], y * size[1], w * size[0], h * size[1])]
        if frame:
            draw.rectangle([px[0] - frame, px[1] - frame, px[0] + px[2] + frame, px[1] + px[3] + frame], fill=(255, 255, 255))
        image.paste(photo(seed * 10 + index, (px[2], px[3])), (px[0], px[1]))
    return jpeg(image, 90)


SPREAD_LAYOUTS = [
    [(0.04, 0.06, 0.42, 0.88), (0.54, 0.06, 0.20, 0.42), (0.76, 0.06, 0.20, 0.42), (0.54, 0.52, 0.42, 0.42)],
    [(0.05, 0.08, 0.40, 0.84), (0.55, 0.08, 0.40, 0.84)],
    [(0.03, 0.05, 0.30, 0.90), (0.36, 0.05, 0.30, 0.90), (0.69, 0.05, 0.28, 0.90)],
    [(0.04, 0.06, 0.20, 0.40), (0.26, 0.06, 0.20, 0.40), (0.04, 0.54, 0.42, 0.40), (0.54, 0.06, 0.42, 0.88)],
    [(0.0, 0.0, 1.0, 1.0)],
    [(0.06, 0.1, 0.38, 0.8), (0.56, 0.1, 0.18, 0.36), (0.76, 0.1, 0.18, 0.36), (0.56, 0.54, 0.18, 0.36), (0.76, 0.54, 0.18, 0.36)],
    [(0.08, 0.15, 0.35, 0.7), (0.57, 0.15, 0.35, 0.7)],
    [(0.04, 0.06, 0.92, 0.42), (0.04, 0.52, 0.45, 0.42), (0.51, 0.52, 0.45, 0.42)],
]
