"""Exact approved Design 4 artwork: real source crops, alpha layers, official x2 AI.
Never embed the movie, example channel names, fake badges or fake diagnostics."""
from pathlib import Path
import hashlib, sys, json, os
import cv2
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageStat, ImageChops
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer
src=Path("ci/artwork/sky_shark_design4_reference.png")
assert hashlib.sha256(src.read_bytes()).hexdigest()=="6a1676e7175174d657bbe75e9629208212872724439eb64e620d455fde2dfaf8"
original=Image.open(src).convert("RGB")
assert original.size==(1672,941)
ref=original.resize((1648,928),Image.Resampling.LANCZOS)
dst=Path("app/src/main/res/drawable-nodpi")
torch.set_num_threads(min(4,os.cpu_count() or 2))
torch.set_num_interop_threads(1)
model=RRDBNet(num_in_ch=3,num_out_ch=3,num_feat=64,num_block=23,num_grow_ch=32,scale=2)
up=RealESRGANer(scale=2,model_path=sys.argv[1],model=model,tile=256,tile_pad=32,pre_pad=0,half=False,device=torch.device("cpu"))
report={}
def smooth_fill(im,rect,x0,x1):
    x,y,X,Y=rect
    a=np.array(im)
    left=a[y:Y,x0:x0+2].astype(float).mean(axis=1)
    right=a[y:Y,x1:x1+2].astype(float).mean(axis=1)
    weight=np.linspace(0,1,X-x)[None,:,None]
    a[y:Y,x:X]=np.round(left[:,None,:]*(1-weight)+right[:,None,:]*weight).astype(np.uint8)
    return Image.fromarray(a)
def finish(name,im,shape,points):
    # Upscale only isolated artwork. Conservative blend protects logos and geometry.
    proxy=im.resize((shape[0]//2,shape[1]//2),Image.Resampling.LANCZOS)
    with torch.inference_mode():
        enhanced,_=up.enhance(cv2.cvtColor(np.array(proxy),cv2.COLOR_RGB2BGR),outscale=2)
    ai=Image.fromarray(cv2.cvtColor(enhanced,cv2.COLOR_BGR2RGB))
    baseline=im.resize(shape,Image.Resampling.LANCZOS)
    blended=Image.blend(baseline,ai,.40)
    delta=sum(ImageStat.Stat(ImageChops.difference(baseline,blended)).mean)/3
    assert delta<=12,(name,delta)
    mask=Image.new("L",shape,0)
    ImageDraw.Draw(mask).polygon([(round(x*shape[0]/im.width),round(y*shape[1]/im.height)) for x,y in points],fill=255)
    blended.putalpha(mask)
    fp=dst/(name+".png");blended.save(fp)
    report[name]={"size":list(shape),"mean_delta":delta,"sha256":hashlib.sha256(fp.read_bytes()).hexdigest()}
    print("PASS: original crop, alpha and Real-ESRGAN",name,report[name],flush=True)
# Header: retain the approved static branding; remove all sample channel data and clock.
header=ref.crop((16,10,1632,96))
header=smooth_fill(header,(600,17,1580,62),1165,1210)
finish("design4_player_header",header,(3764,208),[(1,25),(23,1),(1596,1),(1615,24),(1596,84),(23,84),(1,61)])
# Dock: only the original outer metallic trim remains. Buttons and progress are separate.
dock=ref.crop((28,713,1628,908))
dock=smooth_fill(dock,(23,18,1575,128),20,1578)
dock=smooth_fill(dock,(35,135,1565,177),26,1570)
finish("design4_player_dock",dock,(3732,454),[(1,31),(32,1),(1568,1),(1598,28),(1598,166),(1570,193),(32,193),(1,166)])
# Unfocused key artwork sampled from the first button. Remove baked text and icon.
key=ref.crop((59,733,217,829))
key=smooth_fill(key,(13,12,145,83),10,146)
finish("design4_player_key",key,(368,224),[(0,0),(157,0),(157,95),(0,95)])
# Info panel frame only; all diagnostic data is rendered from actual player values.
info=ref.crop((1119,116,1628,624))
info=smooth_fill(info,(19,13,491,492),15,492)
finish("design4_info_panel",info,(1184,1184),[(1,22),(23,1),(486,1),(508,22),(508,485),(486,507),(22,507),(1,485)])
Path("design4-artwork-verification.json").write_text(json.dumps(report,indent=2))
