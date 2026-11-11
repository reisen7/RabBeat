"""Generate demo cover art and a short looping tone for the RabBeat prototype."""

import math
import struct
import wave
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"


def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: Path, width: int, height: int, pixel):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixel(x, y))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def blend(base, overlay, alpha):
    return tuple(int(base[i] * (1 - alpha) + overlay[i] * alpha) for i in range(3)) + (255,)


def cover(name, base, accent, style):
    w = h = 480

    def pixel(x, y):
        nx = x / w
        ny = y / h
        color = base
        if style == "bands":
            color = accent if int((nx + ny) * 8) % 2 == 0 else base
        elif style == "orb":
            d = math.hypot(nx - 0.62, ny - 0.38)
            color = blend(base, accent, max(0, 1 - d * 1.6))
        elif style == "waves":
            wave_y = 0.5 + 0.12 * math.sin(nx * 10 + ny)
            color = accent if abs(ny - wave_y) < 0.06 else base
        elif style == "bloom":
            for cx, cy, r in ((0.3, 0.35, 0.22), (0.68, 0.42, 0.28), (0.48, 0.72, 0.18)):
                if math.hypot(nx - cx, ny - cy) < r:
                    color = blend(color, accent, 0.85)
        elif style == "blocks":
            color = accent if (int(nx * 4) + int(ny * 4)) % 2 == 0 else base
        elif style == "moon":
            color = (18, 16, 28, 255)
            if math.hypot(nx - 0.68, ny - 0.32) < 0.18:
                color = accent
        elif style == "arcs":
            d = abs(math.hypot(nx - 0.2, ny - 1.1) - 0.9)
            color = accent if d < 0.06 or abs(math.hypot(nx - 0.2, ny - 1.1) - 0.62) < 0.04 else base
        elif style == "grid":
            color = accent if int(nx * 10) % 3 == 0 or int(ny * 10) % 3 == 0 else base
        if isinstance(color, tuple) and len(color) == 3:
            return color + (255,)
        return color

    write_png(DRAWABLE / f"{name}.png", w, h, pixel)


def write_tone():
    rate = 22050
    seconds = 90
    notes = [261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 392.00, 329.63]
    frames = rate * seconds
    step = rate // 4
    with wave.open(str(RAW / "sample_tone.wav"), "w") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        data = bytearray()
        for i in range(frames):
            note = notes[(i // step) % len(notes)]
            phase = (i % step) / step
            env = math.sin(math.pi * min(phase * 8, 1)) * math.exp(-2.2 * phase)
            sample = 0.45 * math.sin(2 * math.pi * note * i / rate) * env
            sample += 0.08 * math.sin(2 * math.pi * 130.81 * i / rate)
            value = max(-1, min(1, sample))
            data += struct.pack("<h", int(value * 32767 * 0.8))
        handle.writeframes(data)


def main():
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    RAW.mkdir(parents=True, exist_ok=True)
    cover("cover_aurora", (36, 20, 72), (124, 92, 255), "bands")
    cover("cover_ember", (92, 42, 24), (255, 184, 107), "orb")
    cover("cover_tide", (16, 42, 72), (92, 200, 255), "waves")
    cover("cover_bloom", (92, 28, 52), (255, 107, 138), "bloom")
    cover("cover_moss", (22, 58, 42), (61, 220, 151), "blocks")
    cover("cover_nocturne", (14, 14, 18), (245, 245, 247), "moon")
    cover("cover_honey", (92, 72, 20), (242, 232, 107), "arcs")
    cover("cover_lilac", (48, 32, 78), (192, 132, 252), "grid")
    write_tone()
    print("assets ready")


if __name__ == "__main__":
    main()
