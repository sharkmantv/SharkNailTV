"""Exclude archived/build-only resources from APK; leave source history intact."""
from pathlib import Path
import json, re, hashlib
root=Path("app/src/main")
res=root/"res/drawable-nodpi"
names=("design2_home","sky_shark_home_34","sky_shark_logo_34","player_dock_blue","player_key_blue","player_sky_colour")
sources=[p for p in root.rglob("*") if p.is_file() and p.suffix in (".java",".kt",".xml")]
report=[]
for name in names:
    hits=[str(p) for p in sources if re.search(r"\b"+re.escape(name)+r"\b",p.read_text(encoding="utf-8"))]
    assert not hits,(name,"runtime reference exists",hits)
    p=res/(name+".png")
    assert p.is_file(),p
    data=p.read_bytes()
    report.append({"name":p.name,"bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()})
    p.unlink()
Path("mot-pruned-resources.json").write_text(json.dumps({"removed":report,"uncompressed_bytes":sum(x["bytes"] for x in report)},indent=2))
print("PASS: removed build-only resources from packaging; runtime reference scan clean",report)
