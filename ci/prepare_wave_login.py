import base64
import io
from pathlib import Path
import numpy as np
import torch
from PIL import Image
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer

torch.set_num_threads(2)
source = Image.open(io.BytesIO(base64.b64decode(Path("ci/artwork/wave_login_source.b64").read_text()))).convert("RGBA")
assert source.size == (728, 938), source.size
# Retain the original device and football montage, not the old baked-in logo or wording.
crop = source.crop((0, 550, 728, 938))
model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=2)
enhancer = RealESRGANer(scale=2, model_path="RealESRGAN_x2plus.pth", model=model, tile=128, tile_pad=16, pre_pad=0, half=False, device=torch.device("cpu"))
rgb = np.array(crop.convert("RGB"))
bgr = np.ascontiguousarray(rgb[:, :, ::-1])
output, _ = enhancer.enhance(bgr, outscale=2)
assert output.shape[:2] == (776,1456), output.shape
image = Image.fromarray(np.ascontiguousarray(output[:, :, ::-1]), "RGB").convert("RGBA")
image.putalpha(crop.getchannel("A").resize(image.size, Image.Resampling.LANCZOS))
target = Path("app/src/main/res/drawable-nodpi/wave_login_promo.png")
target.parent.mkdir(parents=True, exist_ok=True)
image.save(target, optimize=True)
assert Image.open(target).size == (1456,776)
print(f"Real-ESRGAN x2plus completed: {target}, {image.size}")
