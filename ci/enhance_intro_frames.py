"""Apply the same official Real-ESRGAN x2plus model as the approved player artwork.
Conservative blending limits hallucinated logo changes and frame-to-frame flicker.
The final 4K stage is Lanczos, not native 4K generation."""
import os, sys, subprocess
from pathlib import Path
import cv2
import numpy as np
import torch
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer

src, dst, weights = sys.argv[1:]
root = Path(os.environ["RUNNER_TEMP"]) / "shark-intro-frames"
original = root / "source"
enhanced = root / "enhanced"
original.mkdir(parents=True, exist_ok=True)
enhanced.mkdir(parents=True, exist_ok=True)
subprocess.run(["ffmpeg","-hide_banner","-loglevel","error","-y","-i",src,
                "-vf","crop=768:432:0:8,fps=24","-start_number","0",
                str(original / "%05d.png")], check=True)
frames = sorted(original.glob("*.png"))
assert 116 <= len(frames) <= 125, len(frames)
torch.set_num_threads(min(4,os.cpu_count() or 2))
torch.set_num_interop_threads(1)
model = RRDBNet(num_in_ch=3,num_out_ch=3,num_feat=64,num_block=23,num_grow_ch=32,scale=2)
up = RealESRGANer(scale=2,model_path=weights,model=model,tile=256,tile_pad=32,pre_pad=0,half=False,device=torch.device("cpu"))
for i, frame in enumerate(frames):
    im = cv2.imread(str(frame))
    assert im is not None and im.shape[:2] == (432,768), frame
    with torch.inference_mode():
        ai,_ = up.enhance(im,outscale=2)
    baseline = cv2.resize(im,(1536,864),interpolation=cv2.INTER_LANCZOS4)
    assert ai.shape == baseline.shape
    blend = cv2.addWeighted(baseline,.55,ai,.45,0)
    assert cv2.imwrite(str(enhanced / frame.name),blend)
    if i % 10 == 0 or i+1 == len(frames):
        print(f"Real-ESRGAN: {i+1}/{len(frames)} frames",flush=True)
subprocess.run(["ffmpeg","-hide_banner","-loglevel","error","-y","-framerate","24",
                "-start_number","0","-i",str(enhanced / "%05d.png"),
                "-i",src,"-map","0:v:0","-map","1:a:0","-c:v","ffv1",
                "-c:a","copy",dst],check=True)
print("PASS: Real-ESRGAN x2plus applied to every source frame; original audio preserved",flush=True)
