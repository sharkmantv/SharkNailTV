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
 final ExecutorService epgIo=Executors.newFixedThreadPool(2);
 final ThreadPoolExecutor rowGuideIo=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(24),new ThreadPoolExecutor.AbortPolicy());
 final Runnable tickRows=new Runnable(){public void run(){if(stopped||!page.equals("Live TV"))return;if(!full&&right!=null&&right.getAdapter() instanceof LiveGuideRows)((LiveGuideRows)right.getAdapter()).refreshVisible(right);ui.postDelayed(this,60000);}};
 final android.util.LruCache<String,GuideData> guideCache=new android.util.LruCache<>(80);
 Future<?> previewGuideJob,fullGuideJob;
 Item highlightedGuideItem;
 Runnable pendingGuideFocus;
 int guideRequest;
 Dialog guideDialog;
 final Runnable refreshEpg=new Runnable(){public void run(){if(!stopped&&!full&&page.equals("Live TV")&&highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);else if(!stopped&&page.equals("Live TV"))ui.postDelayed(this,60000);}};
 static class Programme{String title,description,rawStart,rawEnd;long start,end;}
 static class GuideData{final List<Programme> shows;final long fetched=System.currentTimeMillis();final boolean extended;GuideData(List<Programme>s,boolean x){shows=s;extended=x;}}
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
 TextView rangeButton;
 Item playbackItem;
 FrameLayout liveSurfaceHost;
 View.OnLayoutChangeListener livePreviewLayout;
 int liveBodyFocusMode,liveBodyAccessibility;
 View liveReturnFocus;
 TextView liveReturnStatus;
 String activeStreamId="";
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
 static final int UI_TEXT=0xffe8edf6, UI_SECONDARY=0xffaebcd0, UI_ACCENT=0xff8dcaff;
 final android.graphics.Typeface uiRegular=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL);
 final android.graphics.Typeface uiMedium=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);
 final android.graphics.Typeface uiLight=android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL);
 StateListDrawable focus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},bg(0xff223c60,0xff142642,0xff9fc7f7,2));d.addState(new int[]{},bg(0xff111d34,0xff0b1428,0xff314764,1));return d;}
 StateListDrawable posterFocus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},bg(0xff223c60,0xff142642,0xffffcf53,2));d.addState(new int[]{},bg(0xff111d34,0xff0b1428,0xff314764,1));return d;}
 GradientDrawable screenBackground(){return bg(0xff172946,0xff070d1e,0,0);}
 void styleInput(EditText e){e.setTypeface(uiRegular);e.setTextColor(UI_TEXT);e.setHintTextColor(UI_SECONDARY);e.setTextSize(16);}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(size<=12?UI_SECONDARY:UI_TEXT);t.setTypeface(size>=21?uiLight:uiRegular);t.setTextSize(size);t.setLetterSpacing(size>=21?0.005f:0.01f);t.setGravity(Gravity.CENTER_VERTICAL);t.setMaxLines(2);return t;}
 LinearLayout row(){return new LinearLayout(this);} LinearLayout col(){LinearLayout l=row();l.setOrientation(1);return l;}
 void button(LinearLayout r,String s,Runnable action){TextView t=text(s,15);t.setTypeface(uiMedium);t.setGravity(Gravity.CENTER);t.setFocusable(true);t.setClickable(true);t.setBackground(focus());t.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(t,p);}
 ImageView art(int res){ImageView v=new ImageView(this);v.setImageResource(res);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}
 void release(){removeRangeButton();playbackItem=null;if(preview!=null&&livePreviewLayout!=null)preview.removeOnLayoutChangeListener(livePreviewLayout);livePreviewLayout=null;
  if(liveSurfaceHost!=null&&full&&body!=null){body.setDescendantFocusability(liveBodyFocusMode);body.setImportantForAccessibility(liveBodyAccessibility);}
  liveReturnFocus=null;liveReturnStatus=null;activeStreamId="";if(nativeControls!=null){nativeControls.hide();nativeControls=null;}if(nativeVideo!=null){VideoView n=nativeVideo;nativeVideo=null;n.stopPlayback();}if(video!=null){video.setPlayer(null);video=null;}if(player!=null){ExoPlayer p=player;player=null;p.release();}
  if(liveSurfaceHost!=null){ViewGroup parent=(ViewGroup)liveSurfaceHost.getParent();if(parent!=null)parent.removeView(liveSurfaceHost);liveSurfaceHost=null;}activeDecoder="";}
 void screen(String title){clearGuideFocus();ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);if(previewGuideJob!=null)previewGuideJob.cancel(true);release();screenToken++;loadToken++;epgToken++;full=false;status=null;epg=null;preview=null;body=col();body.setPadding(dp(16),dp(10),dp(16),dp(10));body.setBackground(screenBackground());setContentView(body);immersive();LinearLayout head=row();head.addView(art(R.drawable.shark_logo),new LinearLayout.LayoutParams(dp(90),dp(43)));TextView t=text(title,22);head.addView(t,new LinearLayout.LayoutParams(0,dp(43),1));body.addView(head);}
 void home(){page="Home";screen("SHARK SKY");body.addView(art(R.drawable.shark_brand),new LinearLayout.LayoutParams(-1,0,1));LinearLayout r=row();String[] names={"Live TV","Movies","Series","Settings"};int[] icons={R.drawable.metal48_live,R.drawable.metal48_movies,R.drawable.metal48_series,R.drawable.metal48_settings};for(int i=0;i<4;i++){String dest=names[i];LinearLayout tile=col();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),dp(6),dp(8),dp(6));tile.setBackground(focus());tile.setFocusable(true);tile.setClickable(true);tile.addView(art(icons[i]),new LinearLayout.LayoutParams(-1,0,1));TextView t=text(dest,19);t.setGravity(Gravity.CENTER);tile.addView(t,new LinearLayout.LayoutParams(-1,dp(36)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(140),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(tile,p);tile.setOnClickListener(v->{if(dest.equals("Settings"))settings();else if(server.isEmpty())login(dest);else open(dest);});}body.addView(r);r.getChildAt(0).requestFocus();}
 void msg(String s){if(status!=null)status.setText(s);else Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
 static String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","SharkSky/5.0");try{if(c.getResponseCode()!=200)throw new IOException("HTTP");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(out.size()+n>48*1024*1024)throw new IOException("Response too large");out.write(buf,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}}
 String api(String action,String args){return server+"/player_api.php?username="+enc(user)+"&password="+enc(pass)+(action.isEmpty()?"":"&action="+action)+args;}
 <T> void task(Work<T> w,Done<T> done){int token=screenToken;io.execute(()->{try{T data=w.run();ui.post(()->{if(token==screenToken&&!isFinishing())done.run(data);});}catch(Exception e){ui.post(()->{if(token==screenToken&&!isFinishing())msg("Request failed. Check connection or retry.");});}});}
 EditText field(String label,String value,int type){EditText e=new EditText(this);e.setHint(label);e.setText(value);e.setSingleLine();e.setInputType(type);styleInput(e);body.addView(e);return e;}
 void login(String next){page="Settings";screen("Xtream Codes");EditText h=field("Server URL",server,17),u=field("Username",user,1),p=field("Password",pass,129);status=text("Enter your provider details",14);body.addView(status);LinearLayout r=row();button(r,"Connect",()->{String host=h.getText().toString().trim().replaceAll("/+$",""),name=u.getText().toString().trim(),pw=p.getText().toString();Uri uri=Uri.parse(host);if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getQuery()!=null||uri.getUserInfo()!=null||uri.getFragment()!=null||name.isEmpty()||pw.isEmpty()){msg("Invalid login details.");return;}msg("Connecting…");task(()->new JSONObject(get(host+"/player_api.php?username="+enc(name)+"&password="+enc(pw))),j->{JSONObject info=j.optJSONObject("user_info");if(info==null||info.optInt("auth",0)!=1){msg("Login rejected.");return;}server=host;user=name;pass=pw;guideCache.evictAll();JSONObject serverInfo=j.optJSONObject("server_info");String zone=serverInfo==null?"":serverInfo.optString("timezone","");prefs.edit().putString("server",host).putString("user",name).putString("pass",pw).putString("epg_zone",zone).apply();if(next.equals("Settings"))settings();else open(next);});});button(r,"Back",this::home);body.addView(r);}
 void settings(){page="Settings";screen("Settings");LinearLayout r=row();button(r,"Xtream Codes",()->login("Settings"));button(r,"Live format: "+liveExt,()->{liveExt=liveExt.equals("ts")?"m3u8":"ts";prefs.edit().putString("format",liveExt).apply();settings();});body.addView(r);LinearLayout r2=row();button(r2,"Software update",this::update);button(r2,"Install downloaded APK",this::install);body.addView(r2);status=text("5.0.15  ·  Media3 1.11.1  ·  "+(server.isEmpty()?"Not connected":"Playlist saved"),14);body.addView(status);LinearLayout r3=row();button(r3,"Playback & display",this::playbackSettings);button(r3,"TMDB artwork",this::tmdbSettings);button(r3,"Home",this::home);body.addView(r3);r.getChildAt(0).requestFocus();}
 void tmdbSettings(){page="Settings";screen("TMDB artwork");EditText key=field("TMDB v3 key (optional override)",prefs.getString("tmdb_override",""),129);status=text(tmdbKey().isEmpty()?"No TMDB key configured":"TMDB configured · high resolution posters",14);body.addView(status);LinearLayout r=row();button(r,"Save",()->{String v=key.getText().toString().trim();if(!v.isEmpty()&&!v.matches("[a-fA-F0-9]{32}")){msg("Invalid TMDB v3 key");return;}prefs.edit().putString("tmdb_override",v).apply();posterUrls.evictAll();posterCache.evictAll();tmdbSettings();});button(r,"Refresh artwork",()->{posterUrls.evictAll();posterCache.evictAll();msg("Artwork cache cleared");});button(r,"Back",this::settings);body.addView(r);}
 String tmdbKey(){String v=prefs.getString("tmdb_override","").trim();return v.isEmpty()?BuildConfig.TMDB_API_KEY:v;}
 void playbackSettings(){
  page="Settings";screen("Playback & display");
  LinearLayout r=row();button(r,"Subtitles: "+(prefs.getBoolean("subtitles_on",true)?"On":"Off"),()->{prefs.edit().putBoolean("subtitles_on",!prefs.getBoolean("subtitles_on",true)).apply();playbackSettings();});
  button(r,"Codec info: "+(prefs.getBoolean("show_codec_info",false)?"On":"Off"),()->{prefs.edit().putBoolean("show_codec_info",!prefs.getBoolean("show_codec_info",false)).apply();playbackSettings();});body.addView(r);
  LinearLayout r2=row();int mode=prefs.getInt("resize_mode",0);button(r2,"Picture: "+(mode==3?"Stretch":mode==4?"Zoom":"Fit"),()->{prefs.edit().putInt("resize_mode",mode==0?3:mode==3?4:0).apply();playbackSettings();});
  button(r2,"Audio: "+prefs.getString("audio_lang","Automatic"),()->languageDialog("audio_lang","Preferred audio language"));button(r2,"Text: "+prefs.getString("text_lang","Automatic"),()->languageDialog("text_lang","Preferred subtitle language"));body.addView(r2);
  LinearLayout range=row();String dr=prefs.getString("dynamic_range","Auto");button(range,"Dynamic range: "+dr,()->{String[] options={"Auto","Prefer SDR","Prefer HDR"};new AlertDialog.Builder(this).setTitle("Video dynamic range").setSingleChoiceItems(options,Arrays.asList(options).indexOf(dr),(d,i)->{prefs.edit().putString("dynamic_range",options[i]).apply();d.dismiss();playbackSettings();}).setNegativeButton("Cancel",null).show();});body.addView(range);
  TextView hint=text(dr.equals("Prefer SDR")?"SDR mode requests HDR-to-SDR conversion from the device decoder. Restart playback after changing. Android 12+ required for decoder tone mapping.":dr.equals("Prefer HDR")?"Prefer a compatible HDR track when available; otherwise play normally.":"Auto follows the source and device capabilities.",13);body.addView(hint);
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
  else{if(!(right.getLayoutManager() instanceof LinearLayoutManager)||right.getLayoutManager() instanceof GridLayoutManager)right.setLayoutManager(new LinearLayoutManager(this));if(page.equals("Live TV")){right.setAdapter(new LiveGuideRows(data,pick,false,0,null));ui.removeCallbacks(tickRows);ui.postDelayed(tickRows,60000);}else right.setAdapter(new Rows(data,pick));}
 }
 class Posters extends RecyclerView.Adapter<Posters.H>{
  final List<Item> data;final Pick pick;final String type;final int cols;
  Posters(List<Item>d,Pick p,String t,int n){data=d;pick=p;type=t;cols=n;}
  class H extends RecyclerView.ViewHolder{ImageView art;TextView title;Future<?> job;Item bound;H(LinearLayout v,ImageView a,TextView t){super(v);art=a;title=t;}}
  @Override public H onCreateViewHolder(ViewGroup parent,int viewType){
   LinearLayout v=col();v.setPadding(dp(4),dp(4),dp(4),dp(4));v.setFocusable(true);v.setClickable(true);v.setBackground(posterFocus());
   int w=Math.max(dp(64),parent.getWidth()/cols-dp(12));int height=parent.getHeight()>0?parent.getHeight()/2-dp(6):Math.round(w*1.5f)+dp(8);
   ImageView a=new ImageView(MainActivity.this);a.setScaleType(ImageView.ScaleType.FIT_CENTER);a.setBackgroundColor(0xff0b1529);v.addView(a,new LinearLayout.LayoutParams(-1,-1));
   TextView t=text("",12);t.setVisibility(View.GONE);
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,Math.max(dp(64),height));lp.setMargins(dp(3),dp(3),dp(3),dp(3));v.setLayoutParams(lp);
   H h=new H(v,a,t);v.setOnFocusChangeListener((view,focused)->{if(focused&&h.bound!=null&&status!=null)status.setText(h.bound.name);});
   v.setOnClickListener(view->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});return h;
  }
  @Override public void onBindViewHolder(H h,int pos){if(h.job!=null)h.job.cancel(true);Item i=data.get(pos);h.bound=i;h.title.setText(i.name);h.itemView.setContentDescription(i.name);h.art.setImageDrawable(null);if(h.itemView.hasFocus()&&status!=null)status.setText(i.name);int token=screenToken;String key=tmdbKey();
   h.job=posterIo.submit(()->{android.graphics.Bitmap b=null;try{b=posterBitmap(artwork(i,type,key));}catch(Exception ignored){}if(b==null&&!Thread.currentThread().isInterrupted())try{b=posterBitmap(i.art);}catch(Exception ignored){}final android.graphics.Bitmap result=b;if(result!=null&&!Thread.currentThread().isInterrupted())ui.post(()->{if(h.bound==i&&token==screenToken&&!isFinishing())h.art.setImageBitmap(result);});});
  }
  @Override public void onViewRecycled(H h){if(h.job!=null)h.job.cancel(true);h.bound=null;h.art.setImageDrawable(null);super.onViewRecycled(h);}
  public int getItemCount(){return data.size();}
 }
 void focusAt(RecyclerView r,int p){if(r.getAdapter()==null||r.getAdapter().getItemCount()==0)return;int i=Math.max(0,Math.min(p,r.getAdapter().getItemCount()-1));r.scrollToPosition(i);r.post(()->{RecyclerView.ViewHolder h=r.findViewHolderForAdapterPosition(i);if(h!=null)h.itemView.requestFocus();else r.requestFocus();});}
 void catAdapter(){left.setAdapter(new Rows(cats,(i,p)->{catPos=p;itemPos=0;cat=i.id;seriesId="";loadItems();}));}
 void browser(boolean restore){screen(page);LinearLayout heads=row();heads.addView(text("Categories",14),new LinearLayout.LayoutParams(0,dp(25),1));heads.addView(text(page.equals("Live TV")?"Channels":"Titles",14),new LinearLayout.LayoutParams(0,dp(25),1.4f));boolean live=page.equals("Live TV");if(live)heads.addView(new View(this),new LinearLayout.LayoutParams(0,dp(25),1.6f));body.addView(heads);LinearLayout r=row();body.addView(r,new LinearLayout.LayoutParams(-1,0,1));left=list();right=list();r.addView(left,new LinearLayout.LayoutParams(0,-1,1));r.addView(right,new LinearLayout.LayoutParams(0,-1,live?1.4f:2.5f));catAdapter();setItemsAdapter(items,this::pick);if(live){LinearLayout c=col();c.setPadding(dp(4),0,0,0);r.addView(c,new LinearLayout.LayoutParams(0,-1,1.6f));preview=new FrameLayout(this);preview.setBackgroundColor(Color.BLACK);c.addView(preview,new LinearLayout.LayoutParams(-1,0,1.7f));TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));epg=text(selected==null?"Programme information":selected.name,13);epg.setMaxLines(7);epg.setGravity(Gravity.TOP);epg.setPadding(dp(5),dp(7),dp(5),0);c.addView(epg,new LinearLayout.LayoutParams(-1,0,1));LinearLayout b=row();button(b,"Full screen",()->{if(selected!=null)playFull(selected);});button(b,"Favourite",this::fav);c.addView(b);}status=text("",12);body.addView(status,new LinearLayout.LayoutParams(-1,dp(23)));LinearLayout b=row();button(b,"Home",this::home);button(b,"Search",this::search);if(live)button(b,"TV Guide",this::openGuide);button(b,"Retry",()->{if(cats.isEmpty())loadCats();else loadItems();});body.addView(b);if(restore){left.scrollToPosition(catPos);focusAt(right,itemPos);}}
 void loadCats(){String url=api("get_"+prefix()+"_categories","");msg("Loading categories…");task(()->parse(new JSONArray(get(url)),"category_id","category_name"),a->{cats=new ArrayList<>();cats.add(new Item("","All",""));if(page.equals("Live TV"))cats.add(new Item("@fav","Favourites",""));cats.addAll(a);catAdapter();focusAt(left,0);loadItems();});}
 void loadItems(){clearGuideFocus();ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);if(previewGuideJob!=null)previewGuideJob.cancel(true);release();selected=null;epgToken++;if(epg!=null)epg.setText("Programme information");if(preview!=null){preview.removeAllViews();TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));}int req=++loadToken;String k=prefix(),c=cat.equals("@fav")?"":cat;String url=api(k.equals("series")?"get_series":"get_"+k+"_streams",c.isEmpty()?"":"&category_id="+enc(c));if(page.equals("Live TV")&&right!=null)right.setAdapter(new Rows(Collections.emptyList(),this::pick));msg("Loading…");task(()->parse(new JSONArray(get(url)),k.equals("series")?"series_id":"stream_id","name"),a->{if(req!=loadToken)return;items=a;if(cat.equals("@fav")){Set<String> f=prefs.getStringSet("fav",Collections.emptySet());items.removeIf(i->!f.contains(i.id));}setItemsAdapter(items,this::pick);msg(items.size()+" titles");});}
 void search(){EditText e=new EditText(this);e.setSingleLine();styleInput(e);new AlertDialog.Builder(this).setTitle("Search current list").setView(e).setPositiveButton("Search",(d,w)->{String q=e.getText().toString().toLowerCase(Locale.ROOT);List<Item>a=new ArrayList<>();for(Item i:items)if(i.name.toLowerCase(Locale.ROOT).contains(q))a.add(i);setItemsAdapter(a,(i,p)->pick(i,items.indexOf(i)));focusAt(right,0);}).setNegativeButton("Cancel",null).show();}
 void pick(Item i,int p){itemPos=p;if(page.equals("Live TV")){if(selected!=null&&selected.id.equals(i.id)&&player!=null){playFull(i);return;}selected=i;attach(preview,i,false);loadEpg(i);return;}if(page.equals("Series")&&seriesId.isEmpty()){episodes(i);return;}playFull(i);}
 void episodes(Item s){int req=++loadToken;String url=api("get_series_info","&series_id="+enc(s.id));task(()->{JSONObject j=new JSONObject(get(url)).optJSONObject("episodes");List<Item>a=new ArrayList<>();if(j!=null){List<String> keys=new ArrayList<>();j.keys().forEachRemaining(keys::add);Collections.sort(keys);for(String k:keys){JSONArray e=j.optJSONArray(k);if(e!=null)for(int n=0;n<e.length();n++){JSONObject o=e.optJSONObject(n);if(o!=null&&!o.optString("id").isEmpty())a.add(new Item(o.optString("id"),"S"+k+" E"+o.optString("episode_num")+" · "+o.optString("title"),o.optString("container_extension","mp4")));}}}return a;},a->{if(req!=loadToken)return;seriesId=s.id;items=a;setItemsAdapter(items,this::pick);msg(s.name);focusAt(right,0);});}
 static String epgText(String value){
  if(value==null||value.isEmpty()||value.equals("null"))return "";
  if(value.length()>=4&&value.matches("[A-Za-z0-9+/]+={0,2}")&&value.length()%4==0)try{
   byte[] bytes=android.util.Base64.decode(value,android.util.Base64.DEFAULT);
   String decoded=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
   boolean printable=!decoded.trim().isEmpty();for(int n=0;n<decoded.length();n++)if(Character.isISOControl(decoded.charAt(n))&&!Character.isWhitespace(decoded.charAt(n)))printable=false;
   if(printable)return decoded;
  }catch(Exception ignored){}
  return value;
 }
 static long epgEpoch(JSONObject o,String zone,String[] numeric,String[] dates){
  for(String k:numeric){long v=o.optLong(k,0);if(v>0){if(v<100000000000L)v*=1000;if(v>=946684800000L&&v<4102444800000L)return v;}}
  for(String k:dates){String v=o.optString(k,"").trim();if(v.isEmpty())continue;
   if(v.matches("[0-9]{10,13}"))try{long t=Long.parseLong(v);return t<100000000000L?t*1000:t;}catch(Exception ignored){}
   if(v.endsWith("Z")||v.matches(".*[+-][0-9]{2}:[0-9]{2}$"))try{java.text.SimpleDateFormat iso=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);iso.setLenient(false);return iso.parse(v.replace(' ','T')).getTime();}catch(Exception ignored){}
   if(!zone.isEmpty()&&Arrays.asList(TimeZone.getAvailableIDs()).contains(zone))try{
    java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone(zone));return f.parse(v.replace('T',' ')).getTime();
   }catch(Exception ignored){}
  }return 0;
 }
 static List<Programme> parseGuide(String raw,String zone)throws Exception{
  Object root=new JSONTokener(raw).nextValue();JSONArray a=root instanceof JSONArray?(JSONArray)root:root instanceof JSONObject?((JSONObject)root).optJSONArray("epg_listings"):null;
  if(a==null&&root instanceof JSONObject)a=((JSONObject)root).optJSONArray("epg_list");
  List<Programme> out=new ArrayList<>();if(a==null)return out;
  Set<String> seen=new HashSet<>();
  for(int n=0;n<a.length()&&out.size()<1200;n++){JSONObject o=a.optJSONObject(n);if(o==null)continue;Programme p=new Programme();
   p.title=epgText(o.optString("title",""));if(p.title.isEmpty())continue;
   p.description=epgText(o.optString("description",""));
   p.rawStart=o.optString("start","");p.rawEnd=o.optString("end",o.optString("stop",""));
   p.start=epgEpoch(o,zone,new String[]{"start_timestamp","start_ts"},new String[]{"start","start_time"});
   p.end=epgEpoch(o,zone,new String[]{"stop_timestamp","end_timestamp","stop_ts"},new String[]{"end","stop","end_time"});
   String key=p.title+"|"+p.start+"|"+p.rawStart;if(seen.add(key))out.add(p);
  }
  Collections.sort(out,(x,y)->{if(x.start>0&&y.start>0)return Long.compare(x.start,y.start);if(x.start>0)return -1;if(y.start>0)return 1;return x.rawStart.compareTo(y.rawStart);});return out;
 }
 static String formatGuideTime(long time,String raw){
  if(time>0)return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(time));
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|[ T])([0-2][0-9]:[0-5][0-9])").matcher(raw==null?"":raw);return m.find()?m.group(1)+"*":"--:--";
 }
 static String guideTimes(Programme p){return formatGuideTime(p.start,p.rawStart)+" – "+formatGuideTime(p.end,p.rawEnd);}
 static String guideDate(Programme p){if(p.start>0)return new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(p.start));return p.rawStart.length()>=10?p.rawStart.substring(0,10)+"*":"Date unavailable";}
 String guideKey(Item i,boolean extended){return server+"|"+user+"|"+i.id+"|"+extended;}
 static String getGuide(String url)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
  connection.setConnectTimeout(5000);connection.setReadTimeout(8000);connection.setRequestProperty("User-Agent","SharkSky/5");
  try{if(connection.getResponseCode()!=200)throw new IOException("Guide HTTP error");
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Guide cancelled");if(out.size()+n>4*1024*1024)throw new IOException("Guide too large");out.write(buffer,0,n);}return out.toString("UTF-8");}
  }finally{connection.disconnect();}
 }
 GuideData fetchGuide(String fullUrl,String shortUrl,String zone,boolean extended)throws Exception{
  List<Programme> shows=new ArrayList<>();
  if(extended)try{shows=parseGuide(getGuide(fullUrl),zone);if(!shows.isEmpty())return new GuideData(shows,true);}catch(Exception ignored){}
  if(Thread.currentThread().isInterrupted())throw new InterruptedException();
  shows=parseGuide(getGuide(shortUrl),zone);return new GuideData(shows,false);
 }
 void clearGuideFocus(){if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;highlightedGuideItem=null;}
 void highlightGuide(Item i,GuideData cached){
  if(i==null||epg==null||full||!page.equals("Live TV"))return;
  if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);
  if(previewGuideJob!=null)previewGuideJob.cancel(true);epgToken++;
  highlightedGuideItem=i;
  GuideData data=cached!=null?cached:guideCache.get(guideKey(i,false));
  if(data!=null)paintPreviewGuide(i,data);else epg.setText(i.name+"\nLoading programme information…");
  int token=screenToken;pendingGuideFocus=()->{pendingGuideFocus=null;if(token==screenToken&&!full&&highlightedGuideItem==i&&epg!=null)loadEpg(i);};
  ui.postDelayed(pendingGuideFocus,250);
 }
 void paintPreviewGuide(Item i,GuideData data){
  if(epg==null||i==null||data==null)return;long now=System.currentTimeMillis();Programme current=null,next=null;
  for(Programme p:data.shows){if(p.start>0&&p.end>0&&p.start<=now&&now<p.end)current=p;else if(p.start>now&&next==null)next=p;}
  StringBuilder b=new StringBuilder(i.name);
  if(current!=null)b.append("\nNOW ").append(guideTimes(current)).append("\n").append(current.title);
  if(next!=null)b.append("\nNEXT ").append(guideTimes(next)).append("\n").append(next.title);
  if(current==null&&next==null){int n=0;for(Programme p:data.shows){if(p.end>0&&p.end<=now)continue;b.append("\n").append(guideTimes(p)).append("  ").append(p.title);if(++n==3)break;}if(n==0)b.append("\nNo current programme information");}
  epg.setText(b.toString());
 }
 void loadEpg(Item i){
  if(epg==null||i==null)return;highlightedGuideItem=i;if(pendingGuideFocus!=null){ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;}ui.removeCallbacks(refreshEpg);ui.postDelayed(refreshEpg,60000);
  int req=++epgToken,token=screenToken;String key=guideKey(i,false),fullUrl=api("get_simple_data_table","&stream_id="+enc(i.id)),shortUrl=api("get_short_epg","&stream_id="+enc(i.id)+"&limit=12"),zone=prefs.getString("epg_zone","");
  if(previewGuideJob!=null)previewGuideJob.cancel(true);
  GuideData cached=guideCache.get(key);if(cached!=null){paintPreviewGuide(i,cached);if(System.currentTimeMillis()-cached.fetched<180000)return;}else epg.setText(i.name+"\nLoading programme information…");
  previewGuideJob=epgIo.submit(()->{try{GuideData data=fetchGuide(fullUrl,shortUrl,zone,false);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(token!=screenToken||req!=epgToken||isFinishing())return;guideCache.put(key,data);paintPreviewGuide(i,data);});}
   catch(Exception e){ui.post(()->{if(token==screenToken&&req==epgToken&&epg!=null&&cached==null)epg.setText(i.name+"\nProgramme information unavailable");});}});
 }
 static final long GUIDE_SLOT=30*60*1000L;
 static Programme currentProgramme(GuideData data,long now){if(data!=null)for(Programme p:data.shows)if(p.start>0&&p.start<=now&&p.end>now)return p;return null;}
 static Programme nextProgramme(GuideData data,long now){if(data!=null)for(Programme p:data.shows)if(p.start>now)return p;return null;}
 class LiveGuideRows extends RecyclerView.Adapter<LiveGuideRows.H>{
  final List<Item> data;final Pick pick;final boolean timeline;long from;final TextView info;boolean disposed=false;
  LiveGuideRows(List<Item>d,Pick p,boolean grid,long start,TextView details){data=d;pick=p;timeline=grid;from=start;info=details;}
  class H extends RecyclerView.ViewHolder{
   LinearLayout root,slots;TextView name,sub;Item item;Future<?> job;GuideData guide;int request;
   H(LinearLayout r,TextView n,TextView s,LinearLayout cells){super(r);root=r;name=n;sub=s;slots=cells;}
  }
  @Override public H onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout root=timeline?row():col();root.setBackground(focus());root.setFocusable(true);root.setClickable(true);root.setPadding(dp(8),dp(3),dp(8),dp(3));
   TextView name=text("",14);name.setTypeface(uiMedium);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   TextView sub=text("",11);sub.setSingleLine();sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
   LinearLayout slots=row();if(timeline){root.addView(name,new LinearLayout.LayoutParams(0,-1,1.15f));root.addView(slots,new LinearLayout.LayoutParams(0,-1,3f));}
   else{root.addView(name,new LinearLayout.LayoutParams(-1,0,1));root.addView(sub,new LinearLayout.LayoutParams(-1,0,1));}
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(timeline?58:44));lp.setMargins(dp(2),dp(1),dp(2),dp(2));root.setLayoutParams(lp);
   H h=new H(root,name,sub,slots);root.setOnClickListener(v->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});
   root.setOnFocusChangeListener((v,focused)->{if(focused&&h.item!=null){if(timeline)describe(h);else highlightGuide(h.item,h.guide);}});return h;
  }
  void describe(H h){if(info==null||h.item==null)return;Programme p=currentProgramme(h.guide,System.currentTimeMillis());if(p==null)p=nextProgramme(h.guide,System.currentTimeMillis());info.setText(h.item.name+(p==null?"  •  Programme information unavailable":"  •  "+guideTimes(p)+"  "+p.title+(p.description.isEmpty()?"":"\n"+p.description)));}
  void paint(H h,GuideData d,boolean loading){
   h.guide=d;long now=System.currentTimeMillis();Programme p=currentProgramme(d,now),next=nextProgramme(d,now);
   if(!timeline){h.sub.setText(p!=null?guideTimes(p)+"  "+p.title:next!=null?"Next "+guideTimes(next)+"  "+next.title:loading?"Loading guide…":"No current listings");if(d!=null&&h.root.hasFocus()&&!full&&highlightedGuideItem==h.item)paintPreviewGuide(h.item,d);return;}
   h.slots.removeAllViews();long end=from+6*GUIDE_SLOT,cursor=from;
   if(d!=null)for(Programme show:d.shows){if(show.start<=0||show.end<=show.start||show.end<=cursor||show.start>=end)continue;long a=Math.max(cursor,show.start),b=Math.min(end,show.end);if(a>cursor)cell(h,"",a-cursor,false);if(b>a){cell(h,show.title,b-a,show.start<=now&&show.end>now);cursor=b;}if(cursor>=end)break;}
   if(cursor<end)cell(h,cursor==from?(loading?"Loading guide…":"No listings for this time"):"",end-cursor,false);
   if(h.root.hasFocus())describe(h);
  }
  void cell(H h,String title,long duration,boolean now){
   TextView t=text(title,12);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(5),0,dp(4),0);
   t.setBackground(bg(now?0xff254b73:0xff111e35,0xff0b1529,0xff314764,1));
   h.slots.addView(t,new LinearLayout.LayoutParams(0,-1,(float)duration/GUIDE_SLOT));
  }
  void bindGuide(H h,Item i,boolean force){
   if(h.job!=null)h.job.cancel(true);int request=++h.request;rowGuideIo.purge();h.item=i;h.name.setText(i.name);
   String key=guideKey(i,false),zone=prefs.getString("epg_zone",""),fullUrl=api("get_simple_data_table","&stream_id="+enc(i.id)),shortUrl=api("get_short_epg","&stream_id="+enc(i.id)+"&limit=16");
   GuideData d=guideCache.get(key);if(timeline){GuideData ext=guideCache.get(guideKey(i,true));if(ext!=null)d=ext;}
   final boolean extended=timeline&&(from>System.currentTimeMillis()+2*GUIDE_SLOT||from+6*GUIDE_SLOT<System.currentTimeMillis());
   if(!force&&d!=null&&System.currentTimeMillis()-d.fetched<180000&&(!extended||d.extended)){paint(h,d,false);return;}
   paint(h,d,d==null);int token=screenToken;final GuideData previous=d;
   try{h.job=rowGuideIo.submit(()->{try{GuideData result=fetchGuide(fullUrl,shortUrl,zone,extended);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(disposed||token!=screenToken||h.item!=i||h.request!=request||isFinishing())return;guideCache.put(extended?guideKey(i,true):key,result);paint(h,result,false);});}
   catch(Exception e){ui.post(()->{if(!disposed&&token==screenToken&&h.item==i&&h.request==request&&!isFinishing())paint(h,previous,false);});}});}catch(RejectedExecutionException e){h.job=null;paint(h,previous,false);}
  }
  @Override public void onBindViewHolder(H h,int position){bindGuide(h,data.get(position),false);}
  @Override public void onViewRecycled(H h){h.request++;if(h.job!=null)h.job.cancel(true);h.item=null;h.guide=null;super.onViewRecycled(h);}
  @Override public void onViewDetachedFromWindow(H h){h.request++;if(h.job!=null)h.job.cancel(true);h.job=null;super.onViewDetachedFromWindow(h);}
  @Override public void onViewAttachedToWindow(H h){super.onViewAttachedToWindow(h);if(h.item!=null&&(h.job==null||h.job.isCancelled()))bindGuide(h,h.item,false);}
  @Override public void onDetachedFromRecyclerView(RecyclerView list){disposed=true;super.onDetachedFromRecyclerView(list);}
  @Override public int getItemCount(){return data.size();}
  void refreshVisible(RecyclerView list){for(int n=0;n<list.getChildCount();n++){RecyclerView.ViewHolder holder=list.getChildViewHolder(list.getChildAt(n));if(holder instanceof LiveGuideRows.H){H h=(H)holder;if(h.item!=null)bindGuide(h,h.item,false);}}}
 }
 void openGuide(){
  if(!page.equals("Live TV")||items.isEmpty()){msg("Select a Live TV category first.");return;}
  if(guideDialog!=null&&guideDialog.isShowing())return;
  Dialog dialog=new Dialog(this,R.style.SharkTheme_Fullscreen);guideDialog=dialog;
  LinearLayout layout=col();layout.setPadding(dp(16),dp(10),dp(16),dp(8));layout.setBackground(screenBackground());
  TextView title=text("TV Guide",22);layout.addView(title,new LinearLayout.LayoutParams(-1,dp(32)));
  TextView info=text("Select a channel to see its programme schedule",15);info.setMaxLines(3);layout.addView(info,new LinearLayout.LayoutParams(-1,dp(55)));
  LinearLayout header=row();header.setPadding(dp(10),0,dp(10),0);layout.addView(header,new LinearLayout.LayoutParams(-1,dp(31)));
  RecyclerView channels=list();layout.addView(channels,new LinearLayout.LayoutParams(-1,0,1));
  long now=System.currentTimeMillis();final long[] start={now-now%GUIDE_SLOT};
  List<Item> channelList=new ArrayList<>(items);
  LiveGuideRows adapter=new LiveGuideRows(channelList,(item,n)->{dialog.dismiss();openChannelGuide(item);},true,start[0],info);channels.setAdapter(adapter);
  Runnable headerUpdate=()->{header.removeAllViews();TextView label=text(new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(start[0])),13);header.addView(label,new LinearLayout.LayoutParams(0,-1,1.15f));LinearLayout times=row();header.addView(times,new LinearLayout.LayoutParams(0,-1,3f));for(int i=0;i<6;i++){TextView t=text(formatGuideTime(start[0]+i*GUIDE_SLOT,""),13);t.setTextColor(UI_ACCENT);times.addView(t,new LinearLayout.LayoutParams(0,-1,1));}};
  Runnable change=()->{adapter.from=start[0];headerUpdate.run();adapter.refreshVisible(channels);};
  LinearLayout actions=row();button(actions,"Earlier",()->{start[0]-=3*GUIDE_SLOT;change.run();});button(actions,"Now",()->{long t=System.currentTimeMillis();start[0]=t-t%GUIDE_SLOT;change.run();});button(actions,"Later",()->{start[0]+=3*GUIDE_SLOT;change.run();});button(actions,"Close",dialog::dismiss);layout.addView(actions);
  headerUpdate.run();dialog.setContentView(layout);dialog.setOnDismissListener(d->{adapter.disposed=true;channels.setAdapter(null);if(guideDialog==dialog)guideDialog=null;});dialog.show();
  if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);}
  focusAt(channels,Math.min(Math.max(itemPos,0),channelList.size()-1));
 }
 void openChannelGuide(Item initial){
  if(!page.equals("Live TV")||items.isEmpty()){msg("Select a Live TV category first.");return;}
  if(guideDialog!=null&&guideDialog.isShowing())return;
  Dialog dialog=new Dialog(this,R.style.SharkTheme_Fullscreen);guideDialog=dialog;
  LinearLayout layout=col();layout.setPadding(dp(16),dp(12),dp(16),dp(10));layout.setBackground(screenBackground());
  TextView heading=text("TV Guide  •  Local time",22);layout.addView(heading,new LinearLayout.LayoutParams(-1,dp(38)));
  LinearLayout content=row();layout.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  RecyclerView channels=list();content.addView(channels,new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout schedule=col();schedule.setPadding(dp(8),0,0,0);content.addView(schedule,new LinearLayout.LayoutParams(0,-1,2));
  TextView label=text("Select a channel",17);schedule.addView(label,new LinearLayout.LayoutParams(-1,dp(44)));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout entries=col();scroll.addView(entries);schedule.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  final Item[] choice={initial};
  List<Item> channelList=new ArrayList<>(items);
  channels.setAdapter(new Rows(channelList,(item,n)->{choice[0]=item;loadFullGuide(dialog,item,label,entries,scroll,false);}));
  LinearLayout actions=row();button(actions,"Refresh guide",()->loadFullGuide(dialog,choice[0],label,entries,scroll,true));button(actions,"Back to grid",()->{dialog.dismiss();openGuide();});layout.addView(actions);
  dialog.setContentView(layout);dialog.setOnDismissListener(d->{guideRequest++;if(fullGuideJob!=null)fullGuideJob.cancel(true);if(guideDialog==dialog)guideDialog=null;});dialog.show();
  if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);}
  int index=0;for(int n=0;n<channelList.size();n++)if(channelList.get(n).id.equals(choice[0].id)){index=n;break;}
  focusAt(channels,index);loadFullGuide(dialog,choice[0],label,entries,scroll,false);
 }
 void showFullGuide(Item item,GuideData data,TextView label,LinearLayout entries,ScrollView scroll){
  label.setText(item.name+"  •  "+(data.extended?"Full schedule":"Available listings")+"  •  * Provider time");
  entries.removeAllViews();long now=System.currentTimeMillis();String lastDate="";View firstCurrent=null;
  for(Programme p:data.shows){String day=guideDate(p);if(!day.equals(lastDate)){TextView header=text(day,15);header.setTextColor(UI_ACCENT);header.setTypeface(uiMedium);entries.addView(header,new LinearLayout.LayoutParams(-1,dp(29)));lastDate=day;}
   boolean current=p.start>0&&p.end>0&&p.start<=now&&now<p.end;
   TextView entry=text((current?"NOW  ":"")+guideTimes(p)+"  "+p.title+(p.description.isEmpty()?"":"\n"+p.description),14);
   entry.setMaxLines(5);entry.setPadding(dp(10),dp(7),dp(10),dp(7));entry.setFocusable(true);entry.setBackground(focus());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(2),0,dp(2));entries.addView(entry,lp);
   if(firstCurrent==null&&(current||p.start>now))firstCurrent=entry;
  }
  if(data.shows.isEmpty())entries.addView(text("No programme schedule provided for this channel.",15));
  View target=firstCurrent;scroll.post(()->scroll.scrollTo(0,target==null?0:target.getTop()));
 }
 void loadFullGuide(Dialog dialog,Item item,TextView label,LinearLayout entries,ScrollView scroll,boolean force){
  int req=++guideRequest;if(fullGuideJob!=null)fullGuideJob.cancel(true);
  String key=guideKey(item,true),fullUrl=api("get_simple_data_table","&stream_id="+enc(item.id)),shortUrl=api("get_short_epg","&stream_id="+enc(item.id)+"&limit=24"),zone=prefs.getString("epg_zone","");
  GuideData cached=guideCache.get(key);if(!force&&cached!=null&&System.currentTimeMillis()-cached.fetched<180000){showFullGuide(item,cached,label,entries,scroll);return;}
  label.setText(item.name+"  •  Loading schedule…");entries.removeAllViews();
  fullGuideJob=epgIo.submit(()->{try{GuideData data=fetchGuide(fullUrl,shortUrl,zone,true);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(req!=guideRequest||!dialog.isShowing()||isFinishing())return;guideCache.put(key,data);showFullGuide(item,data,label,entries,scroll);});}
   catch(Exception e){ui.post(()->{if(req!=guideRequest||!dialog.isShowing())return;if(cached!=null)showFullGuide(item,cached,label,entries,scroll);else label.setText(item.name+"  •  Schedule unavailable. Try Refresh guide.");});}});
 }

 void fav(){if(selected==null)return;Set<String>s=new HashSet<>(prefs.getStringSet("fav",Collections.emptySet()));if(!s.add(selected.id)){s.remove(selected.id);msg("Removed favourite");}else msg("Favourite saved");prefs.edit().putStringSet("fav",s).apply();}
 String stream(Item i){String k=page.equals("Live TV")?"live":page.equals("Movies")?"movie":"series";String e=k.equals("live")?liveExt:i.ext;if(!e.matches("[a-zA-Z0-9]{1,8}"))e="mp4";return server+"/"+k+"/"+enc(user)+"/"+enc(pass)+"/"+enc(i.id)+"."+e;}
 String decoderKey(Item i){return UUID.nameUUIDFromBytes((server+"|"+page+"|"+i.id+"|"+i.ext).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
 android.content.SharedPreferences decoderPrefs(){return getSharedPreferences("decoder_recovery_v1",0);}
 boolean rememberedDecoder(Item i){long when=decoderPrefs().getLong(decoderKey(i),0);long age=System.currentTimeMillis()-when;return when>0&&age>=0&&age<7L*24*60*60*1000;}
 void rememberDecoder(Item i){android.content.SharedPreferences d=decoderPrefs();android.content.SharedPreferences.Editor e=d.edit();Map<String,?> all=d.getAll();if(all.size()>=128){String oldest=null;long time=Long.MAX_VALUE;for(Map.Entry<String,?> entry:all.entrySet())if(entry.getValue() instanceof Long&&(Long)entry.getValue()<time){oldest=entry.getKey();time=(Long)entry.getValue();}if(oldest!=null)e.remove(oldest);}e.putLong(decoderKey(i),System.currentTimeMillis()).apply();}
 void attach(FrameLayout host,Item i,boolean fullscreen){boolean cached=fullscreen&&!page.equals("Live TV")&&rememberedDecoder(i);attachExo(host,i,fullscreen,cached?1:0,0,cached);}
 static boolean isHdr(Format f){return f!=null&&(MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType)||(f.colorInfo!=null&&(f.colorInfo.colorTransfer==C.COLOR_TRANSFER_ST2084||f.colorInfo.colorTransfer==C.COLOR_TRANSFER_HLG||f.colorInfo.hdrStaticInfo!=null)));}
 void attachExo(FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst){
  release();host.removeAllViews();
  PlayerView v=new PlayerView(this);v.setUseController(fullscreen);v.setControllerAutoShow(fullscreen);
  if(!fullscreen&&page.equals("Live TV")&&host==preview&&findViewById(android.R.id.content) instanceof FrameLayout){
   FrameLayout root=(FrameLayout)findViewById(android.R.id.content);
   liveSurfaceHost=new FrameLayout(this);liveSurfaceHost.setBackgroundColor(Color.BLACK);liveSurfaceHost.setFocusable(false);liveSurfaceHost.setClickable(false);liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
   v.setFocusable(false);liveSurfaceHost.addView(v,new FrameLayout.LayoutParams(-1,-1));
   root.addView(liveSurfaceHost,new FrameLayout.LayoutParams(1,1,Gravity.TOP|Gravity.LEFT));
   livePreviewLayout=(view,l,t,r,b,ol,ot,or,ob)->{if(!full)positionLiveSurface();};
   preview.addOnLayoutChangeListener(livePreviewLayout);positionLiveSurface();preview.post(()->{if(!full)positionLiveSurface();});
  }else host.addView(v,new FrameLayout.LayoutParams(-1,-1));
  final String range=prefs.getString("dynamic_range","Auto");
  final java.util.concurrent.atomic.AtomicInteger outputTransfer=new java.util.concurrent.atomic.AtomicInteger(0);
  final java.util.concurrent.atomic.AtomicBoolean sourceHdr=new java.util.concurrent.atomic.AtomicBoolean(false);
  DefaultRenderersFactory factory=new DefaultRenderersFactory(this){
   @Override protected androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory getCodecAdapterFactory(){
    final androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory delegate=super.getCodecAdapterFactory();
    if(!range.equals("Prefer SDR"))return delegate;
    return new androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory(){
     @Override public androidx.media3.exoplayer.mediacodec.MediaCodecAdapter createAdapter(androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration config)throws java.io.IOException{
      boolean isVideo=config.format.sampleMimeType!=null&&config.format.sampleMimeType.startsWith("video/");
      if(!isVideo)return delegate.createAdapter(config);
      sourceHdr.set(isHdr(config.format));outputTransfer.set(0);
      if(Build.VERSION.SDK_INT>=31){
       config.mediaFormat.setInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER_REQUEST,android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
       android.util.Log.i("SharkPlayback","SDR output requested; HDR source="+sourceHdr.get());
      }else if(sourceHdr.get())throw new java.io.IOException("HDR-to-SDR decoder tone mapping requires Android 12 or later");
      return new androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter(delegate.createAdapter(config)){
       @Override public android.media.MediaFormat getOutputFormat(){
        android.media.MediaFormat f=super.getOutputFormat();
        int transfer=f.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER)?f.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER):0;
        outputTransfer.set(transfer);
        android.util.Log.i("SharkPlayback","Decoder output color transfer="+transfer+"; SDR requested");
        return f;
       }
      };
     }
    };
   }
  }.setEnableDecoderFallback(true);
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
  video=v;player=p;playbackItem=i;activeStreamId=i.id;v.setPlayer(p);v.setResizeMode(prefs.getInt("resize_mode",0));
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
  class RecoveryListener implements Player.Listener {
   boolean recovering,firstFrame,rangeSelected;
   long audioStartedAt=-1;
   final Runnable watch=new Runnable(){@Override public void run(){
    if(player!=p||recovering||firstFrame||stopped||token!=screenToken)return;
    if(p.isPlaying()&&p.getPlaybackState()==Player.STATE_READY){
     long now=android.os.SystemClock.elapsedRealtime();
     if(audioStartedAt<0)audioStartedAt=now;
     if(now-audioStartedAt>=6500){
      boolean videoExists=false;
      for(Tracks.Group group:p.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO){videoExists=true;break;}
      if(videoExists){android.util.Log.w("SharkPlayback","No first video frame while playing; attempt="+attempt+" decoder="+activeDecoder);recover("No video frame",position);return;}
     }
    }else audioStartedAt=-1;
    ui.postDelayed(this,750);
   }};
   void startWatch(){if(vod)ui.postDelayed(watch,750);}
   @Override public void onTracksChanged(Tracks tracks){
    if(player!=p||recovering||rangeSelected||!range.equals("Prefer HDR"))return;
    boolean selectedDesired=false;Tracks.Group candidate=null;int index=-1;long score=-1;
    boolean wantHdr=range.equals("Prefer HDR");
    for(Tracks.Group group:tracks.getGroups()){
     if(group.getType()!=C.TRACK_TYPE_VIDEO)continue;
     for(int n=0;n<group.length;n++){Format f=group.getTrackFormat(n);boolean desired=isHdr(f)==wantHdr;if(!desired||!group.isTrackSupported(n))continue;
      if(group.isTrackSelected(n))selectedDesired=true;
      long pixels=(long)Math.max(0,f.width)*Math.max(0,f.height);
      if(pixels>score){score=pixels;candidate=group;index=n;}
     }
    }
    if(selectedDesired){rangeSelected=true;return;}
    if(candidate!=null){rangeSelected=true;p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(candidate.getMediaTrackGroup(),index)).build());}
   }

   @Override public void onRenderedFirstFrame(){
    if(player!=p||recovering)return;
    firstFrame=true;ui.removeCallbacks(watch);
    if(vod&&attempt==1)rememberDecoder(i);
    if((full||fullscreen)&&prefs.getBoolean("show_codec_info",false)){
     Format f=p.getVideoFormat();
     String details=(activeDecoder.isEmpty()?"Decoder unavailable":activeDecoder)+(f==null?"":" · "+f.width+"×"+f.height+(f.bitrate>0?" · "+String.format(Locale.US,"%.1f Mb/s",f.bitrate/1000000.0):""));
     if(range.equals("Prefer SDR")){
      int tr=outputTransfer.get();
      details+=" · "+(tr==android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO?"SDR decoder output":tr==android.media.MediaFormat.COLOR_TRANSFER_ST2084||tr==android.media.MediaFormat.COLOR_TRANSFER_HLG?"HDR decoder output (SDR request unsupported)":"SDR requested; output unverified");
     }else details+=" · "+(isHdr(f)?"HDR source":"SDR/unknown source");
     msg(details);
    }
    if(range.equals("Prefer SDR")){
     int tr=outputTransfer.get();
     if(tr==android.media.MediaFormat.COLOR_TRANSFER_ST2084||tr==android.media.MediaFormat.COLOR_TRANSFER_HLG)msg("This decoder still reports HDR output. SDR conversion is not supported by this decoder.");
     else if(sourceHdr.get()&&tr==0)msg("SDR requested; decoder did not report its output range. Check the TV picture information.");
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
    if(range.equals("Prefer SDR")&&Build.VERSION.SDK_INT<31&&(isHdr(fmt)||sourceHdr.get())&&decoderError){ui.removeCallbacks(watch);msg("HDR-to-SDR requires a compatible Android 12+ decoder. Select Auto to play this HDR source.");return;}
    if(!vod||!decoderError||stopped){ui.removeCallbacks(watch);msg("Playback failed: "+e.getErrorCodeName());return;}
    recover(e.getErrorCodeName(),firstFrame?Math.max(0,p.getCurrentPosition()):position);
   }
   void recover(String reason,long resume){
    if(player!=p||recovering||stopped)return;
    recovering=true;ui.removeCallbacks(watch);
    if(attempt==1)decoderPrefs().edit().remove(decoderKey(i)).apply();
    msg(attempt==0&&!cachedFirst?"Retrying compatible hardware decoder…":attempt==1&&cachedFirst?"Retrying standard decoder…":"Trying Android native playback…");
    release();
    ui.postDelayed(()->{
     if(token!=screenToken||!full||stopped||isFinishing()||player!=null||nativeVideo!=null)return;
     if(attempt==0&&!cachedFirst)attachExo(host,i,true,1,resume,false);
     else if(attempt==1&&cachedFirst)attachExo(host,i,true,0,resume,true);
     else if(range.equals("Prefer SDR")){msg("Playback failed in SDR mode. Select Auto to allow native fallback.");}else attachNative(host,i,resume);
    },500);
   }
  }
  RecoveryListener listener=new RecoveryListener();p.addListener(listener);
  p.setMediaItem(m.build());if(vod&&position>0)p.seekTo(position);p.setPlaybackSpeed(1f);p.prepare();p.play();listener.startWatch();if(fullscreen)addRangeButton(host,i,v);
 }
 void attachNative(FrameLayout host,Item i,long position){
  release();host.removeAllViews();
  VideoView n=new VideoView(this);nativeVideo=n;playbackItem=i;
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);host.addView(n,lp);
  MediaController controls=new MediaController(this);nativeControls=controls;controls.setAnchorView(n);n.setMediaController(controls);
  final boolean[] rendered={false};
  n.setOnInfoListener((mp,what,extra)->{if(nativeVideo==n&&what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){rendered[0]=true;android.util.Log.i("SharkPlayback","Native first frame");}return false;});
  n.setOnPreparedListener(mp->{if(nativeVideo!=n)return;if(position>0)n.seekTo((int)Math.min(position,Integer.MAX_VALUE));android.util.Log.i("SharkPlayback","Android native prepared "+mp.getVideoWidth()+"x"+mp.getVideoHeight());if(!stopped)n.start();
   ui.postDelayed(new Runnable(){int checks=0;@Override public void run(){if(nativeVideo!=n||rendered[0]||stopped||isFinishing())return;
    if(n.isPlaying()&&n.getCurrentPosition()>position+2000){if(++checks>=7){n.pause();msg("Video unavailable on this decoder. Try a compatible 4K version.");return;}}
    else checks=0;ui.postDelayed(this,1000);}},1000);
  });
  n.setOnErrorListener((mp,what,extra)->{if(nativeVideo==n){android.util.Log.w("SharkPlayback","Android native error="+what+"/"+extra);msg("Playback failed on both players ("+what+"/"+extra+")");}return true;});
  n.setVideoURI(Uri.parse(stream(i)));n.requestFocus();n.start();addRangeButton(host,i,null);
 }
 void removeRangeButton(){
  if(rangeButton!=null){ViewGroup group=(ViewGroup)rangeButton.getParent();if(group!=null)group.removeView(rangeButton);rangeButton=null;}
  if(video!=null)video.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)null);
 }
 String rangeLabel(){String r=prefs.getString("dynamic_range","Auto");return r.equals("Prefer SDR")?"SDR":r.equals("Prefer HDR")?"HDR":"Auto";}
 void addRangeButton(FrameLayout host,Item item,PlayerView playerView){
  removeRangeButton();
  TextView button=text("Picture  •  "+rangeLabel(),14);button.setTypeface(uiMedium);button.setGravity(Gravity.CENTER);button.setPadding(dp(14),0,dp(14),0);button.setBackground(focus());button.setFocusable(true);button.setClickable(true);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(40),Gravity.TOP|Gravity.RIGHT);lp.topMargin=dp(18);lp.rightMargin=dp(22);
  host.addView(button,lp);rangeButton=button;
  button.setOnClickListener(view->choosePlayerRange(item));
  if(playerView!=null){
   button.setVisibility(playerView.isControllerFullyVisible()?View.VISIBLE:View.GONE);
   playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{if(rangeButton==button)button.setVisibility(visibility);});
   button.setOnFocusChangeListener((v,focused)->{if(focused)playerView.showController();});
   playerView.showController();
  }
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(full&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&rangeButton!=null&&playbackItem!=null){
   if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){choosePlayerRange(playbackItem);return true;}
   if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&getCurrentFocus()!=rangeButton){
    if(video!=null)video.showController();rangeButton.setVisibility(View.VISIBLE);rangeButton.requestFocus();return true;
   }
   if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&getCurrentFocus()==rangeButton&&video!=null){video.requestFocus();video.showController();return true;}
  }return super.dispatchKeyEvent(event);
 }
 void choosePlayerRange(Item item){
  String[] choices={"Auto (source)","SDR (tone mapping)","HDR (prefer HDR source)"};
  String[] values={"Auto","Prefer SDR","Prefer HDR"};
  int current=Arrays.asList(values).indexOf(prefs.getString("dynamic_range","Auto"));
  new AlertDialog.Builder(this).setTitle("Video dynamic range").setSingleChoiceItems(choices,current,(dialog,n)->{
   dialog.dismiss();String range=values[n];if(range.equals(prefs.getString("dynamic_range","Auto")))return;
   prefs.edit().putString("dynamic_range",range).apply();
   if(!full||playbackItem!=item)return;
   if(liveSurfaceHost!=null&&page.equals("Live TV")){
    if(!exitLiveFullscreen())return;
    attach(preview,item,false);if(player!=null&&liveSurfaceHost!=null)enterLiveFullscreen();
   }else{
    long pos=player!=null?Math.max(0,player.getCurrentPosition()):nativeVideo!=null?Math.max(0,nativeVideo.getCurrentPosition()):0;
    View content=findViewById(android.R.id.content);
    if(content instanceof FrameLayout&&((FrameLayout)content).getChildCount()>0&&((FrameLayout)content).getChildAt(0) instanceof FrameLayout){
     FrameLayout host=(FrameLayout)((FrameLayout)content).getChildAt(0);
     boolean cached=!page.equals("Live TV")&&rememberedDecoder(item);
     attachExo(host,item,true,cached?1:0,pos,cached);
    }
   }
  }).setNegativeButton("Cancel",null).show();
 }
 void playFull(Item i){
  if(page.equals("Live TV")&&!full&&player!=null&&video!=null&&preview!=null&&liveSurfaceHost!=null&&video.getParent()==liveSurfaceHost&&i.id.equals(activeStreamId)&&player.getPlayerError()==null&&player.getPlaybackState()!=Player.STATE_IDLE&&player.getPlaybackState()!=Player.STATE_ENDED&&enterLiveFullscreen())return;
  release();screenToken++;loadToken++;epgToken++;full=true;status=null;FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.BLACK);setContentView(f);immersive();attach(f,i,true);if(video!=null)video.requestFocus();
 }
 void positionLiveSurface(){
  if(liveSurfaceHost==null||preview==null||full||preview.getWidth()<=0||preview.getHeight()<=0)return;
  View root=(View)liveSurfaceHost.getParent();if(root==null)return;
  int[] target=new int[2],origin=new int[2];preview.getLocationInWindow(target);root.getLocationInWindow(origin);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(preview.getWidth(),preview.getHeight(),Gravity.TOP|Gravity.LEFT);
  lp.leftMargin=target[0]-origin[0];lp.topMargin=target[1]-origin[1];
  liveSurfaceHost.setLayoutParams(lp);
 }
 boolean enterLiveFullscreen(){
  if(liveSurfaceHost==null||video==null||player==null)return false;
  liveReturnFocus=getCurrentFocus();liveReturnStatus=status;
  full=true;status=null;
  if(body!=null){liveBodyFocusMode=body.getDescendantFocusability();liveBodyAccessibility=body.getImportantForAccessibility();body.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
  // Do not detach the video surface or create a second PlayerView.
  liveSurfaceHost.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
  liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
  video.setFocusable(true);video.setUseController(true);video.setControllerAutoShow(true);
  player.setVolume(1f);if(!player.getPlayWhenReady())player.play();video.requestFocus();if(playbackItem!=null)addRangeButton(liveSurfaceHost,playbackItem,video);
  android.util.Log.i("SharkPlayback","Live fullscreen: same player and surface resized");return true;
 }
 boolean exitLiveFullscreen(){
  if(liveSurfaceHost==null||player==null||video==null||preview==null)return false;
  removeRangeButton();video.hideController();video.setUseController(false);video.setFocusable(false);
  liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
  player.setVolume(0f);full=false;status=liveReturnStatus;liveReturnStatus=null;
  positionLiveSurface();if(highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);else if(selected!=null&&epg!=null)loadEpg(selected);
  if(body!=null){body.setDescendantFocusability(liveBodyFocusMode);body.setImportantForAccessibility(liveBodyAccessibility);}
  View focus=liveReturnFocus;liveReturnFocus=null;if(focus!=null&&focus.isAttachedToWindow())focus.requestFocus();else if(right!=null)focusAt(right,itemPos);
  android.util.Log.i("SharkPlayback","Live preview: same player and surface resized");return true;
 }
 @Override public void onBackPressed(){if(full){if(exitLiveFullscreen())return;release();browser(true);return;}if(page.equals("Series")&&!seriesId.isEmpty()){seriesId="";loadItems();return;}if(!page.equals("Home")){home();return;}super.onBackPressed();}
 @Override protected void onStart(){super.onStart();stopped=false;ui.removeCallbacks(tickRows);if(page.equals("Live TV"))ui.postDelayed(tickRows,60000);if(page.equals("Live TV")&&!full&&highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);}
 @Override protected void onStop(){if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);stopped=true;super.onStop();if(player!=null)player.pause();if(nativeVideo!=null)nativeVideo.pause();}
 @Override protected void onDestroy(){screenToken++;if(guideDialog!=null&&guideDialog.isShowing())guideDialog.dismiss();release();io.shutdownNow();epgIo.shutdownNow();rowGuideIo.shutdownNow();updateIo.shutdownNow();posterIo.shutdownNow();posterCache.evictAll();ui.removeCallbacksAndMessages(null);super.onDestroy();}
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
  LinearLayout r=row();button(r,"Check / download",this::checkUpdate);button(r,"Install verified update",this::install);button(r,"Back",this::settings);body.addView(r);LinearLayout alt=row();button(alt,"Alternative installer",()->installUpdate(true));body.addView(alt);r.getChildAt(0).requestFocus();if(!updateBusy){File saved=updateFile();if(saved.isFile()){try{verifyUpdate(saved,null);}catch(Exception e){saved.delete();}}if(!saved.isFile())checkUpdate();}
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
 void install(){installUpdate(false);}
 @SuppressWarnings("deprecation") void installUpdate(boolean alternate){
  if(updateBusy){msg("Update is still downloading");return;}
  File apk=updateFile();if(!apk.isFile()){update();return;}
  try{verifyUpdate(apk,null);if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){updateMessage("Allow Shark Sky to install updates in Android settings, then return and select Install verified update.",100);try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));}return;}
   Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",apk);
   Intent in=new Intent(alternate?Intent.ACTION_INSTALL_PACKAGE:Intent.ACTION_VIEW);in.setDataAndType(uri,"application/vnd.android.package-archive");in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);in.setClipData(ClipData.newRawUri("Verified Shark Sky update",uri));
   try{startActivity(in);}catch(ActivityNotFoundException e){in.setAction(alternate?Intent.ACTION_VIEW:Intent.ACTION_INSTALL_PACKAGE);startActivity(in);}
   updateMessage("Approve the update in Android. If installation fails, the verified APK is saved for another attempt.",100);
  }catch(Exception e){updateMessage("Installation failed: "+updateError(e),0);msg("Installation failed: "+updateError(e));}
 }
}
