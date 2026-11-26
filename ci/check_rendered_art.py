"""Verify rendered artwork, single blended warm glow, and remote focus across all four cards."""
import subprocess,time
from PIL import Image
def adb(*args):
    return subprocess.check_output(["adb",*map(str,args)])
im=Image.open("premium-home-preview.png").convert("RGB")
w,h=im.size
ref=Image.open("design34_reference.png").convert("RGB").resize((w,h),Image.Resampling.BILINEAR)
def crop(im,x,y,X,Y):
    return im.crop((round(x*w/1648),round(y*h/928),round(X*w/1648),round(Y*h/928)))
for i,(x,X) in enumerate(((55,440),(480,825),(865,1215),(1250,1600))):
    for y,Y in ((155,470),(565,735)):
        a=crop(im,x+35,y,X-35,Y);b=crop(ref,x+35,y,X-35,Y)
        hit=sum(sum(abs(u-v) for u,v in zip(p,q))<100 for p,q in zip(a.getdata(),b.getdata()))/(a.width*a.height)
        assert hit>.80,(i,y,hit,"artwork differs")
a=crop(im,611,5,1037,114);b=crop(ref,611,5,1037,114)
assert sum(sum(abs(u-v) for u,v in zip(p,q))<100 for p,q in zip(a.getdata(),b.getdata()))/(a.width*a.height)>.82,"header logo mismatch"
rims=[(58,510,439,831),(475,510,829,828),(867,511,1219,828),(1257,511,1601,828)]
def chroma_count(im,box,pred,vertical=False):
    p=crop(im,*box)
    return sum(pred(r,g,b) for r,g,b in p.getdata())/max(1,p.height if vertical else p.width)
def warm(rgb):
    r,g,b=rgb
    return r>=190 and r>b*1.5 and b<125
def yellow(rgb):
    r,g,b=rgb
    return r>210 and g>=165 and b<130
def red(rgb):
    r,g,b=rgb
    return r>=195 and g<115 and b<90
def orange(rgb):
    r,g,b=rgb
    return r>=205 and 80<=g<165 and b<100
def flame_band(pixels,minimum):
    runs=[]; run=[]
    for p in pixels:
        if warm(p): run.append(p)
        elif run: runs.append(run);run=[]
    if run:runs.append(run)
    for band in runs:
        if len(band)<minimum:continue
        core=[i for i,p in enumerate(band) if yellow(p)]
        if not core:continue
        left=band[:core[0]];right=band[core[-1]+1:]
        if all(yellow(p) for p in band[core[0]:core[-1]+1]) and all(any(test(p) for p in side) for side in (left,right) for test in (red,orange)):
            return True
    return False
def check(im,selected):
    for i,(l,t,r,b) in enumerate(rims):
        edges=[((l+75,t-9,r-75,t+9),False),((l+65,b-9,r-65,b+9),False),((l-9,t+75,l+9,b-65),True),((r-9,t+75,r+9,b-65),True)]
        counts=[chroma_count(im,box,lambda R,G,B:yellow((R,G,B)),vertical) for box,vertical in edges]
        if i==selected:
            assert min(counts)>2,(i,counts,"incomplete warm glow")
            hits=0
            for frac in (.32,.40,.50,.60,.68):
                px=round((l+(r-l)*frac)*w/1648)
                strip=[im.getpixel((px,yy)) for yy in range(round((b-19)*h/928),round((b+19)*h/928))]
                hits+=flame_band(strip,max(5,round(16*h/928)))
            assert hits>=3,(i,hits,"expected one continuous red/orange/yellow glow")
        else:
            assert counts[1]<2,(i,counts,"warm glow leaked to unfocused card")
def shot(name):
    with open(name,"wb") as out:out.write(adb("exec-out","screencap","-p"))
    return Image.open(name).convert("RGB")
check(im,0)
for i in range(1,4):
    adb("shell","input","keyevent","22");time.sleep(.5)
    check(shot("premium-focus-%d.png"%i),i)
for i in range(3):
    adb("shell","input","keyevent","21");time.sleep(.15)
check(shot("premium-focus-return.png"),0)
print("PASS: all four artwork panels, header, single blended warm glow and independent remote focus")
