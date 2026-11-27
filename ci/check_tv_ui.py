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
live_bytes=Path(args.fixture_video).with_suffix(".ts").read_bytes()
assert len(live_bytes)>10000
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
        if path.startswith("/live/fixture/fixture/") and path.endswith(".ts"):
            self.send_response(200)
            self.send_header("Content-Type","video/mp2t")
            self.send_header("Content-Length",str(len(live_bytes)))
            self.end_headers()
            try:
                self.wfile.write(live_bytes)
            except (BrokenPipeError,ConnectionResetError): pass
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
    assert find(root,"Surfshark") is None,"Removed VPN shortcut still present"
    for label in ("Home",):
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

def benchmark_live(profile,save_capture=True):
    import statistics
    results=[]
    for n in range(1,6):
        node=expect("Test channel %03d"%n)
        adb("logcat","-c")
        tap(node)
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            log=adb("logcat","-d","-s","SharkPlayback:I")
            match=re.search(r"startup profile="+profile+r" firstFrameMs=(\d+) firstByteMs=(\d+) decoderMs=(\d+)",log)
            if match: break
            time.sleep(.5)
        else: raise AssertionError("Live first frame missing: "+profile+" / "+str(n)+"\n"+log[-3000:])
        frame,first_byte,decoder=map(int,match.groups())
        assert 0<=first_byte<=frame and 0<=decoder<=frame,(profile,n,match.groups())
        assert "Live player reused" not in log,("Failed cross-channel reuse unexpectedly enabled",n)
        results.append({"channel":n,"first_frame_ms":frame,"first_network_byte_ms":first_byte,"decoder_ready_ms":decoder})
    if save_capture: capture("live-playing-"+profile)
    print("PASS: five actual live TS first frames",profile,results,"median_ms",statistics.median([x["first_frame_ms"] for x in results]),flush=True)
    return results

def perf_sample(label):
    raw=adb("shell","dumpsys","meminfo","com.example.xtreamtv")
    pss=re.search(r"TOTAL PSS:\s*(\d+)",raw)
    if not pss: pss=re.search(r"^\s*TOTAL\s+(\d+)",raw,re.M)
    assert pss,("Unable to measure app PSS",raw[-1500:])
    Path("performance-"+label+"-meminfo.txt").write_text(raw)
    return {"sample":label,"pss_kb":int(pss.group(1))}

def performance_audit(warm_results):
    # Warm caches and decoders first; compare equivalent channel-list state.
    samples=[perf_sample("warm")]
    adb("shell","dumpsys","gfxinfo","com.example.xtreamtv","reset")
    runs=[]
    for cycle in range(5):
        runs.append(benchmark_live("fast-live",False))
        time.sleep(2)
        samples.append(perf_sample("switch-round-"+str(cycle+1)))
    # One sustained local stream, then inspect memory and frame statistics.
    time.sleep(120)
    samples.append(perf_sample("soak"))
    Path("performance-gfxinfo.txt").write_text(adb("shell","dumpsys","gfxinfo","com.example.xtreamtv","framestats"))
    Path("performance-cpuinfo.txt").write_text(adb("shell","dumpsys","cpuinfo"))
    report={"fixture":"local synthetic TS, 1080p emulator","channel_switches":len(warm_results)+sum(map(len,runs)),"soak_seconds":120,"pss":samples,"switch_rounds":runs}
    Path("performance-audit.json").write_text(json.dumps(report,indent=2))
    # Detect gross retention, not claim that finite emulator samples prove no leaks.
    assert not (samples[-2]["pss_kb"]>samples[0]["pss_kb"]+131072 and samples[-1]["pss_kb"]>samples[0]["pss_kb"]+131072),("Sustained PSS growth >128 MiB",samples)
    print("PASS: repeated live switching and bounded gross memory-growth check",report,flush=True)

def check_player():
    adb("logcat","-c")
    tap(expect("Layout fixture 001"))
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        log=adb("logcat","-d","-s","SharkPlayback:I")
        if re.search(r"640×360",log): break
        time.sleep(.5)
    else: raise AssertionError("Fixture video never rendered first frame")
    metrics=re.search(r"startup profile=stock-buffer firstFrameMs=(\d+) firstByteMs=(\d+) decoderMs=(\d+)",log)
    assert metrics and int(metrics[2])<=int(metrics[1]),("Missing network/first frame measurement",log[-3000:])
    print("PASS: measured VOD first frame and network first byte",metrics.groups(),flush=True)
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
    from PIL import ImageChops, ImageStat
    sw,sh=im.size
    # The video and overlay must both cover the full window, including while controls are visible.
    assert f"video surface {sw}x{sh} overlay {sw}x{sh}" in player_events(),("Video resized into preview",player_events()[-2500:])
    x,y,X,Y=bounds(play_control)
    assert sh*.80<=y<=sh*.84 and sh*.93<=Y<=sh*.96,("Transport not in lower dock",y,Y,sh)
    assert sw*.16<X-x<sw*.21,("Transport key allocation incorrect",x,X,sw)
    assert find(root,"Search titles") is None and find(root,"Search channels") is None,"Unexpected catalogue inside full player"
    # Approved compact player: timeline, narrow option strip, then lower transport dock.
    transport=[bounds(find(root,label)) for label in ("Previous","Rewind","Play","Forward","Next")]
    options=[bounds(find(root,label)) for label in ("Info","Subtitles","Audio","Aspect","Picture")]
    line=bounds(find(root,"Playback progress"))
    assert line[3]<=options[0][1]+2 and options[0][3]<=transport[0][1]+2,("Incorrect row order",line,transport,options)
    assert transport[0][0]<sw*.07 and transport[-1][2]>sw*.93,("Transport not spread across dock",transport)
    assert all(abs(r[1]-transport[0][1])<=2 for r in transport),transport
    assert all(abs(r[1]-options[0][1])<=2 for r in options),options
    assert abs((transport[0][0]+transport[-1][2])/2-sw/2)<sw*.02,transport
    # Title and brand must be inside the thin header, not over the video.
    title=find(root,"Layout fixture 001")
    assert title is not None,"Actual movie title not rendered"
    tx,ty,tX,tY=bounds(title)
    assert sw*.025<=tx<=sw*.06 and ty<sh*.025 and tY<=sh*.13,("Header title misplaced",bounds(title))
    assert sh*.69<=line[1]<=sh*.73,("Timeline misplaced",line)
    assert sh*.73<=options[0][1]<=sh*.76 and sh*.80<=options[0][3]<=sh*.83,("Option strip misplaced",options)
    logo=bounds(find(root,"Sky"))
    assert logo[0]>sw*.72 and logo[2]<sw*.99 and logo[3]<sh*.14,("Logo placement differs",logo)
    resolution=find(root,"360p")
    assert resolution is not None and bounds(resolution)[0]>sw*.40,("Actual resolution missing from header",resolution)
    # Move upwards from central transport to the options, then right to Info.
    adb("shell","input","dpad","keyevent","19")
    time.sleep(.15)
    assert expect("Aspect").get("focused")=="true","Option row focus missing"
    for i in range(2):
        adb("shell","input","dpad","keyevent","22")
        time.sleep(.15)
    info_control=expect("Info")
    assert info_control.get("focused")=="true",("Info remote focus missing",ET.tostring(ui(),encoding="unicode")[-6000:])
    before_info=player_events().count("Info dialog shown")
    adb("shell","input","dpad","keyevent","23")
    wait_player_event("Info dialog shown",after=before_info,timeout=15)
    try:
        close=expect("Close")
    except AssertionError:
        capture("player-info-failure")
        print("INFO FAILURE HIERARCHY:",ET.tostring(ui(),encoding="unicode"),flush=True)
        print("INFO FAILURE EVENTS:",player_events()[-12000:],flush=True)
        raise
    assert close.get("focused")=="true","Close must receive TV focus"
    info=ui()
    combined=" ".join(n.get("text","") for n in info.iter("node"))
    for label in ("Engine:","Stream FPS:","Display:","Auto FPS:","Requested refresh:","Request result:","Supported modes (same resolution):","Dropped frames (reported):","Buffered:","Startup to first frame:","Load requested:","Video decoder ready:","Player ready:","First network byte:","Startup profile:"):
        assert label in combined,("Missing diagnostics",label)
    capture("player-info")
    assert bounds(close)[1]>=sh*.12 and bounds(close)[3]<=sh*.78,("Close outside panel",bounds(close))
    close=expect("Close")
    assert close.get("clickable")=="true","Close is not actionable"
    before_close=player_events().count("Info dismissed")
    adb("shell","input","keyevent","4")
    wait_player_event("Info dismissed",after=before_close,timeout=15)
    assert expect("Info").get("focused")=="true","Info focus not restored"
    # Also exercise accessibility click/physical touch after the remote path.
    node=expect("Info")
    before_info=player_events().count("Info dialog shown")
    tap(node)
    wait_player_event("Info dialog shown",after=before_info,timeout=15)
    close=expect("Close")
    before_close=player_events().count("Info dismissed")
    tap(close)
    wait_player_event("Info dismissed",after=before_close,timeout=15)
    print("PASS: full video surface, approved artwork, compact controls, remote/touch Info and complete diagnostic text",flush=True)
    # First touch on a different focused control must activate, not merely focus.
    assert expect("Info").get("focused")=="true","Info focus not restored after touch Close"
    before=player_events().count("playWhenReady=true")
    clicks=player_events().count("control clicked=Play")
    tap(expect("Play"))
    try:
        wait_player_event("control clicked=Play",after=clicks,timeout=10)
        wait_player_event("playWhenReady=true",after=before,timeout=15)
    except AssertionError:
        capture("player-resume-failure")
        print("RESUME FAILURE HIERARCHY:",ET.tostring(ui(),encoding="unicode"),flush=True)
        print("RESUME FAILURE EVENTS:",player_events()[-12000:],flush=True)
        raise
    assert player_events().count("control clicked=Play")==clicks+1,"Single tap activated Play more than once"
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
    labels=("Xtream Codes","Playback","Display","Audio","Subtitles","Updates","About","Cleaner","Connection Test","Home","Exit")
    tiles=[bounds(find(root,label)) for label in labels]
    first,second,third=tiles[:4],tiles[4:8],tiles[8:]
    assert all(abs(r[1]-first[0][1])<=2 and abs(r[3]-first[0][3])<=2 for r in first),tiles
    assert all(abs(r[1]-second[0][1])<=2 and abs(r[3]-second[0][3])<=2 for r in second),tiles
    assert all(abs(a[0]-b[0])<=2 and abs(a[2]-b[2])<=2 for a,b in zip(first,second)),tiles
    assert all(abs(r[1]-third[0][1])<=2 and abs(r[3]-third[0][3])<=2 for r in third),tiles
    assert len(third)==3 and find(root,"Surfshark") is None,tiles
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
    def soft_gold_band(pixels,minimum):
        # Measure the yellow core of a feathered red-yellow glow.
        scores=[max(0,min(r-b,g-b)) for r,g,b in pixels]
        peak=max(scores,default=0)
        if peak<70:return False
        i=scores.index(peak)
        core=[j for j,val in enumerate(scores) if val>=peak*.55]
        if len(core)<minimum:return False
        if any(b-a>1 for a,b in zip(core,core[1:])):return False
        # The yellow core must fade towards the red shoulders.
        if i==0 or i==len(scores)-1:return False
        if min(scores[:i])>peak*.70 or min(scores[i+1:])>peak*.70:return False
        r,g,b=pixels[i]
        return g>=r*.65 and r>b+65
    depth=max(1,round(h*.28))
    for frac in (.40,.50,.60):
        cx=round(x+w*frac);cy=round(y+h*frac)
        strips=[[settings_image.getpixel((xx,cy)) for xx in range(x,x+depth)],
                [settings_image.getpixel((cx,yy)) for yy in range(y,y+depth)],
                [settings_image.getpixel((cx,yy)) for yy in range(Y-depth,Y)],
                [settings_image.getpixel((xx,cy)) for xx in range(X-depth,X)]]
        assert all(soft_gold_band(strip,max(2,round(h*.025))) for strip in strips),("Settings blended warm glow missing",frac)
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
    nav=[expect(label) for label in ("Connection Test","Home","Exit")]
    assert all(n.get("clickable")=="true" and n.get("focusable")=="true" for n in nav),"Settings navigation not actionable"
    assert all(bounds(n)[1]>=second[0][3] for n in nav),"Settings navigation overlaps tiles"
    tap(expect("Exit"))
    expect("Exit Sky Shark?")
    tap(expect("CANCEL"))
    expect("Playback")
    assert find(ui(),"Surfshark") is None,"VPN shortcut still present"
    adb("shell","input","keyevent","4")
    expect("SHARK SKY")
    tap(expect("Settings"))
    tap(expect("Home"))
    expect("SHARK SKY")
    print("PASS: Settings physical Back/Home navigation and safe Exit confirmation")
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
    # deliberately expose Home; TVs must not gain these actions.
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
    if args.resolution=="1080p":
        stock_results=benchmark_live("stock-buffer")
        adb("shell","input","keyevent","4")
        expect("SHARK SKY")
        tap(expect("Settings"))
        tap(expect("Playback"))
        tap(expect("Playback engine: Default ExoPlayer"))
        expect("Playback engine: Custom")
        # Compare native output on both engines, without GPU tone mapping.
        tap(expect("Live TV: SDR"))
        tap(expect("Auto"))
        expect("Live TV: Auto")
        tap(expect("Back"))
        tap(expect("Home"))
        tap(expect("Live TV"))
        expect("Test channel 001")
        custom_results=benchmark_live("fast-live")
        Path("live-startup-benchmark.json").write_text(json.dumps({"stock":stock_results,"fast":custom_results},indent=2))
        performance_audit(custom_results)
        adb("logcat","-c")
        tap(expect("Full screen"))
        deadline=time.monotonic()+25
        while time.monotonic()<deadline:
            refresh_log=adb("logcat","-d","-s","SharkDisplay:I","SharkPlayback:I")
            if "Auto FPS request:" in refresh_log: break
            time.sleep(.5)
        else: raise AssertionError("Fullscreen custom live did not request FPS matching\n"+refresh_log[-3000:])
        assert "Live fullscreen: same player and surface resized" in refresh_log,refresh_log[-3000:]
        print("PASS: actual custom live fullscreen automatic FPS request",flush=True)
        adb("shell","input","keyevent","4")
        expect("Test channel 005")
        adb("shell","input","keyevent","4")
        expect("SHARK SKY")
        tap(expect("Settings"))
        tap(expect("Playback"))
        tap(expect("Reset playback defaults"))
        expect("Reset playback and display preferences?")
        tap(expect("RESET"))
        tap(expect("Home"))
        tap(expect("Live TV"))
        expect("Test channel 001")
    if mobile_navigation_expected():
        tap(expect("Home"))
        expect("SHARK SKY")
    print("PASS: TV footer unchanged; mobile Home and catalogue options retained")
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
