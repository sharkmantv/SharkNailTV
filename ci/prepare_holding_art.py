"""Prepare independently composited transparent card art from the approved source."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageStat
import hashlib

dst=Path("app/src/main/res/drawable-nodpi")
source=dst/"design2_home.png"
raw=source.read_bytes()
assert hashlib.sha1(("blob "+str(len(raw))+"\0").encode()+raw).hexdigest()=="409f8da27c0ef8f7514fd5e1fae71709c821e975", "Wrong uploaded artwork"
im=Image.open(source).convert("RGB")
assert im.width>=1200 and im.height>=650 and abs(im.width/im.height-16/9)<.04, ("Invalid design dimensions",im.size)
# Use the same reference coordinate space as the native TV view.
im=im.resize((1648,928),Image.Resampling.LANCZOS)
# Compare large patches against the reference image supplied in chat, allowing re-encoding.
ref=[[(31,84,160),(25,89,216),(20,79,172),(20,112,210)],[(124,69,97),(33,48,76),(60,48,58),(65,109,215)],[(74,134,216),(71,86,113),(66,93,146),(36,92,170)],[(102,106,92),(68,115,174),(63,105,171),(62,109,176)],[(145,129,106),(29,91,184),(25,88,196),(16,83,180)]]
errors=[]
for j,y in enumerate((180,370,620,760,880)):
    for k,x in enumerate((250,640,1030,1420)):
        avg=ImageStat.Stat(im.crop((x-30,y-30,x+30,y+30))).mean
        errors.append(sum(abs(a-b) for a,b in zip(avg,ref[j][k]))/3)
assert sum(errors)/len(errors)<23 and max(errors)<68, ("Uploaded artwork differs from approved holding-icons design",errors)
# Remove the original still-image gold focus and reflection; live focus is rendered by Android.
px=im.load()
for y in range(485,928):
    for x in range(40,461):
        if y<551 and not (505<=y<=535 and 165<=x<=345) and not (x<89 or x>413):
            continue
        r,g,b=px[x,y]
        if r>65 and g>r*.43 and g>b*1.08 and r>b*1.18:
            strength=min(1,max(0,(min(r,g)-b)/42))
            blue=(int(r*.19+b*.34),min(255,int(g*.75+r*.14)),min(255,int(r*.96+g*.25)))
            px[x,y]=tuple(round(a*(1-strength)+v*strength) for a,v in zip((r,g,b),blue))
neutral=im.convert("RGBA")
neutral.save("design2_neutral_reference.png")
backdrop=neutral.copy()
areas=[(48,490,400,350),(456,490,380,350),(842,490,386,350),(1238,490,382,350)]
rims=[(64,505,438,832),(478,517,821,825),(849,517,1207,825),(1254,517,1602,825)]
recon=neutral.copy()
for i,((x,y,w,h),(l,t,r,b)) in enumerate(zip(areas,rims)):
    mask=Image.new("L",(w,h),0)
    ImageDraw.Draw(mask).rounded_rectangle((l-x,t-y,r-x,b-y),radius=32,fill=255)
    tile=neutral.crop((x,y,x+w,y+h))
    tile.putalpha(mask)
    assert tile.getchannel("A").getextrema()==(0,255)
    tile.save(dst/("design2_card_%d.png"%i))
    backdrop.paste((0,0,0,0),(x,y,x+w,y+h),mask)
backdrop.save(dst/"design2_backdrop.png")
# Verify layer reconstruction is lossless and all cards actually have alpha.
recon=backdrop.copy()
for i,(x,y,w,h) in enumerate(areas):
    recon.alpha_composite(Image.open(dst/("design2_card_%d.png"%i)),(x,y))
assert recon.tobytes()==neutral.tobytes(), "Artwork layers fail exact reconstruction"
print("PASS: approved source fingerprint, four transparent cards, exact layer composition")
