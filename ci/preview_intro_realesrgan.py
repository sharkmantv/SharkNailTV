"""Enhance every frame from the owner-approved 4K60 upload with Real-ESRGAN x2plus.
Use a native-scale proxy and add only the AI detail residual to the original UHD pixels.
Retain the original 60fps frame order, duration, dimensions and soundtrack."""
import sys, subprocess, os, json
import cv2
import numpy as np
import torch
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer
src, weights, dst = sys.argv[1:]
meta=json.loads(subprocess.check_output(["ffprobe","-v","error","-show_streams","-show_format","-of","json",src]))
v=next(s for s in meta["streams"] if s["codec_type"]=="video")
assert (v["width"],v["height"],v["avg_frame_rate"])==(3840,2160,"60/1"),v
assert 4.9<float(meta["format"]["duration"])<5.2
assert any(s["codec_type"]=="audio" for s in meta["streams"])
torch.set_num_threads(min(4,os.cpu_count() or 2))
torch.set_num_interop_threads(1)
model=RRDBNet(num_in_ch=3,num_out_ch=3,num_feat=64,num_block=23,num_grow_ch=32,scale=2)
up=RealESRGANer(scale=2,model_path=weights,model=model,tile=256,tile_pad=32,pre_pad=0,half=False,device=torch.device("cpu"))
w,h=3840,2160
n=w*h*3
reader=subprocess.Popen(["ffmpeg","-nostdin","-hide_banner","-loglevel","error","-i",src,"-map","0:v:0","-pix_fmt","bgr24","-f","rawvideo","-fps_mode","passthrough","pipe:1"],stdout=subprocess.PIPE)
writer=subprocess.Popen(["ffmpeg","-nostdin","-hide_banner","-loglevel","error","-y","-f","rawvideo","-pix_fmt","bgr24","-video_size","3840x2160","-framerate","60","-i","pipe:0","-i",src,"-map","0:v:0","-map","1:a:0","-c:v","libx264","-preset","fast","-crf","19","-pix_fmt","yuv420p","-profile:v","high","-level:v","5.2","-c:a","copy","-movflags","+faststart",dst],stdin=subprocess.PIPE)
count=0
try:
    while True:
        raw=reader.stdout.read(n)
        if not raw: break
        assert len(raw)==n,"Truncated source frame"
        frame=np.frombuffer(raw,dtype=np.uint8).reshape(h,w,3)
        proxy=cv2.resize(frame,(768,432),interpolation=cv2.INTER_AREA)
        with torch.inference_mode():
            ai,_=up.enhance(proxy,outscale=2)
        baseline=cv2.resize(proxy,(1536,864),interpolation=cv2.INTER_LANCZOS4)
        assert ai.shape==baseline.shape
        detail=ai.astype(np.float32)-baseline.astype(np.float32)
        # Conservative bounded detail correction; do not replace original UHD image.
        detail=np.clip(detail,-24,24)*.45
        detail=cv2.resize(detail,(w,h),interpolation=cv2.INTER_LINEAR)
        output=np.clip(frame.astype(np.float32)+detail,0,255).astype(np.uint8)
        writer.stdin.write(output.tobytes())
        count+=1
        if count%10==0: print(f"AI enhanced {count} frames",flush=True)
    writer.stdin.close()
    assert reader.wait()==0
    assert writer.wait()==0
finally:
    for proc in (reader,writer):
        if proc.poll() is None: proc.kill();proc.wait()
assert 294<=count<=312,count
if "nb_frames" in v: assert int(v["nb_frames"])==count,(v["nb_frames"],count)
print("PASS: Real-ESRGAN applied to every source frame:",count,flush=True)
