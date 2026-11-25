"""Compare real Home artwork to the transparent approved atlas, and verify top strips."""
from PIL import Image
im=Image.open("premium-home-preview.png").convert("RGB")
w,h=im.size
atlas=Image.open("app/src/main/res/drawable-nodpi/four_sharks_atlas.png").convert("RGBA")
atlas=atlas.resize((w,h),Image.Resampling.LANCZOS)
for i in range(4):
    # Only sample opaque character pixels, excluding the blue background and antialiasing.
    hit=total=0
    for y in range(round(.19*h),round(.64*h),4):
        for x in range(round((i+.06)*w/4),round((i+.94)*w/4),4):
            r,g,b,a=atlas.getpixel((x,y))
            if a<250: continue
            q=im.getpixel((x,y))
            hit+=sum(abs(q[j]-p) for j,p in enumerate((r,g,b)))<110
            total+=1
    assert total>150 and hit/total>.70,(i,hit,total,"shark artwork mismatch")
    x=52+i*395
    top=im.crop((round((x+30)*w/1648),round(685*h/928),round((x+335)*w/1648),round(697*h/928)))
    n=sum((r>120 and r>g*1.2) or (b>145 and r>80 and b>g*1.25) for r,g,b in top.getdata())
    assert n>top.width*top.height*.10,(i,n,"missing colour strip")
    print("PASS: shark and top strip",i,hit,total)

# Live TV is initially focused. Inspect its lower edge to avoid the coloured top strip.
for i in range(4):
    x=52+i*395
    patch=im.crop((round((x+35)*w/1648),round(851*h/928),round((x+330)*w/1648),round(863*h/928)))
    gold=sum(r>200 and g>150 and b<150 and r>b*1.5 for r,g,b in patch.getdata())
    if i==0: assert gold>patch.width*1.5,(i,gold,"focused yellow outline missing")
    else: assert gold<patch.width*.25,(i,gold,"unfocused yellow outline")
print("PASS: yellow focus frame only on selected Home tile")
