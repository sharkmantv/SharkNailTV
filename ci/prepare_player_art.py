"""Render separate high-resolution Sky-indigo player plates from the approved
blue mockup's palette and sweeping light geometry. No controls, text or video
are baked into these assets; Android lays those out independently."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math
DST=Path("app/src/main/res/drawable-nodpi")
DST.mkdir(parents=True,exist_ok=True)
def plate(w,h,key=False):
    # Smooth navy-indigo base matched to the Home background, with cobalt light.
    bw,bh=(240,240) if key else (960,180)
    base=Image.new("RGBA",(bw,bh))
    pix=base.load()
    for y in range(bh):
        v=y/max(1,bh-1)
        for x in range(bw):
            u=x/max(1,bw-1)
            upper=math.exp(-v*10)
            side=math.exp(-((u-.08)/.22)**2)+math.exp(-((u-.93)/.20)**2)
            lower=math.exp(-((v-.9)/.23)**2)
            if key:
                r=8+13*upper+4*lower
                g=12+40*upper+12*lower
                b=60+66*upper+36*lower
            else:
                r=10+9*upper+3*side*lower
                g=12+23*upper+22*side*lower
                b=60+42*upper+30*side*lower+18*v
            pix[x,y]=(round(r),round(g),round(b),255)
    base=base.resize((w,h),Image.Resampling.BICUBIC)
    light=Image.new("RGBA",(w,h))
    d=ImageDraw.Draw(light)
    if not key:
        # Low, sweeping blue ribbons follow the selected mock's silhouette.
        def curve(a,b,c,e):
            return [(round(w*((1-t)**3*a[0]+3*(1-t)**2*t*b[0]+3*(1-t)*t*t*c[0]+t**3*e[0])),round(h*((1-t)**3*a[1]+3*(1-t)**2*t*b[1]+3*(1-t)*t*t*c[1]+t**3*e[1]))) for t in (i/240 for i in range(241))]
        lines=[curve((0,.30),(.12,.70),(.19,.83),(.38,.80)),curve((.62,.80),(.80,.83),(.90,.67),(1,.27))]
        for line in lines:
            d.line(line,fill=(0,78,240,150),width=max(3,round(h*.035)))
        base=Image.alpha_composite(base,light.filter(ImageFilter.GaussianBlur(h*.024)))
        light=Image.new("RGBA",(w,h));d=ImageDraw.Draw(light)
        for line in lines:
            d.line(line,fill=(15,125,255,180),width=max(2,round(h*.012)))
            d.line(line,fill=(83,190,255,210),width=max(1,round(h*.003)))
    # Thin blue edge, no opaque silver frame. Small corner radius preserves
    # the rectangular silhouette and the player's existing layout.
    border=max(2,round(h*(.012 if key else .006)))
    radius=max(2,round(h*.025))
    d.rounded_rectangle((border,border,w-border-1,h-border-1),radius=radius,outline=(21,105,245,220),width=border)
    d.line((radius*2,border,w-radius*2,border),fill=(83,188,255,215),width=max(1,border//2))
    glow=light.filter(ImageFilter.GaussianBlur(max(1,h*.012)))
    base=Image.alpha_composite(Image.alpha_composite(base,glow),light)
    mask=Image.new("L",(w,h));ImageDraw.Draw(mask).rounded_rectangle((0,0,w-1,h-1),radius=radius,fill=255)
    base.putalpha(mask)
    return base
for name,size,key in (("player_dock_blue.png",(3840,720),False),("player_key_blue.png",(384,384),True)):
    im=plate(*size,key)
    assert im.mode=="RGBA" and im.size==size
    assert im.getpixel((size[0]//2,size[1]//2))[2]>im.getpixel((size[0]//2,size[1]//2))[0]+30
    im.save(DST/name,optimize=True)
    print("PASS:",name,size)
