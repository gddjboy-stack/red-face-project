#!/usr/bin/env python3
"""
把抠好的 logo 叠在大屏实际背景色上，检查是否残留白边。

必要性：透明 PNG 单独看无法判断抠图质量——白底残留在白色预览背景上完全不可见，
只有放到深色背景上才会暴露。大屏背景为 #12060f，分享图同色系。
"""

from PIL import Image

LOGO = ("/home/ubuntu/red-face-project/backend/redface-backend/"
        "src/main/resources/static/display/assets/logo-220.png")
OUT = "/home/ubuntu/red-face-project/evidence/C20-5/logo_on_dark_check.png"
BG = (18, 6, 15, 255)  # #12060f 大屏底色

logo = Image.open(LOGO).convert("RGBA")
canvas = Image.new("RGBA", (logo.width + 80, logo.height + 80), BG)
canvas.alpha_composite(logo, (40, 40))
canvas.save(OUT, "PNG")
print(f"已输出 {OUT} -> {canvas.size}")
