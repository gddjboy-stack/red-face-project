#!/usr/bin/env python3
"""
从彬少提供的 SVG 定稿生成大屏页与分享图所需的 PNG 素材。

背景：上一版素材来自 JPG 白底图，需按亮度抠图。但该 logo 的白色文字描边
与白色背景亮度相同，抠图会连描边一起抠掉，只能退而使用白色圆角底板。
现在拿到 SVG 源文件（文字已全部转为路径、无外部字体依赖），可以直接
输出真正的透明底 PNG，去掉底板。

用法：python3 tools/prepare_logo_from_svg.py
"""

import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC_DIR = REPO / "assets" / "logo"
OUT_DIR = REPO / "backend" / "redface-backend" / "src" / "main" / "resources" / "static" / "display" / "assets"
EVIDENCE = REPO / "evidence" / "C20-5"

# 版本 01：紧凑，无英文副标题，用于大屏页头部（头部高度受限）
# 版本 02：含 Game Of Beauty，用于分享图（对外物料需完整品牌信息）
SOURCES = {
    "logo-compact": "logo_v01_compact.svg",
    "logo-full": "logo_v02_full.svg",
}

# 需要的输出宽度（高度按原始宽高比自动计算，绝不拉伸变形）
WIDTHS = [120, 220, 440, 880]


def render(svg_path: Path, out_path: Path, width: int) -> None:
    """用 rsvg-convert 渲染 SVG 到透明底 PNG。

    选 rsvg-convert 而非 Pillow：Pillow 不支持 SVG。
    也不用 Inkscape：体积大、启动慢，此处不需要它的额外能力。
    """
    cmd = [
        "rsvg-convert",
        "--width", str(width),
        "--keep-aspect-ratio",
        "--format", "png",
        "--output", str(out_path),
        str(svg_path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)


def main() -> int:
    if not SRC_DIR.exists():
        print(f"错误：找不到源目录 {SRC_DIR}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)

    from PIL import Image

    for stem, filename in SOURCES.items():
        svg = SRC_DIR / filename
        if not svg.exists():
            print(f"错误：找不到 {svg}", file=sys.stderr)
            return 1

        for w in WIDTHS:
            out = OUT_DIR / f"{stem}-{w}.png"
            render(svg, out, w)
            with Image.open(out) as im:
                # 校验确实是带 alpha 的透明底，而不是被填充成白底
                assert im.mode == "RGBA", f"{out.name} 不是 RGBA，透明通道丢失"
                # 抽查四角是否透明——若不透明说明渲染时被加了背景
                corners = [
                    im.getpixel((0, 0)),
                    im.getpixel((im.width - 1, 0)),
                    im.getpixel((0, im.height - 1)),
                    im.getpixel((im.width - 1, im.height - 1)),
                ]
                opaque_corners = [c for c in corners if c[3] > 10]
                flag = "" if not opaque_corners else f"  [注意] {len(opaque_corners)} 个角不透明"
                print(f"  生成 {out.name}  {im.width}x{im.height}{flag}")

    print("\n完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
