"""Pin the new artwork, create transparent UHD card layers and check reconstruction."""
from pathlib import Path
from PIL import Image, ImageDraw
import hashlib
dst=Path("app/src/main/res/drawable-nodpi")
home=dst/"sky_shark_home_34.png"
logo=dst/"sky_shark_logo_34.png"
for f,sha in [(home,"4359e0a0a6976b6d2c6bbf172eb7504b7f7241fb0d3722198193db623e334c58"),(logo,"d8ae7168c70c283ff73791a6e43de8f88a70c76643cfe221619246b74598467d")]:
    assert hashlib.sha256(f.read_bytes()).hexdigest()==sha,("Wrong asset",f)
im=Image.open(home).convert("RGBA")
assert im.size==(3840,2160),im.size
lg=Image.open(logo)
assert lg.mode=="RGBA" and lg.getchannel("A").getextrema()==(0,255)
areas=[(20,477,438,390),(459,477,385,390),(845,477,389,390),(1235,477,397,390)]
rims=[(43,493,453,849),(463,497,840,845),(852,497,1230,845),(1242,497,1620,845)]
def X(x):return round(x*3840/1648)
def Y(y):return round(y*2160/928)
back=im.copy()
for i,((x,y,w,h),(l,t,r,b)) in enumerate(zip(areas,rims)):
    box=(X(x),Y(y),X(x+w),Y(y+h)); ww=box[2]-box[0];hh=box[3]-box[1]
    mask=Image.new("L",(ww,hh),0)
    ImageDraw.Draw(mask).rounded_rectangle((X(l)-box[0],Y(t)-box[1],X(r)-box[0],Y(b)-box[1]),radius=round(35*3840/1648),fill=255)
    tile=im.crop(box);tile.putalpha(mask)
    assert tile.getchannel("A").getextrema()==(0,255)
    tile.save(dst/("design34_card_%d.png"%i))
    back.paste((0,0,0,0),box,mask)
back.save(dst/"design34_backdrop.png")
recon=back.copy()
for i,(x,y,w,h) in enumerate(areas):
    recon.alpha_composite(Image.open(dst/("design34_card_%d.png"%i)),(X(x),Y(y)))
assert recon.tobytes()==im.tobytes(),"UHD transparent layer reconstruction failed"
ref=im.copy()
lg=lg.resize((X(500),Y(128)),Image.Resampling.LANCZOS)
ref.alpha_composite(lg,(X(574),Y(5)))
ref.convert("RGB").resize((1648,928),Image.Resampling.LANCZOS).save("design34_reference.png")
print("PASS: 3840x2160 prepared source, exact approved logo, four transparent layers")
