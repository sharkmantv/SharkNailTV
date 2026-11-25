package com.example.xtreamtv;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
 final ExecutorService io=Executors.newFixedThreadPool(2);
 final Handler ui=new Handler(Looper.getMainLooper());
 final ThreadPoolExecutor posterIo=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
 final android.util.LruCache<String,String> posterUrls=new android.util.LruCache<>(500);
 final android.util.LruCache<String,android.graphics.Bitmap> posterCache=new android.util.LruCache<String,android.graphics.Bitmap>(24*1024*1024){@Override protected int sizeOf(String k,android.graphics.Bitmap b){return b.getAllocationByteCount();}};
 android.content.SharedPreferences prefs;
 LinearLayout body;
 FrameLayout preview;
 RecyclerView left,right;
 TextView status,epg;
 ExoPlayer player;
 PlayerView video;
 VideoView nativeVideo;
 MediaController nativeControls;
 boolean stopped;
 String activeDecoder="";
 String server="",user="",pass="",page="Home",cat="",seriesId="",liveExt="ts";
 int screenToken,loadToken,epgToken,catPos,itemPos;
 boolean full;
 Item selected;
 List<Item> cats=new ArrayList<>(),items=new ArrayList<>();
 long download=-1;
 static class Item { String id,name,ext,art="",year="",tmdb=""; Item(String i,String n,String e){id=i;name=n;ext=e;} }
 interface Work<T>{T run()throws Exception;} interface Done<T>{void run(T t);} interface Pick{void run(Item i,int p);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(128);prefs=getSharedPreferences("clean_shark_sky",0);server=prefs.getString("server","");user=prefs.getString("user","");pass=prefs.getString("pass","");liveExt=prefs.getString("format","ts");download=prefs.getLong("download",-1);applyDisplayMode();home();}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
 GradientDrawable bg(int a,int b,int border,int width){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(6));if(width>0)g.setStroke(dp(width),border);return g;}
 StateListDrawable focus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},bg(0xff153970,0xff071835,0xffffcf53,2));d.addState(new int[]{},bg(0xff10326a,0xff061331,0xff269cfa,1));return d;}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(0xfff1f6ff);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);t.setMaxLines(2);return t;}
 LinearLayout row(){return new LinearLayout(this);} LinearLayout col(){LinearLayout l=row();l.setOrientation(1);return l;}
 void button(LinearLayout r,String s,Runnable action){TextView t=text(s,15);t.setGravity(Gravity.CENTER);t.setFocusable(true);t.setClickable(true);t.setBackground(focus());t.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(t,p);}
 ImageView art(int res){ImageView v=new ImageView(this);v.setImageResource(res);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}
 void release(){if(nativeControls!=null){nativeControls.hide();nativeControls=null;}if(nativeVideo!=null){VideoView n=nativeVideo;nativeVideo=null;n.stopPlayback();}if(video!=null){video.setPlayer(null);video=null;}if(player!=null){ExoPlayer p=player;player=null;p.release();}activeDecoder="";}
 void screen(String title){release();screenToken++;loadToken++;epgToken++;full=false;status=null;epg=null;preview=null;body=col();body.setPadding(dp(16),dp(10),dp(16),dp(10));body.setBackground(bg(0xff123e87,0xff050d31,0,0));setContentView(body);immersive();LinearLayout head=row();head.addView(art(R.drawable.shark_logo),new LinearLayout.LayoutParams(dp(90),dp(43)));TextView t=text(title,22);head.addView(t,new LinearLayout.LayoutParams(0,dp(43),1));body.addView(head);}
 void home(){page="Home";screen("SHARK SKY");body.addView(art(R.drawable.shark_brand),new LinearLayout.LayoutParams(-1,0,1));LinearLayout r=row();String[] names={"Live TV","Movies","Series","Settings"};int[] icons={R.drawable.metal48_live,R.drawable.metal48_movies,R.drawable.metal48_series,R.drawable.metal48_settings};for(int i=0;i<4;i++){String dest=names[i];LinearLayout tile=col();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),dp(6),dp(8),dp(6));tile.setBackground(focus());tile.setFocusable(true);tile.setClickable(true);tile.addView(art(icons[i]),new LinearLayout.LayoutParams(-1,0,1));TextView t=text(dest,19);t.setGravity(Gravity.CENTER);tile.addView(t,new LinearLayout.LayoutParams(-1,dp(36)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(140),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(tile,p);tile.setOnClickListener(v->{if(dest.equals("Settings"))settings();else if(server.isEmpty())login(dest);else open(dest);});}body.addView(r);r.getChildAt(0).requestFocus();}
 void msg(String s){if(status!=null)status.setText(s);else Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
 static String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","SharkSky/5.0");try{if(c.getResponseCode()!=200)throw new IOException("HTTP");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>48*1024*1024)throw new IOException("Response too large");out.write(buf,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}}
 String api(String action,String args){return server+"/player_api.php?username="+enc(user)+"&password="+enc(pass)+(action.isEmpty()?"":"&action="+action)+args;}
 <T> void task(Work<T> w,Done<T> done){int token=screenToken;io.execute(()->{try{T data=w.run();ui.post(()->{if(token==screenToken&&!isFinishing())done.run(data);});}catch(Exception e){ui.post(()->{if(token==screenToken&&!isFinishing())msg("Request failed. Check connection or retry.");});}});}
 EditText field(String label,String value,int type){EditText e=new EditText(this);e.setHint(label);e.setText(value);e.setSingleLine();e.setInputType(type);body.addView(e);return e;}
 void login(String next){page="Settings";screen("Xtream Codes");EditText h=field("Server URL",server,17),u=field("Username",user,1),p=field("Password",pass,129);status=text("Enter your provider details",14);body.addView(status);LinearLayout r=row();button(r,"Connect",()->{String host=h.getText().toString().trim().replaceAll("/+$",""),name=u.getText().toString().trim(),pw=p.getText().toString();Uri uri=Uri.parse(host);if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getQuery()!=null||uri.getUserInfo()!=null||uri.getFragment()!=null||name.isEmpty()||pw.isEmpty()){msg("Invalid login details.");return;}msg("Connecting…");task(()->new JSONObject(get(host+"/player_api.php?username="+enc(name)+"&password="+enc(pw))),j->{JSONObject info=j.optJSONObject("user_info");if(info==null||info.optInt("auth",0)!=1){msg("Login rejected.");return;}server=host;user=name;pass=pw;prefs.edit().putString("server",host).putString("user",name).putString("pass",pw).apply();if(next.equals("Settings"))settings();else open(next);});});button(r,"Back",this::home);body.addView(r);}
 void settings(){page="Settings";screen("Settings");LinearLayout r=row();button(r,"Xtream Codes",()->login("Settings"));button(r,"Live format: "+liveExt,()->{liveExt=liveExt.equals("ts")?"m3u8":"ts";prefs.edit().putString("format",liveExt).apply();settings();});body.addView(r);LinearLayout r2=row();button(r2,"Software update",this::update);button(r2,"Install downloaded APK",this::install);body.addView(r2);status=text("5.0.3  ·  Media3 1.11.1  ·  "+(server.isEmpty()?"Not connected":"Playlist saved"),14);body.addView(status);LinearLayout r3=row();button(r3,"Playback & display",this::playbackSettings);button(r3,"TMDB artwork",this::tmdbSettings);button(r3,"Home",this::home);body.addView(r3);r.getChildAt(0).requestFocus();}
 void tmdbSettings(){page="Settings";screen("TMDB artwork");EditText key=field("TMDB v3 key (optional override)",prefs.getString("tmdb_override",""),129);status=text(tmdbKey().isEmpty()?"No TMDB key configured":"TMDB configured · high resolution posters",14);body.addView(status);LinearLayout r=row();button(r,"Save",()->{String v=key.getText().toString().trim();if(!v.isEmpty()&&!v.matches("[a-fA-F0-9]{32}")){msg("Invalid TMDB v3 key");return;}prefs.edit().putString("tmdb_override",v).apply();posterUrls.evictAll();posterCache.evictAll();tmdbSettings();});button(r,"Refresh artwork",()->{posterUrls.evictAll();posterCache.evictAll();msg("Artwork cache cleared");});button(r,"Back",this::settings);body.addView(r);}
 String tmdbKey(){String v=prefs.getString("tmdb_override","").trim();return v.isEmpty()?BuildConfig.TMDB_API_KEY:v;}
 void playbackSettings(){
  page="Settings";screen("Playback & display");
  LinearLayout r=row();button(r,"Subtitles: "+(prefs.getBoolean("subtitles_on",true)?"On":"Off"),()->{prefs.edit().putBoolean("subtitles_on",!prefs.getBoolean("subtitles_on",true)).apply();playbackSettings();});
  button(r,"Codec info: "+(prefs.getBoolean("show_codec_info",false)?"On":"Off"),()->{prefs.edit().putBoolean("show_codec_info",!prefs.getBoolean("show_codec_info",false)).apply();playbackSettings();});body.addView(r);
  LinearLayout r2=row();int mode=prefs.getInt("resize_mode",0);button(r2,"Picture: "+(mode==3?"Stretch":mode==4?"Zoom":"Fit"),()->{prefs.edit().putInt("resize_mode",mode==0?3:mode==3?4:0).apply();playbackSettings();});
  button(r2,"Audio: "+prefs.getString("audio_lang","Automatic"),()->languageDialog("audio_lang","Preferred audio language"));button(r2,"Text: "+prefs.getString("text_lang","Automatic"),()->languageDialog("text_lang","Preferred subtitle language"));body.addView(r2);
  LinearLayout r3=row();button(r3,"Display mode",this::displayDialog);button(r3,"Clear remembered decoders",()->{decoderPrefs().edit().clear().apply();msg("Decoder memory cleared.");});body.addView(r3);
  android.view.Display.Mode m=getWindowManager().getDefaultDisplay().getMode();status=text("Display: "+m.getPhysicalWidth()+" × "+m.getPhysicalHeight()+" · "+String.format(Locale.US,"%.2f Hz",m.getRefreshRate())+"  |  Native fallback uses Android system controls.",14);body.addView(status);
  LinearLayout back=row();button(back,"Back",this::settings);body.addView(back);r.getChildAt(0).requestFocus();
 }
 void languageDialog(String key,String title){String[] labels={"Automatic","English","Spanish","French","German","Italian","Arabic"},codes={null,"en","es","fr","de","it","ar"};new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->{prefs.edit().putString(key,codes[i]).apply();playbackSettings();}).setNegativeButton("Cancel",null).show();}
 void displayDialog(){android.view.Display display=getWindowManager().getDefaultDisplay();android.view.Display.Mode[] modes=display.getSupportedModes();String[] labels=new String[modes.length+2];labels[0]="Automatic (system)";labels[1]="Highest refresh at current resolution";for(int i=0;i<modes.length;i++)labels[i+2]=modes[i].getPhysicalWidth()+" × "+modes[i].getPhysicalHeight()+" · "+String.format(Locale.US,"%.2f Hz",modes[i].getRefreshRate());new AlertDialog.Builder(this).setTitle("Supported display modes").setItems(labels,(d,i)->{prefs.edit().putInt("display_mode",i==0?0:i==1?-1:modes[i-2].getModeId()).apply();applyDisplayMode();playbackSettings();}).setNegativeButton("Cancel",null).show();}
 void applyDisplayMode(){android.view.Display display=getWindowManager().getDefaultDisplay();android.view.Display.Mode current=display.getMode();int request=prefs.getInt("display_mode",0),chosen=0;float rate=0;for(android.view.Display.Mode m:display.getSupportedModes()){if(request>0&&m.getModeId()==request){chosen=request;break;}if(request==-1&&m.getPhysicalWidth()==current.getPhysicalWidth()&&m.getPhysicalHeight()==current.getPhysicalHeight()&&m.getRefreshRate()>rate){rate=m.getRefreshRate();chosen=m.getModeId();}}WindowManager.LayoutParams a=getWindow().getAttributes();a.preferredDisplayModeId=chosen;getWindow().setAttributes(a);}
 String prefix(){return page.equals("Live TV")?"live":page.equals("Movies")?"vod":"series";}
 static List<Item> parse(JSONArray a,String id,String name){List<Item> list=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&!j.optString(id).isEmpty()){Item item=new Item(j.optString(id),j.optString(name),j.optString("container_extension","mp4"));if(!id.equals("category_id")){item.art=j.optString("stream_icon",j.optString("cover",""));item.year=j.optString("year",j.optString("releaseDate",j.optString("release_date","")));item.tmdb=j.optString("tmdb_id",j.optString("tmdb",""));}list.add(item);}}return list;}
 void open(String p){page=p;cat="";seriesId="";catPos=itemPos=0;selected=null;cats=new ArrayList<>();items=new ArrayList<>();browser(false);loadCats();}
 RecyclerView list(){RecyclerView r=new RecyclerView(this);r.setLayoutManager(new LinearLayoutManager(this));r.setHasFixedSize(true);r.setItemAnimator(null);return r;}
 class Rows extends RecyclerView.Adapter<Rows.H>{List<Item> data;Pick pick;Rows(List<Item>d,Pick p){data=d;pick=p;}class H extends RecyclerView.ViewHolder{TextView t;H(TextView t){super(t);this.t=t;}}
 public H onCreateViewHolder(ViewGroup p,int type){TextView t=text("",15);t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(8),0,dp(5),0);t.setFocusable(true);t.setBackground(focus());RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(38));lp.setMargins(dp(2),dp(1),dp(2),dp(2));t.setLayoutParams(lp);H h=new H(t);t.setOnClickListener(v->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});return h;}
 public void onBindViewHolder(H h,int i){h.t.setText(data.get(i).name);} public int getItemCount(){return data.size();}}
 static String cleanTitle(String title){
  return title.replaceAll("(?i)^\\s*(?:[A-Z]{2,3}\\s*[|:•]\\s*)+","").replaceAll("(?i)\\b(?:4K|8K|UHD|FHD|HD|1080p|2160p|720p|HEVC|HDR10|HDR|DOLBY VISION)\\b"," ").replaceAll("\\s*[\\[(](?:19|20)\\d{2}[\\])]","").replaceAll("\\s+"," ").trim();
 }
 static String normalizedTitle(String s){return java.text.Normalizer.normalize(cleanTitle(s),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 static String yearOf(Item i){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(i.year);if(m.find())return m.group();m=java.util.regex.Pattern.compile("[\\[(]((?:19|20)\\d{2})[\\])]").matcher(i.name);return m.find()?m.group(1):"";}
 String artwork(Item i,String type,String key)throws Exception{
  if(key.isEmpty())return i.art;
  String cacheKey=key+"|"+type+"|"+i.tmdb+"|"+i.name+"|"+yearOf(i);
  String cached=posterUrls.get(cacheKey);if(cached!=null)return cached.isEmpty()?i.art:cached;
  String endpoint="https://api.themoviedb.org/3/",path="",wanted=normalizedTitle(i.name),year=yearOf(i);
  if(i.tmdb.matches("[1-9][0-9]{0,9}")){
   try{JSONObject o=new JSONObject(get(endpoint+type+"/"+i.tmdb+"?api_key="+enc(key)));String t=o.optString(type.equals("tv")?"name":"title",""),original=o.optString(type.equals("tv")?"original_name":"original_title","");String p=o.optString("poster_path","");if(!wanted.isEmpty()&&(wanted.equals(normalizedTitle(t))||wanted.equals(normalizedTitle(original)))&&p.startsWith("/"))path=p;}catch(Exception ignored){}
  }
  if(path.isEmpty()&&!wanted.isEmpty()){
   String q=cleanTitle(i.name),u=endpoint+"search/"+type+"?api_key="+enc(key)+"&query="+enc(q)+"&include_adult=false"+(year.isEmpty()?"":(type.equals("tv")?"&first_air_date_year=":"&year=")+year);
   JSONArray a=new JSONObject(get(u)).optJSONArray("results");int matches=0;
   if(a!=null)for(int n=0;n<a.length();n++){JSONObject o=a.optJSONObject(n);if(o==null)continue;String t=o.optString(type.equals("tv")?"name":"title",""),original=o.optString(type.equals("tv")?"original_name":"original_title",""),date=o.optString(type.equals("tv")?"first_air_date":"release_date",""),p=o.optString("poster_path","");if((wanted.equals(normalizedTitle(t))||wanted.equals(normalizedTitle(original)))&&(year.isEmpty()||date.startsWith(year))&&p.startsWith("/")){matches++;path=p;}}
   if(matches!=1)path="";
  }
  String url=path.matches("/[A-Za-z0-9_.-]+")?"https://image.tmdb.org/t/p/w780"+path:"";posterUrls.put(cacheKey,url);return url.isEmpty()?i.art:url;
 }
 android.graphics.Bitmap posterBitmap(String url)throws Exception{
  if(url==null||!(url.startsWith("https://")||url.startsWith("http://")))return null;
  android.graphics.Bitmap cached=posterCache.get(url);if(cached!=null)return cached;
  HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(5000);conn.setReadTimeout(7000);
  try{if(conn.getResponseCode()!=200)return null;try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted()||out.size()+n>8*1024*1024)return null;out.write(buf,0,n);}byte[] data=out.toByteArray();android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return null;android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();opt.inSampleSize=1;while(bounds.outWidth/opt.inSampleSize>1000||bounds.outHeight/opt.inSampleSize>1500)opt.inSampleSize*=2;android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,opt);if(b!=null)posterCache.put(url,b);return b;}}finally{conn.disconnect();}
 }
 boolean showPosters(){return page.equals("Movies")||(page.equals("Series")&&seriesId.isEmpty());}
 void setItemsAdapter(List<Item> data,Pick pick){
  if(showPosters()){int screenDp=getResources().getConfiguration().screenWidthDp;int cols=Math.max(2,Math.min(6,Math.round(screenDp*.70f/125f)));right.setLayoutManager(new GridLayoutManager(this,cols));right.setAdapter(new Posters(data,pick,page.equals("Series")?"tv":"movie",cols));}
  else{if(!(right.getLayoutManager() instanceof LinearLayoutManager)||right.getLayoutManager() instanceof GridLayoutManager)right.setLayoutManager(new LinearLayoutManager(this));right.setAdapter(new Rows(data,pick));}
 }
 class Posters extends RecyclerView.Adapter<Posters.H>{
  final List<Item> data;final Pick pick;final String type;final int cols;
  Posters(List<Item>d,Pick p,String t,int n){data=d;pick=p;type=t;cols=n;}
  class H extends RecyclerView.ViewHolder{ImageView art;TextView title;Future<?> job;Item bound;H(LinearLayout v,ImageView a,TextView t){super(v);art=a;title=t;}}
  @Override public H onCreateViewHolder(ViewGroup parent,int viewType){
   LinearLayout v=col();v.setPadding(dp(4),dp(4),dp(4),dp(4));v.setFocusable(true);v.setClickable(true);v.setBackground(focus());
   int w=Math.max(dp(80),parent.getWidth()/cols-dp(12));ImageView a=new ImageView(MainActivity.this);a.setScaleType(ImageView.ScaleType.FIT_CENTER);a.setBackgroundColor(0xff081629);v.addView(a,new LinearLayout.LayoutParams(-1,Math.round(w*1.5f)));
   TextView t=text("",12);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER);v.addView(t,new LinearLayout.LayoutParams(-1,dp(38)));
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(dp(3),dp(3),dp(3),dp(3));v.setLayoutParams(lp);H h=new H(v,a,t);v.setOnClickListener(view->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});return h;
  }
  @Override public void onBindViewHolder(H h,int pos){if(h.job!=null)h.job.cancel(true);Item i=data.get(pos);h.bound=i;h.title.setText(i.name);h.art.setImageDrawable(null);int token=screenToken;String key=tmdbKey();
   h.job=posterIo.submit(()->{android.graphics.Bitmap b=null;try{b=posterBitmap(artwork(i,type,key));}catch(Exception ignored){}if(b==null&&!Thread.currentThread().isInterrupted())try{b=posterBitmap(i.art);}catch(Exception ignored){}final android.graphics.Bitmap result=b;if(result!=null&&!Thread.currentThread().isInterrupted())ui.post(()->{if(h.bound==i&&token==screenToken&&!isFinishing())h.art.setImageBitmap(result);});});
  }
  @Override public void onViewRecycled(H h){if(h.job!=null)h.job.cancel(true);h.bound=null;h.art.setImageDrawable(null);super.onViewRecycled(h);}
  public int getItemCount(){return data.size();}
 }
 void focusAt(RecyclerView r,int p){if(r.getAdapter()==null||r.getAdapter().getItemCount()==0)return;int i=Math.max(0,Math.min(p,r.getAdapter().getItemCount()-1));r.scrollToPosition(i);r.post(()->{RecyclerView.ViewHolder h=r.findViewHolderForAdapterPosition(i);if(h!=null)h.itemView.requestFocus();else r.requestFocus();});}
 void catAdapter(){left.setAdapter(new Rows(cats,(i,p)->{catPos=p;itemPos=0;cat=i.id;seriesId="";loadItems();}));}
 void browser(boolean restore){screen(page);LinearLayout heads=row();heads.addView(text("Categories",14),new LinearLayout.LayoutParams(0,dp(25),1));heads.addView(text(page.equals("Live TV")?"Channels":"Titles",14),new LinearLayout.LayoutParams(0,dp(25),1.4f));boolean live=page.equals("Live TV");if(live)heads.addView(new View(this),new LinearLayout.LayoutParams(0,dp(25),1.6f));body.addView(heads);LinearLayout r=row();body.addView(r,new LinearLayout.LayoutParams(-1,0,1));left=list();right=list();r.addView(left,new LinearLayout.LayoutParams(0,-1,1));r.addView(right,new LinearLayout.LayoutParams(0,-1,live?1.4f:2.5f));catAdapter();setItemsAdapter(items,this::pick);if(live){LinearLayout c=col();c.setPadding(dp(4),0,0,0);r.addView(c,new LinearLayout.LayoutParams(0,-1,1.6f));preview=new FrameLayout(this);preview.setBackgroundColor(Color.BLACK);c.addView(preview,new LinearLayout.LayoutParams(-1,dp(150)));TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));epg=text(selected==null?"Programme information":selected.name,13);epg.setMaxLines(5);c.addView(epg,new LinearLayout.LayoutParams(-1,0,1));LinearLayout b=row();button(b,"Full screen",()->{if(selected!=null)playFull(selected);});button(b,"Favourite",this::fav);c.addView(b);}status=text("",12);body.addView(status,new LinearLayout.LayoutParams(-1,dp(23)));LinearLayout b=row();button(b,"Home",this::home);button(b,"Search",this::search);button(b,"Retry",()->{if(cats.isEmpty())loadCats();else loadItems();});body.addView(b);if(restore){left.scrollToPosition(catPos);focusAt(right,itemPos);}}
 void loadCats(){String url=api("get_"+prefix()+"_categories","");msg("Loading categories…");task(()->parse(new JSONArray(get(url)),"category_id","category_name"),a->{cats=new ArrayList<>();cats.add(new Item("","All",""));if(page.equals("Live TV"))cats.add(new Item("@fav","Favourites",""));cats.addAll(a);catAdapter();focusAt(left,0);loadItems();});}
 void loadItems(){release();selected=null;epgToken++;if(preview!=null){preview.removeAllViews();TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));}int req=++loadToken;String k=prefix(),c=cat.equals("@fav")?"":cat;String url=api(k.equals("series")?"get_series":"get_"+k+"_streams",c.isEmpty()?"":"&category_id="+enc(c));msg("Loading…");task(()->parse(new JSONArray(get(url)),k.equals("series")?"series_id":"stream_id","name"),a->{if(req!=loadToken)return;items=a;if(cat.equals("@fav")){Set<String> f=prefs.getStringSet("fav",Collections.emptySet());items.removeIf(i->!f.contains(i.id));}setItemsAdapter(items,this::pick);msg(items.size()+" titles");});}
 void search(){EditText e=new EditText(this);e.setSingleLine();new AlertDialog.Builder(this).setTitle("Search current list").setView(e).setPositiveButton("Search",(d,w)->{String q=e.getText().toString().toLowerCase(Locale.ROOT);List<Item>a=new ArrayList<>();for(Item i:items)if(i.name.toLowerCase(Locale.ROOT).contains(q))a.add(i);setItemsAdapter(a,(i,p)->pick(i,items.indexOf(i)));focusAt(right,0);}).setNegativeButton("Cancel",null).show();}
 void pick(Item i,int p){itemPos=p;if(page.equals("Live TV")){if(selected!=null&&selected.id.equals(i.id)&&player!=null){playFull(i);return;}selected=i;attach(preview,i,false);loadEpg(i);return;}if(page.equals("Series")&&seriesId.isEmpty()){episodes(i);return;}playFull(i);}
 void episodes(Item s){int req=++loadToken;String url=api("get_series_info","&series_id="+enc(s.id));task(()->{JSONObject j=new JSONObject(get(url)).optJSONObject("episodes");List<Item>a=new ArrayList<>();if(j!=null){List<String> keys=new ArrayList<>();j.keys().forEachRemaining(keys::add);Collections.sort(keys);for(String k:keys){JSONArray e=j.optJSONArray(k);if(e!=null)for(int n=0;n<e.length();n++){JSONObject o=e.optJSONObject(n);if(o!=null&&!o.optString("id").isEmpty())a.add(new Item(o.optString("id"),"S"+k+" E"+o.optString("episode_num")+" · "+o.optString("title"),o.optString("container_extension","mp4")));}}}return a;},a->{if(req!=loadToken)return;seriesId=s.id;items=a;setItemsAdapter(items,this::pick);msg(s.name);focusAt(right,0);});}
 void loadEpg(Item i){if(epg==null)return;epg.setText(i.name);int req=++epgToken;String url=api("get_short_epg","&stream_id="+enc(i.id)+"&limit=4");task(()->new JSONObject(get(url)),j->{if(req!=epgToken||epg==null)return;StringBuilder b=new StringBuilder(i.name);JSONArray a=j.optJSONArray("epg_listings");if(a!=null)for(int n=0;n<a.length();n++){JSONObject o=a.optJSONObject(n);if(o!=null){String v=o.optString("title");try{v=new String(android.util.Base64.decode(v,0),"UTF-8");}catch(Exception ignored){}b.append("\n").append(v);}}if(a==null||a.length()==0)b.append("\nNo programme information");epg.setText(b);});}
 void fav(){if(selected==null)return;Set<String>s=new HashSet<>(prefs.getStringSet("fav",Collections.emptySet()));if(!s.add(selected.id)){s.remove(selected.id);msg("Removed favourite");}else msg("Favourite saved");prefs.edit().putStringSet("fav",s).apply();}
 String stream(Item i){String k=page.equals("Live TV")?"live":page.equals("Movies")?"movie":"series";String e=k.equals("live")?liveExt:i.ext;if(!e.matches("[a-zA-Z0-9]{1,8}"))e="mp4";return server+"/"+k+"/"+enc(user)+"/"+enc(pass)+"/"+enc(i.id)+"."+e;}
 String decoderKey(Item i){return UUID.nameUUIDFromBytes((server+"|"+page+"|"+i.id+"|"+i.ext).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
 android.content.SharedPreferences decoderPrefs(){return getSharedPreferences("decoder_recovery_v1",0);}
 boolean rememberedDecoder(Item i){long when=decoderPrefs().getLong(decoderKey(i),0);long age=System.currentTimeMillis()-when;return when>0&&age>=0&&age<7L*24*60*60*1000;}
 void rememberDecoder(Item i){android.content.SharedPreferences d=decoderPrefs();android.content.SharedPreferences.Editor e=d.edit();Map<String,?> all=d.getAll();if(all.size()>=128){String oldest=null;long time=Long.MAX_VALUE;for(Map.Entry<String,?> entry:all.entrySet())if(entry.getValue() instanceof Long&&(Long)entry.getValue()<time){oldest=entry.getKey();time=(Long)entry.getValue();}if(oldest!=null)e.remove(oldest);}e.putLong(decoderKey(i),System.currentTimeMillis()).apply();}
 void attach(FrameLayout host,Item i,boolean fullscreen){boolean cached=fullscreen&&!page.equals("Live TV")&&rememberedDecoder(i);attachExo(host,i,fullscreen,cached?1:0,0,cached);}
 void attachExo(FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst){
  release();host.removeAllViews();
  PlayerView v=new PlayerView(this);v.setUseController(fullscreen);v.setControllerAutoShow(fullscreen);
  host.addView(v,new FrameLayout.LayoutParams(-1,-1));
  DefaultRenderersFactory factory=new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
  if(attempt==1){
   factory.forceDisableMediaCodecAsynchronousQueueing();
   factory.setMediaCodecSelector((mime,secure,tunnel)->{
    List<MediaCodecInfo> decoders=new ArrayList<>(MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunnel));
    if(!secure&&!tunnel&&MimeTypes.VIDEO_H265.equals(mime)){
     // Prefer the observed MediaTek decoder only if this device advertises it.
     for(int n=0;n<decoders.size();n++){if("c2.mtk.hevc.decoder".equals(decoders.get(n).name)){MediaCodecInfo codec=decoders.remove(n);decoders.add(0,codec);break;}}
    }
    return decoders;
   });
  }
  ExoPlayer p=new ExoPlayer.Builder(this,factory).build();
  video=v;player=p;v.setPlayer(p);v.setResizeMode(prefs.getInt("resize_mode",0));
  p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
   .setPreferredAudioLanguage(prefs.getString("audio_lang",null))
   .setPreferredTextLanguage(prefs.getString("text_lang",null))
   .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!prefs.getBoolean("subtitles_on",true)).build());
  p.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF);
  if(!fullscreen)p.setVolume(0);
  final boolean vod=fullscreen&&!page.equals("Live TV");
  final int token=screenToken;
  p.addAnalyticsListener(new AnalyticsListener(){
   @Override public void onVideoDecoderInitialized(AnalyticsListener.EventTime time,String name,long ts,long duration){
    if(player!=p)return;activeDecoder=name;android.util.Log.i("SharkPlayback","decoder="+name+" attempt="+attempt+" initMs="+duration);
   }
  });
  MediaItem.Builder m=new MediaItem.Builder().setUri(stream(i));
  if(page.equals("Live TV"))m.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build());
  p.addListener(new Player.Listener(){
   boolean recovering;
   @Override public void onRenderedFirstFrame(){
    if(player!=p||recovering)return;
    if(vod&&attempt==1)rememberDecoder(i);
    if(fullscreen&&prefs.getBoolean("show_codec_info",false)){
     Format f=p.getVideoFormat();
     String details=(activeDecoder.isEmpty()?"Decoder unavailable":activeDecoder)+(f==null?"":" · "+f.width+"×"+f.height+(f.bitrate>0?" · "+String.format(Locale.US,"%.1f Mb/s",f.bitrate/1000000.0):""));
     msg(details);
    }
   }
   @Override public void onPlayerError(PlaybackException e){
    if(player!=p||recovering)return;
    Format fmt=p.getVideoFormat();
    String detail=fmt==null?"format unknown":fmt.sampleMimeType+" "+fmt.codecs+" "+fmt.width+"x"+fmt.height+" bitrate="+fmt.bitrate;
    android.util.Log.w("SharkPlayback",e.getErrorCodeName()+" "+detail+" decoder="+activeDecoder+" attempt="+attempt);
    // Do not log source URLs, usernames, passwords, or exception text.
    for(Throwable cause=e.getCause();cause!=null;cause=cause.getCause()){
     if(cause instanceof android.media.MediaCodec.CodecException){android.util.Log.w("SharkPlayback","codec diagnostic="+((android.media.MediaCodec.CodecException)cause).getDiagnosticInfo());break;}
    }
    boolean decoderError=e.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED;
    if(!vod||!decoderError||stopped){msg("Playback failed: "+e.getErrorCodeName());return;}
    if(attempt==1)decoderPrefs().edit().remove(decoderKey(i)).apply();
    recovering=true;long resume=Math.max(0,p.getCurrentPosition());
    msg(attempt==0&&!cachedFirst?"Retrying compatible hardware decoder…":attempt==1&&cachedFirst?"Retrying standard decoder…":"Trying Android native playback…");
    release();
    ui.postDelayed(()->{
     if(token!=screenToken||!full||stopped||isFinishing()||player!=null||nativeVideo!=null)return;
     if(attempt==0&&!cachedFirst)attachExo(host,i,true,1,resume,false);
     else if(attempt==1&&cachedFirst)attachExo(host,i,true,0,resume,true);
     else attachNative(host,i,resume);
    },500);
   }
  });
  p.setMediaItem(m.build());if(vod&&position>0)p.seekTo(position);p.setPlaybackSpeed(1f);p.prepare();p.play();
 }
 void attachNative(FrameLayout host,Item i,long position){
  release();host.removeAllViews();
  VideoView n=new VideoView(this);nativeVideo=n;
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);host.addView(n,lp);
  MediaController controls=new MediaController(this);nativeControls=controls;controls.setAnchorView(n);n.setMediaController(controls);
  n.setOnPreparedListener(mp->{if(nativeVideo!=n)return;if(position>0)n.seekTo((int)Math.min(position,Integer.MAX_VALUE));android.util.Log.i("SharkPlayback","Android native prepared "+mp.getVideoWidth()+"x"+mp.getVideoHeight());if(!stopped)n.start();});
  n.setOnErrorListener((mp,what,extra)->{if(nativeVideo==n){android.util.Log.w("SharkPlayback","Android native error="+what+"/"+extra);msg("Playback failed on both players ("+what+"/"+extra+")");}return true;});
  n.setVideoURI(Uri.parse(stream(i)));n.requestFocus();n.start();
 }
 void playFull(Item i){release();screenToken++;loadToken++;epgToken++;full=true;status=null;FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.BLACK);setContentView(f);immersive();attach(f,i,true);video.requestFocus();}
 @Override public void onBackPressed(){if(full){release();browser(true);return;}if(page.equals("Series")&&!seriesId.isEmpty()){seriesId="";loadItems();return;}if(!page.equals("Home")){home();return;}super.onBackPressed();}
 @Override protected void onStart(){super.onStart();stopped=false;}
 @Override protected void onStop(){stopped=true;super.onStop();if(player!=null)player.pause();if(nativeVideo!=null)nativeVideo.pause();}
 @Override protected void onDestroy(){screenToken++;release();io.shutdownNow();updateIo.shutdownNow();posterIo.shutdownNow();posterCache.evictAll();ui.removeCallbacksAndMessages(null);super.onDestroy();}
 final ExecutorService updateIo=Executors.newSingleThreadExecutor();
 boolean updateBusy=false;
 volatile int updateProgress=0;
 String updateStatus="Check for the latest Shark Sky update.";
 ProgressBar updateBar;
 TextView updateText;
 File updateFile(){return new File(new File(getFilesDir(),"updates"),"SharkNailTV.apk");}
 void updateMessage(String s,int progress){updateStatus=s;updateProgress=progress;if(updateText!=null&&updateText.isAttachedToWindow()){updateText.setText(s);updateBar.setIndeterminate(progress<0);if(progress>=0)updateBar.setProgress(progress);}}
 void update(){page="Settings";screen("Software update");
  updateText=text(updateStatus,16);updateText.setMaxLines(4);body.addView(updateText,new LinearLayout.LayoutParams(-1,dp(76)));
  updateBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);updateBar.setMax(100);updateBar.setIndeterminate(updateProgress<0);if(updateProgress>=0)updateBar.setProgress(updateProgress);body.addView(updateBar,new LinearLayout.LayoutParams(-1,dp(24)));
  LinearLayout r=row();button(r,"Check / download",this::checkUpdate);button(r,"Install verified update",this::install);button(r,"Back",this::settings);body.addView(r);r.getChildAt(0).requestFocus();if(!updateBusy){File saved=updateFile();if(saved.isFile()){try{verifyUpdate(saved,null);}catch(Exception e){saved.delete();}}if(!saved.isFile())checkUpdate();}
 }
 void checkUpdate(){
  if(updateBusy)return;updateBusy=true;updateMessage("Checking GitHub…",-1);
  updateIo.execute(()->{try{
   JSONObject j=new JSONObject(get("https://api.github.com/repos/sharkmantv/SharkNailTV/releases/latest"));
   String tag=j.optString("tag_name").replaceFirst("^v","");if(!tag.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Invalid release version");
   if(compare(tag,BuildConfig.VERSION_NAME)<=0){ui.post(()->{updateBusy=false;updateMessage("You have the latest version: "+BuildConfig.VERSION_NAME,0);});return;}
   JSONArray assets=j.optJSONArray("assets");JSONObject found=null;if(assets!=null)for(int n=0;n<assets.length();n++){JSONObject x=assets.optJSONObject(n);if(x!=null&&"SharkNailTV.apk".equals(x.optString("name"))){found=x;break;}}
   if(found==null)throw new IOException("Release has no APK");
   String link=found.optString("browser_download_url");long size=found.optLong("size",-1);String digest=found.optString("digest","");
   String expected="https://github.com/sharkmantv/SharkNailTV/releases/download/v"+tag+"/SharkNailTV.apk";
   if(!expected.equals(link)||size<=0||size>150L*1024*1024)throw new IOException("Invalid release asset");
   ui.post(()->{if(isFinishing()||isDestroyed()){updateBusy=false;return;}if(updateText==null||!updateText.isAttachedToWindow()){updateBusy=false;updateStatus="Update available: "+tag;return;}
    updateMessage("Version "+tag+" available · "+String.format(Locale.US,"%.1f MB",size/1048576.0),0);
    new AlertDialog.Builder(this).setTitle("Download Shark Sky "+tag+"?").setMessage("The update will be downloaded and verified inside Shark Sky. Android will ask for installation approval.").setPositiveButton("Download",(dialog,w)->downloadUpdate(link,size,digest,tag)).setNegativeButton("Cancel",(dialog,w)->{updateBusy=false;updateMessage("Download cancelled",0);}).setOnCancelListener(dialog->{updateBusy=false;updateMessage("Download cancelled",0);}).show();
   });
  }catch(Exception e){final String reason=updateError(e);ui.post(()->{updateBusy=false;updateMessage("Update check failed: "+reason,0);});}});
 }
 static String updateError(Exception e){if(e instanceof java.net.SocketTimeoutException)return "connection timed out";if(e instanceof java.net.UnknownHostException)return "cannot reach GitHub";if(e instanceof javax.net.ssl.SSLException)return "secure connection failed";String m=e.getMessage();return m!=null&&m.matches("[A-Za-z0-9 .:/()_-]{1,120}")&&!m.contains("://")?m:e.getClass().getSimpleName();}
 HttpURLConnection openUpdate(String link)throws Exception{
  URL url=new URL(link);for(int n=0;n<8;n++){String h=url.getHost().toLowerCase(Locale.ROOT);
   if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||!(h.equals("github.com")||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com")||h.equals("github-releases.githubusercontent.com")))throw new IOException("Unexpected download host");
   HttpURLConnection con=(HttpURLConnection)url.openConnection();con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(30000);con.setRequestProperty("User-Agent","SharkSky/"+BuildConfig.VERSION_NAME);con.setRequestProperty("Accept","application/octet-stream");con.setRequestProperty("Accept-Encoding","identity");
   int code;try{code=con.getResponseCode();}catch(Exception ex){con.disconnect();throw ex;}
   if(code==200)return con;
   String location=con.getHeaderField("Location");con.disconnect();if(code>=300&&code<400&&location!=null){url=new URL(url,location);continue;}throw new IOException("Download HTTP "+code);
  }throw new IOException("Too many redirects");
 }
 void downloadUpdate(String link,long size,String digest,String tag){
  updateMessage("Downloading "+tag+"…",0);
  updateIo.execute(()->{File dest=updateFile(),part=new File(dest.getParentFile(),"update-part.apk");try{
   if(!dest.getParentFile().isDirectory()&&!dest.getParentFile().mkdirs())throw new IOException("Storage unavailable");
   java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
   HttpURLConnection con=openUpdate(link);
   try(InputStream in=con.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] buf=new byte[65536];long total=0;int n,last=-1;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download interrupted");total+=n;if(total>size)throw new IOException("Unexpected APK size");out.write(buf,0,n);hash.update(buf,0,n);int p=(int)(total*100/size);if(p!=last){last=p;final int percent=p;ui.post(()->updateMessage("Downloading "+tag+" · "+percent+"%",percent));}}out.getFD().sync();if(total!=size)throw new IOException("Incomplete APK download");}finally{con.disconnect();}
   if(digest.startsWith("sha256:")){StringBuilder actual=new StringBuilder();for(byte x:hash.digest())actual.append(String.format(Locale.US,"%02x",x&255));if(!digest.substring(7).equalsIgnoreCase(actual.toString()))throw new IOException("APK checksum mismatch");}
   ui.post(()->updateMessage("Verifying APK signature and version…",100));
   verifyUpdate(part,tag);
   if(dest.exists()&&!dest.delete())throw new IOException("Cannot replace old update");if(!part.renameTo(dest))throw new IOException("Cannot save verified update");
   ui.post(()->{updateBusy=false;updateMessage("Shark Sky "+tag+" verified. Ready to install.",100);if(updateText!=null&&updateText.isAttachedToWindow()&&!isFinishing()&&!isDestroyed())install();});
  }catch(Exception e){part.delete();final String reason=updateError(e);ui.post(()->{updateBusy=false;updateMessage("Download failed: "+reason,0);});}});
 }
 @SuppressWarnings("deprecation") void verifyUpdate(File f,String tag)throws Exception{
  android.content.pm.PackageManager pm=getPackageManager();
  android.content.pm.PackageInfo archive=pm.getPackageArchiveInfo(f.getAbsolutePath(),android.content.pm.PackageManager.GET_SIGNATURES);
  android.content.pm.PackageInfo installed=pm.getPackageInfo(getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);
  if(archive==null||!getPackageName().equals(archive.packageName))throw new IOException("Incorrect APK package");
  if(archive.versionCode<=installed.versionCode||(tag!=null&&!tag.equals(archive.versionName)))throw new IOException("APK version mismatch");
  if(archive.signatures==null||installed.signatures==null||!new HashSet<>(Arrays.asList(archive.signatures)).equals(new HashSet<>(Arrays.asList(installed.signatures))))throw new IOException("APK signing certificate mismatch");
 }
 static int compare(String a,String b){try{String[]x=a.split("\\."),y=b.split("\\.");for(int i=0;i<Math.max(x.length,y.length);i++){int n=i<x.length?Integer.parseInt(x[i]):0,m=i<y.length?Integer.parseInt(y[i]):0;if(n!=m)return Integer.compare(n,m);}return 0;}catch(Exception e){return -1;}}
 void install(){
  if(updateBusy){msg("Update is still downloading");return;}
  File apk=updateFile();if(!apk.isFile()){update();return;}
  try{verifyUpdate(apk,null);if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){updateMessage("Allow Shark Sky to install updates in Android settings, then return and select Install verified update.",100);startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}
   Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",apk);
   Intent in=new Intent(Intent.ACTION_VIEW);in.setDataAndType(uri,"application/vnd.android.package-archive");in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(in);
  }catch(Exception e){updateMessage("Installation failed: "+updateError(e),0);msg("Installation failed: "+updateError(e));}
 }
}
