"""Validate the real native premium home at landscape size before catalogue smoke tests."""
import subprocess, time, re, xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.check_output(["adb",*map(str,args)],text=True)
def dump():
    adb("shell","uiautomator","dump","/sdcard/premium-home.xml")
    return ET.fromstring(adb("shell","cat","/sdcard/premium-home.xml"))
def rect(node):
    return tuple(map(int,re.findall(r"\d+",node.get("bounds",""))))
def tile_nodes(root):
    labels=["Live TV","Movies","Series","Settings"]
    matches={}
    for label in labels:
        nodes=[n for n in root.iter("node") if n.get("content-desc")==label and n.get("focusable")=="true"]
        assert len(nodes)==1, "Expected one tile and no side rail: "+label
        matches[label]=max(nodes,key=lambda n:(rect(n)[2]-rect(n)[0])*(rect(n)[3]-rect(n)[1]))
    return matches
deadline=time.monotonic()+90
while True:
    if time.monotonic()>deadline: raise AssertionError('Premium home did not become ready')
    root=dump()
    # The phone emulator's Pixel Launcher can ANR independently of Shark Sky.
    # Dismiss only that known external dialog; never dismiss an app crash.
    if any("Pixel Launcher isn't responding" in n.get("text","") for n in root.iter("node")):
        close=next((n for n in root.iter("node") if n.get("text")=="Close app"),None)
        if close is not None:
            x,y,X,Y=rect(close)
            adb("shell","input","tap",(x+X)//2,(y+Y)//2)
            adb("shell","am","force-stop","com.example.xtreamtv")
            adb("shell","am","start","-W","-n","com.example.xtreamtv/.MainActivity")
            time.sleep(3)
            print("Dismissed unrelated Pixel Launcher ANR; restarted Shark Sky")
            continue
    try:
        tiles=tile_nodes(root)
        break
    except AssertionError:
        if time.monotonic()>deadline: raise
        time.sleep(1)
assert any(n.get("content-desc")=="SHARK SKY" for n in root.iter("node")), "Premium hero branding missing"
r=[rect(n) for n in tiles.values()]
assert all(y==r[0][1] and Y==r[0][3] and X>x for x,y,X,Y in r),r
assert all(r[i][2]<=r[i+1][0] for i in range(3)),r
assert not any(n.get("text") in ("TV Guide","Sports","Trending") for n in root.iter("node"))
assert tiles["Live TV"].get("focused")=="true", "Initial focus missing: "+str([(n.get("text"),n.get("content-desc"),n.get("bounds")) for n in root.iter("node") if n.get("focused")=="true"])
adb("shell","input","keyevent","22")
time.sleep(.5)
right=dump()
assert tile_nodes(right)["Movies"].get("focused")=="true","Right remote navigation failed: "+str([(n.get("text"),n.get("content-desc"),n.get("bounds")) for n in right.iter("node") if n.get("focused")=="true"])
adb("shell","input","keyevent","21")
time.sleep(.5)
assert tile_nodes(dump())["Live TV"].get("focused")=="true","Left remote navigation failed"
print("PASS: four metallic tiles, no posters/guide, DPAD and layout",r)
