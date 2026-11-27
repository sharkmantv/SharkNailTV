"""Exact approved Design 4 artwork: real source crops, alpha layers, official x2 AI.
Never embed the movie, example channel names, fake badges or fake diagnostics."""
from pathlib import Path
import hashlib, sys, json, os, base64, io
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
def finish(name,im,shape,points=None,radius=None):
    # Upscale only isolated artwork. Conservative blend protects logos and geometry.
    proxy=im.resize((shape[0]//2,shape[1]//2),Image.Resampling.LANCZOS)
    with torch.inference_mode():
        enhanced,_=up.enhance(cv2.cvtColor(np.array(proxy),cv2.COLOR_RGB2BGR),outscale=2)
    ai=Image.fromarray(cv2.cvtColor(enhanced,cv2.COLOR_BGR2RGB))
    baseline=im.resize(shape,Image.Resampling.LANCZOS)
    blended=Image.blend(baseline,ai,.55 if radius is not None else .40)
    delta=sum(ImageStat.Stat(ImageChops.difference(baseline,blended)).mean)/3
    assert delta<=12,(name,delta)
    mask=Image.new("L",shape,0)
    if radius is not None:
        ImageDraw.Draw(mask).rounded_rectangle((1,1,shape[0]-2,shape[1]-2),radius=round(radius*shape[1]/im.height),fill=255)
    else:
        ImageDraw.Draw(mask).polygon([(round(x*shape[0]/im.width),round(y*shape[1]/im.height)) for x,y in points],fill=255)
    blended.putalpha(mask)
    fp=dst/(name+".png");blended.save(fp)
    report[name]={"size":list(shape),"mean_delta":delta,"sha256":hashlib.sha256(fp.read_bytes()).hexdigest()}
    print("PASS: original crop, alpha and Real-ESRGAN",name,report[name],flush=True)
# Header: retain the approved static branding; remove all sample channel data and clock.
header=ref.crop((16,10,1632,96))
header=smooth_fill(header,(600,17,1580,62),1165,1210)
finish("design4_player_header",header,(3764,208),[(1,25),(23,1),(1596,1),(1615,24),(1596,84),(23,84),(1,61)])
# Samples extracted from the user's approved 1672x941 racing mockup.
# 64-colour quantisation for the compact source transfer; no movie pixels,
# labels, logos, selected glow or static laser hotspot are copied.
samples={"dock":"iVBORw0KGgoAAAANSUhEUgAAACgAAABYCAMAAACwAkWeAAAAwFBMVEWCrttGh844d8EmbcknY7YeXrMZWa8YU6QaTJITSZMQRI0PP4QOPYANOXoMNXQJNHQVK1MLLmcJLmcILmcHKV8FKmIFJ1sEJFkEI1QJHlADH08CHk8BHlMBG0sAHlUAG04BGk0AGU0AGUgAGEgBGUMAGEQAF0MAFkUAFUEAFT4AFD4BFTwBFDwAFDwAFDoAFDgBEzcAEzsAEzkAEzcAEjsAEjcAEjYBEjUAETUCEjIAETQADzcBDSYCDSIBCiQBBRYHYcNfAAAFEElEQVR42n3XCVvaTBQF4EmKlYoiO1pZjCwhQHZIgA/I//9XPffOTDKpfb4DDVZeziwQQHHXuVHuRXHrNG3re0SFEChLUP4fFtcmmx8qD8hPHVG1Fcx+lGk8yCio2LW4gRmqYfQ9Irqx6JhtJXssI1Rfx6j7B3v8Jb45Zgb6JSP+cor9rCHkSfDuadcg9009URgKYZd1ipnm6anVagke2PonK1FLQR5YjkqqqZUirWeKoEJb1ZVMNz3LvCCCC6s6o8xQBIuzsB707JpNzQzy0qYIvBR461SdZi811W6/isKyao7LaoTS7YpC2PKJaJquXaVL6fXETTQelatGVV1dlV6v1xcdQL1Ys+613e0p1JPQ+mkw3VaJXn84HCCA5WJNR6LXG+I6kJFQzk4qWmK310eGGg1GoxFgVacWQF1Q7EaERgq2niSj/eJZ6TJlxhSGpev3JRuUDOSNQ/BZOZ6YUcZNb28lfHqBe1VOTgxoVJnfFIKvxF5ZaabQRCIFW6/K9f9ipD5YfTB81myoZlYOB8GZIoAv/X7lxtW0NJEhOOzL7a23VegL1y/ANqlhuWfc9LtCX5TlkuCoPjs1ogLL1ZJDcDZ+m0wm1CNX+CHVshbAgTt13fV6s16vV6tV/W78bs03G8D3/zZbz/c8b7fzOHyrfi7D0PN3HM/HD8EuDJIwDMIg4H+7IPCD3U7B/Q6X2AfEPWESRlEYhWkapqAhR8I9EgRxHIYJwTCNopQuR0CgtISHIAniIElS9OAapWka5VmeZrIt0nB7OCQxYJjCHqMMg0Zpnud4AM0AD1TQPxzCfRxDRUeCGPmUny55FuU5/TfDAzQMGB6jY8TJTnCcTB3V0DEFEDKLMjDt8vySXy6XEtIcAZME8AiYn0qmoodODgkWg+WgjxupCYeLtgpSGz0fWG+GyjwnSSHGP4iOzY3YHILYHTjsISYpnQ5DXgyeGRr6SOHhpVSFEnpxjM442QchViOhBDiezwb0aXf82AdMokg2qo0BPl1OCm62+z0YOgNeeEQ7xHdTaK9KuN1vt9QZkMSK0oxL9TQNSPF5SQm9MI4prSrj3aSJnhgeCHq44IULGmP0tFo972l+YrjeeJCeR9CP91SqnNwoTDhTEPHQ6ctgfFWpWpEKUquiOHHiJMaSjsR5thpKuqVWj061HfUm1IvTAzNOGW7p/F9X0uezl2bArz1AbALBzcJlWk4Vbwc4fSWnjcW5mZTQ1bWQW3+rloVnLKCbJPgOaVX0HsKH3Z7mi6Mv3q0xoKIuv1VJrUNPme/5IrK67nQBuliseKI8WdLGOxTaxd16ISSjFrXWK9Ohd7PCbrqOU9FV1Wt4vD8Wz/a7IdG5cs1injUiiiUmOXc4X0qvKOuVfAfmI+AVYy+c+XQqqc6qDNVJ2LUGi/nn5yfc5+f0q57Si+v9bjUdB3I+dz7nDjXLjyBNF3wj7teibXWdyRwhPDViVgNeC6vhzOa1TB3j801+Ft7xTXMlWs4Mn0j4WJrPP+iKw7RWPpXfmtui684YThjPlTdCf1Tczvhu2HXHk8lsNjHCn9X0GArg7YppNqhzVoeKyxsBxaM3RXMzG3PrTDfTcaYCeMcFnV1hj11Q+fsSGPBK9noudrb1OHPn9GXHAGMVCaW+D2yr0XcWzmQ2LgHC3xAMeL0X986jZTda/fEMe2SU1uD9fsUfcbdzp9mw7ca31BpvoKi9nZfv7x2EvmKob3yjP5bKrWUAg2iuAAAAAElFTkSuQmCC","key":"iVBORw0KGgoAAAANSUhEUgAAABQAAAArCAMAAABPe1hHAAAAwFBMVEW70uyVrMt1kblPisZXfKxLYoU6VHssUIctS3soSHsnRXYnQW0jQ3QiP24aQn4gPGoeO2cdOGUcNF4ZM14ROXgONHEVMV8MMWsaLk8ULlcUK1IRK1YLLWUILGQJKVsHKFkRJUkNJEsMIkgJJFIJH0MHJlYGI1AHIEsHHEAEIlUFIEsEH0oEHkgDHUcEHUUDHEUDG0IJGTkDGkECGUECGDsCFzoBGD0BFzoBFjgMEzAJEi4BEi8BECwBDysBCSQAAhjyRKjqAAAB1UlEQVR42k3T2ZKiUBAE0IJh0wYVERcUFVu0xQVGEVwA//+vJuuC02bwwjHJwAguPZGiqu59TdPUJvQsnuVrqUr0maIoXxqRrOqtVqcJPQqY1Oq0DEN/h57VmGRX141Wk3bbpOdDklymNsc0TcuyqNTIqE3cW1a326WHLJm6MAG9nm3bdJcUw6gNZNv9vuMA1bpXk+NMJhP6K6mmmIPV5Hloapagnu2weV4QMPa4ZdtMU7YVI6B+cjoFBStGY9h3mrFA2JrucnsqpoSAVts1Xv7L82oCrNYcNL8Cbxk09z9IkggMhPyInBGgvV0xpO9kGXB4ic9Zml6vtxuuHAFOgZcsvabA/MYReEgwhHKap3mDw8shjmPmLPvFODoicZJcGqa7AjwcfjXPb/QAbvf7PZxVMONxG0VglBtl3G+iTcR+PJ7QPl8Yo++NSLTH9CnBfwfuFmEY8g9bnkYZ6O7m8/liwcwbh9MJ2JnPfJ89DBumStXnoxkYHoY7no6o0hTfHSFcX/D6ZkOVJ7vuwGVHX4x8U1kpit+BunWZ2/ho17LiDzqDwbs+m+Obr8ay4o7c/+j74iAsFVnR6zMw4FBRQh9t5c9HqCzLoni97uOPAIuyxPn6zD8cY2hnVFeq0gAAAABJRU5ErkJggg==","info":"iVBORw0KGgoAAAANSUhEUgAAABoAAAAzCAMAAAC+N+gCAAAAwFBMVEXY9f2q2/V7wfNnqORmoNdMj9M2jeMygthDb6cudsYrccAqarQjbsInZrAnYqoiYawvW5YjXKEhWZ4hVpksTYEiS4YfU5UfTYwbUpYaTo8cS4oZS4waSIUZRYEWRYMbQnkWQnsbPnIWPXMVPnYWOm4UOm8UOGwVN2gUNmkUNWcSOXETNmkTNWcSNGYUMmISMmMRMmISL14RLVsQLlwQLFkOLV0QK1cQKlcPK1cPKlYPKlUNKlcPKVUOKVULKVsLJleLRMvGAAAChElEQVR42l3QDZupUBQF4JOMpqgkQ6EPIwkJMXV5uPP//9Vde5+amTuLeYb9nnVsxGaJRFEQBIvFLNztQs/uG7quv+piQ/D2Np1OXG/uGd2O0kZA3jAeWaY96CpCUTrqS48jSFzLNAaaCuioPU3T9AGFyLUMjUAoKg5DdN0wjIFAadTXegDRacUw+v2+SeQYWue3mCbRxB6o/wlVLMsCubYmeAEWrli27ThMHVRYuGKi4jjOSERT/0UovZ8VBtcVWFCRIisEmLuTiRjHXaHKCoEsTChiHCiKJgFbOaMGfN8Xy1ehspgS5Nj3Z3Nx7Ar+FNNqrpoiM4oolA4JVQj8Zr5YLMRSUQfckXc1YyQQY+Vl0IrvNxBQxFDRTf4cyHw+a8YxIoYdw7YtllkDcRzFUbQC9W3aDR2+ic4TrN7FUO2TeJAwZOE5AjLpi/r+PAxDaqwkJGuQ7ZHMFmHwDe/rdcLksYQtJOskSdMU5Hi0mxQ+jnG6TzPQqL0NsluvMc/2SA5y53NeGsKQZtk+PxwOYtz18IV4g92OZI8OSQ7yw6bU3JbleX7AH2geLn5ct8+yzTYnFa8glL5WSLPNNy3iIJJrr3m5LNvmW4QpjkgSuTY18my7IQpW8RI/QYKrMhw+UPJ8m4PiHeaArBm3Aa12Sbqh8bHJCY8jaNh9P+B8cSyKE3I+NTnSr5F+YHzmXPiJEDKd5Pw7fAC0/3suy/JStrleLx/0oBZTecXsK+XlWoKyvxf5/g+lwlO+bam6ynnVRNIWVFf17Xar6vomhc4xlVVdVUTInZ53ekX0eX3Uj1uTu8yNW4fPa13fb8+GnvfHE6+Zis/68cBbTvsfkfT8JdW5KIp/l12cYXGdGvMAAAAASUVORK5CYII="}
checks={"dock":"9a6a3d66232dc8214ebdfa6525152183295562f889030dcc31c86ca2a56bea49","key":"b6cab08ac28a8c787cd125ccb6d912cbd24a65a451ac8993cbda155273959da4","info":"c95d9b736e3eb29bfb84b4160473a1b1df4e57f964d5be5f2d05cfc499686ffa"}
def sample(name):
    raw=base64.b64decode(samples[name],validate=True)
    assert hashlib.sha256(raw).hexdigest()==checks[name],name
    return Image.open(io.BytesIO(raw)).convert("RGB")
def stretch_panel(edge,width):
    # Extend clean, icon-free glass across the interior; retain the original
    # curved edges, horizontal highlight and bottom reflection.
    out=Image.new("RGB",(width,edge.height))
    center=edge.crop((edge.width-1,0,edge.width,edge.height))
    out.paste(center.resize((width,edge.height),Image.Resampling.BICUBIC),(0,0))
    out.paste(edge,(0,0))
    out.paste(edge.transpose(Image.Transpose.FLIP_LEFT_RIGHT),(width-edge.width,0))
    return out
dock=stretch_panel(sample("dock"),1598)
finish("design4_player_dock",dock,(3664,220),radius=21)
key=stretch_panel(sample("key"),69)
finish("design4_player_key",key,(240,144),radius=9)
# Info glass uses the exact top corner colour and reflection, extended with
# a dark blue interior and mirrored lower corner. Diagnostics remain native.
corner=sample("info")
top=stretch_panel(corner,355)
panel=Image.new("RGB",(355,328))
middle=top.crop((0,top.height-1,top.width,top.height))
panel.paste(middle.resize((355,328),Image.Resampling.BICUBIC),(0,0))
panel.paste(top,(0,0))
panel.paste(top.transpose(Image.Transpose.FLIP_TOP_BOTTOM),(0,328-top.height))
finish("design4_info_panel",panel,(1240,1240),radius=22)
for name in ("design4_player_dock","design4_player_key","design4_info_panel"):
    report[name]["method"]="mockup-derived isolated texture, reconstructed without labels, Real-ESRGAN x2 and alpha"
Path("design4-artwork-verification.json").write_text(json.dumps(report,indent=2))
