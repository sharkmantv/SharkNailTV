"""Verify rendered artwork, single Sky spectrum border, and remote focus across all four cards."""
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
a=crop(im,574,5,1074,133);b=crop(ref,574,5,1074,133)
assert sum(sum(abs(u-v) for u,v in zip(p,q))<100 for p,q in zip(a.getdata(),b.getdata()))/(a.width*a.height)>.82,"header logo mismatch"
rims=[(58,510,439,831),(475,510,829,828),(867,511,1219,828),(1257,511,1601,828)]
def chroma_count(im,box,pred,vertical=False):
    p=crop(im,*box)
    return sum(pred(r,g,b) for r,g,b in p.getdata())/max(1,p.height if vertical else p.width)
def check(im,selected):
    for i,(l,t,r,b) in enumerate(rims):
        # Sample the actual rendered spectrum in all four edge zones.
        gold=lambda R,G,B:R>205 and G>125 and R>B*1.3 and G>B*1.12
        pink=lambda R,G,B:R>165 and B>110 and R>G*1.3 and B>G*1.12
        cyan=lambda R,G,B:B>180 and G>130 and B>R*1.2
        blue=lambda R,G,B:B>180 and B>R*1.35 and B>G*1.10
        zones=[((l-9,t+80,l+9,b-80),gold,True),
               ((l+70,t-9,r-70,t+9),pink,False),
               ((l+70,b-9,r-70,b+9),pink,False),
               ((r-9,t+80,r+9,b-80),lambda R,G,B:cyan(R,G,B) or blue(R,G,B),True)]
        counts=[chroma_count(im,box,pred,vertical) for box,pred,vertical in zones]
        if i==selected:
            assert min(counts)>2,(i,counts,"incomplete spectrum border")
        else:
            # Pink is absent from the original metallic bottom rim.
            assert counts[2]<2,(i,counts,"spectrum leaked to unfocused tile")
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
print("PASS: all four artwork panels, header, single Sky spectrum border and independent remote focus")
