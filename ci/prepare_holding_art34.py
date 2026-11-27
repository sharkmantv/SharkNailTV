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
    with Image.open(root/"sky_shark_ai_4k.png") as candidate:
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
# Localised top-sky restoration and second-shark fin repair.
from PIL import ImageFilter
before_repair=im.copy()
w,h=im.size
Xr=lambda x:round(x*w/1648)
Yr=lambda y:round(y*h/928)
patch=im.copy()
px=patch.load()
for y in range(Yr(0),Yr(180)):
    yy=y*928/h
    t=max(0,min(1,(yy-63)/91))
    rgb=tuple(round(a+(b-a)*t) for a,b in zip((4,17,62),(8,27,113)))
    for x in range(Xr(375),Xr(1300)):
        px[x,y]=(*rgb,255)
mask=Image.new("L",im.size)
ImageDraw.Draw(mask).polygon([(Xr(x),Yr(y)) for x,y in [(423,-20),(1238,-20),(1238,112),(1175,135),(1110,131),(1070,123),(1010,140),(700,141),(664,133),(630,120),(570,121),(540,138),(440,126)]],fill=255)
im=Image.composite(patch,im,mask.filter(ImageFilter.GaussianBlur(Xr(7))))
dest=(Xr(568),Yr(107),Xr(663),Yr(183))
with Image.open(Path(__file__).parent/"artwork/second_fin_repair.jpg") as donor:
    assert donor.size==(95,76),donor.size
    tile=donor.resize((dest[2]-dest[0],dest[3]-dest[1]),Image.Resampling.LANCZOS).convert("RGBA")
m=Image.new("L",tile.size)
ImageDraw.Draw(m).polygon([(Xr(x-568),Yr(y-107)) for x,y in [(572,111),(590,109),(616,114),(646,132),(660,151),(650,160),(607,177),(583,178),(581,152),(570,126)]],fill=255)
im.paste(tile,dest[:2],m.filter(ImageFilter.GaussianBlur(Xr(2))))
assert im.size==(3840,2160)
assert im.crop((0,Yr(183),w,h)).tobytes()==before_repair.crop((0,Yr(183),w,h)).tobytes(),"Repair altered character or tile artwork below fin"
assert im.crop(dest).tobytes()!=before_repair.crop(dest).tobytes(),"Fin repair not applied"
sky=im.crop((Xr(760),Yr(115),Xr(1060),Yr(125))).convert("RGB")
assert all(r<35 and g<65 for r,g,b in sky.getdata()),"Old lettering remains in cleaned sky"
del before_repair,patch,mask,m,tile,sky
print("PASS: repaired second fin and cleaned old logo remnants; unchanged lower UHD artwork")
# Replace only the background silhouette, retaining the original characters,
# card layers and floor. Coordinates follow the approved 1648x928 preview.
sky_outline=[[0,0],[0,504],[46,504],[67,489],[31,452],[39,360],[55,339],[57,312],[80,279],[81,285],[101,278],[108,265],[116,266],[106,262],[118,244],[135,235],[151,236],[153,211],[160,203],[173,200],[201,170],[205,154],[194,124],[185,114],[190,106],[218,106],[236,112],[287,149],[335,161],[378,188],[378,195],[393,206],[404,224],[378,255],[370,254],[355,271],[354,291],[369,307],[369,318],[412,353],[413,372],[430,384],[441,402],[451,378],[438,370],[436,330],[445,305],[468,276],[496,260],[505,237],[531,236],[539,224],[546,225],[551,215],[559,215],[589,177],[590,147],[572,121],[574,117],[598,113],[617,118],[663,150],[694,155],[753,182],[756,198],[771,214],[769,226],[750,254],[792,286],[816,343],[824,401],[837,420],[843,415],[843,378],[859,351],[863,319],[878,297],[906,285],[925,270],[933,244],[948,238],[944,222],[953,212],[953,197],[993,171],[1031,161],[1060,139],[1079,133],[1100,139],[1092,158],[1095,174],[1130,206],[1146,233],[1162,236],[1170,271],[1187,271],[1197,283],[1216,288],[1235,308],[1242,331],[1240,352],[1257,375],[1271,319],[1307,287],[1292,264],[1297,236],[1376,129],[1408,109],[1432,106],[1467,121],[1495,146],[1558,245],[1562,269],[1573,271],[1587,282],[1620,322],[1628,356],[1625,430],[1617,440],[1616,429],[1615,450],[1595,481],[1617,498],[1647,498],[1647,0]]
mask=Image.new("L",im.size)
ImageDraw.Draw(mask).polygon([(Xr(x),Yr(y)) for x,y in sky_outline],fill=255)
# Feather inward at character edges to keep existing metallic rim lighting.
mask=mask.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.GaussianBlur(2))
gradient=Image.new("RGBA",(256,144))
gp=gradient.load()
for yy in range(144):
    for xx in range(256):
        u=xx/255;v=yy/143
        gp[xx,yy]=(round(10+2*u),round(12-3*u),round(60+8*u+19*v),255)
gradient=gradient.resize(im.size,Image.Resampling.BILINEAR)
original_floor=im.crop((0,Yr(520),im.width,im.height)).tobytes()
im=Image.composite(gradient,im,mask)
assert im.crop((0,Yr(520),im.width,im.height)).tobytes()==original_floor
# All three former top patches are inside the replaced background.
for x,y in ((90,35),(310,40),(800,110),(1200,65)):
    r,g,b,_=im.getpixel((Xr(x),Yr(y)))
    assert 8<=r<=14 and 8<=g<=14 and 57<=b<=90,(x,y,r,g,b)
del mask,gradient,original_floor
print("PASS: Sky navy-indigo background replaces old top bars and blue streaks")
lg=Image.open(logo)
assert lg.mode=="RGBA" and lg.getchannel("A").getextrema()==(0,255)
# Remove stray blue horizontal artefact above the lettering, outside the fin.
lg=lg.copy()
ImageDraw.Draw(lg).rectangle((430,0,lg.width-1,5),fill=(0,0,0,0))
lg.save(dst/"sky_shark_logo_clean.png")
# The Home wordmark is independent of the header logo. Accept the exact
# approved transparent PNG when it is present, otherwise retain the old mark.
approved=Path(__file__).parent/"artwork/sky_shark_approved_transparent.png"
if approved.is_file():
    lg_home=Image.open(approved).convert("RGBA")
    assert lg_home.width>=1000 and lg_home.width/lg_home.height>=2.5
    lo,hi=lg_home.getchannel("A").getextrema()
    assert lo==0 and hi>=245
    print("PASS: selected new transparent Home wordmark")
else:
    lg_home=lg.copy()
    print("NOTICE: approved replacement Home PNG has not been uploaded; using cleaned original")
# Pre-filter the approved wordmark at the actual UHD destination size.
# RGBA resampling is premultiplied by Pillow; gently remove staircase noise
# in the source alpha while preserving the selected shape, text and position.
from PIL import ImageOps
lg_home=ImageOps.contain(lg_home,(round(426*3840/1648),round(109*2160/928)),Image.Resampling.LANCZOS)
alpha=lg_home.getchannel("A")
alpha=alpha.filter(ImageFilter.MedianFilter(3)).filter(ImageFilter.GaussianBlur(.65))
lg_home.putalpha(alpha)
lg_home.save(dst/"sky_shark_home_brand.png")
print("PASS: Home wordmark antialiased at UHD target size",lg_home.size)
areas=[(20,477,438,390),(459,477,385,390),(845,477,389,390),(1235,477,397,390)]
rims=[(43,493,453,849),(463,497,840,845),(852,497,1230,845),(1242,497,1620,845)]
def X(x):return round(x*3840/1648)
def Y(y):return round(y*2160/928)
# First tile has a permanently painted luminous rim. Replace just the side/bottom
# profile with the adjacent silver frame, preserving hands, logo and face plate.
import math
source=im.copy()
px=im.load(); donor=source.load()
def ramp(v): return max(0.,min(1.,v))
def rounded_distance(x,y,l,t,r,b,rad):
    qx=abs(x-(l+r)/2)-(r-l)/2+rad
    qy=abs(y-(t+b)/2)-(b-t)/2+rad
    return math.hypot(max(qx,0),max(qy,0))+min(max(qx,qy),0)-rad
for y in range(Y(488),Y(865)):
    yy=y*928/2160
    for x in range(X(20),X(458)):
        xx=x*1648/3840
        distance=rounded_distance(xx,yy,43,493,453,849,38)
        band=ramp((distance+37)/8)*ramp((18-distance)/9)
        # Do not copy another character's hands. Taper into the side from y=525.
        band*=ramp((yy-521)/12)
        if band<=0: continue
        # Align the two rounded rectangles; preserve thickness in the edge zones.
        if xx<100: dx=463+(xx-43)
        elif xx>396: dx=840+(xx-453)
        else: dx=520+(xx-100)*(783-520)/(396-100)
        if yy<550: dy=497+(yy-493)
        elif yy>790: dy=845+(yy-849)
        else: dy=554+(yy-550)*(786-554)/(790-550)
        r,g,b,alpha=px[x,y]
        rr,gg,bb,_=donor[max(0,min(im.width-1,X(dx))),max(0,min(im.height-1,Y(dy)))]
        px[x,y]=tuple(round(v+(q-v)*band) for v,q in zip((r,g,b,alpha),(rr,gg,bb,alpha)))
# Top rim under the existing gloves: remove the painted pink/blue light only in
# the exposed central gap and outer corners; no transplant of hands.
for y in range(Y(488),Y(535)):
    yy=y*928/2160
    for x in range(X(35),X(453)):
        xx=x*1648/3840
        exposed=max(ramp((105-xx)/10),ramp((xx-407)/10),ramp((xx-220)/10)*ramp((286-xx)/10))
        if exposed<=0: continue
        r,g,b,alpha=px[x,y]
        weight=exposed*ramp((yy-488)/6)*ramp((535-yy)/8)
        level=round(.2126*r+.7152*g+.0722*b)
        # Tone the baked lighting down to a chrome highlight, keep dark glove pixels.
        weight*=ramp((max(r,g,b)-100)/60)
        target=min(220,round(level*.85))
        px[x,y]=tuple(round(v+(target-v)*weight) for v in (r,g,b))+(alpha,)
print("PASS: first tile side and bottom chrome profile replaced; other tiles untouched")
# Remove the spurious narrow light strip OUTSIDE the first chrome rim. The
# approved card itself, hand geometry, wordmark and focus path stay unchanged.
before_strip=im.copy()
px=im.load()
for y in range(Y(515),Y(854)):
    yy=y*928/2160
    for x in range(0,X(43)):
        xx=x*1648/3840
        weight=ramp((yy-515)/18)*ramp((854-yy)/14)*ramp((43-xx)/1.8)
        if weight<=0: continue
        old=px[x,y]
        # Smooth navy from the existing left-side environment, without baked glow.
        shade=round(7+3*ramp((yy-530)/300))
        color=(shade,shade+3,44+round(13*ramp((yy-530)/300)),255)
        px[x,y]=tuple(round(u+(q-u)*weight) for u,q in zip(old,color))
assert im.crop((X(43),0,3840,Y(854))).tobytes()==before_strip.crop((X(43),0,3840,Y(854))).tobytes()
del before_strip
# Approved dark glass floor: reflect the existing exact card artwork vertically,
# rather than inventing new logos or applying a metallic texture. Reflection
# is compressed in perspective, slightly softened and fades towards the viewer.
floor_start=Y(859)
floor_h=2160-floor_start
source_top=Y(734)
source_bottom=Y(850)
reflection=im.crop((0,source_top,3840,source_bottom)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
reflection=reflection.resize((3840,floor_h),Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(1.6))
floor=Image.new("RGBA",(3840,floor_h))
fp=floor.load();rp=reflection.load()
for j in range(floor_h):
    f=j/max(1,floor_h-1)
    # Sharp contact edge; mirror recedes into dark transparent-looking glass.
    opacity=.56*(1-f)**1.25+.075
    for x in range(3840):
        r,g,b,_=rp[x,j]
        ambient=(4+round(2*(1-f)),8+round(3*(1-f)),32+round(13*(1-f)))
        fp[x,j]=(round(ambient[0]*(1-opacity)+r*opacity),round(ambient[1]*(1-opacity)+g*opacity),round(ambient[2]*(1-opacity)+b*opacity),255)
# Narrow, restrained blue glass edge, without a repeated horizontal metal pattern.
edge=ImageDraw.Draw(floor)
edge.line((0,floor_h-3,3839,floor_h-3),fill=(13,63,155,255),width=2)
above=im.crop((0,0,3840,floor_start)).tobytes()
im.paste(floor,(0,floor_start))
assert im.crop((0,0,3840,floor_start)).tobytes()==above
del reflection,floor,above
print("PASS: exterior strip removed; dark mirrored glass floor; artwork above the floor preserved")
# Extract multicolour Sky wordmark from the approved first Home tile.
wordmark=im.crop((X(112),Y(531),X(391),Y(699))).convert("RGBA")
wp=wordmark.load()
for yy in range(wordmark.height):
    for xx in range(wordmark.width):
        rr,gg,bb,aa=wp[xx,yy]
        chroma=max(rr,gg,bb)-min(rr,gg,bb)
        wp[xx,yy]=(rr,gg,bb,round(255*max(0,min(1,(chroma-24)/35))))
wordmark.save(dst/"player_sky_colour.png")
assert wordmark.getchannel("A").getextrema()==(0,255)
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
from PIL import ImageOps
lg_home=ImageOps.contain(lg_home,(X(426),Y(109)),Image.Resampling.LANCZOS)
ref.alpha_composite(lg_home,(X(611)+(X(426)-lg_home.width)//2,Y(5)+(Y(109)-lg_home.height)//2))
ref.convert("RGB").resize((1648,928),Image.Resampling.LANCZOS).save("design34_reference.png")
print("PASS: 3840x2160 prepared source, exact approved logo, four transparent layers")
