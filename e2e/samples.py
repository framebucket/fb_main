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
