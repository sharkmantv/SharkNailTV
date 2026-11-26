"""Isolated artwork experiment. Never modify app resources or publish an APK."""
import hashlib
import json
import os
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image, ImageDraw
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer

source = Path("app/src/main/res/drawable-nodpi/sky_shark_home_34.png")
raw = source.read_bytes()
git_hash = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
assert git_hash == "c7cfeda2fd67972172a3fc6dc1e7e4b40556c2f8", git_hash
original = Image.open(source).convert("RGB")
assert original.size == (3840, 2160), original.size
out = Path("artwork-output")
out.mkdir(exist_ok=True)
# Approved source is already conventionally enlarged. Reconstruct detail from
# a 1080p proxy using the official x2 network, without changing aspect or layout.
proxy = original.resize((1920,1080), Image.Resampling.LANCZOS)
torch.set_num_threads(min(4, os.cpu_count() or 2))
torch.set_num_interop_threads(1)
model = RRDBNet(num_in_ch=3,num_out_ch=3,num_feat=64,num_block=23,num_grow_ch=32,scale=2)
up = RealESRGANer(scale=2, model_path="RealESRGAN_x2plus.pth", model=model,
                  tile=256, tile_pad=32, pre_pad=0, half=False,
                  device=torch.device("cpu"))
print("Running official x2 network on CPU, tiled to limit memory", flush=True)
with torch.inference_mode():
    result, _ = up.enhance(cv2.cvtColor(np.asarray(proxy), cv2.COLOR_RGB2BGR), outscale=2)
enhanced = Image.fromarray(cv2.cvtColor(result, cv2.COLOR_BGR2RGB))
assert enhanced.size == (3840,2160)
# A conservative alternative preserves more of the original lettering and geometry.
blend = Image.blend(original, enhanced, 0.55)
enhanced.save(out/"sky_shark_ai_4k.png")
blend.save(out/"sky_shark_conservative_4k.png")
for name in ["sky_shark_ai_4k.png","sky_shark_conservative_4k.png"]:
    p=out/name
    with Image.open(p) as im:
        im.verify()
    with Image.open(p) as im:
        assert im.size==(3840,2160) and im.format=="PNG"
# Side-by-side comparison: same pixel coordinates, no scaling of detail crops.
comparison = Image.new("RGB",(1800,1080),(12,20,35))
draw = ImageDraw.Draw(comparison)
for i,(name,im) in enumerate([("Existing 4K",original),("Real-ESRGAN 4K",enhanced),("55% blend",blend)]):
    x=600*i
    draw.text((x+15,12),name,fill="white")
    comparison.paste(im.resize((600,338),Image.Resampling.LANCZOS),(x,40))
    comparison.paste(im.crop((350,360,950,690)),(x,395))
    comparison.paste(im.crop((1230,1450,1830,1765)),(x,745))
comparison.save(out/"comparison.png")
diff=np.abs(np.asarray(original,dtype=np.float32)-np.asarray(enhanced,dtype=np.float32))
report={"method":"Real-ESRGAN x2plus CPU, 1080p proxy from approved pre-upscaled 4K artwork",
        "native_generation":False,"source_git_blob":git_hash,"output_dimensions":[3840,2160],
        "mean_absolute_pixel_difference":float(diff.mean()),
        "model_sha256":hashlib.sha256(Path("RealESRGAN_x2plus.pth").read_bytes()).hexdigest(),
        "outputs":{p.name:{"bytes":p.stat().st_size,"sha256":hashlib.sha256(p.read_bytes()).hexdigest()} for p in out.glob("*.png")},
        "visual_approval":"required before APK integration"}
(out/"verification.json").write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
