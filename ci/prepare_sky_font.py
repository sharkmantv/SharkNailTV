"""Bundle a pinned OFL-licensed Nunito Sans, static weights for Android 24+."""
from pathlib import Path
from urllib.request import urlopen
from hashlib import sha1
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
import io
base="https://raw.githubusercontent.com/google/fonts/a0e524c05906bece66cd5bcdc9216ff1d044fcbf/ofl/nunitosans/"
def get(path,expected):
    with urlopen(base+path,timeout=45) as response:
        data=response.read()
    assert sha1(("blob "+str(len(data))+"\0").encode()+data).hexdigest()==expected,path
    return data
data=get("NunitoSans%5BYTLC%2Copsz%2Cwdth%2Cwght%5D.ttf","ccc8565733e7d037ede2c9cc971ceb6375e28733")
out=Path("app/src/main/assets/fonts");out.mkdir(parents=True,exist_ok=True)
for name,weight in [("regular",400),("medium",600)]:
    f=TTFont(io.BytesIO(data))
    axes={axis.axisTag:axis.defaultValue for axis in f["fvar"].axes}
    axes["wght"]=weight
    instantiateVariableFont(f,axes,inplace=True).save(out/("sky_"+name+".ttf"))
lic=Path("app/src/main/assets/licenses");lic.mkdir(parents=True,exist_ok=True)
(lic/"NunitoSans-OFL.txt").write_bytes(get("OFL.txt","2ae17359801c71b7b568854b98f73969ad822474"))
print("PASS: bundled pinned Nunito Sans regular and medium")
