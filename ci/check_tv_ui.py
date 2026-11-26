"""Check UI geometry using synthetic IPTV data, never a real subscription."""
import argparse, io, json, re, subprocess, threading, time, xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
parser=argparse.ArgumentParser()
parser.add_argument("--resolution", choices=("1080p","4k"), default="1080p")
parser.add_argument("--fixture-video", required=True)
parser.add_argument("--saved-login", action="store_true")
args=parser.parse_args()
expected_size=(3840,2160) if args.resolution=="4k" else (1920,1080)
poster=io.BytesIO()
Image.new("RGB",(780,1170),(190,39,95)).save(poster,format="PNG")
poster_bytes=poster.getvalue()
video_bytes=Path(args.fixture_video).read_bytes()
assert len(video_bytes)>10000
poster_url="http://127.0.0.1:18765/poster.png"
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        path=urlparse(self.path).path
        if path=="/poster.png":
            self.send_response(200)
            self.send_header("Content-Type","image/png")
            self.send_header("Content-Length",str(len(poster_bytes)))
            self.end_headers()
            self.wfile.write(poster_bytes)
            return
        if path.startswith("/movie/fixture/fixture/") and path.endswith(".mp4"):
            match=re.fullmatch(r"bytes=(\d+)-(\d*)",self.headers.get("Range",""))
            start=int(match.group(1)) if match else 0
            end=min(int(match.group(2)),len(video_bytes)-1) if match and match.group(2) else len(video_bytes)-1
            if start>end:
                self.send_response(416)
                self.send_header("Content-Range","bytes */"+str(len(video_bytes)))
                self.end_headers()
                return
            data=video_bytes[start:end+1]
            self.send_response(206 if match else 200)
            self.send_header("Content-Type","video/mp4")
            self.send_header("Accept-Ranges","bytes")
            self.send_header("Content-Length",str(len(data)))
            if match:
                self.send_header("Content-Range",f"bytes {start}-{end}/{len(video_bytes)}")
            self.end_headers()
            try: self.wfile.write(data)
            except (BrokenPipeError,ConnectionResetError): pass
            return
        q = parse_qs(urlparse(self.path).query)
        action = q.get("action", [""])[0]
        if action == "get_vod_categories":
            data = [{"category_id": "1", "category_name": "Fixture movies"}]
        elif action == "get_series_categories":
            data = [{"category_id": "1", "category_name": "Fixture series"}]
        elif action == "get_series":
            data = [{"series_id": n, "name": "Series fixture %03d" % n, "cover":poster_url} for n in range(1, 41)]
        elif action == "get_live_categories":
            data = [{"category_id": "1", "category_name": "Fixture channels"}]
        elif action == "get_live_streams":
            data = [{"stream_id": n, "name": "Test channel %03d" % n} for n in range(1, 21)]
        elif action in ("get_short_epg", "get_simple_data_table"):
            data = {"epg_listings":[]}
        elif action == "get_vod_streams":
            data = [{"stream_id": n, "name": "Layout fixture %03d" % n, "container_extension": "mp4", "stream_icon":poster_url} for n in range(1, 41)]
        else:
            data = {"user_info": {"auth": int(q.get("username")==["fixture"] and q.get("password")==["fixture"])}, "server_info": {"timezone": "Europe/London"}}
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
def mobile_navigation_expected():
    features=set(adb("shell","pm","list","features").splitlines())
    television=any("feature:"+f in features for f in ("android.software.leanback","android.hardware.type.television"))
    mode=adb("shell","dumpsys","uimode")
    television=television or bool(re.search(r"mCurUiMode=0x[0-9a-fA-F]*4(?:\s|$)",mode))
    return not television and "feature:android.hardware.touchscreen" in features

def check_catalogue_nav():
    root=ui()
    for label in ("Back","Home"):
        node=find(root,label)
        if mobile_navigation_expected():
            assert node is not None and node.get("clickable")=="true",("Missing mobile catalogue button",label)
        else:
            assert node is None,("Unexpected TV catalogue footer",label)

def capture(section):
    path=f"premium-{section}-{args.resolution}.png"
    with open(path,"wb") as out:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=True)
    im=Image.open(path).convert("RGB")
    assert im.size==expected_size,(section,im.size,expected_size)
    print("PASS:",section,"actual rendered screenshot",im.size,flush=True)
    return im

def check_posters(prefix,section):
    deadline=time.monotonic()+40
    while time.monotonic()<deadline:
        root=ui()
        cards=[n for n in root.iter("node") if n.get("content-desc","").startswith(prefix)]
        if len(cards)>=4:
            rects=[bounds(n) for n in cards]
            rows=sorted(set(r[1] for r in rects))
            if len(rows)==2:
                im=capture(section)
                loaded=0
                for x,y,X,Y in rects:
                    width,height=X-x,Y-y
                    assert width>0 and height>width and 1.15<=height/width<=1.95,(section,rects)
                    # Check downloaded fixture art in real on-screen pixels, not just title labels.
                    rgb=im.getpixel(((x+X)//2,(y+Y)//2))
                    if rgb[0]>145 and rgb[1]<90 and 50<rgb[2]<145: loaded+=1
                if loaded>=min(4,len(rects)):
                    print("PASS:",section,"two portrait rows and decoded provider posters",loaded,flush=True)
                    return
        time.sleep(1)
    raise AssertionError("Missing rendered poster art or two-row layout: "+section)

def check_player():
    adb("logcat","-c")
    tap(expect("Layout fixture 001"))
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        log=adb("logcat","-d","-s","SharkPlayback:I")
        if re.search(r"640×360",log): break
        time.sleep(.5)
    else: raise AssertionError("Fixture video never rendered first frame")
    def player_events():
        return adb("logcat","-d","-s","SharkPlayerUI:I")
    def wait_player_event(message, after=0, timeout=45):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if player_events().count(message)>after:
                return
            time.sleep(.25)
        raise AssertionError("Missing player event: "+message+"\n"+player_events()[-12000:])
    # Verify the real auto-hide transition and focused playback surface.
    wait_player_event("overlay hidden")
    target=expect("Show playback controls",timeout=30)
    assert target.get("focusable")=="true", "Hidden player must expose a remote-focus target"
    before=player_events().count("overlay shown")
    # Supply the actual TV D-pad input source, not the phone keyboard default.
    adb("shell","input","dpad","keyevent","23")
    wait_player_event("overlay shown",after=before,timeout=25)
    # Paused controls stay visible while UIAutomator captures the hierarchy.
    before=player_events().count("playWhenReady=false")
    adb("shell","input","dpad","keyevent","23")
    wait_player_event("playWhenReady=false",after=before)
    play_control=expect("Play",timeout=30)
    assert play_control.get("focused")=="true", "Play/pause must retain TV focus"
    root=ui()
    for label in ("Sky","Play","Playback progress","Audio","Subtitles","Aspect","Picture","Info"):
        assert find(root,label) is not None,("Missing premium player control",label)
    im=capture("player")
    # Verify the actual square dark-glass panel, rather than just button labels.
    x,y,X,Y=bounds(play_control)
    sw,sh=im.size
    cy=(y+Y)//2
    # The native dock begins at 24dp inset on the 960dp-wide landscape fixture.
    # Its centre must remain deep navy while the side illumination is visible.
    edge_x=round(sw*.025)+max(1,round(sw/960))
    inner_x=round(sw*.13)
    edge=[im.getpixel((edge_x,cy+k)) for k in range(-3,4)]
    interior=[im.getpixel((inner_x,cy+k)) for k in range(-3,4)]
    assert sum(b>g>r and b>110 for r,g,b in edge)>=5,("Missing blue glass edge",edge)
    assert sum(b>g>r and g<85 and r<35 for r,g,b in interior)>=5,("Player glass centre too bright",interior)
    print("PASS: actual dark-glass player and illuminated edge",flush=True)
    before=player_events().count("playWhenReady=true")
    adb("shell","input","dpad","keyevent","23")
    wait_player_event("playWhenReady=true",after=before)
    adb("shell","input","keyevent","4")
    expect("Layout fixture 001")
    print("PASS: actual first frame and premium playback overlay/remote toggle",flush=True)

def enter_field(node,value):
    # On busy emulators, a single bulk input command can reorder characters.
    # Enter fixture credentials slowly, verifying the address before Connect.
    target=bounds(node)
    secret=node.get("password")=="true"
    original_hint=node.get("text","")
    for attempt in range(3):
        tap(node)
        time.sleep(.7)
        adb("shell","input","keycombination","113","29")
        adb("shell","input","keyevent","67")
        time.sleep(.25)
        for ch in value:
            adb("shell","input","text",ch)
            time.sleep(.12)
        time.sleep(.5)
        current=next((n for n in ui().iter("node") if n.get("class")=="android.widget.EditText" and n.get("focused")=="true"),None)
        if current is not None:
            actual=current.get("text","")
            if (not secret and actual==value) or (secret and actual!=original_hint and len(actual)==len(value)):
                return
    raise AssertionError("Could not reliably enter fixture "+("password" if secret else "login field"))

server=ThreadingHTTPServer(("127.0.0.1",18765),Fixture)
threading.Thread(target=server.serve_forever,daemon=True).start()
adb("reverse","tcp:18765","tcp:18765")
try:
    expect("SHARK SKY")
    # First tap on a previously unfocused home item must open it.
    tap(expect("Settings"))
    expect("Playback")
    adb("shell","input","keyevent","4")
    expect("SHARK SKY")
    # Remote path must also open Settings, not merely highlight it.
    for _ in range(3):
        adb("shell","input","keyevent","22")
    adb("shell","input","keyevent","23")
    expect("Playback")
    root=ui()
    labels=("Xtream Codes","Playback","Display","Audio","Subtitles","Updates","About","Cleaner","Connection Test","Back","Home","Exit")
    tiles=[bounds(find(root,label)) for label in labels]
    first,second,third=tiles[:4],tiles[4:8],tiles[8:]
    assert all(abs(r[1]-first[0][1])<=2 and abs(r[3]-first[0][3])<=2 for r in first),tiles
    assert all(abs(r[1]-second[0][1])<=2 and abs(r[3]-second[0][3])<=2 for r in second),tiles
    assert all(abs(a[0]-b[0])<=2 and abs(a[2]-b[2])<=2 for a,b in zip(first,second)),tiles
    assert all(abs(r[1]-third[0][1])<=2 and abs(r[3]-third[0][3])<=2 for r in third),tiles
    assert all(abs(a[0]-b[0])<=2 and abs(a[2]-b[2])<=2 for a,b in zip(first,third)),tiles
    widths=[r[2]-r[0] for r in tiles]
    heights=[r[3]-r[1] for r in tiles]
    assert max(widths)-min(widths)<=2 and max(heights)-min(heights)<=2,tiles
    gaps=[row[i+1][0]-row[i][2] for row in (first,second,third) for i in range(len(row)-1)]
    assert min(gaps)>0 and max(gaps)-min(gaps)<=2,tiles
    assert third[0][1]>second[0][3]>first[0][3] and second[0][1]>first[0][3],tiles
    screen=bounds(root.find("node"))
    sw,sh=screen[2]-screen[0],screen[3]-screen[1]
    cx=(first[0][0]+first[-1][2])/2
    cy=(first[0][1]+third[0][3])/2
    assert abs((third[0][0]+third[-1][2])/2-cx)<=3,tiles
    assert abs(cx-(screen[0]+sw/2))<sw*.025,tiles
    assert screen[1]+sh*.46<=cy<=screen[1]+sh*.66,(cy,screen,tiles)
    print("PASS: Settings evenly spaced, aligned, and vertically centered",tiles)
    settings_image=capture("settings")
    # Verify the continuous shared warm glow on all four sides.
    selected=next((find(root,label) for label in labels if find(root,label).get("focused")=="true"),None)
    assert selected is not None,"Settings tile focus missing"
    x,y,X,Y=bounds(selected)
    h=Y-y;w=X-x
    def flame_band(pixels):
        runs=[];run=[]
        for r,g,b in pixels:
            if r>=190 and r>b*1.5 and b<125:run.append((r,g,b))
            elif run:runs.append(run);run=[]
        if run:runs.append(run)
        for band in runs:
            if len(band)<max(5,round(h*.065)):continue
            core=[i for i,(r,g,b) in enumerate(band) if r>210 and g>=165]
            if not core:continue
            left=band[:core[0]];right=band[core[-1]+1:]
            if all(g>=165 for r,g,b in band[core[0]:core[-1]+1]) and all(any(g<115 for r,g,b in side) and any(80<=g<165 for r,g,b in side) for side in (left,right)):
                return True
        return False
    depth=max(1,round(h*.28))
    for frac in (.40,.50,.60):
        cx=round(x+w*frac);cy=round(y+h*frac)
        strips=[[settings_image.getpixel((xx,cy)) for xx in range(x,x+depth)],
                [settings_image.getpixel((cx,yy)) for yy in range(y,y+depth)],
                [settings_image.getpixel((cx,yy)) for yy in range(Y-depth,Y)],
                [settings_image.getpixel((xx,cy)) for xx in range(X-depth,X)]]
        assert all(flame_band(strip) for strip in strips),("Settings blended warm glow missing",frac)
    print("PASS: shared warm glow on all four Settings edges",flush=True)
    print("PASS: Settings opens by touch and DPAD OK")
    tap(expect("Display"))
    expect("Automatic (system)")
    expect("Highest refresh at current resolution")
    tap(expect("Automatic (system)"))
    expect("Display mode")
    assert any("Current display:" in n.get("text","") and "System controlled" in n.get("text","") for n in ui().iter("node")),"Missing actual refresh-rate status"
    tap(expect("Back"))
    expect("Connection Test")
    print("PASS: display selector and system-controlled fallback status",flush=True)
    tap(expect("Connection Test"))
    expect("Connection Test")
    tap(expect("CLOSE" if args.saved_login else "OK"))
    expect("Playback")
    nav=[expect(label) for label in ("Back","Home","Exit")]
    assert all(n.get("clickable")=="true" and n.get("focusable")=="true" for n in nav),"Settings navigation not actionable"
    assert all(bounds(n)[1]>=second[0][3] for n in nav),"Settings navigation overlaps tiles"
    tap(expect("Exit"))
    expect("Exit Sky Shark?")
    tap(expect("CANCEL"))
    expect("Playback")
    tap(expect("Back"))
    expect("SHARK SKY")
    tap(expect("Settings"))
    tap(expect("Home"))
    expect("SHARK SKY")
    print("PASS: Settings Back/Home navigation and safe Exit confirmation")
    tap(expect("Movies"))
    if not args.saved_login:
        expect("Xtream Codes")
        fields=[n for n in ui().iter("node") if n.get("class")=="android.widget.EditText"]
        assert len(fields)>=3, "Missing Xtream login fields"
        for node,value in zip(fields,["http://127.0.0.1:18765","fixture","fixture"]):
            enter_field(node,value)
            if "mInputShown=true" in adb("shell","dumpsys","input_method"):
                adb("shell","input","keyevent","4")
                time.sleep(.4)
        current_fields=[n for n in ui().iter("node") if n.get("class")=="android.widget.EditText"]
        assert current_fields[0].get("text")=="http://127.0.0.1:18765"
        assert current_fields[1].get("text")=="fixture"
        if current_fields[2].get("text") in ("","Password"):
            enter_field(current_fields[2],"fixture")
            if "mInputShown=true" in adb("shell","dumpsys","input_method"):
                adb("shell","input","keyevent","4")
        tap(expect("Connect"))
    expect("Fixture movies")
    check_posters("Layout fixture ","movies")
    check_catalogue_nav()
    check_player()
    # Recheck the footer after returning from full-screen playback. Phones
    # deliberately expose Home and Back; TVs must not gain these actions.
    check_catalogue_nav()
    root=ui()
    assert not any(find(root,label) is not None for label in ("Search","Retry")), "Movies bottom menu actions still visible"
    tap(expect("Movies options"))
    for label in ("Home","Search","Retry"):
        expect(label)
    tap(expect("Home"))
    tap(expect("Series"))
    expect("Fixture series")
    expect("Series fixture 001")
    check_posters("Series fixture ","series")
    check_catalogue_nav()
    if mobile_navigation_expected(): tap(expect("Home"))
    else: adb("shell","input","keyevent","4")
    expect("SHARK SKY")
    tap(expect("Settings"))
    tap(expect("Cleaner"))
    tap(expect("Clear app cache"))
    # Android native AlertDialog renders action buttons in uppercase.
    expect("Clear app cache?")
    tap(expect("CLEAR CACHE"))
    expect("SHARK SKY")
    # The saved Xtream login must still work after cleaning and activity restart.
    tap(expect("Live TV"))
    expect("Fixture channels")
    expect("Test channel 001")
    capture("live")
    root=ui()
    check_catalogue_nav()
    assert not any(find(root,label) is not None for label in ("Search","TV Guide","Retry")), "Unwanted Live bottom actions still visible"
    tap(expect("Live TV options"))
    for label in ("Home","Search","TV Guide","Retry"):
        expect(label)
    adb("shell","input","keyevent","4")
    # Wait for the options dialog to relinquish focus before sending the remote key.
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        root=ui()
        if find(root,"TV Guide") is None and find(root,"Test channel 001") is not None:
            break
        time.sleep(.25)
    else:
        raise AssertionError("Live options did not dismiss to the channel list")
    adb("shell","input","keyevent","82")
    for label in ("Home","Search","TV Guide","Retry"):
        expect(label)
    tap(expect("TV Guide"))
    expect("Select a channel to see its programme schedule")
    adb("shell","input","keyevent","4")
    expect("Test channel 001")
    if mobile_navigation_expected():
        tap(expect("Back"))
        expect("SHARK SKY")
    print("PASS: TV footer unchanged; mobile Back/Home and catalogue options retained")
except Exception:
    try:
        with open(f"failed-ui-{args.resolution}.xml","w") as out:
            out.write(ET.tostring(ui(),encoding="unicode"))
        with open(f"failed-ui-{args.resolution}.png","wb") as out:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=False)
    except Exception as diagnostic_error:
        print("Could not capture failure diagnostics:",diagnostic_error)
    raise
finally:
    adb("reverse","--remove","tcp:18765")
    server.shutdown()
