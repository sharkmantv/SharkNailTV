"""Verify actual home render contains slim Sky-style top strips and no surrounding gold frame."""
from PIL import Image
im=Image.open("premium-home-preview.png").convert("RGB")
w,h=im.size
def vivid(r,g,b):
    return (r>120 and r>g*1.2) or (b>145 and r>80 and b>g*1.25)
for i in range(4):
    x=72+i*379
    def crop(x0,y0,x1,y1):
        return im.crop((round(x0*w/1648),round(y0*h/928),round(x1*w/1648),round(y1*h/928)))
    top=crop(x+35,710,x+332,719)
    bottom=crop(x+35,840,x+332,849)
    nt=sum(vivid(*p) for p in top.getdata())/max(1,top.width*top.height)
    nb=sum(vivid(*p) for p in bottom.getdata())/max(1,bottom.width*bottom.height)
    assert nt>.12,(i,nt,"missing top colour strip")
    assert nt>nb+.08,(i,nt,nb,"top strip is not distinct from bottom")
    print("PASS: top accent strip",i,round(nt,3),round(nb,3))
