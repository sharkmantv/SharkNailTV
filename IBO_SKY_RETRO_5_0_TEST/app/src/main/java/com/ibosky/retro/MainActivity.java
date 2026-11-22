package com.ibosky.retro;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.util.Base64;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private static final int GOLD=Color.rgb(255,214,36), BLUE=Color.rgb(9,45,115), NAVY=Color.rgb(3,12,32), WHITE=Color.WHITE;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private LinearLayout root;
  private String server="",user="",pass="",currentKind="live",currentCategory="";
  private ExoPlayer player;
  private ValueAnimator pulse;
  private LinearLayout liveCategories,liveChannels;
  private PlayerView liveView;
  private TextView epgTextView,previewStatus,previewTitle;
  private String liveId="",liveName="",liveExt="ts",liveCat="";
  private boolean inLive=false,fullScreenLive=false;
  private int liveRequest=0;
  private final java.util.Map<String,JSONArray> liveCached=new java.util.HashMap<>();
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable bg(int c,int edge){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setStroke(dp(2),edge);d.setCornerRadius(dp(2));return d;}
  private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER);t.setTypeface(null,1);return t;}
  private void screen(String title){
    if(pulse!=null){pulse.cancel();pulse=null;}
    if(player!=null){player.release();player=null;}
    inLive=false;fullScreenLive=false;liveRequest++;
    root=new LinearLayout(this);root.setPadding(dp(18),dp(12),dp(18),dp(12));root.setOrientation(1);root.setBackgroundColor(NAVY);
    setContentView(root);
    TextView h=label("IBO SKY RETRO     •     "+title,25,GOLD);
    root.addView(h,new LinearLayout.LayoutParams(-1,dp(57)));
    View line=new View(this);line.setBackgroundColor(BLUE);root.addView(line,new LinearLayout.LayoutParams(-1,dp(3)));
  }
  private Button button(String text,Runnable task){
    Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(18);b.setBackground(bg(BLUE,GOLD));b.setFocusable(true);
    b.setOnFocusChangeListener((v,focus)->b.setBackground(bg(focus?0xff725000:BLUE,focus?WHITE:GOLD)));
    b.setOnClickListener(v->task.run());return b;
  }
  private void add(LinearLayout l,String text,Runnable task){Button b=button(text,task);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(57));p.setMargins(dp(5),dp(5),dp(5),dp(5));l.addView(b,p);}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    SharedPreferences s=getSharedPreferences("xtream",0);
    server=s.getString("server","");user=s.getString("user","");pass=s.getString("pass","");
    if(server.isEmpty()||user.isEmpty()||pass.isEmpty())login("");else home();
  }
  private void home(){
    screen("HOME");
    String[][] sections={{"LIVE TV","MOVIES","SERIES","SPORTS"},{"FAVOURITES","SEARCH","SETTINGS","EXIT"}};
    LinearLayout tiles=new LinearLayout(this);tiles.setOrientation(1);root.addView(tiles,new LinearLayout.LayoutParams(-1,0,1));
    for(int i=0;i<2;i++){
      LinearLayout row=new LinearLayout(this);row.setOrientation(0);tiles.addView(row,new LinearLayout.LayoutParams(-1,0,1));
      for(int j=0;j<4;j++){
        String title=sections[i][j];
        LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);cell.setBackground(bg(BLUE,GOLD));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.setMargins(dp(7),dp(7),dp(7),dp(7));row.addView(cell,cp);
        TextView body=label(title.equals("LIVE TV")?"◉":title.equals("MOVIES")?"▣":title.equals("SERIES")?"▤":"✦",65,GOLD);
        cell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        Button bar=button(title,()->route(title));bar.setTextSize(18);cell.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        cell.setOnClickListener(v->route(title));cell.setFocusable(true);
        cell.setOnFocusChangeListener((v,f)->cell.setBackground(bg(f?0xff314c8b:BLUE,f?WHITE:GOLD)));
      }
    }
    pulse=ValueAnimator.ofInt(135,255);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setRepeatMode(ValueAnimator.REVERSE);pulse.setDuration(900);
    pulse.addUpdateListener(v->{int a=(int)v.getAnimatedValue();for(int i=0;i<tiles.getChildCount();i++){LinearLayout row=(LinearLayout)tiles.getChildAt(i);for(int j=0;j<row.getChildCount();j++){View c=row.getChildAt(j);if(!c.isFocused())((GradientDrawable)c.getBackground()).setStroke(dp(2),Color.argb(a,255,214,36));}}});pulse.start();
    TextView tip=label("OK to open  •  SKY RETRO  •  MEDIA3 PLAYBACK",13,0xffa7c9fa);root.addView(tip,new LinearLayout.LayoutParams(-1,dp(30)));
  }
  private void route(String t){
    if(t.equals("LIVE TV"))liveTv();
    else if(t.equals("MOVIES"))categories("vod");
    else if(t.equals("SERIES"))categories("series");
    else if(t.equals("SPORTS"))listing("live","","sport");
    else if(t.equals("SETTINGS"))login("");
    else if(t.equals("EXIT"))finish();
    else {screen(t);add(root,"Back to home",this::home);TextView tip=label("This section is not implemented in the first test build.",20,WHITE);root.addView(tip,new LinearLayout.LayoutParams(-1,0,1));}
  }
  private EditText edit(LinearLayout layout,String hint,String value,int type){
    EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);e.setTextColor(WHITE);e.setHintTextColor(0xffaabbcc);e.setInputType(type);
    layout.addView(e,new LinearLayout.LayoutParams(-1,dp(60)));return e;
  }
  private void login(String status){
    screen("XTREAM LOGIN");
    LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(45),dp(8),dp(45),0);
    root.addView(form,new LinearLayout.LayoutParams(-1,-1));
    EditText u=edit(form,"Server URL (http://host:port)",server,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
    EditText n=edit(form,"Xtream username",user,InputType.TYPE_CLASS_TEXT);
    EditText p=edit(form,"Xtream password",pass,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    TextView msg=label(status,15,GOLD);form.addView(msg,new LinearLayout.LayoutParams(-1,dp(42)));
    add(form,"Connect",()->{
      String base=u.getText().toString().trim().replaceAll("/+$","");
      String usr=n.getText().toString().trim(),pw=p.getText().toString();
      if(base.isEmpty()||usr.isEmpty()||pw.isEmpty()){msg.setText("Fill in server, username and password.");return;}
      server=base;user=usr;pass=pw;msg.setText("Connecting...");
      api("",obj->{
        JSONObject info=obj instanceof JSONObject?((JSONObject)obj).optJSONObject("user_info"):null;
        if(info==null || !info.optString("auth","0").equals("1")){msg.setText("Xtream login refused. Check your details.");return;}
        getSharedPreferences("xtream",0).edit().putString("server",server).putString("user",user).putString("pass",pass).apply();home();
      },err->msg.setText(err));
    });
    add(form,"Back to home",this::home);
  }
  private interface Ok{void call(Object x);}
  private interface Fail{void call(String message);}
  private void api(String suffix,Ok ok,Fail fail){
    String base=server+"/player_api.php?username="+enc(user)+"&password="+enc(pass)+suffix;
    worker.execute(()->{
      try{
        HttpURLConnection c=(HttpURLConnection)new URL(base).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);
        int http=c.getResponseCode();if(http!=200)throw new IOException("Server HTTP "+http);
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]buf=new byte[8192];int count;
        try(InputStream in=c.getInputStream()){while((count=in.read(buf))!=-1){out.write(buf,0,count);if(out.size()>12000000)throw new IOException("Response too large");}}
        String result=out.toString("UTF-8").trim();
        Object json=result.startsWith("[")?new JSONArray(result):new JSONObject(result);
        runOnUiThread(()->ok.call(json));
      }catch(Exception e){runOnUiThread(()->fail.call("Network/API error: "+e.getMessage()));}
    });
  }
  private static String enc(String v){try{return java.net.URLEncoder.encode(v,"UTF-8");}catch(Exception e){return v;}}

  // Original three-column layout informed by IBO TV navigation, not extracted proprietary code.
  private LinearLayout column(LinearLayout parent,String name,int weight) {
    LinearLayout column=new LinearLayout(this);column.setOrientation(1);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,weight);
    lp.setMargins(dp(3),dp(2),dp(3),dp(2));parent.addView(column,lp);
    TextView title=label(name,17,GOLD);title.setBackground(bg(BLUE,GOLD));
    column.addView(title,new LinearLayout.LayoutParams(-1,dp(43)));
    return column;
  }
  private LinearLayout.LayoutParams guideRowParams() {
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));
    lp.setMargins(dp(2),dp(2),dp(2),dp(2));return lp;
  }
  private Button guideButton(String text,Runnable action) {
    Button b=button(text,action);b.setTextSize(14);
    b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setPadding(dp(10),0,dp(6),0);
    b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
    return b;
  }
  private void liveTv() {
    screen("LIVE TV");
    inLive=true;fullScreenLive=false;
    root.setPadding(dp(9),dp(7),dp(9),dp(5));
    final int session=liveRequest;
    LinearLayout content=new LinearLayout(this);content.setOrientation(0);
    root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout categories=column(content,"CATEGORIES",25);
    LinearLayout channels=column(content,"CHANNELS",36);
    LinearLayout preview=column(content,"PREVIEW & TV GUIDE",39);
    ScrollView cScroll=new ScrollView(this);
    liveCategories=new LinearLayout(this);liveCategories.setOrientation(1);cScroll.addView(liveCategories);
    categories.addView(cScroll,new LinearLayout.LayoutParams(-1,0,1));
    ScrollView chScroll=new ScrollView(this);
    liveChannels=new LinearLayout(this);liveChannels.setOrientation(1);chScroll.addView(liveChannels);
    channels.addView(chScroll,new LinearLayout.LayoutParams(-1,0,1));
    previewTitle=label("Select channel • OK to preview",16,WHITE);
    preview.addView(previewTitle,new LinearLayout.LayoutParams(-1,dp(38)));
    FrameLayout video=new FrameLayout(this);video.setBackground(bg(Color.BLACK,GOLD));
    LinearLayout.LayoutParams vParams=new LinearLayout.LayoutParams(-1,0,6);
    vParams.setMargins(dp(3),dp(2),dp(3),dp(2));preview.addView(video,vParams);
    liveView=new PlayerView(this);liveView.setUseController(false);liveView.setFocusable(false);
    video.addView(liveView,new FrameLayout.LayoutParams(-1,-1));
    previewStatus=label("Autoplay OFF — press OK to play",13,0xffb9d4ff);
    preview.addView(previewStatus,new LinearLayout.LayoutParams(-1,dp(28)));
    epgTextView=label("EPG\nSelect a channel to load programme data",14,WHITE);
    epgTextView.setGravity(Gravity.TOP|Gravity.LEFT);epgTextView.setPadding(dp(8),dp(8),dp(5),0);
    epgTextView.setBackground(bg(0xff0a2147,BLUE));
    preview.addView(epgTextView,new LinearLayout.LayoutParams(-1,0,3));
    LinearLayout controls=new LinearLayout(this);
    preview.addView(controls,new LinearLayout.LayoutParams(-1,dp(45)));
    Button full=button("FULL SCREEN",this::expandLive);full.setTextSize(12);
    controls.addView(full,new LinearLayout.LayoutParams(0,-1,2));
    Button stop=button("STOP",()->{if(player!=null)player.stop();previewStatus.setText("Stopped");});
    stop.setTextSize(12);controls.addView(stop,new LinearLayout.LayoutParams(0,-1,1));
    root.addView(label("D-PAD categories / channels    •    OK opens preview    •    FULL SCREEN expands    •    BACK returns Home",12,0xffb6d7ff),
       new LinearLayout.LayoutParams(-1,dp(30)));
    liveCategories.addView(label("Loading categories...",15,GOLD));
    liveChannels.addView(label("Loading channels...",15,GOLD));
    api("&action=get_live_categories",obj->{
      if(!inLive || session!=liveRequest)return;
      liveCategories.removeAllViews();
      Button all=guideButton("ALL CHANNELS",()->chooseLiveCategory(""));
      liveCategories.addView(all,guideRowParams());
      if(obj instanceof JSONArray){
        JSONArray arr=(JSONArray)obj;
        for(int i=0;i<arr.length();i++){
          JSONObject c=arr.optJSONObject(i);if(c==null)continue;
          final String id=c.optString("category_id"),name=c.optString("category_name","Category");
          liveCategories.addView(guideButton(name,()->chooseLiveCategory(id)),guideRowParams());
        }
      }
      chooseLiveCategory("");all.requestFocus();
    },err->{
      if(!inLive || session!=liveRequest)return;
      liveCategories.removeAllViews();
      liveCategories.addView(label("Categories unavailable",14,GOLD));
      chooseLiveCategory("");
    });
  }
  private void chooseLiveCategory(String id) {
    liveCat=id;currentCategory=id;currentKind="live";
    int requested=++liveRequest;
    liveChannels.removeAllViews();liveChannels.addView(label("Loading channels...",15,GOLD));
    JSONArray cache=liveCached.get(id);
    if(cache!=null){populateLive(cache,requested);return;}
    api("&action=get_live_streams"+(id.isEmpty()?"":"&category_id="+enc(id)),obj->{
      if(!inLive || liveRequest!=requested)return;
      if(obj instanceof JSONArray){JSONArray channels=(JSONArray)obj;liveCached.put(id,channels);populateLive(channels,requested);}
      else{liveChannels.removeAllViews();liveChannels.addView(label("No channel data",15,GOLD));}
    },err->{
      if(!inLive || liveRequest!=requested)return;
      liveChannels.removeAllViews();liveChannels.addView(label("Channel loading error",15,GOLD));
    });
  }
  private void populateLive(JSONArray channels,int requested) {
    if(!inLive || liveRequest!=requested)return;
    liveChannels.removeAllViews();int count=0;
    for(int i=0;i<channels.length() && count<2500;i++){
      JSONObject c=channels.optJSONObject(i);if(c==null)continue;
      final String id=c.optString("stream_id"),name=c.optString("name","Channel"),ext=c.optString("container_extension","ts");
      if(id.isEmpty())continue;
      liveChannels.addView(guideButton(String.format(java.util.Locale.ROOT,"%03d  %s",++count,name),
          ()->startLivePreview(id,name,ext)),guideRowParams());
    }
    if(count==0)liveChannels.addView(label("No channels in category",14,GOLD));
  }
  private void startLivePreview(String id,String name,String ext) {
    if(!inLive)return;
    liveId=id;liveName=name;liveExt=ext;previewTitle.setText(name);
    previewStatus.setText("Opening stream...");epgTextView.setText("EPG\nLoading...");
    if(player!=null){liveView.setPlayer(null);player.release();player=null;}
    String path=server+"/live/"+enc(user)+"/"+enc(pass)+"/"+enc(id)+"."+(ext.isEmpty()?"ts":ext);
    try{
      player=new ExoPlayer.Builder(this).build();
      player.addListener(new Player.Listener(){
        @Override public void onPlaybackStateChanged(int state){
          if(inLive && id.equals(liveId) && previewStatus!=null){
            if(state==Player.STATE_READY)previewStatus.setText("Playing");
            else if(state==Player.STATE_BUFFERING)previewStatus.setText("Buffering...");
          }
        }
        @Override public void onPlayerError(PlaybackException error){
          if(inLive && id.equals(liveId) && previewStatus!=null)
            previewStatus.setText("Playback error: "+error.getErrorCodeName());
        }
      });
      liveView.setPlayer(player);player.setMediaItem(MediaItem.fromUri(path));player.prepare();player.play();
    }catch(Exception e){previewStatus.setText("Unable to play: "+e.getMessage());}
    api("&action=get_short_epg&stream_id="+enc(id)+"&limit=2",obj->{
      if(!inLive || !id.equals(liveId) || epgTextView==null)return;
      JSONArray entries=obj instanceof JSONObject?((JSONObject)obj).optJSONArray("epg_listings"):null;
      if(entries==null || entries.length()==0){epgTextView.setText("EPG\nProgramme information unavailable");return;}
      StringBuilder result=new StringBuilder("TV GUIDE\n");
      for(int i=0;i<Math.min(2,entries.length());i++){
        JSONObject e=entries.optJSONObject(i);if(e==null)continue;
        result.append(i==0?"NOW: ":"NEXT: ").append(decodeEpg(e.optString("title",""))).append("\n");
        if(i==0)result.append(decodeEpg(e.optString("description",""))).append("\n");
      }
      epgTextView.setText(result.toString());
    },err->{if(inLive && id.equals(liveId) && epgTextView!=null)epgTextView.setText("EPG\nNo guide supplied");});
  }
  private static String decodeEpg(String s) {
    if(s==null || s.isEmpty())return "";
    try {
      String decoded=new String(Base64.decode(s,Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
      if(!decoded.isEmpty() && !decoded.contains("\uFFFD")
          && decoded.chars().filter(c->c<32 && c!=9 && c!=10 && c!=13).count()==0)
        return decoded;
    }catch(Exception ignored){}
    return s;
  }
  private void expandLive() {
    if(!inLive || player==null || liveId.isEmpty())return;
    fullScreenLive=true;
    ViewGroup parent=(ViewGroup)liveView.getParent();if(parent!=null)parent.removeView(liveView);
    root.removeAllViews();root.setPadding(0,0,0,0);
    root.addView(liveView,new LinearLayout.LayoutParams(-1,-1));
    liveView.setUseController(true);liveView.setFocusable(true);liveView.requestFocus();
  }
  private void loading(String title){screen(title);root.addView(label("Loading...",23,GOLD),new LinearLayout.LayoutParams(-1,0,1));}
  private void categories(String kind){
    currentKind=kind;loading(kind.toUpperCase()+" CATEGORIES");
    api("&action=get_"+(kind.equals("vod")?"vod":kind)+"_categories",obj->{
      screen(kind.toUpperCase()+" CATEGORIES");add(root,"ALL",()->listing(kind,"",""));
      if(obj instanceof JSONArray){JSONArray arr=(JSONArray)obj;
        ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<arr.length();i++){JSONObject c=arr.optJSONObject(i);if(c==null)continue;String id=c.optString("category_id");String title=c.optString("category_name");add(list,title,()->listing(kind,id,""));}
      }
      add(root,"Back",this::home);
    },err->{screen("ERROR");add(root,err,this::home);});
  }
  private void listing(String kind,String category,String filter){
    currentKind=kind;currentCategory=category;loading(kind.toUpperCase()+" CHANNELS");
    String action=kind.equals("live")?"get_live_streams":kind.equals("vod")?"get_vod_streams":"get_series";
    String suffix="&action="+action+(category.isEmpty()?"":"&category_id="+enc(category));
    api(suffix,obj->{
      screen(kind.toUpperCase()+" LIST");add(root,"Categories",()->categories(kind));
      ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);
      root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
      if(obj instanceof JSONArray){JSONArray arr=(JSONArray)obj;int shown=0;
        for(int i=0;i<arr.length()&&shown<500;i++){
          JSONObject c=arr.optJSONObject(i);if(c==null)continue;
          String title=c.optString("name",c.optString("title","Untitled"));
          if(filter.equals("sport")&&!title.toLowerCase().matches(".*(sport|football|soccer|sky sports|espn|premier).*"))continue;
          String id=c.optString(kind.equals("series")?"series_id":"stream_id");
          String ext=c.optString("container_extension",kind.equals("live")?"ts":"mp4");
          add(list,title,()->{if(kind.equals("series"))series(id,title);else play(title,kind,id,ext);});shown++;
        }
        if(shown==0)list.addView(label("No items returned.",20,GOLD));
      }
    },err->{screen("ERROR");add(root,err,this::home);});
  }
  private void series(String id,String title){
    loading(title);
    api("&action=get_series_info&series_id="+enc(id),obj->{
      screen(title);add(root,"Back to series",()->listing("series",currentCategory,""));
      ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
      if(obj instanceof JSONObject){
        JSONObject episodes=((JSONObject)obj).optJSONObject("episodes");
        if(episodes!=null){JSONArray keys=episodes.names();if(keys!=null)for(int x=0;x<keys.length();x++){
          JSONArray season=episodes.optJSONArray(keys.optString(x));if(season==null)continue;
          for(int y=0;y<season.length();y++){JSONObject ep=season.optJSONObject(y);if(ep==null)continue;String eid=ep.optString("id"),label=ep.optString("title","Episode "+(y+1)),ext=ep.optString("container_extension","mp4");add(list,label,()->play(label,"series",eid,ext));}
        }}
      }
    },err->{screen("ERROR");add(root,err,this::home);});
  }
  private void play(String title,String kind,String id,String ext){
    screen(title);
    String path=kind.equals("vod")?"movie":kind.equals("series")?"series":"live";
    if(ext.isEmpty())ext=kind.equals("live")?"ts":"mp4";
    String url=server+"/"+path+"/"+enc(user)+"/"+enc(pass)+"/"+enc(id)+"."+ext;
    FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
    PlayerView view=new PlayerView(this);view.setUseController(true);stage.addView(view,new FrameLayout.LayoutParams(-1,-1));
    try{player=new ExoPlayer.Builder(this).build();view.setPlayer(player);player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.play();}
    catch(Exception e){Toast.makeText(this,"Playback error: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    add(root,"Back to channels",()->listing(currentKind,currentCategory,""));
  }
  @Override public void onBackPressed(){if(inLive && fullScreenLive){liveTv();return;}home();}
  @Override protected void onDestroy(){if(player!=null)player.release();if(pulse!=null)pulse.cancel();worker.shutdownNow();super.onDestroy();}
}
