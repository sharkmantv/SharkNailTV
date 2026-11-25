"""Real emulator render, including each remote focus position."""
import subprocess, time, xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.check_output(["adb",*args])
labels=["Live TV","Movies","Series","Settings"]
for i,label in enumerate(labels):
    if i: adb("shell","input","keyevent","22")
    time.sleep(1)
    adb("shell","uiautomator","dump","/sdcard/design02.xml")
    root=ET.fromstring(adb("shell","cat","/sdcard/design02.xml"))
    nodes=[n for n in root.iter("node") if n.get("content-desc")==label and n.get("focused")=="true"]
    assert nodes,("Expected remote focus",label)
    with open("design02-focus-%s.png"%i,"wb") as f:
        f.write(adb("exec-out","screencap","-p"))
adb("shell","input","keyevent","23")
time.sleep(2)
adb("shell","uiautomator","dump","/sdcard/design02.xml")
assert b"Playback" in adb("shell","cat","/sdcard/design02.xml")
print("PASS: all four focus positions and Settings activation")
