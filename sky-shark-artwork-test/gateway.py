"""Sky Shark isolated metadata test gateway. No streaming proxy or public Xtream credential forwarding."""
import os, json, re, time, hmac, base64, hashlib, threading, datetime as dt, xml.etree.ElementTree as ET
from functools import lru_cache
from urllib.parse import urlencode, urlsplit, parse_qs, quote
from urllib.request import Request, urlopen
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
KEY=os.environ.get("TMDB_API_KEY","")
UP=os.environ.get("XTREAM_BASE","").rstrip("/")
USER=os.environ.get("XTREAM_USER","")
PASSWORD=os.environ.get("XTREAM_PASSWORD","")
ALIAS=os.environ.get("TEST_USER","")
TOKEN=os.environ.get("TEST_PASSWORD","")
PUBLIC=os.environ.get("PUBLIC_BASE",os.environ.get("RENDER_EXTERNAL_URL","")).rstrip("/")
FEEDS=[x.strip() for x in os.environ.get("XMLTV_FEEDS","").split(",") if x.strip()]
MAP=json.loads(os.environ.get("EPG_ID_MAP","{}"))
SECRET=os.environ.get("ARTWORK_SECRET","").encode()
LIMIT=16*1024*1024
SLOTS=threading.BoundedSemaphore(8)
def ready():
    return bool(KEY and UP.startswith("https://") and USER and PASSWORD and ALIAS and len(TOKEN)>=24 and len(SECRET)>=32 and PUBLIC.startswith("https://"))
def fetch(url,params=None):
    if not url.startswith("https://"): raise ValueError("HTTPS required")
    if params: url+="?"+urlencode(params)
    with urlopen(Request(url,headers={"User-Agent":"SkySharkMetadata/1.0"}),timeout=15) as r:
        if urlsplit(r.geturl()).scheme!="https": raise ValueError("HTTPS required")
        data=r.read(LIMIT+1)
        if len(data)>LIMIT:raise ValueError("response too large")
        return data
def norm(s):return "".join(c for c in str(s).casefold() if c.isalnum())
def title_year(s):
    s=re.sub(r"^[A-Z]{2,3}\s*[|:]\s*","",str(s or "")).strip()
    m=re.search(r"\((19\d{2}|20\d{2})\)",s)
    y=m.group(1) if m else ""
    if m:s=s[:m.start()]+" "+s[m.end():]
    return re.sub(r"\b(?:4K|UHD|FHD|1080p|720p|2160p)\b","",s,flags=re.I).strip(" -|"),y
@lru_cache(maxsize=4096)
def lookup(kind,title,year,epoch):
    if not KEY:return ""
    p={"api_key":KEY,"query":title,"include_adult":"false"}
    if year:p["year" if kind=="movie" else "first_air_date_year"]=year
    results=json.loads(fetch("https://api.themoviedb.org/3/search/"+kind,p)).get("results",[])
    hits=set()
    for r in results:
        names=[r.get(k,"") for k in ("title","original_title","name","original_name")]
        if norm(title) not in map(norm,names):continue
        if year and str(r.get("release_date") or r.get("first_air_date") or "")[:4]!=year:continue
        path=r.get("poster_path")
        if path and re.fullmatch(r"/[A-Za-z0-9_.-]+",path):hits.add(path)
    return "https://image.tmdb.org/t/p/w780"+next(iter(hits)) if len(hits)==1 else ""
def b64(b):return base64.urlsafe_b64encode(b).decode().rstrip("=")
def unb64(s):return base64.urlsafe_b64decode(s+"="*(-len(s)%4))
def art(kind,row,field):
    fallback=row.get(field,"")
    title,year=title_year(row.get("name") or row.get("title"))
    year=year or str(row.get("year") or row.get("releaseDate") or row.get("release_date") or "")[:4]
    if not re.fullmatch(r"\d{4}",year):year=""
    if not title:return fallback
    payload=b64(json.dumps([kind,title,year,fallback]).encode())
    return PUBLIC+"/art/"+payload+"."+b64(hmac.digest(SECRET,payload.encode(),"sha256"))
def target(token):
    payload,sig=token.rsplit(".",1)
    if not hmac.compare_digest(unb64(sig),hmac.digest(SECRET,payload.encode(),"sha256")):raise ValueError("bad signature")
    kind,title,year,fallback=json.loads(unb64(payload))
    if kind not in ("movie","tv"):raise ValueError("bad kind")
    try:url=lookup(kind,title,year,int(time.time()//86400))
    except (OSError,ValueError):url=""
    if url:return url
    if isinstance(fallback,str) and fallback.startswith("https://"):return fallback
    return ""
def timestamp(s):
    try:
        p=s.split();return dt.datetime.strptime(p[0]+(p[1] if len(p)>1 else "+0000"),"%Y%m%d%H%M%S%z").timestamp()
    except (ValueError,IndexError,AttributeError):return None
def merge(primary,extra,known=None):
    root=ET.fromstring(primary);ids={x.get("id") for x in root.findall("channel")};spans={}
    for ident,name in (known or {}).items():
        if ident not in ids:
            c=ET.SubElement(root,"channel",{"id":ident});ET.SubElement(c,"display-name").text=name;ids.add(ident)
    for p in root.findall("programme"):
        a,b=timestamp(p.get("start")),timestamp(p.get("stop"))
        if a is not None and b is not None:spans.setdefault(p.get("channel"),[]).append((a,b))
    for xml in extra:
        try:other=ET.fromstring(xml)
        except ET.ParseError:continue
        for p in other.findall("programme"):
            ident=MAP.get(p.get("channel"),p.get("channel"))
            a,b=timestamp(p.get("start")),timestamp(p.get("stop"))
            if ident not in ids or a is None or b is None or b<=a:continue
            existing=spans.setdefault(ident,[])
            if any(a<y and b>x for x,y in existing):continue
            p.set("channel",ident);root.append(p);existing.append((a,b))
    return ET.tostring(root,encoding="utf-8",xml_declaration=True)
def upstream(path,params):
    return fetch(UP+path,{**params,"username":USER,"password":PASSWORD})
@lru_cache(maxsize=1)
def guide(epoch):
    primary=upstream("/xmltv.php",{})
    extra=[]
    for f in FEEDS:
        try:extra.append(fetch(f))
        except (OSError,ValueError):pass
    if not extra:return primary
    known={}
    try:
        rows=json.loads(upstream("/player_api.php",{"action":"get_live_streams"}))
        known={str(r["epg_channel_id"]):str(r.get("name") or r["epg_channel_id"]) for r in rows if r.get("epg_channel_id")}
    except (OSError,ValueError,TypeError,KeyError):pass
    return merge(primary,extra,known)
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def reply(self,status,data,typ="application/json"):
        if isinstance(data,dict):data=json.dumps(data).encode()
        self.send_response(status);self.send_header("Content-Type",typ);self.send_header("Content-Length",str(len(data)));self.send_header("Cache-Control","no-store");self.end_headers();self.wfile.write(data)
    def redirect(self,url):
        if not url or "\r" in url or "\n" in url:return self.reply(404,b"")
        self.send_response(302);self.send_header("Location",url);self.send_header("Content-Length","0");self.send_header("Cache-Control","no-store");self.end_headers()
    def do_GET(self):
        if not SLOTS.acquire(blocking=False):return self.reply(503,{"error":"busy"})
        try:self.route()
        except (OSError,ValueError,TypeError,KeyError,ET.ParseError):self.reply(502,{"error":"metadata unavailable"})
        finally:SLOTS.release()
    def route(self):
        u=urlsplit(self.path);p={k:v[-1] for k,v in parse_qs(u.query).items()}
        if u.path in ("/","/health"):
            return self.reply(200,{"status":"ok","mode":"private-account-test" if ready() else "demo","tmdb_configured":bool(KEY),"provider_configured":ready(),"supplementary_epg_configured":bool(FEEDS),"notice":"This product uses the TMDB API but is not endorsed or certified by TMDB."})
        if u.path=="/demo":
            results={}
            for kind,title,year in (("movie","Inception","2010"),("tv","Breaking Bad","2008")):
                try:results[title]=lookup(kind,title,year,int(time.time()//86400))
                except (OSError,ValueError):results[title]=""
            a=b'<tv><channel id="test"/><programme channel="test" start="20261124090000 +0000" stop="20261124100000 +0000"><title>Original</title></programme></tv>'
            b=b'<tv><programme channel="test" start="20261124100000 +0000" stop="20261124110000 +0000"><title>Supplement</title></programme></tv>'
            results["epg_merge_demo_passed"]=len(ET.fromstring(merge(a,[b])).findall("programme"))==2
            return self.reply(200,results)
        if not ready():return self.reply(503,{"error":"private test account not configured"})
        if u.path.startswith("/art/"):return self.redirect(target(u.path[5:]))
        if u.path not in ("/player_api.php","/xmltv.php"):return self.reply(404,{"error":"metadata only"})
        if not hmac.compare_digest(p.pop("username",""),ALIAS) or not hmac.compare_digest(p.pop("password",""),TOKEN):
            return self.reply(403,{"error":"invalid test credentials"})
        if u.path=="/xmltv.php":return self.reply(200,guide(int(time.time()//300)),"application/xml")
        action=p.get("action","")
        if action not in ("","get_vod_categories","get_series_categories","get_live_categories","get_vod_streams","get_series","get_live_streams","get_vod_info","get_series_info","get_short_epg","get_simple_data_table"):
            return self.reply(400,{"error":"unsupported action"})
        data=json.loads(upstream(u.path,p))
        if action in ("get_vod_streams","get_series") and isinstance(data,list):
            kind,field=("movie","stream_icon") if action=="get_vod_streams" else ("tv","cover")
            for row in data:
                if isinstance(row,dict):row[field]=art(kind,row,field)
        elif action in ("get_vod_info","get_series_info") and isinstance(data,dict) and isinstance(data.get("info"),dict):
            info=data["info"];kind="movie" if action=="get_vod_info" else "tv"
            for field in ("movie_image","cover","cover_big"):
                if field in info:info[field]=art(kind,info,field)
        # Never expose upstream account credentials or offer stream redirects.
        if not action and isinstance(data,dict):
            data={"user_info":{"username":ALIAS,"auth":1 if data.get("user_info",{}).get("auth")==1 else 0},"server_info":{"url":urlsplit(PUBLIC).hostname,"port":"443","https_port":"443","server_protocol":"https"}}
        return self.reply(200,json.dumps(data,separators=(",",":")).encode())
if __name__=="__main__":ThreadingHTTPServer(("0.0.0.0",int(os.environ.get("PORT","10000"))),Handler).serve_forever()
