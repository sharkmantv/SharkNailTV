"""Remove baked gold selection while retaining the approved artwork and hands."""
from PIL import Image
from pathlib import Path
import hashlib, colorsys
src=Path("app/src/main/assets/design2_home.png")
assert hashlib.sha256(src.read_bytes()).hexdigest()=="61f1779d7840e718079b80af4ce8e6935a69b591bb3ae227e646ecfdc79fb929"
im=Image.open(src).convert("RGB")
assert im.size==(1672,941)
pix=im.load()
# Restrict the recolour to the first tile and its reflected light, not the shark faces or header.
for y in range(490,940):
    for x in range(15,470):
        r,g,b=pix[x,y]
        hue,sat,val=colorsys.rgb_to_hsv(r/255,g/255,b/255)
        if .055<hue<.19 and sat>.18:
            mix=min(1,(y-490)/15,(470-x)/12,(x-15)/12)
            nr,ng,nb=colorsys.hsv_to_rgb(.59,sat*.80,val)
            pix[x,y]=tuple(round(a*(1-mix)+v*255*mix) for a,v in zip((r,g,b),(nr,ng,nb)))
im.save(src.with_name("design2_neutral.png"))
print("PASS: exact design 02 source artwork verified; baked gold focus neutralized")
