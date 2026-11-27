"""Create isolated player skin layers from the 2560x1440 vector design.
Real-ESRGAN x2 operates on original QHD raster pixels; output is resampled to UHD geometry.
No channel text, video, icons or focus state are baked into the production layers."""
from pathlib import Path
from io import BytesIO
import hashlib, sys, json, os
import cairosvg, cv2, numpy as np, torch
from PIL import Image, ImageStat, ImageChops
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer
dst=Path("app/src/main/res/drawable-nodpi")
dst.mkdir(parents=True,exist_ok=True)
defs="""<defs>
<linearGradient id="glass" x2="0" y2="1" gradientUnits="objectBoundingBox"><stop stop-color="#154878" stop-opacity=".93"/><stop offset=".12" stop-color="#0b264a" stop-opacity=".96"/><stop offset=".49" stop-color="#041127" stop-opacity=".95"/><stop offset=".82" stop-color="#071831" stop-opacity=".97"/><stop offset="1" stop-color="#144580" stop-opacity=".98"/></linearGradient>
<linearGradient id="metal" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#c5eaff"/><stop offset=".10" stop-color="#329be8"/><stop offset=".25" stop-color="#154781"/><stop offset=".70" stop-color="#09274e"/><stop offset=".9" stop-color="#238cdd"/><stop offset="1" stop-color="#8ae4ff"/></linearGradient>
<linearGradient id="button" x2="0" y2="1"><stop stop-color="#163e6f"/><stop offset=".35" stop-color="#0b2447"/><stop offset="1" stop-color="#051128"/></linearGradient>
</defs>"""
def plate(x,y,w,h,r):
    return f'<rect x="{x+2}" y="{y+2}" width="{w-4}" height="{h-4}" rx="{r}" fill="url(#glass)" stroke="url(#metal)" stroke-width="4"/><rect x="{x+9}" y="{y+9}" width="{w-18}" height="{h-18}" rx="{max(1,r-5)}" fill="none" stroke="#3389cd" stroke-width="1" opacity=".5"/>'
def key(x,y,w,h):
    return f'<rect x="{x+1}" y="{y+1}" width="{w-2}" height="{h-2}" rx="12" fill="url(#button)" stroke="#2b5c92" stroke-width="1"/>'
# Coordinates and color stops from the selected QHD vector overlay.
layers=[
 ("design4_player_header",(44,36,2516,178),plate(44,36,2472,142,18)),
 ("design4_player_dock",(44,1208,2516,1376),plate(44,1208,2472,168,20)),
 ("design4_player_key",(80,1227,410,1352),key(80,1227,330,125)),
 ("design4_player_options",(244,1104,2316,1186),plate(244,1104,2072,82,12)),
]
def render(shapes):
    source=f'<svg xmlns="http://www.w3.org/2000/svg" width="2560" height="1440" viewBox="0 0 2560 1440">{defs}{shapes}</svg>'
    im=Image.open(BytesIO(cairosvg.svg2png(bytestring=source.encode(),output_width=2560,output_height=1440))).convert("RGBA")
    assert im.size==(2560,1440)
    return im
torch.set_num_threads(min(4,os.cpu_count() or 2))
torch.set_num_interop_threads(1)
model=RRDBNet(num_in_ch=3,num_out_ch=3,num_feat=64,num_block=23,num_grow_ch=32,scale=2)
up=RealESRGANer(scale=2,model_path=sys.argv[1],model=model,tile=256,tile_pad=32,pre_pad=0,half=False,device=torch.device("cpu"))
report={}
preview2=Image.new("RGBA",(2560,1440))
preview4=Image.new("RGBA",(3840,2160))
for name,box,shape in layers:
    source=render(shape).crop(box)
    size=(round(source.width*1.5),round(source.height*1.5))
    # Keep transparency independent of the RGB neural model. Matte the few transparent edge pixels in matching navy.
    matte=Image.new("RGB",source.size,(8,23,47))
    matte.paste(source,mask=source.getchannel("A"))
    with torch.inference_mode():
        enhanced,_=up.enhance(cv2.cvtColor(np.array(matte),cv2.COLOR_RGB2BGR),outscale=2)
    assert enhanced.shape[:2]==(source.height*2,source.width*2)
    ai=Image.fromarray(cv2.cvtColor(enhanced,cv2.COLOR_BGR2RGB)).resize(size,Image.Resampling.LANCZOS)
    baseline=matte.resize(size,Image.Resampling.LANCZOS)
    result=Image.blend(baseline,ai,.40)
    delta=sum(ImageStat.Stat(ImageChops.difference(baseline,result)).mean)/3
    assert delta<=12,(name,delta)
    result.putalpha(source.getchannel("A").resize(size,Image.Resampling.LANCZOS))
    fp=dst/(name+".png");result.save(fp)
    preview2.alpha_composite(source,(box[0],box[1]))
    preview4.alpha_composite(result,(round(box[0]*1.5),round(box[1]*1.5)))
    report[name]={"size":list(size),"source_size":list(source.size),"source_canvas":[2560,1440],"model":"RealESRGAN_x2plus","ai_output_size":[source.width*2,source.height*2],"mean_delta":delta,"sha256":hashlib.sha256(fp.read_bytes()).hexdigest()}
    print("PASS QHD source and AI UHD layer",name,report[name],flush=True)
preview2.save("player-skin-qhd.png")
preview4.save("player-skin-uhd.png")
Path("design4-artwork-verification.json").write_text(json.dumps(report,indent=2))
