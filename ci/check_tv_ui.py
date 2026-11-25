"""Check UI geometry using synthetic IPTV data, never a real subscription."""
import json, re, subprocess, threading, time, xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        action = q.get("action", [""])[0]
        if action == "get_vod_categories":
            data = [{"category_id": "1", "category_name": "Fixture movies"}]
        elif action == "get_live_categories":
            data = [{"category_id": "1", "category_name": "Fixture channels"}]
        elif action == "get_live_streams":
            data = [{"stream_id": n, "name": "Test channel %03d" % n} for n in range(1, 21)]
        elif action in ("get_short_epg", "get_simple_data_table"):
            data = {"epg_listings":[]}
        elif action == "get_vod_streams":
            data = [{"stream_id": n, "name": "Layout fixture %03d" % n, "container_extension": "mp4"} for n in range(1, 41)]
        else:
            data = {"user_info": {"auth": 1}, "server_info": {"timezone": "Europe/London"}}
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *args):
        pass

def adb(*args):
    return subprocess.check_output(["adb", *map(str,args)], text=True)
def ui():
    adb("shell", "uiautomator", "dump", "/sdcard/shark-test.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/shark-test.xml"))
def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds",""))))
def tap(node):
    x,y,X,Y = bounds(node)
    adb("shell","input","tap",(x+X)//2,(y+Y)//2)
def find(root,label):
    nodes=[n for n in root.iter("node") if n.get("text")==label or n.get("content-desc")==label]
    return max(nodes,key=lambda n:(n.get("clickable")=="true",n.get("focusable")=="true"),default=None)
def expect(label, timeout=30):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        root=ui()
        tutorial=find(root,"Got it")
        if tutorial is not None:
            tap(tutorial)
            time.sleep(1)
            continue
        node=find(root,label)
        if node is not None:
            return node
        time.sleep(1)
    raise AssertionError("Missing UI label: "+label)
def enter_field(node,value):
    tap(node)
    adb("shell","input","text",value)

server=ThreadingHTTPServer(("127.0.0.1",18765),Fixture)
threading.Thread(target=server.serve_forever,daemon=True).start()
adb("reverse","tcp:18765","tcp:18765")
try:
    expect("SHARK SKY")
    # First tap on a previously unfocused home item must open it.
    tap(expect("Settings"))
    expect("Playback & display")
    tap(expect("Home"))
    expect("SHARK SKY")
    # Remote path must also open Settings, not merely highlight it.
    for _ in range(3):
        adb("shell","input","keyevent","22")
    adb("shell","input","keyevent","23")
    expect("Playback & display")
    print("PASS: Settings opens by touch and DPAD OK")
    tap(expect("Home"))
    expect("SHARK SKY")
    tap(expect("Movies"))
    expect("Xtream Codes")
    fields=[n for n in ui().iter("node") if n.get("class")=="android.widget.EditText"]
    assert len(fields)>=3, "Missing Xtream login fields"
    for node,value in zip(fields,["http://127.0.0.1:18765","fixture","fixture"]):
        enter_field(node,value)
        adb("shell","input","keyevent","4")
    tap(expect("Connect"))
    expect("Fixture movies")
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        root=ui()
        cards=[n for n in root.iter("node") if n.get("content-desc","").startswith("Layout fixture ")]
        if len(cards)>=4:
            break
        time.sleep(1)
    else:
        raise AssertionError("No movie poster cards loaded")
    rects=[bounds(n) for n in cards]
    rows=sorted(set(r[1] for r in rects))
    print("Poster card bounds:",rects)
    assert len(rows)==2, "Expected two complete poster rows; got "+str(rows)
    for x,y,X,Y in rects:
        width,height=X-x,Y-y
        assert width>0 and height>width, "Poster is not portrait"
        assert 1.15<=height/width<=1.95, "Poster ratio outside acceptable portrait range"
    print("PASS: home, settings, test Xtream login and two portrait rows")
    tap(expect("Home"))
    tap(expect("Live TV"))
    expect("Fixture channels")
    expect("Test channel 001")
    root=ui()
    assert not any(find(root,label) is not None for label in ("Home","Search","TV Guide","Retry")), "Live bottom actions still visible"
    tap(expect("Live TV options"))
    for label in ("Home","Search","TV Guide","Retry"):
        expect(label)
    adb("shell","input","keyevent","4")
    adb("shell","input","keyevent","82")
    expect("TV Guide")
    adb("shell","input","keyevent","4")
    print("PASS: Live TV bottom row removed; touch and remote options retain all actions")
except Exception:
    try:
        with open("failed-ui.xml","w") as out:
            out.write(ET.tostring(ui(),encoding="unicode"))
        with open("failed-ui.png","wb") as out:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=False)
    except Exception as diagnostic_error:
        print("Could not capture failure diagnostics:",diagnostic_error)
    raise
finally:
    adb("reverse","--remove","tcp:18765")
    server.shutdown()
