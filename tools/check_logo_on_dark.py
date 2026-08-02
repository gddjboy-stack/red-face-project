#!/usr/bin/env python3
"""
把透明底 logo 合成到实际使用的深色背景上，检查是否还看得清。

必要性：透明 PNG 在图片查看器里默认以白色或棋盘格作预览背景，
白色描边在白底上不可见 —— 上一版 JPG 抠图方案就是这样漏掉了描边丢失问题。
必须显式合成到真实背景色上才能判断。

背景色取自 static/display/index.html 实测值，非凭记忆填写。
"""

from pathlib import Path
from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
ASSETS = (REPO / "backend" / "redface-backend" / "src" / "main"
          / "resources" / "static" / "display" / "assets")
OUT = REPO / "evidence" / "C20-5" / "logo_svg_on_dark_check.png"

# 实测背景色：#0d0a10 为大屏页底色，#17121b 为卡片底，
# #221826 / #120d15 为分享图渐变两端
BACKGROUNDS = [
    ("大屏页底 #0d0a10", (0x0d, 0x0a, 0x10)),
    ("卡片底 #17121b", (0x17, 0x12, 0x1b)),
    ("分享图渐变亮端 #221826", (0x22, 0x18, 0x26)),
    ("纯黑（最坏情况）", (0, 0, 0)),
]

SAMPLES = ["logo-compact-220.png", "logo-full-220.png"]


def main() -> None:
    pad = 24
    cell_w = 260 + pad * 2
    rows = len(BACKGROUNDS)
    cols = len(SAMPLES)

    heights = []
    for name in SAMPLES:
        with Image.open(ASSETS / name) as im:
            heights.append(im.height)
    cell_h = max(heights) + pad * 2 + 22

    canvas = Image.new("RGB", (cell_w * cols, cell_h * rows), (40, 40, 40))
    draw = ImageDraw.Draw(canvas)

    for r, (label, bg) in enumerate(BACKGROUNDS):
        for c, name in enumerate(SAMPLES):
            x0 = c * cell_w
            y0 = r * cell_h
            draw.rectangle([x0, y0, x0 + cell_w - 2, y0 + cell_h - 2], fill=bg)
            with Image.open(ASSETS / name) as im:
                im = im.convert("RGBA")
                px = x0 + (cell_w - im.width) // 2
                py = y0 + 18 + (cell_h - 18 - im.height) // 2
                canvas.paste(im, (px, py), im)
            draw.text((x0 + 8, y0 + 4), f"{label} / {name}", fill=(150, 150, 150))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT)
    print(f"已生成检查图：{OUT}  {canvas.width}x{canvas.height}")


if __name__ == "__main__":
    main()
