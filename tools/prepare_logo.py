#!/usr/bin/env python3
"""
C20-5 logo 素材处理：把 John 提供的 JPG（白底）加工为可叠在深色大屏上的 PNG。

为什么不做纯透明抠图（实测结论，勿回退）
----------------------------------------
最初版本按亮度阈值抠白底，产物在深色背景上暴露了一个致命问题：
这枚 logo 的设计**依赖白色描边**（文字外围勾边）提供对比，而按亮度抠图
无法区分「背景白」与「设计元素白」——两者亮度完全相同。结果是描边连同
背景一起被抠掉，紫色文字直接贴在 #12060f 深底上，轮廓消失、可读性大幅下降。

因此改为：保留白底，但把它做成**规整的圆角白色卡片**。这样描边完整保留，
同时避免了原始 JPG 直接使用时那种不规则白色方块的廉价感。

输出两个尺寸（均为 2 倍图以适配高分屏）：大屏角标 160px、分享图 220px。

注意：这是位图加工的临时可用版本。彬少提供 SVG 源文件后应直接替换——
矢量源文件在任意尺寸下都优于位图加工，且可按需要出深色版本免去底板。
"""

from PIL import Image, ImageDraw
import os

SRC = "/home/ubuntu/upload/logo.jpg"
OUT_DIR = ("/home/ubuntu/red-face-project/backend/redface-backend/"
           "src/main/resources/static/display/assets")

WHITE_THRESHOLD = 242  # 高于该亮度视为背景白，仅用于裁掉四周留白
PADDING_RATIO = 0.06   # 卡片内边距占宽度比例
CORNER_RATIO = 0.10    # 圆角半径占卡片宽度比例


def content_bbox(img: Image.Image):
    """定位 logo 实际内容范围，用于裁掉四周多余白边。"""
    gray = img.convert("L")
    mask = gray.point(lambda v: 0 if v >= WHITE_THRESHOLD else 255)
    return mask.getbbox()


def build_card(src: Image.Image, width: int) -> Image.Image:
    """把 logo 放进白色圆角卡片，返回 RGBA 图（卡片外为透明）。"""
    bbox = content_bbox(src)
    logo = src.crop(bbox) if bbox else src

    pad = int(width * PADDING_RATIO)
    inner_w = width - pad * 2
    ratio = inner_w / logo.width
    logo = logo.resize((inner_w, int(logo.height * ratio)), Image.LANCZOS)

    height = logo.height + pad * 2
    radius = int(width * CORNER_RATIO)

    # 圆角遮罩：4 倍超采样后缩回，得到平滑边缘（PIL 的圆角矩形本身有锯齿）
    scale = 4
    mask = Image.new("L", (width * scale, height * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [(0, 0), (width * scale - 1, height * scale - 1)],
        radius=radius * scale, fill=255)
    mask = mask.resize((width, height), Image.LANCZOS)

    card = Image.new("RGBA", (width, height), (255, 255, 255, 255))
    card.paste(logo.convert("RGB"), (pad, pad))
    card.putalpha(mask)
    return card


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    src = Image.open(SRC).convert("RGB")

    for name, width in (("logo-160", 320), ("logo-220", 440)):
        card = build_card(src, width)
        path = os.path.join(OUT_DIR, f"{name}.png")
        card.save(path, "PNG", optimize=True)
        print(f"已输出 {path} -> {card.size} ({os.path.getsize(path)} bytes)")


if __name__ == "__main__":
    main()
