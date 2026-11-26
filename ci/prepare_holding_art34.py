"""Pin the new artwork, create transparent UHD card layers and check reconstruction."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops, ImageStat
import hashlib, argparse, json
dst=Path("app/src/main/res/drawable-nodpi")
home=dst/"sky_shark_home_34.png"
logo=dst/"sky_shark_logo_34.png"
for f,sha in [(home,"4359e0a0a6976b6d2c6bbf172eb7504b7f7241fb0d3722198193db623e334c58"),(logo,"d8ae7168c70c283ff73791a6e43de8f88a70c76643cfe221619246b74598467d")]:
    assert hashlib.sha256(f.read_bytes()).hexdigest()==sha,("Wrong asset",f)
im=Image.open(home).convert("RGBA")
assert im.size==(3840,2160),im.size
parser=argparse.ArgumentParser()
parser.add_argument("--enhanced-dir",type=Path)
args=parser.parse_args()
if args.enhanced_dir:
    root=args.enhanced_dir
    report=json.loads((root/"verification.json").read_text())
    assert report["source_git_blob"]=="c7cfeda2fd67972172a3fc6dc1e7e4b40556c2f8"
    assert report["output_dimensions"]==[3840,2160] and report["native_generation"] is False
    for name in ("sky_shark_ai_4k.png","sky_shark_conservative_4k.png","comparison.png"):
        fp=root/name
        expected=report["outputs"][name]
        assert fp.stat().st_size==expected["bytes"] and hashlib.sha256(fp.read_bytes()).hexdigest()==expected["sha256"],name
        with Image.open(fp) as test: test.verify()
    with Image.open(root/"sky_shark_conservative_4k.png") as candidate:
        assert candidate.format=="PNG" and candidate.size==im.size
        enhanced=candidate.convert("RGBA")
    # Guard the approved artwork's layout, colours and text geometry; no generated logo replacements.
    original_rgb=im.convert("RGB")
    candidate_rgb=enhanced.convert("RGB")
    difference=ImageChops.difference(original_rgb,candidate_rgb)
    mae=sum(ImageStat.Stat(difference).mean)/3
    assert mae<=12,("Excessive global artwork change",mae)
    for i in range(4):
        box=(i*960,0,(i+1)*960,2160)
        d=sum(ImageStat.Stat(difference.crop(box)).mean)/3
        assert d<=18,("Excessive panel artwork change",i,d)
    # Same geometry at reduced scale, to catch layout/colour shifts independently of fine details.
    thumb=ImageChops.difference(original_rgb.resize((480,270),Image.Resampling.LANCZOS),
                               candidate_rgb.resize((480,270),Image.Resampling.LANCZOS))
    assert sum(ImageStat.Stat(thumb).mean)/3<=5,"Enhanced artwork changes low-frequency layout/colours"
    print("PASS: verified enhanced PNG and approved-layout preservation; mean pixel delta",mae)
    im=enhanced
lg=Image.open(logo)
assert lg.mode=="RGBA" and lg.getchannel("A").getextrema()==(0,255)
areas=[(20,477,438,390),(459,477,385,390),(845,477,389,390),(1235,477,397,390)]
rims=[(43,493,453,849),(463,497,840,845),(852,497,1230,845),(1242,497,1620,845)]
def X(x):return round(x*3840/1648)
def Y(y):return round(y*2160/928)
# Neutralise only the previously baked warm glow at the first tile perimeter
# and its floor reflection. Preserve the interior lettering, logo and hands.
px=im.load()
for y in range(Y(488),Y(928)):
    yy=y*928/2160
    for x in range(X(20),X(458)):
        xx=x*1648/3840
        side=max(0,min(1,(84-xx)/16,(yy-485)/16),min(1,(xx-416)/16,(yy-485)/16))
        bottom=max(0,min(1,(yy-819)/18))
        if yy>850:
            bottom*=max(0,min(1,(xx-20)/20,(458-xx)/20))
            side*=max(0,min(1,(867-yy)/17))
        top=max(0,min(1,(yy-488)/8,(522-yy)/8)) if 224<xx<280 else 0
        zone=max(side,bottom,top)
        if zone<=0: continue
        r,g,b,a=px[x,y]
        strength=zone*min(1,max(0,(r-min(g,b)-4)/35))
        if strength<=0: continue
        cool=(min(r,round(g*.82+b*.18)),min(255,round(max(g,b*.91))),min(255,round(max(b,g*1.10))),a)
        px[x,y]=tuple(round(v*(1-strength)+c*strength) for v,c in zip((r,g,b,a),cool))
print("PASS: fixed source focus tint neutralised in the outer rim/reflection")
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
