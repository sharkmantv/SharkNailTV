"""Verify the actual Home screenshot and dynamic selection against the approved artwork."""
import subprocess,time
from PIL import Image
def adb(*args):
    return subprocess.check_output(["adb",*map(str,args)])
im=Image.open("premium-home-preview.png").convert("RGB")
w,h=im.size
ref=Image.open("design2_neutral_reference.png").convert("RGB").resize((w,h),Image.Resampling.BILINEAR)
def crop(im,x,y,X,Y):
    return im.crop((round(x*w/1648),round(y*h/928),round(X*w/1648),round(Y*h/928)))
# Compare both shark heads and the held card interiors, not merely whether images exist.
for i,(x,X) in enumerate(((55,445),(456,836),(842,1228),(1238,1620))):
    for y,Y in ((150,480),(590,755)):
        a=crop(im,x+25,y,X-25,Y);b=crop(ref,x+25,y,X-25,Y)
        aa=list(a.getdata());bb=list(b.getdata())
        hit=sum(sum(abs(u-v) for u,v in zip(p,q))<95 for p,q in zip(aa,bb))/len(aa)
        assert hit>.83,(i,y,hit,"rendered shark/card differs from approved artwork")
def gold(im,i):
    xs=((125,365),(535,760),(910,1150),(1320,1540))
    bottoms=(823,815,815,815)
    x,X=xs[i];y=bottoms[i]
    p=crop(im,x,y-5,X,y+6)
    return sum(r>195 and g>145 and b<145 and r>b*1.35 for r,g,b in p.getdata())/max(1,p.width)
def check_focus(im,selected):
    counts=[gold(im,i) for i in range(4)]
    assert counts[selected]>2,(counts,"selected gold frame missing")
    assert all(n<.9 for i,n in enumerate(counts) if i!=selected),(counts,"gold leaked to unfocused card")
check_focus(im,0)
adb("shell","input","keyevent","22");time.sleep(.6)
try:
    with open("premium-focus-movies.png","wb") as out:
        out.write(adb("exec-out","screencap","-p"))
    check_focus(Image.open("premium-focus-movies.png").convert("RGB"),1)
finally:
    adb("shell","input","keyevent","21")
    time.sleep(.4)
print("PASS: all four real rendered shark/card layers and focus moves independently")
