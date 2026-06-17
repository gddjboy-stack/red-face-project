from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
H5 = ROOT / "frontend" / "h5-bridge"
MINI_REDEEM = ROOT / "frontend" / "douyin-miniprogram" / "pages" / "redeem" / "index.js"

index_html = (H5 / "index.html").read_text(encoding="utf-8")
style_css = (H5 / "style.css").read_text(encoding="utf-8")
script_js = (H5 / "script.js").read_text(encoding="utf-8")
redeem_js = MINI_REDEEM.read_text(encoding="utf-8")

assert "./style.css" in index_html, "index.html must load local style.css"
assert "./script.js" in index_html, "index.html must load local script.js"
assert "get('t')" in script_js, "H5 bridge must read t token parameter"
assert "get('oid')" in script_js, "H5 bridge must read oid parameter"
assert "miniProgramLinkBase: ''" in script_js, "miniProgramLinkBase must be blank by default in rehearsal stage"
assert "snssdk" not in script_js.lower(), "must not hard-code unverified snssdk scheme"
assert "douyin://" not in script_js.lower(), "must not hard-code unverified douyin scheme"
assert "navigator.clipboard" in script_js and "execCommand('copy')" in script_js, "must support clipboard copy with fallback"
assert "仅用于人工核对，本页不上报后端" in index_html, "oid must be displayed only, not submitted"
assert "投票" not in index_html + script_js + style_css, "must not use sensitive term 投票"
assert "打赏" not in index_html + script_js + style_css, "must not use sensitive term 打赏"

assert "async onLoad(options)" in redeem_js, "redeem page onLoad must accept options"
assert "options && options.token" in redeem_js, "redeem page must read options.token"
assert "String(options.token).trim().toUpperCase()" in redeem_js, "redeem token prefill must match existing normalization"

onload_match = re.search(r"async onLoad\(options\) \{(?P<body>.*?)\n  \},\n  onTokenInput", redeem_js, re.S)
assert onload_match, "must locate onLoad body"
onload_body = onload_match.group("body")
assert "getClipboardData" not in onload_body, "onLoad must not auto-read clipboard"
assert "redeemToken" not in onload_body, "onLoad must not auto-redeem token"

print("C12 static verification passed")
