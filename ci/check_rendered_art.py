"""Verify all four actual rendered tiles contain the gold cutout, not the old silver icons."""
from PIL import Image
im=Image.open("premium-home-preview.png").convert("RGB")
w,h=im.size
for i in range(4):
    x=72+i*379
    region=im.crop((round((x+70)*w/1648),round(670*h/928),round((x+297)*w/1648),round(806*h/928)))
    n=sum(r>150 and g>90 and r-b>42 and g-b>14 for r,g,b in region.getdata())
    assert n>region.width*region.height*.015,(i,n,"missing gold icon")
    print("Verified rendered gold icon",i,n)
