"""Verify rendered artwork, single blended flame border, and remote focus across all four cards."""
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
def color_count(im,box,pred,vertical=False):
    p=crop(im,*box);return sum(pred(r,g,b) for r,g,b in p.getdata())/max(1,p.height if vertical else p.width)
gold=lambda r,g,b:r>200 and g>145 and b<155 and r>b*1.35
red=lambda r,g,b:r>175 and g<145 and b<100 and r>g*1.45
def check(im,selected):
    for i,(l,t,r,b) in enumerate(rims):
        # Straight central sections of every edge, including previously missing sides.
        edges=[((l+75,t-7,r-75,t+7),False),((l+65,b-7,r-65,b+7),False),((l-7,t+75,l+7,b-65),True),((r-7,t+75,r+7,b-65),True)]
        counts=[color_count(im,box,gold,vertical) for box,vertical in edges]
        if i==selected:
            assert min(counts)>3,(i,counts,"incomplete thick gold border")
            # A single continuous band: look across the same bottom rim.
            # Gold centre must touch orange transition and red shoulder on both sides.
            # Reject the old two-rim design, even if both colours are present.
            middle=(l+r)/2
            def profile(x):
                px=round(x*w/1648)
                lo=round((b-15)*h/928)
                hi=round((b+15)*h/928)
                return [(y,im.getpixel((px,y))) for y in range(lo,hi+1)]
            orange=lambda R,G,B:R>210 and 70<=G<=175 and B<100
            # Bright pale-yellow highlight is part of the same continuous gold core.
            core=lambda R,G,B:R>220 and G>145 and R>B*1.25 and G>B*1.25
            successes=0
            for fraction in (.32,.40,.50,.60,.68):
                seq=profile(l+(r-l)*fraction)
                gidx=[n for n,(_,rgb) in enumerate(seq) if core(*rgb)]
                if not gidx: continue
                first,last=min(gidx),max(gidx)
                left=[rgb for _,rgb in seq[:first]]
                right=[rgb for _,rgb in seq[last+1:]]
                if (any(red(*rgb) for rgb in left) and any(red(*rgb) for rgb in right)
                    and any(orange(*rgb) for rgb in left) and any(orange(*rgb) for rgb in right)
                    and all(core(*rgb) or orange(*rgb) for _,rgb in seq[first:last+1])):
                    successes+=1
            assert successes>=3,(i,successes,"expected one blended red/orange/gold rim")
        else:
            assert counts[1]<2.0,(i,counts,"gold leaked to unfocused card")
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
print("PASS: all four artwork panels, header, single blended flame border and independent remote focus")
