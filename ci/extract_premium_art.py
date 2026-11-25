from pathlib import Path
from PIL import Image
dst=Path("app/src/main/res/drawable-nodpi")
src=Image.open(dst/"premium_mockup3.png").convert("RGB")
assert src.size==(1400,1000)
def clamp(x): return max(0,min(1,x))
# Remove wallpaper from tight crops of the approved artwork; preserve metal highlights.
for name,box in [
 ("live",(92,771,231,888)),
 ("movies",(443,770,578,886)),
 ("series",(788,770,935,882)),
 ("settings",(1148,768,1271,886))]:
    im=src.crop(box).convert("RGBA")
    px=[]
    for r,g,b,a in im.getdata():
        alpha=max(clamp((min(r,g)-48)/100)*clamp((r-b+30)/55),clamp((min(r,g,b)-115)/100))
        a=round(255*alpha)
        px.append((r,g,b,a) if a>4 else (0,0,0,0))
    im.putdata(px)
    assert im.getchannel("A").getextrema()==(0,255)
    assert sum(a==0 for a in im.getchannel("A").getdata())>im.width*im.height*.25
    im.save(dst/("premium_cut_"+name+".png"))
    print("Verified alpha PNG",name,im.size)
im=src.crop((0,622,537,717)).convert("RGBA")
px=[]
for r,g,b,a in im.getdata():
    silver=min(r,g,b)>=53 and r>=b*.79 and g>=b*.83
    gold=r>=80 and g>=r*.49 and b<=r*.91
    a=round(255*clamp((max(r,g,b)-64)/82)) if silver or gold else 0
    px.append((r,g,b,a) if a>4 else (0,0,0,0))
im.putdata(px)
assert im.getchannel("A").getextrema()==(0,255)
im.save(dst/"premium_cut_brand.png")
