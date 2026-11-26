"""Import the exact approved Design 1 PNG assets. Never substitute an approximation."""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import hashlib
p=Path("ci/artwork/sky_shark_design1_artwork.zip")
assert p.is_file(),"Upload the approved Design 1 artwork ZIP before building"
assert hashlib.sha256(p.read_bytes()).hexdigest()=="681686d69db6967baf5ca3133340fc1dd52f2fbe80570ec1c0edabd0e5722c12","Wrong artwork ZIP"
dst=Path("app/src/main/res/drawable-nodpi")
with ZipFile(p) as z:
    for source,name,size,digest in [
        ("sky_shark_design1_frame.png","design1_player_frame.png",(3840,2160),"4811b8f5059c22cf548fbe56db98abe41766e0d474d9ba0acfe0113f790056cd"),
        ("sky_shark_design1_key.png","design1_player_key.png",(384,288),"0b30354f4251574b227b4ea4d4da37249f5260ae7ea5dbed0af4e268d9fdb607")]:
        b=z.read(source)
        assert hashlib.sha256(b).hexdigest()==digest
        (dst/name).write_bytes(b)
        im=Image.open(dst/name)
        assert im.size==size and im.mode=="RGBA"
        print("PASS: exact approved layer",name,size)
