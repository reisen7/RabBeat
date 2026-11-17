"""Build a multi-size Windows icon from the 512px desktop master.

Windows picks one embedded size for the taskbar, tray, and Explorer.
A single 256px frame gets stretched, which is what makes the small
icons look soft. Each size here is resampled from the master.
"""
import io
import struct
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
MASTER = ROOT / "sharedUi/src/commonMain/composeResources/drawable/ic_desktop_icon.png"
RES = ROOT / "desktopApp/src/jvmMain/resources/icons"
ICO = Path(__file__).resolve().parent / "rabbeat.ico"
SIZES = (16, 20, 24, 32, 40, 48, 64, 128, 256)


def render(master: Image.Image, size: int) -> Image.Image:
    if size >= 64:
        return master.resize((size, size), Image.Resampling.LANCZOS)
    # Flat-color art: an area average keeps the ear and headphone edges
    # harder than Lanczos, which feathers them into the white plate.
    # Contrast is lifted first so a one-pixel headphone band stays pink
    # instead of turning into a gray average.
    scale = 4
    big = master.resize((size * scale, size * scale), Image.Resampling.LANCZOS)
    big = ImageEnhance.Contrast(big).enhance(1.25)
    img = big.resize((size, size), Image.Resampling.BOX)
    img = img.filter(ImageFilter.UnsharpMask(radius=0.4, percent=50, threshold=1))
    return img


def write_ico(frames: list[Image.Image], path: Path) -> None:
    blobs = []
    for frame in frames:
        buf = io.BytesIO()
        frame.save(buf, format="PNG", optimize=True)
        blobs.append(buf.getvalue())
    header = struct.pack("<HHH", 0, 1, len(blobs))
    offset = 6 + 16 * len(blobs)
    entries = b""
    body = b""
    for frame, data in zip(frames, blobs):
        side = frame.width
        w = 0 if side >= 256 else side
        h = 0 if side >= 256 else side
        entries += struct.pack("<BBBBHHII", w, h, 0, 0, 1, 32, len(data), offset)
        body += data
        offset += len(data)
    path.write_bytes(header + entries + body)


def main() -> None:
    master = Image.open(MASTER).convert("RGBA")
    if master.size != (512, 512):
        raise SystemExit(f"expected 512 master, got {master.size}")
    RES.mkdir(parents=True, exist_ok=True)
    frames = []
    for size in SIZES:
        frame = render(master, size)
        frame.save(RES / f"rabbeat-{size}.png", format="PNG", optimize=True)
        frames.append(frame)
    write_ico(frames, ICO)
    # Contact sheet, nearest-neighbor scaled so the pixels are visible.
    cell = 64
    sheet = Image.new("RGBA", (cell * len(frames), cell * 2), (40, 40, 40, 255))
    for i, frame in enumerate(frames):
        big = frame.resize((cell, cell), Image.Resampling.NEAREST)
        sheet.paste(big, (i * cell, 0), big)
        native = frame.resize(frame.size, Image.Resampling.NEAREST)
        sheet.paste(native, (i * cell + (cell - frame.width) // 2, cell + (cell - frame.height) // 2), native)
    sheet.save(Path(__file__).resolve().parent / "_icon_sheet.png")
    print(f"ico {ICO.stat().st_size} bytes, frames {len(frames)}")


if __name__ == "__main__":
    main()
