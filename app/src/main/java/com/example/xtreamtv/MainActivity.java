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
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import okhttp3.OkHttpClient;
import okhttp3.ConnectionPool;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.util.concurrent.atomic.AtomicLong;
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
 final ThreadPoolExecutor io=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
 final Handler ui=new Handler(Looper.getMainLooper());
 final ThreadPoolExecutor epgIo=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
 final ThreadPoolExecutor rowGuideIo=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(24),new ThreadPoolExecutor.AbortPolicy());
 final Runnable tickRows=new Runnable(){public void run(){if(stopped||!page.equals("Live TV"))return;if(!full&&right!=null&&right.getAdapter() instanceof LiveGuideRows)((LiveGuideRows)right.getAdapter()).refreshVisible(right);ui.postDelayed(this,60000);}};
 final android.util.LruCache<String,GuideData> guideCache=new android.util.LruCache<String,GuideData>(4800){@Override protected int sizeOf(String key,GuideData value){return Math.max(60,value.shows.size());}};
 Future<?> previewGuideJob,fullGuideJob;
 RegionalGuide regionalGuide;
 Item highlightedGuideItem;
 Runnable pendingGuideFocus;
 int guideRequest;
 Dialog guideDialog;
 Runnable regionalGuideRefresh;
 final Runnable refreshEpg=new Runnable(){public void run(){if(!stopped&&!full&&page.equals("Live TV")&&highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);else if(!stopped&&page.equals("Live TV"))ui.postDelayed(this,60000);}};
 static class Programme{String title,description,rawStart,rawEnd;long start,end;}
 static class GuideData{final List<Programme> shows;final long fetched=System.currentTimeMillis();final boolean extended;GuideData(List<Programme>s,boolean x){shows=s;extended=x;}}
 final ThreadPoolExecutor posterIo=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(64),new ThreadPoolExecutor.AbortPolicy());
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
 LinearLayout playerHeader,playerDetails;
 PremiumPlayerOverlay premiumPlayer;
 Future<?> catalogueJob;
 Runnable decoderWatch, decoderRetry, liveHealthWatch;
 ExoPlayer resumeExo;
 ProgressBar programmeProgress;
 String playbackDiagnostics="No video information yet";
 long startupBeginMs, startupFirstMs=-1, startupLoadMs=-1, startupDecoderMs=-1, startupDecoderCostMs=-1, startupReadyMs=-1; int droppedVideoFrames;
 FrameRateSample frameRateSample;
 PlaybackProbe playbackProbe;
 Player.Listener streamListener;
 AnalyticsListener streamAnalytics;
 long epgQuietUntil;
 static final OkHttpClient API_HTTP=new OkHttpClient.Builder().connectionPool(new ConnectionPool(5,3,TimeUnit.MINUTES)).connectTimeout(10,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).followSslRedirects(false).build();
 static final OkHttpClient GUIDE_HTTP=API_HTTP.newBuilder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).build();
 static final OkHttpClient STREAM_HTTP=API_HTTP.newBuilder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build();
 Runnable matchTask;
 boolean matchedWindow;
 android.view.Surface matchSurface;
 PlaybackProbe matchProbe;
 float requestedFps=-1,requestedHz=-1;
 long refreshRequestedAt=-1;
 String refreshRequest="Not requested";
 static final class FrameCadence {
  volatile long count,firstNs,lastNs;
  void record(){long now=System.nanoTime();if(count==0)firstNs=now;lastNs=now;count++;}
  String label(){long n=count,a=firstNs,b=lastNs;return n>1&&b>a?String.format(Locale.US,"%.2f/s (%d)",(n-1)*1e9/(b-a),n):"collecting ("+n+")";}
 }
 static final class StageTiming {
  volatile long count,totalNs,maxNs,over20ms;
  private long firstSampleNs=Long.MIN_VALUE;
  volatile long steadyCount,steadyTotalNs,steadyMaxNs,steadyOver20ms;
  void record(long ns){long now=System.nanoTime();if(firstSampleNs==Long.MIN_VALUE)firstSampleNs=now;totalNs+=ns;maxNs=Math.max(maxNs,ns);if(ns>20_000_000L)over20ms++;count++;if(now-firstSampleNs>=5_000_000_000L){steadyTotalNs+=ns;steadyMaxNs=Math.max(steadyMaxNs,ns);if(ns>20_000_000L)steadyOver20ms++;steadyCount++;}}
  String steadyLabel(){long n=steadyCount;return n==0?"collecting":String.format(Locale.US,"%.2f ms avg / %.2f max; >20 ms %d/%d",steadyTotalNs/1e6/n,steadyMaxNs/1e6,steadyOver20ms,n);}
  String label(){long n=count;return n==0?"collecting":String.format(Locale.US,"%.2f ms avg / %.2f max",totalNs/1e6/n,maxNs/1e6);}
 }
 // Single-writer counters; no frame-sized storage, allocations or locks in the hot path.
 static final class IntervalProbe {
  private long previous=Long.MIN_VALUE,firstObservedNs=Long.MIN_VALUE;
  private boolean steadyPrevious;
  volatile long count,gaps,nonIncreasing,maxUs,totalUs,steadyCount,steadyGaps,steadyMaxUs;
  void record(long stampUs,long expectedUs){
   long now=System.nanoTime();if(firstObservedNs==Long.MIN_VALUE)firstObservedNs=now;
   boolean steady=now-firstObservedNs>=5_000_000_000L;
   if(previous!=Long.MIN_VALUE){
    long delta=stampUs-previous;
    if(delta<=0)nonIncreasing++;
    else{boolean gap=expectedUs>0&&delta>expectedUs*3/2;count++;totalUs+=delta;maxUs=Math.max(maxUs,delta);if(gap)gaps++;if(steady&&steadyPrevious){steadyCount++;steadyMaxUs=Math.max(steadyMaxUs,delta);if(gap)steadyGaps++;}}
   }
   previous=stampUs;steadyPrevious=steady;
  }
  String label(){long n=count;return n==0?"collecting":String.format(Locale.US,"%.2f ms avg / %.2f max; gaps %d/%d; non-increasing %d",totalUs/1000.0/n,maxUs/1000.0,gaps,n,nonIncreasing);}
  String steadyLabel(){long n=steadyCount;return n==0?"collecting":String.format(Locale.US,"gaps %d/%d; max %.2f ms",steadyGaps,n,steadyMaxUs/1000.0);}
 }
 static final class ModeRenderStats {
  final FrameCadence surface=new FrameCadence(); final StageTiming late=new StageTiming(),draw=new StageTiming(),swap=new StageTiming();
  final IntervalProbe intervals=new IntervalProbe(); final AtomicLong graphDrops=new AtomicLong(),reportedDrops=new AtomicLong();
 }
 static final class PlaybackProbe implements TransferListener {
  volatile ModeRenderStats modeStats=new ModeRenderStats();
  void resetModeStats(){modeStats=new ModeRenderStats();}
  void submitted(){graphSubmitted.record();modeStats.surface.record();}
  void graphDrop(){graphOutputDrops++;modeStats.graphDrops.incrementAndGet();}
  void late(long ns){directLate.record(ns);modeStats.late.record(ns);}
  void drawn(long ns){directDraw.record(ns);modeStats.draw.record(ns);}
  void swapped(long ns){directSwap.record(ns);modeStats.swap.record(ns);}
  void submissionInterval(long ns){long us=ns/1000;long expected=expectedFrameUs();submittedIntervals.record(us,expected);modeStats.intervals.record(us,expected);}
  final StageTiming directLatch=new StageTiming(),directDraw=new StageTiming(),directSwap=new StageTiming(),directLate=new StageTiming(),directQueue=new StageTiming(),directDrawCall=new StageTiming(),directPresentCall=new StageTiming();
  volatile String directGraphics="Not initialized";
  final FrameCadence graphInput=new FrameCadence(), graphReady=new FrameCadence(), graphSubmitted=new FrameCadence();
  volatile long graphOutputDrops; volatile boolean directGraph;
  volatile String directShader="Not initialized";
  final IntervalProbe decodedPts=new IntervalProbe(),targetPts=new IntervalProbe(),submittedIntervals=new IntervalProbe();
  volatile long audioUnderruns,videoOffsetSumUs,videoOffsetFrames;
  long memorySampleAt=-1,heapStart=-1,heapCurrent,heapPeak,nativeCurrent;
  long expectedFrameUs(){float f=sourceFps;return f>=10&&f<=240?Math.round(1_000_000.0/f):0;}
  void sampleMemory(long now){
   if(memorySampleAt>=0&&now-memorySampleAt<30_000)return;
   memorySampleAt=now;Runtime rt=Runtime.getRuntime();heapCurrent=rt.totalMemory()-rt.freeMemory();
   if(heapStart<0)heapStart=heapCurrent;heapPeak=Math.max(heapPeak,heapCurrent);
   nativeCurrent=android.os.Debug.getNativeHeapAllocatedSize();
  }
  String memoryLabel(){return heapStart<0?"collecting":String.format(Locale.US,"Java %.1f MB (start %.1f / sampled peak %.1f); native heap %.1f MB",heapCurrent/1048576.0,heapStart/1048576.0,heapPeak/1048576.0,nativeCurrent/1048576.0);}
  String videoOffsetLabel(){long n=videoOffsetFrames;return n==0?"Not reported":String.format(Locale.US,"%+.2f ms avg (%d frames; not A/V offset)",videoOffsetSumUs/1000.0/n,n);}

  final long started;final boolean fast;final AtomicLong opening=new AtomicLong(-1),firstByte=new AtomicLong(-1),lastByte=new AtomicLong(-1),lastFrame=new AtomicLong(-1);
  final PlaybackHealth health=new PlaybackHealth(); String recoveryReason="None"; volatile int outputTransfer=-1; volatile boolean outputKnown=false; boolean sdrRejected; boolean headroomRequested; volatile float sourceFps=-1; volatile long graphFrames,graphFirstNs,graphLastNs; volatile int sourceTransfer=-1; volatile int decodedTransfer=-1; Item sourceItem; boolean alternate;
  int rebuffers;long stallStart=-1,stallTotal;
  final int startupBufferMs; PlaybackProbe(long start,boolean fast,int startupBufferMs){started=start;this.fast=fast;this.startupBufferMs=startupBufferMs;}
  @Override public void onTransferInitializing(DataSource source,DataSpec spec,boolean network){if(network)opening.compareAndSet(-1,SystemClock.elapsedRealtime()-started);}
  @Override public void onTransferStart(DataSource source,DataSpec spec,boolean network){}
  @Override public void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int count){if(network&&count>0){long now=SystemClock.elapsedRealtime();firstByte.compareAndSet(-1,now-started);lastByte.set(now);}}
  @Override public void onTransferEnd(DataSource source,DataSpec spec,boolean network){}
  void closeStall(){if(stallStart>=0){stallTotal+=SystemClock.elapsedRealtime()-stallStart;stallStart=-1;}}
 }
 // Per-player bounded estimator; callback runs on the playback thread.
 static final class FrameRateSample {
  private final long[] intervals=new long[48]; private long previous=Long.MIN_VALUE; private int count, next;
  synchronized void accept(long pts){
   if(previous==Long.MIN_VALUE){previous=pts;return;}
   long delta=pts-previous;previous=pts;
   if(delta==0)return;
   if(delta<0||delta>250000){count=next=0;return;}
   if(delta<5000)return;
   intervals[next]=delta;next=(next+1)%intervals.length;if(count<intervals.length)count++;
  }
  synchronized float estimate(){
   if(count<12)return -1;
   long[] copy=Arrays.copyOf(intervals,count);Arrays.sort(copy);long median=copy[count/2];
   int near=0;double sum=0;for(long d:copy)if(Math.abs(d-median)<=median*.12){near++;sum+=d;}
   return near>=count*.7? (float)(1000000.0*near/sum):-1;
  }
 }
 static String age(long stamp){return stamp<0?"Not available":String.format(Locale.US,"%.1f s ago",Math.max(0,SystemClock.elapsedRealtime()-stamp)/1000.0);}
 static String timing(long ms){return ms<0?"Not available":ms+" ms";}
 int activeSdrPath=-1;
 Item playbackItem;
 FrameLayout liveSurfaceHost;
 View.OnLayoutChangeListener livePreviewLayout;
 int liveBodyFocusMode,liveBodyAccessibility;
 View liveReturnFocus;
 TextView liveReturnStatus;
 String activeStreamId="";
 VideoView nativeVideo;
 android.media.MediaPlayer nativeMedia;
 Runnable nativeWatch;
 boolean stopped;
 String activeDecoder="", activeDecoderKind="Unknown";
 String server="",user="",pass="",page="Home",cat="",seriesId="",liveExt="ts";
 int screenToken,loadToken,epgToken,catPos,itemPos;
 int returnAnchor=-1,returnOffset,returnCategory=-1,returnCategoryOffset;String returnCatalogue="";
 volatile boolean full;
 Item selected;
 List<Item> cats=new ArrayList<>(),items=new ArrayList<>();
 long download=-1;
 // Bounded in-process catalogue cache. No login URLs or credentials are persisted.
 static final long CATEGORY_TTL=300000L, STREAM_TTL=120000L;
 static final class CatalogueEntry {
  final List<Item> items; final long created=android.os.SystemClock.elapsedRealtime();
  CatalogueEntry(List<Item> source){items=Collections.unmodifiableList(new ArrayList<>(source));}
 }
 final Object catalogueLock=new Object();
 long catalogueGeneration=0;
 final android.util.LruCache<String,CatalogueEntry> catalogue=new android.util.LruCache<String,CatalogueEntry>(24000){
  @Override protected int sizeOf(String k,CatalogueEntry e){return Math.max(32,e.items.size());}
 };
 void clearCatalogue(){synchronized(catalogueLock){catalogueGeneration++;catalogue.evictAll();}}
 void catalogueTask(String key,long ttl,boolean force,int req,Work<List<Item>> fetch,Done<List<Item>> done){
  if(catalogueJob!=null){catalogueJob.cancel(true);catalogueJob=null;io.purge();}
  final int token=screenToken;
  final long generation;
  final CatalogueEntry cached;
  synchronized(catalogueLock){generation=catalogueGeneration;cached=force?null:catalogue.get(key);}
  if(cached!=null&&android.os.SystemClock.elapsedRealtime()-cached.created<ttl){
   if(token==screenToken&&req==loadToken&&!isFinishing())done.run(new ArrayList<>(cached.items));
   return;
  }
  catalogueJob=io.submit(()->{
   try{
    List<Item> loaded=fetch.run();
    if(Thread.currentThread().isInterrupted())return;
    // Do not evict the entire useful cache for one oversized provider response.
    if(loaded.size()<=12000)synchronized(catalogueLock){if(generation==catalogueGeneration&&req==loadToken&&token==screenToken)catalogue.put(key,new CatalogueEntry(loaded));}
    ui.post(()->{if(token==screenToken&&req==loadToken&&!isFinishing())done.run(new ArrayList<>(loaded));});
   }catch(Exception e){
    ui.post(()->{if(token==screenToken&&req==loadToken&&!isFinishing())msg("Request failed. Check connection or retry.");});
   }
  });
 }
 @Override public void onTrimMemory(int level){
  super.onTrimMemory(level);
  if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN){posterCache.trimToSize(8*1024*1024);if(!page.equals("Home"))PremiumMockupHome.trimArtwork();}
  if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW&&level<android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN){clearCatalogue();posterCache.trimToSize(8*1024*1024);}
  if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND){clearCatalogue();posterCache.evictAll();posterUrls.evictAll();guideCache.evictAll();posterIo.purge();rowGuideIo.purge();if(!page.equals("Home"))PremiumMockupHome.trimArtwork();}
 }
 @Override public void onLowMemory(){super.onLowMemory();clearCatalogue();posterCache.evictAll();posterUrls.evictAll();guideCache.evictAll();posterIo.purge();rowGuideIo.purge();if(!page.equals("Home"))PremiumMockupHome.trimArtwork();}
 static class Item { String id,name,ext,art="",year="",tmdb="",epgId="",epgRegion=""; Item(String i,String n,String e){id=i;name=n;ext=e;} }
 interface Work<T>{T run()throws Exception;} interface Done<T>{void run(T t);} interface Pick{void run(Item i,int p);}
 @Override public void onCreate(Bundle b){super.onCreate(b);posterIo.allowCoreThreadTimeOut(true);rowGuideIo.allowCoreThreadTimeOut(true);getWindow().addFlags(128);prefs=getSharedPreferences("clean_shark_sky",0);regionalGuide=new RegionalGuide(this,()->ui.post(()->{if(isFinishing()||isDestroyed())return;guideCache.evictAll();if(!full&&!stopped&&page.equals("Live TV")){if(right!=null&&right.getAdapter() instanceof LiveGuideRows)((LiveGuideRows)right.getAdapter()).refreshVisible(right);if(highlightedGuideItem!=null)loadEpg(highlightedGuideItem);if(regionalGuideRefresh!=null)regionalGuideRefresh.run();}}));if(!prefs.getBoolean("baseline_reset_50043",false)){restorePlaybackDefaults();prefs.edit().putBoolean("baseline_reset_50043",true).apply();}if(!prefs.getBoolean("refresh_policy_50060",false)){prefs.edit().putBoolean("match_frame_rate",true).putBoolean("refresh_policy_50060",true).apply();}if(!prefs.getBoolean("native_range_50068",false)){android.content.SharedPreferences.Editor migration=prefs.edit().putBoolean("native_range_50068",true);if("Prefer SDR".equals(prefs.getString("range_live","Auto")))migration.putString("range_live","Auto");if("Prefer SDR".equals(prefs.getString("range_vod","Auto")))migration.putString("range_vod","Auto");migration.apply();}if(!prefs.getBoolean("decoder_sdr_50069",false)){prefs.edit().putBoolean("decoder_sdr_50069",true).putString("range_live","Prefer SDR").apply();}server=prefs.getString("server","");user=prefs.getString("user","");pass=prefs.getString("pass","");liveExt=prefs.getString("format","ts");download=prefs.getLong("download",-1);uiRegular=PremiumVisuals.font(this,false);uiMedium=PremiumVisuals.font(this,true);uiLight=uiRegular;applyDisplayMode();home();}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
 GradientDrawable bg(int a,int b,int border,int width){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(0);if(width>0)g.setStroke(dp(width),border);return g;}
 static final int UI_TEXT=0xfff2f5fc, UI_SECONDARY=0xffb5c7e3, UI_ACCENT=0xff8dcaff, UI_GOLD=0xffffcf63;
 android.graphics.Typeface uiRegular,uiMedium,uiLight;
 Drawable premiumFrame(boolean focused,boolean selected){return new PremiumVisuals.Glass(focused,selected);}
 StateListDrawable focus(){
  StateListDrawable d=new StateListDrawable();
  d.addState(new int[]{android.R.attr.state_focused},premiumFrame(false,true));
  d.addState(new int[]{android.R.attr.state_selected},premiumFrame(false,true));
  d.addState(new int[]{},premiumFrame(false,false));
  return d;
 }
 // Draw-only pulse; only focused controls and the visible preview animate.
 static final class PulseFrame extends Drawable {
  final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  final boolean always;final float width;boolean focused;
  final Runnable tick=()->invalidateSelf();
  PulseFrame(boolean always,float width){this.always=always;this.width=width;paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(width);}
  @Override public boolean isStateful(){return !always;}
  @Override protected boolean onStateChange(int[] state){boolean next=false;for(int s:state)if(s==android.R.attr.state_focused)next=true;if(focused==next)return false;focused=next;if(!focused)unscheduleSelf(tick);invalidateSelf();return true;}
  @Override public void draw(android.graphics.Canvas canvas){
   if(!isVisible()||(!always&&!focused))return;
   android.graphics.Rect bounds=getBounds();
   float shortSide=Math.min(bounds.width(),bounds.height());
   float scale=Math.max(1f,width*1.35f)/25f;
   scale=Math.min(scale,shortSide*.14f/30f);
   float inset=scale*17;
   android.graphics.RectF rim=new android.graphics.RectF(bounds);rim.inset(inset,inset);
   FlamePulse.draw(canvas,paint,rim,0,scale,FlamePulse.phase());
   if(FlamePulse.animates()){unscheduleSelf(tick);scheduleSelf(tick,android.os.SystemClock.uptimeMillis()+40);}
  }
  @Override public boolean setVisible(boolean visible,boolean restart){if(!visible)unscheduleSelf(tick);return super.setVisible(visible,restart);}
  @Override public void setAlpha(int alpha){}
  @Override public void setColorFilter(android.graphics.ColorFilter filter){}
  @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }
 void premiumHomeFocus(View tile){tile.setForeground(new PulseFrame(false,dp(5)));}
 Drawable previewPulse(){return new PulseFrame(true,dp(5));}
 StateListDrawable posterFocus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},bg(0xff223c60,0xff142642,0xff397eb9,2));d.addState(new int[]{},bg(0xff111d34,0xff0b1428,0xff397eb9,2));return d;}
 Drawable screenBackground(){return new PremiumVisuals.Backdrop(false);}
 void styleInput(EditText e){e.setTypeface(uiRegular);e.setTextColor(UI_TEXT);e.setHintTextColor(UI_SECONDARY);e.setTextSize(16);}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(size<=12?UI_SECONDARY:UI_TEXT);t.setTypeface(size>=16?uiMedium:uiRegular);t.setTextSize(size);t.setLetterSpacing(size>=21?0f:0.005f);t.setGravity(Gravity.CENTER_VERTICAL);t.setMaxLines(2);return t;}
 LinearLayout row(){return new LinearLayout(this);} LinearLayout col(){LinearLayout l=row();l.setOrientation(1);return l;}
 void button(LinearLayout r,String s,Runnable action){TextView t=text(s,15);t.setTypeface(uiMedium);t.setGravity(Gravity.CENTER);t.setFocusable(true);t.setClickable(true);t.setBackground(focus());premiumHomeFocus(t);t.setPadding(dp(6),0,dp(6),0);t.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(t,p);}
 ImageView art(int res){ImageView v=new ImageView(this);v.setImageResource(res);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}
 void cancelDecoderTasks(){if(decoderWatch!=null){ui.removeCallbacks(decoderWatch);decoderWatch=null;}if(decoderRetry!=null){ui.removeCallbacks(decoderRetry);decoderRetry=null;}}
 void stopLiveHealth(){if(liveHealthWatch!=null)ui.removeCallbacks(liveHealthWatch);}
 void release(){stopLiveHealth();liveHealthWatch=null;resumeExo=null;cancelDecoderTasks();detachStreamListeners();epgQuietUntil=0;clearRefreshMatch(true);playbackProbe=null;removeRangeButton();playbackItem=null;if(preview!=null&&livePreviewLayout!=null)preview.removeOnLayoutChangeListener(livePreviewLayout);livePreviewLayout=null;
  if(liveSurfaceHost!=null&&full&&body!=null){body.setDescendantFocusability(liveBodyFocusMode);body.setImportantForAccessibility(liveBodyAccessibility);}
  liveReturnFocus=null;liveReturnStatus=null;activeStreamId="";if(nativeWatch!=null){ui.removeCallbacks(nativeWatch);nativeWatch=null;}nativeMedia=null;if(nativeVideo!=null){VideoView n=nativeVideo;nativeVideo=null;n.setOnPreparedListener(null);n.setOnInfoListener(null);n.setOnErrorListener(null);n.setOnCompletionListener(null);n.stopPlayback();}if(video!=null){video.setPlayer(null);video=null;}if(player!=null){ExoPlayer p=player;player=null;p.release();}
  if(liveSurfaceHost!=null){ViewGroup parent=(ViewGroup)liveSurfaceHost.getParent();if(parent!=null)parent.removeView(liveSurfaceHost);liveSurfaceHost=null;if(preview!=null&&preview.getForeground()==null)preview.setForeground(previewPulse());}activeDecoder="";activeDecoderKind="Unknown";}
 void detachCatalogueViews(){if(left!=null){left.setAdapter(null);left=null;}if(right!=null){right.setAdapter(null);right=null;}posterIo.purge();rowGuideIo.purge();io.purge();epgIo.purge();}
 void screen(String title){displayStatus=null;if(catalogueJob!=null){catalogueJob.cancel(true);catalogueJob=null;io.purge();}clearGuideFocus();ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);cancelPreviewGuideJob();release();screenToken++;loadToken++;epgToken++;detachCatalogueViews();updateText=null;updateBar=null;full=false;if(regionalGuide!=null)regionalGuide.setBusy(stopped);applyDisplayMode();status=null;epg=null;programmeProgress=null;preview=null;body=col();body.setPadding(dp(16),dp(10),dp(16),dp(10));body.setBackground(screenBackground());setContentView(body);if(!page.equals("Home"))PremiumMockupHome.trimArtwork();immersive();LinearLayout head=row();LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(dp(136),dp(43));brandParams.rightMargin=dp(12);head.addView(PremiumVisuals.brand(this),brandParams);TextView t=text(title,22);t.setTextColor(UI_TEXT);t.setLetterSpacing(0.005f);head.addView(t,new LinearLayout.LayoutParams(0,dp(43),1));TextClock clock=new TextClock(this);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("h:mm a");clock.setTextColor(UI_TEXT);clock.setTextSize(18);clock.setTypeface(uiRegular);clock.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);head.addView(clock,new LinearLayout.LayoutParams(dp(100),dp(43)));body.addView(head);View divider=new View(this);divider.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x003d85ff,0xff4c9eff,0x003d85ff}));body.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
 void navigateHome(String dest){if(dest.equals("Home")){home();return;}if(dest.equals("Settings"))settings();else if(server.isEmpty())login(dest);else open(dest);}
 void home(){
  page="Home";screen("SHARK SKY");
  boolean wide=getResources().getConfiguration().screenWidthDp>=650;
  if(wide){PremiumMockupHome.show(this);return;}
  LinearLayout shell=wide?row():col();body.addView(shell,new LinearLayout.LayoutParams(-1,0,1));
  String[] names={"Live TV","Movies","Series","Settings"};
  int[] icons={R.drawable.metal48_live,R.drawable.metal48_movies,R.drawable.metal48_series,R.drawable.metal48_settings};
  LinearLayout main=col();
  if(wide){
   LinearLayout rail=col();rail.setPadding(dp(8),dp(8),dp(8),dp(8));rail.setBackground(bg(0xff122747,0xff081429,0xff325786,1));
   shell.addView(rail,new LinearLayout.LayoutParams(dp(160),-1));
   TextView brand=text("PREMIUM",12);brand.setTextColor(UI_GOLD);brand.setLetterSpacing(.16f);rail.addView(brand,new LinearLayout.LayoutParams(-1,dp(35)));
   String[] nav={"Home","Live TV","Movies","Series","Settings"};
   for(String dest:nav){
    TextView label=text(dest,16);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(dp(12),0,dp(8),0);label.setFocusable(true);label.setClickable(true);label.setBackground(focus());premiumHomeFocus(label);label.setSelected(dest.equals("Home"));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(3),0,dp(3));rail.addView(label,lp);label.setOnClickListener(v->navigateHome(dest));
   }
   main.setPadding(dp(12),0,0,0);shell.addView(main,new LinearLayout.LayoutParams(0,-1,1));
  }else shell.addView(main,new LinearLayout.LayoutParams(-1,-1));
  LinearLayout hero=wide?row():col();hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(12),dp(8),dp(8),dp(8));hero.setBackground(bg(0xff173b67,0xff07172f,0xff29568c,1));
  main.addView(hero,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout welcome=col();welcome.setGravity(Gravity.CENTER_VERTICAL);
  TextView eyebrow=text("WELCOME TO",12);eyebrow.setTextColor(UI_GOLD);eyebrow.setLetterSpacing(.15f);welcome.addView(eyebrow);
  TextView title=text("Shark Sky Premium",wide?28:23);title.setMaxLines(2);welcome.addView(title);
  TextView sub=text("Live TV, movies and series",14);sub.setTextColor(UI_SECONDARY);welcome.addView(sub);
  if(wide)hero.addView(welcome,new LinearLayout.LayoutParams(0,-1,1));else hero.addView(welcome,new LinearLayout.LayoutParams(-1,-2));
  ImageView mascot=art(R.drawable.shark_brand);hero.addView(mascot,wide?new LinearLayout.LayoutParams(0,-1,1.35f):new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout tiles=row();main.addView(tiles,new LinearLayout.LayoutParams(-1,wide?dp(132):dp(108)));
  for(int i=0;i<names.length;i++){
   String dest=names[i];LinearLayout tile=col();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(5),dp(5),dp(5),dp(5));tile.setBackground(focus());tile.setFocusable(true);tile.setClickable(true);premiumHomeFocus(tile);
   tile.addView(art(icons[i]),new LinearLayout.LayoutParams(-1,0,1));TextView label=text(dest,wide?18:14);label.setTypeface(uiMedium);label.setGravity(Gravity.CENTER);tile.addView(label,new LinearLayout.LayoutParams(-1,dp(30)));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(3),dp(6),dp(3),dp(4));tiles.addView(tile,lp);tile.setOnClickListener(v->navigateHome(dest));
  }
  TextView footer=text("SHARK SKY  •  "+(server.isEmpty()?"Connect your playlist":"Playlist saved")+"  •  "+BuildConfig.VERSION_NAME,12);footer.setGravity(Gravity.CENTER_VERTICAL);main.addView(footer,new LinearLayout.LayoutParams(-1,dp(25)));
  tiles.getChildAt(0).requestFocus();
 }
 void msg(String s){if(status!=null)status.setText(s);else Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
 static String readProvider(OkHttpClient client,String url,String userAgent,int limit)throws Exception{
  if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request cancelled");
  Request request=new Request.Builder().url(url).header("User-Agent",userAgent).build();
  okhttp3.Call call=client.newCall(request);GuideTask guide=client==GUIDE_HTTP?runningGuide.get():null;if(guide!=null)guide.attach(call);
  try(Response response=call.execute()){
   if(!response.isSuccessful())throw new IOException("Provider HTTP error");
   ResponseBody body=response.body();if(body==null)throw new IOException("Empty response");
   if(body.contentLength()>limit)throw new IOException("Response too large");
   try(InputStream in=body.byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buf=new byte[16384];int n;
    while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request cancelled");if(out.size()+n>limit)throw new IOException("Response too large");out.write(buf,0,n);}
    return out.toString("UTF-8");
   }
  }finally{if(guide!=null)guide.detach(call);}
 }
 String get(String u)throws Exception{return readProvider(API_HTTP,u,"SharkSky/5.0",48*1024*1024);}
 String api(String action,String args){return server+"/player_api.php?username="+enc(user)+"&password="+enc(pass)+(action.isEmpty()?"":"&action="+action)+args;}
 <T> void task(Work<T> w,Done<T> done){int token=screenToken;io.execute(()->{try{T data=w.run();ui.post(()->{if(token==screenToken&&!isFinishing())done.run(data);});}catch(Exception e){ui.post(()->{if(token==screenToken&&!isFinishing())msg("Request failed. Check connection or retry.");});}});}
 EditText field(String label,String value,int type){EditText e=new EditText(this);e.setHint(label);e.setText(value);e.setSingleLine();e.setInputType(type);styleInput(e);body.addView(e);return e;}
 void login(String next){page="Settings";screen("Xtream Codes");EditText h=field("Server URL",server,17),u=field("Username",user,1),p=field("Password",pass,129);status=text("Enter your provider details",14);body.addView(status);LinearLayout r=row();button(r,"Connect",()->{String host=h.getText().toString().trim().replaceAll("/+$",""),name=u.getText().toString().trim(),pw=p.getText().toString();Uri uri=Uri.parse(host);if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getQuery()!=null||uri.getUserInfo()!=null||uri.getFragment()!=null||name.isEmpty()||pw.isEmpty()){msg("Invalid login details.");return;}msg("Connecting…");task(()->new JSONObject(get(host+"/player_api.php?username="+enc(name)+"&password="+enc(pw))),j->{JSONObject info=j.optJSONObject("user_info");if(info==null||info.optInt("auth",0)!=1){msg("Login rejected.");return;}server=host;user=name;pass=pw;clearCatalogue();guideCache.evictAll();JSONObject serverInfo=j.optJSONObject("server_info");String zone=serverInfo==null?"":serverInfo.optString("timezone","");prefs.edit().putString("server",host).putString("user",name).putString("pass",pw).putString("epg_zone",zone).apply();if(next.equals("Settings"))settings();else open(next);});});button(r,"Back",this::home);body.addView(r);}
 void settingsTile(LinearLayout r,String label,int icon,Runnable action){
  PremiumVisuals.Tile tile=new PremiumVisuals.Tile(this,label,icon,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(5),dp(5),dp(5),dp(5));r.addView(tile,lp);tile.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.requestFocus();return false;});tile.setOnClickListener(v->action.run());
 }
 void restorePlaybackDefaults(){
  prefs.edit().remove("display_mode").remove("resize_mode").remove("audio_lang").remove("text_lang").remove("subtitles_on").remove("show_codec_info").remove("match_frame_rate").remove("live_startup_buffer_ms").remove("range_live").remove("range_vod").remove("format").putBoolean("default_live_engine",false).apply();
  decoderPrefs().edit().clear().apply();liveExt="ts";
 }
 void resetVisualSettings(){
  new AlertDialog.Builder(this).setTitle("Reset playback and display preferences?").setMessage("Restore Custom ExoPlayer with native output for Live TV, native Movies/Series, automatic Live FPS and TS live format. Xtream login and favourites will be kept.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,n)->{restorePlaybackDefaults();applyDisplayMode();settings();}).show();
 }
 void settings(){
  page="Settings";screen("Settings");
  // Centre the compact grid in the usable area below the header.
  boolean wide=getResources().getConfiguration().screenWidthDp>=650;
  int rowGap=wide?14:10, edge=wide?8:4;
  int available=getResources().getConfiguration().screenHeightDp-76-2*rowGap;
  int tileHeight=Math.max(44,Math.min(wide?106:84,available/3));
  LinearLayout area=col();area.setGravity(Gravity.CENTER_VERTICAL);
  area.setPadding(dp(edge),0,dp(edge),0);
  body.addView(area,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout r=row();area.addView(r,new LinearLayout.LayoutParams(-1,dp(tileHeight)));
  settingsTile(r,"Xtream Codes",4,()->login("Settings"));settingsTile(r,"Playback",5,this::playbackSettings);settingsTile(r,"Display",6,this::displayDialog);settingsTile(r,"Audio",7,()->languageDialog("audio_lang","Preferred audio language"));
  LinearLayout r2=row();LinearLayout.LayoutParams secondRow=new LinearLayout.LayoutParams(-1,dp(tileHeight));secondRow.topMargin=dp(rowGap);area.addView(r2,secondRow);
  settingsTile(r2,"Subtitles",8,()->languageDialog("text_lang","Preferred subtitle language"));settingsTile(r2,"Updates",9,this::update);settingsTile(r2,"About",10,()->new AlertDialog.Builder(this).setTitle("Shark Sky Premium").setMessage("Version "+BuildConfig.VERSION_NAME+" · Media3 1.12.0-rc01").setPositiveButton("OK",null).show());settingsTile(r2,"Cleaner",11,this::maintenance);
  // Match the first eight tiles: same artwork, same dimensions and pulse.
  LinearLayout r3=row();
  LinearLayout.LayoutParams thirdRow=new LinearLayout.LayoutParams(-1,dp(tileHeight));
  thirdRow.topMargin=dp(rowGap);area.addView(r3,thirdRow);
  LinearLayout.LayoutParams side=new LinearLayout.LayoutParams(0,-1,.5f);side.width=dp(5);r3.addView(new View(this),side);
  settingsTile(r3,"Connection Test",15,this::connectionTest);
  settingsTile(r3,"Home",13,this::home);
  settingsTile(r3,"Exit",14,this::confirmExit);
  LinearLayout.LayoutParams sideEnd=new LinearLayout.LayoutParams(0,-1,.5f);sideEnd.width=dp(5);r3.addView(new View(this),sideEnd);
  r.getChildAt(0).requestFocus();
 }
 String videoDecoderKind(String name){
  if(name==null||name.isEmpty())return "Unknown";
  if(Build.VERSION.SDK_INT>=29){
   try{for(android.media.MediaCodecInfo codec:new android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).getCodecInfos()){
    if(name.equals(codec.getName())||name.equals(codec.getCanonicalName())){
     if(codec.isSoftwareOnly())return "Software";
     if(codec.isHardwareAccelerated())return "Hardware";
     return "Unknown";
    }
   }}catch(RuntimeException ignored){}
  }
  return "Unknown";
 }
 String transferName(int t){
  switch(t){case 3:return "SDR";case 6:return "HDR10 / PQ";case 7:return "HLG HDR";case 1:return "Linear";default:return "Unknown ("+t+")";}
 }
 String sdrStatus(Format f){
  PlaybackProbe probe=playbackProbe;

  if(activeSdrPath==0&&probe!=null)return "ExoPlayer BT.709 SDR requested; "+(probe.directGraph?"direct single pass":"reference graph")+" (panel unverified)\nDecoder output before GPU: "+transferName(probe.decodedTransfer);
  if(probe!=null&&probe.outputKnown)return "Decoder output "+transferName(probe.outputTransfer)+" (native surface)";
  if(f!=null&&f.colorInfo!=null&&f.colorInfo.colorTransfer==C.COLOR_TRANSFER_SDR&&!isHdr(f))return "SDR source; output unverified";

  return "Decoder output unknown; SDR not verified";
 }
 String directPlaybackSummary(PlaybackProbe q,Format f,float rate,float hz){
  if(q==null)return "";
  if(!q.directGraph){
   if(full||activeSdrPath!=0)return "";
   return "SDR preview • "+BuildConfig.VERSION_NAME+" • reference graph\n"
    +String.format(Locale.US,"%s | %.2f fps / %.2f Hz\n",f==null?"Unknown size":f.width+"x"+f.height,rate,hz)
    +"Input: "+q.graphInput.label()+"\nReady: "+q.graphReady.label()+"\n"
    +"Render requests: "+q.graphSubmitted.label()+"\n"
    +"Graph/reported drops: "+q.graphOutputDrops+"/"+droppedVideoFrames+"\n"
    +"Decoded gaps: "+q.decodedPts.steadyLabel()+"\n"
    +"Render requests are not measured surface swaps.\nPanel timing unverified • Details below\n\n";
  }
  ModeRenderStats window=q.modeStats;
  String[] lines=q.directGraphics.split("\\n");
  String fence="GPU fence: collecting",lead="Lead: collecting",pre="Pre-draw: collecting";
  for(String line:lines){
   if(line.startsWith("GPU fence vs requested target:"))fence=line.replace("GPU fence vs requested target:","Fence:").replace("uncertain","uncert.").replace("no target","no tgt");
   else if(line.startsWith("Submission lead (target minus swap end):"))lead=line.replace("Submission lead (target minus swap end):","Lead:").replace("EGL timestamp rejects","EGL rejects");
   else if(line.startsWith("Pre-draw used:"))pre=line;
  }
  return "SDR "+(full?"full-screen":"preview")+" • "+BuildConfig.VERSION_NAME+"\n"
   +String.format(Locale.US,"%s | %.2f fps / %.2f Hz\n",f==null?"Unknown size":f.width+"x"+f.height,rate,hz)
   +"Surface: "+window.surface.label()+" | drops "+window.graphDrops.get()+"/"+window.reportedDrops.get()+"\n"
   +"Steady gaps: "+window.intervals.steadyGaps+"/"+window.intervals.steadyCount+" | late >20ms "+window.late.steadyOver20ms+"/"+window.late.steadyCount+"\n"
   +"Session totals — "+fence+"\n"+lead+"\n"+pre+"\n"+(video!=null?"Output: "+(video.getVideoSurfaceView() instanceof android.view.TextureView?"TextureView":"SurfaceView")+"\n":"")
   +"Draw: "+window.draw.label()+"\nSwap: "+window.swap.label()+"\n"
   +"Panel timing unverified • Details below\n\n";
 }
 String currentPlaybackDiagnostics(){
  ExoPlayer p=player;
  if(p==null){android.media.MediaPlayer mp=nativeMedia;return playbackDiagnostics+"\nEngine: Android native fallback\nSource picture mode: "+rangeLabel()+"\nDisplay: "+String.format(Locale.US,"%.2f Hz",getWindowManager().getDefaultDisplay().getMode().getRefreshRate())+(movieDisplayPolicy()?"\nDisplay policy: Movies / Series 60 Hz preference":"")+(mp!=null?"\nVideo resolution: "+mp.getVideoWidth()+"x"+mp.getVideoHeight():"");}
  Format f=p.getVideoFormat(), a=p.getAudioFormat();
  float hz=getWindowManager().getDefaultDisplay().getMode().getRefreshRate();
  float declared=f!=null?f.frameRate:-1;if(!RefreshMatch.valid(declared)&&playbackProbe!=null)declared=playbackProbe.sourceFps;
  float estimated=frameRateSample==null?-1:frameRateSample.estimate();
  float rate=declared>0?declared:estimated;
  String fps=rate>0?String.format(Locale.US,"%.2f fps (%s)",rate,declared>0?"declared":"estimated from frame timestamps"):"Unknown / collecting frames";
  String match="Unknown";
  if(rate>0)match=RefreshMatch.exact(hz,rate)?"Matched multiple":RefreshMatch.compatible(hz,rate)?"Nominal multiple":"Not an integer multiple";
  int bitrate=f==null?-1:f.bitrate;
  return directPlaybackSummary(playbackProbe,f,rate,hz)+playbackDiagnostics+"\nActive video decoder: "+(activeDecoder.isEmpty()?"Not initialized":activeDecoder)+" · "+activeDecoderKind+(activeSdrPath==0?" (hardware-only policy)":"")+"\nPicture request: "+rangeLabel()+"\n"+sdrStatus(f)+(playbackProbe!=null&&playbackProbe.headroomRequested?"\nSDR headroom requested; panel unverified":"")+"\nAndroid API: "+Build.VERSION.SDK_INT+"\n\nEngine: "+(defaultLiveEngine()?"Default ExoPlayer":"Custom ExoPlayer")+"\nStream FPS: "+fps+"\nDisplay: "+String.format(Locale.US,"%.2f Hz",hz)+"\nFrame matching: "+match+refreshDiagnostics(rate,hz)
   +(playbackProbe!=null&&playbackProbe.alternate?"\nSDR alternative feed: "+playbackProbe.sourceItem.name:"")
   +"\nVideo bitrate (declared): "+(bitrate>0?String.format(Locale.US,"%.2f Mb/s",bitrate/1000000.0):"Not reported")
   +(activeSdrPath==0&&playbackProbe!=null?"\nGPU input: "+playbackProbe.graphInput.label()+"\nGPU ready: "+playbackProbe.graphReady.label()+(playbackProbe.directGraph?"\nSurface submissions: ":"\nRender requests (not measured swaps): ")+playbackProbe.graphSubmitted.label()+"\nGraph output drops: "+playbackProbe.graphOutputDrops+(playbackProbe.directGraph?"\nDecoder texture latch: "+playbackProbe.directLatch.label()+"\nDraw submission CPU time: "+playbackProbe.directDraw.label()+"\nEGL swap: "+playbackProbe.directSwap.label()+"\nSubmission past target (not panel): "+playbackProbe.directLate.label()+"\nSubmissions >20 ms late: "+playbackProbe.directLate.over20ms+"/"+playbackProbe.directLate.count+"\nGL thread queue delay: "+playbackProbe.directQueue.label()+"\nSDR shader: "+playbackProbe.directShader+"\nGL draw API CPU: "+playbackProbe.directDrawCall.steadyLabel()+"\nEGL timestamp API CPU: "+playbackProbe.directPresentCall.steadyLabel()+"\n"+playbackProbe.directGraphics+"\nDecoded timestamp intervals: "+playbackProbe.decodedPts.label()+"\nRequested release intervals: "+playbackProbe.targetPts.label()+"\nSurface submission intervals (not panel): "+playbackProbe.submittedIntervals.label()+"\nAfter first 5 s — draw: "+playbackProbe.directDraw.steadyLabel()+"\nSteady lateness: "+playbackProbe.directLate.steadyLabel()+"\nSteady decoded intervals: "+playbackProbe.decodedPts.steadyLabel()+"\nSteady release intervals: "+playbackProbe.targetPts.steadyLabel()+"\nSteady submission intervals: "+playbackProbe.submittedIntervals.steadyLabel():""):"")
   +"\nDropped frames (reported): "+droppedVideoFrames+(activeSdrPath==0&&playbackProbe!=null?"\nGPU frame callbacks: "+playbackProbe.graphFrames+"; "+(playbackProbe.graphFrames>1&&playbackProbe.graphLastNs>playbackProbe.graphFirstNs?String.format(Locale.US,"%.2f callbacks/s",(playbackProbe.graphFrames-1)*1e9/(playbackProbe.graphLastNs-playbackProbe.graphFirstNs)):"collecting"):"")
   +"\nBuffered: "+String.format(Locale.US,"%.2f s",p.getTotalBufferedDuration()/1000.0)
   +"\nVideo codec: "+(f==null?"Unknown":String.valueOf(f.sampleMimeType))
   +"\nAudio codec: "+(a==null?"Unknown":String.valueOf(a.sampleMimeType))+(playbackProbe==null?"":"\nAudio underruns: "+playbackProbe.audioUnderruns+"\nRenderer video processing offset: "+playbackProbe.videoOffsetLabel()+"\nPlayback heap sample: "+playbackProbe.memoryLabel())
   +"\nStartup to first frame: "+timing(startupFirstMs)
   +"\nLoad requested: "+timing(startupLoadMs)
   +"\nVideo decoder ready: "+timing(startupDecoderMs)+" (init "+timing(startupDecoderCostMs)+")"
   +"\nPlayer ready: "+timing(startupReadyMs)
   +(playbackProbe==null?"":"\nStartup profile: "+(playbackProbe.fast?"Custom live ("+playbackProbe.startupBufferMs+" ms)":"Stock buffer")+"\nNetwork opening: "+timing(playbackProbe.opening.get())+"\nFirst network byte: "+timing(playbackProbe.firstByte.get())+"\nRebuffers: "+playbackProbe.rebuffers+" / "+timing(playbackProbe.stallTotal+(playbackProbe.stallStart<0?0:SystemClock.elapsedRealtime()-playbackProbe.stallStart)))
   +"\nPlayback state: "+(p.getPlayerError()!=null?"Error "+p.getPlayerError().getErrorCodeName():p.getPlaybackState()==Player.STATE_BUFFERING?"Buffering":p.getPlaybackState()==Player.STATE_READY?(p.isPlaying()?"Playing":"Ready / paused"):p.getPlaybackState()==Player.STATE_ENDED?"Ended":"Idle")
   +(playbackProbe==null?"":"\nLast network data: "+age(playbackProbe.lastByte.get())+"\nLast scheduled video frame: "+age(playbackProbe.lastFrame.get())+"\nLive recovery attempts: "+playbackProbe.health.attempts+" / 2\nLast recovery: "+playbackProbe.recoveryReason)
   +"\nStartup times relative to player creation; not server-only timings.";
 }
 void connectionTest(){
  if(server.isEmpty()||user.isEmpty()||pass.isEmpty()){
   new AlertDialog.Builder(this).setTitle("Connection Test").setMessage("Add your IPTV login in Xtream Codes first.").setPositiveButton("OK",null).show();return;
  }
  final String endpoint=api("","");
  final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connection Test")
   .setMessage("Checking your saved provider…").setPositiveButton("Close",null).create();
  dialog.show();
  io.execute(()->{
   String result;long start=android.os.SystemClock.elapsedRealtime();
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(endpoint).openConnection();
    c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(7000);
    c.setRequestProperty("User-Agent","SharkSky/5.0");int code=c.getResponseCode();
    long ms=android.os.SystemClock.elapsedRealtime()-start;
    if(code!=200){result="Server responded: HTTP "+code+"\nResponse time: "+ms+" ms\nLogin could not be verified.";}
    else{
     try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] buf=new byte[4096];int n;
      while((n=in.read(buf))!=-1){if(out.size()+n>65536||android.os.SystemClock.elapsedRealtime()-start>12000)throw new IOException("Diagnostic limit");out.write(buf,0,n);}
      JSONObject info=new JSONObject(out.toString("UTF-8")).optJSONObject("user_info");
      boolean accepted=info!=null&&info.optInt("auth",0)==1;
      result="Server reachable\nResponse time: "+ms+" ms\nLogin: "+(accepted?"Accepted":"Not accepted or unrecognised response");
      if(accepted){String state=info.optString("status","");if(state.matches("[A-Za-z ]{1,30}"))result+="\nAccount: "+state;}
     }
    }
   }catch(java.net.UnknownHostException e){result="Server address could not be resolved. Check your connection and provider URL.";}
   catch(java.net.SocketTimeoutException e){result="Provider timed out. Check your network or try again.";}
   catch(Exception e){result="Unable to verify the provider response. Check your network and provider settings.";}
   finally{if(c!=null)c.disconnect();}
   final String message=result+"\n\nThis checks the provider API, not streaming bandwidth.";
   ui.post(()->{if(!isFinishing()&&!isDestroyed()&&dialog.isShowing())dialog.setMessage(message);});
  });
 }
 void confirmExit(){
  new AlertDialog.Builder(this).setTitle("Exit Sky Shark?").setMessage("Close the app? Your login, favourites and settings will stay saved.")
   .setNegativeButton("Cancel",null).setPositiveButton("Exit",(dialog,which)->{release();finishAffinity();}).show();
 }
 // Equivalent in scope to Android Clear cache, not Clear data.
 boolean maintenanceBusy=false;
 void maintenance(){
  new AlertDialog.Builder(this).setTitle("Cleaner")
   .setItems(new String[]{"Clear app cache","Reset playback & display (optional)"},(d,n)->{if(n==0)confirmCacheClean();else resetVisualSettings();})
   .setNegativeButton("Cancel",null).show();
 }
 void confirmCacheClean(){
  if(updateBusy||maintenanceBusy){msg("Finish the current operation first.");return;}
  new AlertDialog.Builder(this).setTitle("Clear app cache?")
   .setMessage("Remove temporary cache files and refresh the app. Login, favourites, settings and downloaded updates stay saved.")
   .setNegativeButton("Cancel",null).setPositiveButton("Clear cache",(d,n)->cleanAppCache()).show();
 }
 static class CleanResult{long bytes;int failed;}
 static void clearCacheContents(File root,CleanResult result){
  if(root==null||!root.exists())return;
  try{
   File canonical=root.getCanonicalFile();
   File[] children=canonical.listFiles();
   if(children==null){result.failed++;return;}
   for(File child:children)removeCacheEntry(child,canonical,result);
  }catch(Exception ex){result.failed++;}
 }
 static void removeCacheEntry(File entry,File root,CleanResult result){
  try{
   File actual=entry.getCanonicalFile();
   if(!actual.equals(entry.getAbsoluteFile())||!actual.getPath().startsWith(root.getPath()+File.separator)){
    if(!entry.delete()&&entry.exists())result.failed++;
    return;
   }
   if(entry.isDirectory()){
    File[] children=entry.listFiles();
    if(children==null){result.failed++;return;}
    for(File child:children)removeCacheEntry(child,root,result);
   }
   long size=entry.isFile()?entry.length():0;
   if(entry.delete())result.bytes+=size;else if(entry.exists())result.failed++;
  }catch(Exception ex){result.failed++;}
 }
 void cleanAppCache(){
  if(updateBusy||maintenanceBusy)return;
  maintenanceBusy=true;
  screenToken++;loadToken++;epgToken++;guideRequest++;
  clearGuideFocus();release();
  ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);
  cancelPreviewGuideJob();
  cancelFullGuideJob();
  if(left!=null)left.setAdapter(null);
  if(right!=null)right.setAdapter(null);
  posterIo.getQueue().clear();rowGuideIo.getQueue().clear();
  posterUrls.evictAll();posterCache.evictAll();guideCache.evictAll();clearCatalogue();
  final Dialog progress=new AlertDialog.Builder(this).setTitle("Clearing cache").setMessage("Removing temporary files…").setCancelable(false).create();
  progress.show();
  final File internal=getCacheDir();
  final File[] external=getExternalCacheDirs();
  new Thread(()->{
   CleanResult result=new CleanResult();
   clearCacheContents(internal,result);
   if(external!=null)for(File dir:external)clearCacheContents(dir,result);
   ui.post(()->{
    maintenanceBusy=false;
    if(isFinishing()||isDestroyed())return;
    progress.dismiss();
    Toast.makeText(this,result.failed==0?String.format(Locale.US,"Cache cleared · %.1f MB freed",result.bytes/1048576.0):"Cache refreshed; some temporary files could not be removed",Toast.LENGTH_LONG).show();
    // Fresh activity owns fresh queues and memory caches. Preferences are never changed.
    recreate();
   });
  },"shark-cache-clean").start();
 }
 void tmdbSettings(){page="Settings";screen("TMDB artwork");EditText key=field("TMDB v3 key (optional override)",prefs.getString("tmdb_override",""),129);status=text(tmdbKey().isEmpty()?"No TMDB key configured":"TMDB configured · high resolution posters",14);body.addView(status);LinearLayout r=row();button(r,"Save",()->{String v=key.getText().toString().trim();if(!v.isEmpty()&&!v.matches("[a-fA-F0-9]{32}")){msg("Invalid TMDB v3 key");return;}prefs.edit().putString("tmdb_override",v).apply();posterUrls.evictAll();posterCache.evictAll();tmdbSettings();});button(r,"Refresh artwork",()->{posterUrls.evictAll();posterCache.evictAll();msg("Artwork cache cleared");});button(r,"Back",this::settings);body.addView(r);}
 String tmdbKey(){String v=prefs.getString("tmdb_override","").trim();return v.isEmpty()?BuildConfig.TMDB_API_KEY:v;}
 void playbackSettings(){
  page="Settings";screen("Playback & display");
  LinearLayout extra=row();button(extra,"Live format: "+liveExt,()->{liveExt=liveExt.equals("ts")?"m3u8":"ts";prefs.edit().putString("format",liveExt).apply();playbackSettings();});button(extra,"TMDB artwork",this::tmdbSettings);body.addView(extra);
  LinearLayout r=row();button(r,"Subtitles: "+(prefs.getBoolean("subtitles_on",true)?"On":"Off"),()->{prefs.edit().putBoolean("subtitles_on",!prefs.getBoolean("subtitles_on",true)).apply();playbackSettings();});
  button(r,"Codec info: "+(prefs.getBoolean("show_codec_info",false)?"On":"Off"),()->{prefs.edit().putBoolean("show_codec_info",!prefs.getBoolean("show_codec_info",false)).apply();playbackSettings();});body.addView(r);
  LinearLayout r2=row();int mode=prefs.getInt("resize_mode",0);button(r2,"Picture: "+(mode==3?"Stretch":mode==4?"Zoom":"Fit"),()->{prefs.edit().putInt("resize_mode",mode==0?3:mode==3?4:0).apply();playbackSettings();});
  button(r2,"Audio: "+prefs.getString("audio_lang","Automatic"),()->languageDialog("audio_lang","Preferred audio language"));button(r2,"Text: "+prefs.getString("text_lang","Automatic"),()->languageDialog("text_lang","Preferred subtitle language"));body.addView(r2);
  LinearLayout range=row();button(range,"Live TV: "+rangeName(rangeFor(true)),()->rangeSettings(true));button(range,"Movies / Series: "+rangeName(rangeFor(false)),()->rangeSettings(false));body.addView(range);
  TextView hint=text("Auto preserves source resolution and range. Force SDR requests full-resolution ExoPlayer tone mapping; panel output must be verified.",13);body.addView(hint);
  LinearLayout r3=row();button(r3,"Display mode",this::displayDialog);button(r3,"Clear remembered decoders",()->{decoderPrefs().edit().clear().apply();msg("Decoder memory cleared.");});body.addView(r3);
  LinearLayout rate=row();button(rate,"Playback engine: "+(defaultLiveEngine()?"Default ExoPlayer":"Custom"),()->{prefs.edit().putBoolean("default_live_engine",!defaultLiveEngine()).apply();playbackSettings();});button(rate,"Live FPS (custom): "+(prefs.getBoolean("match_frame_rate",true)?"Auto":"Off"),()->{prefs.edit().putBoolean("match_frame_rate",!prefs.getBoolean("match_frame_rate",true)).apply();playbackSettings();});button(rate,"Live buffer: "+(defaultLiveEngine()?"Stock":liveStartupBufferMs()+" ms"),this::liveBufferSettings);body.addView(rate);
  displayStatus=text(displaySummary(),14);body.addView(displayStatus);
  LinearLayout back=row();button(back,"Reset playback defaults",this::resetVisualSettings);button(back,"Back",this::settings);body.addView(back);r.getChildAt(0).requestFocus();
 }
 int liveStartupBufferMs(){int value=prefs.getInt("live_startup_buffer_ms",250);return value==250||value==500||value==750||value==2500?value:250;}
 void liveBufferSettings(){
  if(defaultLiveEngine()){new AlertDialog.Builder(this).setTitle("Default ExoPlayer").setMessage("Stock buffer is unchanged. Select Custom to adjust Live TV startup buffer.").setPositiveButton("OK",null).show();return;}
  int[] values={250,500,750,2500};String[] labels={"Ultra Fast (250 ms)","Fast (500 ms)","Balanced (750 ms)","IBO 4.3 buffer profile (2500 ms / 5 s recovery)"};int selected=liveStartupBufferMs()==2500?3:liveStartupBufferMs()==750?2:liveStartupBufferMs()==500?1:0;
  new AlertDialog.Builder(this).setTitle("Live TV startup buffer (TS / M3U8)").setSingleChoiceItems(labels,selected,(d,n)->{prefs.edit().putInt("live_startup_buffer_ms",values[n]).apply();d.dismiss();playbackSettings();}).setNegativeButton("Cancel",null).show();
 }
 boolean defaultLiveEngine(){return prefs.getBoolean("default_live_engine",false);}
 String rangeFor(boolean live){return !defaultLiveEngine()&&Build.VERSION.SDK_INT>=29&&prefs.getString(live?"range_live":"range_vod","Auto").equals("SDR Full")?"SDR Full":"Auto";}
 String rangeName(String mode){return mode.equals("SDR Full")?"Force SDR":"Auto / native";}
 void rangeSettings(boolean live){
  if(defaultLiveEngine()){new AlertDialog.Builder(this).setTitle("Default ExoPlayer").setMessage("Default mode uses native picture output. Switch Playback engine to Custom to change SDR/HDR preferences.").setPositiveButton("OK",null).show();return;}
  String[] choices=Build.VERSION.SDK_INT>=29?new String[]{"Auto / native (full resolution)","Force SDR (full resolution)"}:new String[]{"Auto / native (full resolution)"};String[] values=Build.VERSION.SDK_INT>=29?new String[]{"Auto","SDR Full"}:new String[]{"Auto"};
  new AlertDialog.Builder(this).setTitle(live?"Live TV picture mode":"Movie and series picture mode").setSingleChoiceItems(choices,Arrays.asList(values).indexOf(rangeFor(live)),(d,n)->{prefs.edit().putString(live?"range_live":"range_vod",values[n]).apply();d.dismiss();playbackSettings();}).setNegativeButton("Cancel",null).show();
 }
 void languageDialog(String key,String title){String[] labels={"Automatic","English","Spanish","French","German","Italian","Arabic"},codes={null,"en","es","fr","de","it","ar"};new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->{prefs.edit().putString(key,codes[i]).apply();playbackSettings();}).setNegativeButton("Cancel",null).show();}
 // Window-local display requests only: no secure settings, Shizuku or global overrides.
 static final int DISPLAY_MAX=-1, DISPLAY_60=-60, DISPLAY_120=-120;
 android.widget.TextView displayStatus;
 android.hardware.display.DisplayManager displayManager;
 final android.hardware.display.DisplayManager.DisplayListener displayListener=new android.hardware.display.DisplayManager.DisplayListener(){
  @Override public void onDisplayAdded(int id){}
  @Override public void onDisplayRemoved(int id){}
  @Override public void onDisplayChanged(int id){if(id==getWindowManager().getDefaultDisplay().getDisplayId()){updateDisplayStatus();if(premiumPlayer!=null)premiumPlayer.refreshInfo(true);if(requestedFps>0)android.util.Log.i("SharkDisplay","Android display changed: "+getWindowManager().getDefaultDisplay().getMode().getRefreshRate()+" Hz");}}
 };
 @Override protected void onResume(){super.onResume();displayManager=(android.hardware.display.DisplayManager)getSystemService(DISPLAY_SERVICE);if(displayManager!=null)displayManager.registerDisplayListener(displayListener,ui);updateDisplayStatus();}
 @Override protected void onPause(){if(displayManager!=null){displayManager.unregisterDisplayListener(displayListener);displayManager=null;}super.onPause();}
 static int chooseDisplayMode(android.view.Display.Mode current,android.view.Display.Mode[] modes,int request){
  if(request==0)return 0;
  int chosen=0;float best=Float.MAX_VALUE;
  for(android.view.Display.Mode m:modes){
   if(request>0){if(m.getModeId()==request)return request;continue;}
   if(m.getPhysicalWidth()!=current.getPhysicalWidth()||m.getPhysicalHeight()!=current.getPhysicalHeight())continue;
   float score;
   if(request==DISPLAY_MAX){score=-m.getRefreshRate();}
   else if(request==DISPLAY_60||request==DISPLAY_120){score=Math.abs(m.getRefreshRate()+request);if(score>1f)continue;}
   else continue;
   if(score<best){best=score;chosen=m.getModeId();}
  }
  return chosen;
 }
 String displaySummary(){
  android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode m=d.getMode();
  int req=displayRequest(),chosen=chooseDisplayMode(m,d.getSupportedModes(),req);
  String policy=req==0?"System automatic":req==DISPLAY_MAX?"Highest at current resolution":req==DISPLAY_60?"60 Hz":req==DISPLAY_120?"120 Hz":"Specific display mode";
  String result=req==0?"System controlled":chosen==0?"Unavailable; system fallback":chosen==m.getModeId()?"Active":"Requested; firmware decides";
  return String.format(Locale.US,"Current display: %d × %d · %.2f Hz\n%s · %s",m.getPhysicalWidth(),m.getPhysicalHeight(),m.getRefreshRate(),policy,result);
 }
 void updateDisplayStatus(){if(displayStatus!=null&&displayStatus.isAttachedToWindow()&&prefs!=null)displayStatus.setText(displaySummary());}
 void displayDialog(){
  android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode current=d.getMode();android.view.Display.Mode[] modes=d.getSupportedModes();
  ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> values=new ArrayList<>();
  labels.add("Automatic (system)");values.add(0);
  labels.add("Highest refresh at current resolution");values.add(DISPLAY_MAX);
  if(chooseDisplayMode(current,modes,DISPLAY_60)!=0){labels.add("60 Hz (current resolution)");values.add(DISPLAY_60);}
  if(chooseDisplayMode(current,modes,DISPLAY_120)!=0){labels.add("120 Hz (current resolution)");values.add(DISPLAY_120);}
  for(android.view.Display.Mode m:modes){labels.add(String.format(Locale.US,"%d × %d · %.2f Hz",m.getPhysicalWidth(),m.getPhysicalHeight(),m.getRefreshRate()));values.add(m.getModeId());}
  new AlertDialog.Builder(this).setTitle(String.format(Locale.US,"Display refresh rate · %.2f Hz active",current.getRefreshRate()))
   .setSingleChoiceItems(labels.toArray(new String[0]),values.indexOf(prefs.getInt("display_mode",0)),(dialog,i)->{prefs.edit().putInt("display_mode",values.get(i)).apply();dialog.dismiss();applyDisplayMode();playbackSettings();})
   .setNegativeButton("Cancel",null).show();
 }
 // An opted-in movie match can request a same-resolution integer-multiple mode.
 // No unsupported 120 Hz modes and no global settings changes.
 boolean autoRefresh(){return page.equals("Live TV")&&!defaultLiveEngine()&&prefs.getBoolean("match_frame_rate",true)&&prefs.getInt("display_mode",0)==0;}
 boolean movieDisplayPolicy(){return full&&(page.equals("Movies")||page.equals("Series"));}
 int displayRequest(){return movieDisplayPolicy()?DISPLAY_60:defaultLiveEngine()?0:prefs.getInt("display_mode",0); }
 void clearSurfaceHint(){
  android.view.Surface surface=matchSurface;matchSurface=null;
  if(Build.VERSION.SDK_INT>=30&&surface!=null&&surface.isValid())try{surface.setFrameRate(0f,android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}catch(RuntimeException ignored){}
 }
 void clearRefreshMatch(boolean restore){
  if(matchTask!=null){ui.removeCallbacks(matchTask);matchTask=null;}
  clearSurfaceHint();matchProbe=null;requestedFps=requestedHz=-1;refreshRequestedAt=-1;refreshRequest="Not requested";
  if(restore&&matchedWindow){matchedWindow=false;applyDisplayMode();}
 }
 // Live preview never requests a content refresh-rate switch, including Force SDR.
 boolean refreshInCurrentView(){return full;}
 void scheduleRefreshMatch(ExoPlayer p,FrameRateSample sample,int attempts){
  if(!refreshInCurrentView()||stopped||!autoRefresh()||sample==null||playbackProbe==null)return;
  final PlaybackProbe probe=playbackProbe;
  if(matchProbe==probe&&requestedFps>0)return;
  if(matchTask!=null)ui.removeCallbacks(matchTask);
  refreshRequest="Collecting stream FPS";
  matchTask=()->{matchTask=null;
   if(player!=p||playbackProbe!=probe||stopped||!refreshInCurrentView()||!autoRefresh()||p.getPlayerError()!=null)return;
   if(startupFirstMs<0||p.getPlaybackState()!=Player.STATE_READY){if(attempts<8)scheduleRefreshMatch(p,sample,attempts+1);else refreshRequest="Waiting for decoded video";return;}
   Format f=p.getVideoFormat();float declared=f==null?-1:f.frameRate,estimated=sample.estimate();if(!RefreshMatch.valid(declared))declared=probe.sourceFps;
   
   float fps=RefreshMatch.valid(declared)?declared:estimated;
   if(!RefreshMatch.valid(fps)){if(attempts<8)scheduleRefreshMatch(p,sample,attempts+1);else refreshRequest="Stream FPS unavailable";return;}
   requestRefresh(probe,fps);
  };ui.postDelayed(matchTask,attempts==0?1500:1000);
 }
 void requestRefresh(PlaybackProbe probe,float fps){
  if(!full||stopped||!autoRefresh()||probe!=playbackProbe)return;
  android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode current=d.getMode();
  ArrayList<android.view.Display.Mode> modes=new ArrayList<>();
  for(android.view.Display.Mode mode:d.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())modes.add(mode);
  float[] rates=new float[modes.size()];for(int n=0;n<rates.length;n++)rates[n]=modes.get(n).getRefreshRate();
  int chosen=RefreshMatch.select(current.getRefreshRate(),fps,rates);
  matchProbe=probe;requestedFps=fps;requestedHz=-1;refreshRequestedAt=SystemClock.elapsedRealtime();
  if(chosen>=0){
   android.view.Display.Mode best=modes.get(chosen);requestedHz=best.getRefreshRate();
   WindowManager.LayoutParams attrs=getWindow().getAttributes();
   if(attrs.preferredDisplayModeId!=best.getModeId()||attrs.preferredRefreshRate!=0f){
    attrs.preferredDisplayModeId=best.getModeId();attrs.preferredRefreshRate=0f;
    try{getWindow().setAttributes(attrs);matchedWindow=true;refreshRequest="Window mode requested";}catch(RuntimeException ignored){refreshRequest="Window request failed";}
   }else refreshRequest="Window mode already requested";
  }else if(RefreshMatch.compatible(current.getRefreshRate(),fps)){
   WindowManager.LayoutParams attrs=getWindow().getAttributes();
   if(matchedWindow&&attrs.preferredDisplayModeId!=current.getModeId()){attrs.preferredDisplayModeId=current.getModeId();attrs.preferredRefreshRate=0f;try{getWindow().setAttributes(attrs);}catch(RuntimeException ignored){}}
   requestedHz=current.getRefreshRate();refreshRequest="Already compatible";
  }else{
   if(matchedWindow){matchedWindow=false;applyDisplayMode();}
   View surfaceView=video==null?null:video.getVideoSurfaceView();
   if(Build.VERSION.SDK_INT>=30&&surfaceView instanceof android.view.SurfaceView){
    android.view.Surface surface=((android.view.SurfaceView)surfaceView).getHolder().getSurface();
    if(surface!=null&&surface.isValid()){try{
     if(Build.VERSION.SDK_INT>=31)surface.setFrameRate(fps,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
     else surface.setFrameRate(fps,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
     matchSurface=surface;refreshRequest="Surface FPS hint; no compatible exposed mode";
    }catch(RuntimeException ignored){refreshRequest="Surface request failed";}}
    else refreshRequest="No compatible display mode";
   }else refreshRequest="No compatible display mode";
  }
  android.util.Log.i("SharkDisplay","Auto FPS request: "+fps+" fps; target="+requestedHz+" Hz; current="+current.getRefreshRate()+" Hz; "+refreshRequest);
 }
 String refreshDiagnostics(float fps,float hz){
  android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode current=d.getMode();
  TreeSet<Float> rates=new TreeSet<>();for(android.view.Display.Mode m:d.getSupportedModes())if(m.getPhysicalWidth()==current.getPhysicalWidth()&&m.getPhysicalHeight()==current.getPhysicalHeight())rates.add(m.getRefreshRate());
  StringBuilder list=new StringBuilder();for(float r:rates){if(list.length()>0)list.append(", ");list.append(String.format(Locale.US,"%.2f",r));}
  boolean movie=movieDisplayPolicy();int movieMode=movie?chooseDisplayMode(current,d.getSupportedModes(),DISPLAY_60):0;
  String request=movie?(movieMode!=0?"Movies / Series: 60 Hz preference":"Movies / Series: 60 Hz unavailable; system fallback"):defaultLiveEngine()?"Stock engine":prefs.getInt("display_mode",0)!=0?"Manual display mode":!autoRefresh()?"Off":!refreshInCurrentView()?"Preview (automatic switch deferred)":refreshRequest;
  String outcome=RefreshMatch.exact(hz,fps)?"Exact integer multiple":RefreshMatch.compatible(hz,fps)?"Nominal integer multiple":requestedFps>0&&refreshRequestedAt>=0&&SystemClock.elapsedRealtime()-refreshRequestedAt<5000?"Request pending; Android has not matched":requestedFps>0?"Not confirmed by Android":"Not matched";
  if(movie)outcome=movieMode==0?"60 Hz unavailable; system controlled":current.getModeId()==movieMode?"Movie display preference active":"60 Hz preference requested; Android decides";
  return "\nAuto FPS: "+request+"\nRequested refresh: "+(movie?(movieMode!=0?"60 Hz (same resolution)":"None"):requestedHz>0?String.format(Locale.US,"%.2f Hz",requestedHz):requestedFps>0?String.format(Locale.US,"%.2f fps content hint",requestedFps):"None")+"\nRequest result: "+outcome+"\nSupported modes (same resolution): "+(list.length()==0?"Not reported":list+" Hz");
 }
 static boolean rateMatches(float hz,float fps){return RefreshMatch.compatible(hz,fps);}
 void applyDisplayMode(){
  android.view.Display d=getWindowManager().getDefaultDisplay();
  int chosen=chooseDisplayMode(d.getMode(),d.getSupportedModes(),displayRequest());
  WindowManager.LayoutParams attrs=getWindow().getAttributes();
  if(attrs.preferredDisplayModeId!=chosen||attrs.preferredRefreshRate!=0f){attrs.preferredDisplayModeId=chosen;attrs.preferredRefreshRate=0f;getWindow().setAttributes(attrs);}
  android.util.Log.i("SharkDisplay",displaySummary());updateDisplayStatus();
 }
 String prefix(){return page.equals("Live TV")?"live":page.equals("Movies")?"vod":"series";}
 static String firstArtwork(JSONObject j){for(String field:new String[]{"stream_icon","cover","cover_big","movie_image","poster_path"}){String u=j.optString(field,"").trim();if(u.startsWith("https://")||u.startsWith("http://"))return u;}return "";}
 static List<Item> parse(JSONArray a,String id,String name){List<Item> list=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&!j.optString(id).isEmpty()){Item item=new Item(j.optString(id),j.optString(name),j.optString("container_extension","mp4"));if(!id.equals("category_id")){item.epgId=j.optString("epg_channel_id","");if("null".equals(item.epgId))item.epgId="";item.art=firstArtwork(j);item.year=j.optString("year",j.optString("releaseDate",j.optString("release_date","")));item.tmdb=j.optString("tmdb_id",j.optString("tmdb",""));}list.add(item);}}return list;}
 void open(String p){returnAnchor=returnCategory=-1;returnCatalogue="";page=p;cat="";seriesId="";catPos=itemPos=0;selected=null;cats=new ArrayList<>();items=new ArrayList<>();browser(false);loadCats();}
 RecyclerView list(){RecyclerView r=new RecyclerView(this);r.setLayoutManager(new LinearLayoutManager(this));r.setHasFixedSize(true);r.setItemAnimator(null);return r;}
 class Rows extends RecyclerView.Adapter<Rows.H>{List<Item> data;Pick pick;Rows(List<Item>d,Pick p){data=d;pick=p;}class H extends RecyclerView.ViewHolder{TextView t;H(TextView t){super(t);this.t=t;}}
 public H onCreateViewHolder(ViewGroup p,int type){TextView t=text("",15);t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(8),0,dp(5),0);t.setFocusable(true);t.setBackground(focus());premiumHomeFocus(t);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(38));lp.setMargins(dp(2),dp(1),dp(2),dp(2));t.setLayoutParams(lp);H h=new H(t);t.setOnClickListener(v->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});return h;}
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
  HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(3500);conn.setReadTimeout(5000);
  try{if(conn.getResponseCode()!=200)return null;try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted()||out.size()+n>8*1024*1024)return null;out.write(buf,0,n);}byte[] data=out.toByteArray();android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return null;android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();opt.inSampleSize=1;while(bounds.outWidth/opt.inSampleSize>1000||bounds.outHeight/opt.inSampleSize>1500)opt.inSampleSize*=2;android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,opt);if(Thread.currentThread().isInterrupted())return null;if(b!=null)posterCache.put(url,b);return b;}}finally{conn.disconnect();}
 }
 boolean showPosters(){return page.equals("Movies")||(page.equals("Series")&&seriesId.isEmpty());}
 void setItemsAdapter(List<Item> data,Pick pick){
  if(showPosters()){
   final RecyclerView grid=right;
   int h=grid.getHeight()-grid.getPaddingTop()-grid.getPaddingBottom();
   int w=grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight();
   if(h<=0)h=dp(Math.max(160,getResources().getConfiguration().screenHeightDp-152));
   if(w<=0)w=dp(Math.max(160,Math.round(getResources().getConfiguration().screenWidthDp*.70f)));
   int cardHeight=posterHeight(h),columns=posterColumns(w,cardHeight);
   GridLayoutManager manager=new GridLayoutManager(this,columns);
   Posters adapter=new Posters(data,pick,page.equals("Series")?"tv":"movie",cardHeight);
   grid.setLayoutManager(manager);grid.setAdapter(adapter);
   grid.post(()->updatePosterGeometry(grid));
  }
  else{if(!(right.getLayoutManager() instanceof LinearLayoutManager)||right.getLayoutManager() instanceof GridLayoutManager)right.setLayoutManager(new LinearLayoutManager(this));if(page.equals("Live TV")){right.setAdapter(new LiveGuideRows(data,pick,false,0,null));ui.removeCallbacks(tickRows);ui.postDelayed(tickRows,60000);}else right.setAdapter(new Rows(data,pick));}
 }
 int posterHeight(int usableHeight){return Math.max(dp(40),usableHeight/2-2*dp(3));}
 int posterColumns(int usableWidth,int cardHeight){
  float idealWidth=(cardHeight-2*dp(3))*2f/3f+2*dp(3);
  return Math.max(2,Math.min(12,Math.round(usableWidth/Math.max(dp(36),idealWidth))));
 }
 void updatePosterGeometry(RecyclerView grid){
  if(grid!=right||!(grid.getAdapter() instanceof Posters)||!(grid.getLayoutManager() instanceof GridLayoutManager))return;
  if(grid.isComputingLayout()){grid.post(()->updatePosterGeometry(grid));return;}
  int h=grid.getHeight()-grid.getPaddingTop()-grid.getPaddingBottom();
  int w=grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight();
  if(h<=0||w<=0)return;
  Posters adapter=(Posters)grid.getAdapter();GridLayoutManager manager=(GridLayoutManager)grid.getLayoutManager();
  int height=posterHeight(h),columns=posterColumns(w,height);
  if(adapter.cardHeight==height&&manager.getSpanCount()==columns)return;
  adapter.cardHeight=height;
  if(manager.getSpanCount()!=columns)manager.setSpanCount(columns);
  for(int i=0;i<grid.getChildCount();i++){View child=grid.getChildAt(i);ViewGroup.LayoutParams lp=child.getLayoutParams();if(lp!=null&&lp.height!=height){lp.height=height;child.setLayoutParams(lp);}}
  adapter.notifyDataSetChanged();
 }
 class Posters extends RecyclerView.Adapter<Posters.H>{
  final List<Item> data;final Pick pick;final String type;int cardHeight;
  Posters(List<Item>d,Pick p,String t,int height){data=d;pick=p;type=t;cardHeight=height;}
  class H extends RecyclerView.ViewHolder{ImageView art;TextView title;Future<?> job;Runnable retryTask;Item bound;H(LinearLayout v,ImageView a,TextView t){super(v);art=a;title=t;}}
  @Override public H onCreateViewHolder(ViewGroup parent,int viewType){
   LinearLayout v=col();v.setPadding(dp(3),dp(3),dp(3),dp(3));v.setFocusable(true);v.setClickable(true);v.setBackground(posterFocus());premiumHomeFocus(v);
   FrameLayout frame=new FrameLayout(MainActivity.this);v.addView(frame,new LinearLayout.LayoutParams(-1,-1));ImageView a=new ImageView(MainActivity.this);a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackgroundColor(0xff0b1529);frame.addView(a,new FrameLayout.LayoutParams(-1,-1));
    TextView t=text("",14);t.setGravity(Gravity.CENTER);t.setPadding(dp(4),dp(4),dp(4),dp(4));t.setMaxLines(4);frame.addView(t,new FrameLayout.LayoutParams(-1,-1));
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,cardHeight);lp.setMargins(dp(3),dp(3),dp(3),dp(3));v.setLayoutParams(lp);
   H h=new H(v,a,t);v.setOnFocusChangeListener((view,focused)->{if(focused&&h.bound!=null&&status!=null)status.setText(h.bound.name);});
   v.setOnClickListener(view->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});return h;
  }
  void cancelPoster(H h){if(h.retryTask!=null){h.itemView.removeCallbacks(h.retryTask);h.retryTask=null;}if(h.job!=null){h.job.cancel(true);h.job=null;posterIo.purge();}}
  @Override public void onBindViewHolder(H h,int pos){ViewGroup.LayoutParams lp=h.itemView.getLayoutParams();if(lp!=null&&lp.height!=cardHeight){lp.height=cardHeight;h.itemView.setLayoutParams(lp);}cancelPoster(h);Item i=data.get(pos);h.bound=i;h.title.setText(i.name);h.title.setVisibility(View.VISIBLE);h.itemView.setContentDescription(i.name);h.art.setImageDrawable(null);if(h.itemView.hasFocus()&&status!=null)status.setText(i.name);int token=screenToken;String key=tmdbKey();
   loadPoster(h,i,token,key,0);
  }
  void paintPoster(H h,Item i,int token,android.graphics.Bitmap b){if(b!=null&&!Thread.currentThread().isInterrupted())ui.post(()->{if(h.bound==i&&token==screenToken&&!isFinishing()){h.art.setImageBitmap(b);h.title.setVisibility(View.GONE);}});}
   void loadPoster(H h,Item i,int token,String key,int retry){
    if(h.bound!=i||token!=screenToken||isFinishing())return;
    try{h.job=posterIo.submit(()->{
     String cacheKey=key+"|"+type+"|"+i.tmdb+"|"+i.name+"|"+yearOf(i);
     String known=posterUrls.get(cacheKey);android.graphics.Bitmap cached=known==null?null:posterCache.get(known);
     if(cached!=null){paintPoster(h,i,token,cached);return;}
     try{paintPoster(h,i,token,posterBitmap(i.art));}catch(Exception ignored){}
     if(!key.isEmpty()&&!Thread.currentThread().isInterrupted())try{String best=artwork(i,type,key);if(!best.equals(i.art))paintPoster(h,i,token,posterBitmap(best));}catch(Exception ignored){}
    });}catch(RejectedExecutionException busy){if(retry<8){h.retryTask=()->{h.retryTask=null;loadPoster(h,i,token,key,retry+1);};h.itemView.postDelayed(h.retryTask,250);}}
   }
   @Override public void onViewRecycled(H h){cancelPoster(h);h.bound=null;h.art.setImageDrawable(null);super.onViewRecycled(h);}
   @Override public void onViewDetachedFromWindow(H h){cancelPoster(h);super.onViewDetachedFromWindow(h);}
   @Override public void onViewAttachedToWindow(H h){super.onViewAttachedToWindow(h);if(h.bound!=null&&h.job==null&&h.art.getDrawable()==null)loadPoster(h,h.bound,screenToken,tmdbKey(),0);}
  public int getItemCount(){return data.size();}
 }
 void focusAt(RecyclerView r,int p){if(r.getAdapter()==null||r.getAdapter().getItemCount()==0)return;int i=Math.max(0,Math.min(p,r.getAdapter().getItemCount()-1));r.scrollToPosition(i);r.post(()->{RecyclerView.ViewHolder h=r.findViewHolderForAdapterPosition(i);if(h!=null)h.itemView.requestFocus();else r.requestFocus();});}
 void catAdapter(){left.setAdapter(new Rows(cats,(i,p)->{catPos=p;itemPos=0;cat=i.id;seriesId="";loadItems();}));}
 boolean mobileCatalogueNavigation(){
  android.content.pm.PackageManager pm=getPackageManager();
  android.app.UiModeManager mode=(android.app.UiModeManager)getSystemService(Context.UI_MODE_SERVICE);
  boolean tv=pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    ||pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
    ||(mode!=null&&mode.getCurrentModeType()==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION);
  return !tv&&pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN);
 }
 void catalogueFooter(){
  status=text("",12);status.setSingleLine();status.setEllipsize(android.text.TextUtils.TruncateAt.END);
  if(!mobileCatalogueNavigation()){body.addView(status,new LinearLayout.LayoutParams(-1,dp(23)));return;}
  LinearLayout footer=row();footer.setGravity(Gravity.CENTER_VERTICAL);
  footer.addView(status,new LinearLayout.LayoutParams(0,dp(48),1));
  LinearLayout nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);
  int width=getResources().getConfiguration().screenWidthDp;
  footer.addView(nav,new LinearLayout.LayoutParams(dp(width<400?160:190),dp(48)));
  button(nav,"Home",this::home);
  body.addView(footer,new LinearLayout.LayoutParams(-1,dp(48)));
 }
 void browser(boolean restore){screen(page);{LinearLayout head=(LinearLayout)body.getChildAt(0);TextView menu=text("⋮",24);menu.setGravity(Gravity.CENTER);menu.setContentDescription(page+" options");menu.setFocusable(true);menu.setClickable(true);premiumHomeFocus(menu);head.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(43)));menu.setOnClickListener(v->{if(page.equals("Live TV"))liveOptions();else catalogueOptions();});}LinearLayout heads=row();heads.addView(text("Categories",14),new LinearLayout.LayoutParams(0,dp(25),1));heads.addView(text(page.equals("Live TV")?"Channels":"Titles",14),new LinearLayout.LayoutParams(0,dp(25),1.4f));boolean live=page.equals("Live TV");if(live)heads.addView(new View(this),new LinearLayout.LayoutParams(0,dp(25),1.6f));body.addView(heads);LinearLayout r=row();body.addView(r,new LinearLayout.LayoutParams(-1,0,1));left=list();right=list();final RecyclerView grid=right;grid.addOnLayoutChangeListener((v,l,t,rr,b,ol,ot,orr,ob)->{if(rr-l!=orr-ol||b-t!=ob-ot)grid.post(()->updatePosterGeometry(grid));});r.addView(left,new LinearLayout.LayoutParams(0,-1,1));r.addView(right,new LinearLayout.LayoutParams(0,-1,live?1.4f:2.5f));catAdapter();setItemsAdapter(items,this::pick);if(live){LinearLayout c=col();c.setPadding(dp(4),0,0,0);r.addView(c,new LinearLayout.LayoutParams(0,-1,1.6f));preview=new FrameLayout(this);preview.setBackgroundColor(Color.BLACK);preview.setForeground(previewPulse());c.addView(preview,new LinearLayout.LayoutParams(-1,0,1.7f));TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));epg=text(selected==null?"Programme information":selected.name,13);epg.setMaxLines(7);epg.setGravity(Gravity.TOP);epg.setPadding(dp(9),dp(5),dp(9),dp(3));LinearLayout guidePanel=col();guidePanel.setBackground(bg(0xff193554,0xff0b172e,0xff3b78b5,1));LinearLayout.LayoutParams guideLp=new LinearLayout.LayoutParams(-1,0,1);guideLp.topMargin=dp(4);c.addView(guidePanel,guideLp);guidePanel.addView(epg,new LinearLayout.LayoutParams(-1,0,1));programmeProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);programmeProgress.setMax(100);programmeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(UI_GOLD));programmeProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff335175));programmeProgress.setVisibility(View.INVISIBLE);LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(-1,dp(6));barLp.setMargins(dp(9),0,dp(9),dp(6));guidePanel.addView(programmeProgress,barLp);LinearLayout b=row();button(b,"Full screen",()->{if(selected!=null)playFull(selected);});button(b,"Favourite",this::fav);c.addView(b);}catalogueFooter();if(restore){if(page.equals(returnCatalogue)&&returnAnchor>=0)restoreCataloguePosition();else{left.scrollToPosition(catPos);focusAt(right,itemPos);}}}
 void catalogueOptions(){if(full||!(page.equals("Movies")||page.equals("Series")))return;new AlertDialog.Builder(this).setTitle(page+" options").setItems(new String[]{"Home","Search","Retry"},(d,n)->{switch(n){case 0:home();break;case 1:search();break;case 2:retryCatalogue();break;}}).setNegativeButton("Cancel",null).show();}
 void retryCatalogue(){clearCatalogue();if(cats.isEmpty())loadCats();else loadItems();}
 void liveOptions(){if(full||!page.equals("Live TV"))return;new AlertDialog.Builder(this).setTitle("Live TV options").setItems(new String[]{"Home","Search","TV Guide","Retry","Playback Information"},(d,n)->{switch(n){case 0:home();break;case 1:search();break;case 2:openGuide();break;case 3:retryCatalogue();break;case 4:new AlertDialog.Builder(this).setTitle("Playback Information").setMessage(currentPlaybackDiagnostics()).setPositiveButton("Close",null).show();break;}}).setNegativeButton("Cancel",null).show();}
 void loadCats(){String k=prefix(),url=api("get_"+k+"_categories","");int req=++loadToken;msg("Loading categories…");catalogueTask("cats:"+k,CATEGORY_TTL,false,req,()->parse(new JSONArray(get(url)),"category_id","category_name"),a->{cats=new ArrayList<>();cats.add(new Item("","All",""));if(page.equals("Live TV"))cats.add(new Item("@fav","Favourites",""));cats.addAll(a);catAdapter();focusAt(left,0);loadItems();});}
 void loadItems(){clearGuideFocus();ui.removeCallbacks(tickRows);ui.removeCallbacks(refreshEpg);cancelPreviewGuideJob();release();selected=null;epgToken++;if(epg!=null)epg.setText("Programme information");if(programmeProgress!=null)programmeProgress.setVisibility(View.INVISIBLE);if(preview!=null){preview.removeAllViews();TextView t=text("Press OK to preview",14);t.setGravity(Gravity.CENTER);preview.addView(t,new FrameLayout.LayoutParams(-1,-1));}int req=++loadToken;String k=prefix(),c=cat.equals("@fav")?"":cat;String url=api(k.equals("series")?"get_series":"get_"+k+"_streams",c.isEmpty()?"":"&category_id="+enc(c));if(right!=null){items=new ArrayList<>();setItemsAdapter(items,this::pick);}msg("Loading "+page+"…");catalogueTask("items:"+k+":"+c,STREAM_TTL,false,req,()->parse(new JSONArray(get(url)),k.equals("series")?"series_id":"stream_id","name"),a->{if(req!=loadToken)return;items=a;if(cat.equals("@fav")){Set<String> f=prefs.getStringSet("fav",Collections.emptySet());items.removeIf(i->!f.contains(i.id));}if(page.equals("Live TV")&&regionalGuide!=null){String categoryRegion="";for(Item category:cats)if(category.id.equals(c)){categoryRegion=RegionalGuideNames.region(category.name,"");break;}Set<String> regions=new LinkedHashSet<>();for(Item channel:items){channel.epgRegion=RegionalGuideNames.region(channel.name,channel.epgId);if(channel.epgRegion.isEmpty())channel.epgRegion=categoryRegion;if(!channel.epgRegion.isEmpty())regions.add(channel.epgRegion);}for(String region:regions)regionalGuide.ensureRegion(region);}setItemsAdapter(items,this::pick);msg(items.size()+" titles");});}
 void search(){EditText e=new EditText(this);e.setSingleLine();styleInput(e);new AlertDialog.Builder(this).setTitle("Search current list").setView(e).setPositiveButton("Search",(d,w)->{String q=e.getText().toString().toLowerCase(Locale.ROOT);List<Item>a=new ArrayList<>();for(Item i:items)if(i.name.toLowerCase(Locale.ROOT).contains(q))a.add(i);setItemsAdapter(a,(i,p)->pick(i,items.indexOf(i)));focusAt(right,0);}).setNegativeButton("Cancel",null).show();}
 void pick(Item i,int p){itemPos=p;if(page.equals("Live TV")){if(selected!=null&&selected.id.equals(i.id)&&player!=null&&player.getPlayerError()==null){playFull(i);return;}selected=i;attach(preview,i,false);loadEpg(i);return;}if(page.equals("Series")&&seriesId.isEmpty()){episodes(i);return;}playFull(i);}
 void episodes(Item s){msg("Loading episodes…");int req=++loadToken;String url=api("get_series_info","&series_id="+enc(s.id));catalogueTask("episodes:"+s.id,STREAM_TTL,false,req,()->{JSONObject j=new JSONObject(get(url)).optJSONObject("episodes");List<Item>a=new ArrayList<>();if(j!=null){List<String> keys=new ArrayList<>();j.keys().forEachRemaining(keys::add);Collections.sort(keys);for(String k:keys){JSONArray e=j.optJSONArray(k);if(e!=null)for(int n=0;n<e.length();n++){JSONObject o=e.optJSONObject(n);if(o!=null&&!o.optString("id").isEmpty())a.add(new Item(o.optString("id"),"S"+k+" E"+o.optString("episode_num")+" · "+o.optString("title"),o.optString("container_extension","mp4")));}}}return a;},a->{if(req!=loadToken)return;seriesId=s.id;items=a;setItemsAdapter(items,this::pick);msg(s.name);focusAt(right,0);});}
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
 static final ThreadLocal<GuideTask> runningGuide=new ThreadLocal<>();
 static final class GuideTask extends FutureTask<Void>{
  private okhttp3.Call call;
  GuideTask(Runnable work){super(work,null);}
  @Override public void run(){runningGuide.set(this);try{super.run();}finally{runningGuide.remove();}}
  synchronized void attach(okhttp3.Call next){if(isCancelled())next.cancel();else call=next;}
  synchronized void detach(okhttp3.Call done){if(call==done)call=null;}
  @Override public boolean cancel(boolean interrupt){boolean cancelled=super.cancel(interrupt);synchronized(this){if(cancelled&&call!=null)call.cancel();}return cancelled;}
 }
 static Future<?> submitGuide(ThreadPoolExecutor pool,Runnable work){GuideTask task=new GuideTask(work);pool.execute(task);return task;}
 static String getGuide(String url)throws Exception{return readProvider(GUIDE_HTTP,url,"SharkSky/5",4*1024*1024);}
 GuideData fetchGuide(Item item,String fullUrl,String shortUrl,String zone,boolean extended)throws Exception{
  List<Programme> shows=new ArrayList<>();long now=System.currentTimeMillis();boolean fullLoaded=false;
  try{shows=parseGuide(getGuide(extended?fullUrl:shortUrl),zone);fullLoaded=extended&&!shows.isEmpty();}catch(Exception ignored){}
  if(Thread.currentThread().isInterrupted())throw new InterruptedException();
  GuideData first=new GuideData(shows,fullLoaded);
  if(extended||currentProgramme(first,now)==null||nextProgramme(first,now)==null){
   try{List<Programme> more=parseGuide(getGuide(extended?shortUrl:fullUrl),zone);if(!more.isEmpty()){shows=mergeGuide(shows,more);if(!extended)fullLoaded=true;}}catch(Exception ignored){}
  }
  if(Thread.currentThread().isInterrupted())throw new InterruptedException();
  String region=item.epgRegion.isEmpty()?RegionalGuideNames.region(item.name,item.epgId):item.epgRegion;
  if(regionalGuide!=null&&!region.isEmpty()){regionalGuide.ensureRegion(region);List<Programme> regional=regionalGuide.lookup(region,item.name,item.epgId,extended);if(!regional.isEmpty()){shows=mergeGuide(shows,regional);if(extended)fullLoaded=true;}}
  return new GuideData(shows,fullLoaded);
 }
 static List<Programme> mergeGuide(List<Programme> primary,List<Programme> fallback){
  List<Programme> out=new ArrayList<>(primary);
  for(Programme p:fallback){if(p.start<=0||p.end<=p.start)continue;boolean overlap=false;for(Programme q:out)if(q.start<p.end&&q.end>p.start){overlap=true;break;}if(!overlap)out.add(p);}
  Collections.sort(out,(a,b)->Long.compare(a.start,b.start));return out;
 }
 // Remove cancelled FutureTasks from the executor immediately, rather than retaining stale channel work.
 void cancelPreviewGuideJob(){if(previewGuideJob!=null){previewGuideJob.cancel(true);previewGuideJob=null;epgIo.purge();}}
 void cancelFullGuideJob(){if(fullGuideJob!=null){fullGuideJob.cancel(true);fullGuideJob=null;epgIo.purge();}}
 void suspendPreviewGuide(){if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;ui.removeCallbacks(refreshEpg);epgToken++;cancelPreviewGuideJob();}
 void clearGuideFocus(){if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;highlightedGuideItem=null;}
 void highlightGuide(Item i,GuideData cached){
  if(i==null||epg==null||full||!page.equals("Live TV"))return;
  if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);
  cancelPreviewGuideJob();epgToken++;
  highlightedGuideItem=i;if(programmeProgress!=null)programmeProgress.setVisibility(View.INVISIBLE);
  GuideData data=cached!=null?cached:guideCache.get(guideKey(i,false));
  if(data!=null)paintPreviewGuide(i,data);else epg.setText(i.name+"\nLoading programme information…");
  int token=screenToken;pendingGuideFocus=()->{pendingGuideFocus=null;if(token==screenToken&&!full&&highlightedGuideItem==i&&epg!=null)loadEpg(i);};
  ui.postDelayed(pendingGuideFocus,80);
 }
 void paintPreviewGuide(Item i,GuideData data){
  if(epg==null||i==null||data==null)return;long now=System.currentTimeMillis();Programme current=null,next=null;
  for(Programme p:data.shows){if(p.start>0&&p.end>0&&p.start<=now&&now<p.end)current=p;else if(p.start>now&&next==null)next=p;}
  StringBuilder b=new StringBuilder(i.name);
  if(current!=null)b.append("\nNOW ").append(guideTimes(current)).append("\n").append(current.title);
  if(next!=null)b.append("\nNEXT ").append(guideTimes(next)).append("\n").append(next.title);
  if(current==null&&next==null){int n=0;for(Programme p:data.shows){if(p.end>0&&p.end<=now)continue;b.append("\n").append(guideTimes(p)).append("  ").append(p.title);if(++n==3)break;}if(n==0)b.append("\nNo current programme information");}
  epg.setText(b.toString());if(programmeProgress!=null){if(current!=null&&current.end>current.start){programmeProgress.setProgress((int)Math.max(0,Math.min(100,100L*(now-current.start)/(current.end-current.start))));programmeProgress.setVisibility(View.VISIBLE);}else programmeProgress.setVisibility(View.INVISIBLE);}
 }
 void loadEpg(Item i){
  if(epg==null||i==null||stopped||full)return;highlightedGuideItem=i;if(!defaultLiveEngine()&&page.equals("Live TV")&&SystemClock.elapsedRealtime()<epgQuietUntil){if(pendingGuideFocus!=null)ui.removeCallbacks(pendingGuideFocus);cancelPreviewGuideJob();epgToken++;GuideData saved=guideCache.get(guideKey(i,false));if(saved!=null)paintPreviewGuide(i,saved);else epg.setText(i.name+"\nLoading programme information…");int generation=screenToken;pendingGuideFocus=()->{pendingGuideFocus=null;if(generation==screenToken&&!full&&highlightedGuideItem==i)loadEpg(i);};ui.postDelayed(pendingGuideFocus,Math.max(50,epgQuietUntil-SystemClock.elapsedRealtime()));return;}if(pendingGuideFocus!=null){ui.removeCallbacks(pendingGuideFocus);pendingGuideFocus=null;}ui.removeCallbacks(refreshEpg);ui.postDelayed(refreshEpg,60000);
  int req=++epgToken,token=screenToken;String key=guideKey(i,false),fullUrl=api("get_simple_data_table","&stream_id="+enc(i.id)),shortUrl=api("get_short_epg","&stream_id="+enc(i.id)+"&limit=12"),zone=prefs.getString("epg_zone","");
  cancelPreviewGuideJob();
  if(programmeProgress!=null)programmeProgress.setVisibility(View.INVISIBLE);GuideData cached=guideCache.get(key);if(cached!=null){paintPreviewGuide(i,cached);if(System.currentTimeMillis()-cached.fetched<180000)return;}else epg.setText(i.name+"\nLoading programme information…");
  previewGuideJob=submitGuide(epgIo,()->{try{GuideData data=fetchGuide(i,fullUrl,shortUrl,zone,false);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(token!=screenToken||req!=epgToken||stopped||full||isFinishing())return;guideCache.put(key,data);paintPreviewGuide(i,data);});}
   catch(Exception e){ui.post(()->{if(token==screenToken&&req==epgToken&&!stopped&&!full&&epg!=null&&cached==null)epg.setText(i.name+"\nProgramme information unavailable");});}});
 }
 static final long GUIDE_SLOT=30*60*1000L;
 static Programme currentProgramme(GuideData data,long now){if(data!=null)for(Programme p:data.shows)if(p.start>0&&p.start<=now&&p.end>now)return p;return null;}
 static Programme nextProgramme(GuideData data,long now){if(data!=null)for(Programme p:data.shows)if(p.start>now)return p;return null;}
 class LiveGuideRows extends RecyclerView.Adapter<LiveGuideRows.H>{
  final List<Item> data;final Pick pick;final boolean timeline;long from;final TextView info;boolean disposed=false;
  LiveGuideRows(List<Item>d,Pick p,boolean grid,long start,TextView details){data=d;pick=p;timeline=grid;from=start;info=details;}
  class H extends RecyclerView.ViewHolder{
   LinearLayout root,slots;TextView name,sub,next;Item item;Future<?> job;GuideData guide;int request;
   H(LinearLayout r,TextView n,TextView s,LinearLayout cells){super(r);root=r;name=n;sub=s;slots=cells;}
  }
  @Override public H onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout root=timeline?row():col();root.setBackground(focus());premiumHomeFocus(root);root.setFocusable(true);root.setClickable(true);root.setPadding(dp(8),dp(3),dp(8),dp(3));
   TextView name=text("",14);name.setTypeface(uiMedium);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   TextView sub=text("",11);sub.setSingleLine();sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
   LinearLayout slots=row();if(timeline){root.addView(name,new LinearLayout.LayoutParams(0,-1,1.15f));root.addView(slots,new LinearLayout.LayoutParams(0,-1,3f));}
   else{root.addView(name,new LinearLayout.LayoutParams(-1,0,1));LinearLayout detail=row();root.addView(detail,new LinearLayout.LayoutParams(-1,0,1));detail.addView(sub,new LinearLayout.LayoutParams(0,-1,1));TextView next=text("",11);next.setSingleLine();next.setEllipsize(android.text.TextUtils.TruncateAt.END);next.setPadding(dp(4),0,0,0);next.setTextColor(UI_SECONDARY);detail.addView(next,new LinearLayout.LayoutParams(0,-1,1));next.setTag("next_programme");}
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,dp(timeline?58:44));lp.setMargins(dp(2),dp(1),dp(2),dp(2));root.setLayoutParams(lp);
   H h=new H(root,name,sub,slots);h.next=root.findViewWithTag("next_programme");root.setOnClickListener(v->{int i=h.getBindingAdapterPosition();if(i>=0&&i<data.size())pick.run(data.get(i),i);});
   root.setOnFocusChangeListener((v,focused)->{if(focused&&h.item!=null){if(timeline)describe(h);else highlightGuide(h.item,h.guide);}});return h;
  }
  void describe(H h){if(info==null||h.item==null)return;Programme p=currentProgramme(h.guide,System.currentTimeMillis());if(p==null)p=nextProgramme(h.guide,System.currentTimeMillis());info.setText(h.item.name+(p==null?"  •  Programme information unavailable":"  •  "+guideTimes(p)+"  "+p.title+(p.description.isEmpty()?"":"\n"+p.description)));}
  void paint(H h,GuideData d,boolean loading){
   h.guide=d;long now=System.currentTimeMillis();Programme p=currentProgramme(d,now),next=nextProgramme(d,now);
   if(!timeline){h.sub.setText(p!=null?"Now  "+p.title:loading?"Loading guide…":"No current listings");if(h.next!=null)h.next.setText(next==null?"":"Next  "+next.title);if(d!=null&&h.root.hasFocus()&&!full&&highlightedGuideItem==h.item)paintPreviewGuide(h.item,d);return;}
   h.slots.removeAllViews();long end=from+6*GUIDE_SLOT,cursor=from;
   if(d!=null)for(Programme show:d.shows){if(show.start<=0||show.end<=show.start||show.end<=cursor||show.start>=end)continue;long a=Math.max(cursor,show.start),b=Math.min(end,show.end);if(a>cursor)cell(h,"",a-cursor,false);if(b>a){cell(h,show.title,b-a,show.start<=now&&show.end>now);cursor=b;}if(cursor>=end)break;}
   if(cursor<end)cell(h,cursor==from?(loading?"Loading guide…":"No listings for this time"):"",end-cursor,false);
   if(h.root.hasFocus())describe(h);
  }
  void cell(H h,String title,long duration,boolean now){
   TextView t=text(title,12);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(5),0,dp(4),0);
   t.setBackground(bg(now?0xff254e77:0xff13233e,now?0xff142c4b:0xff0b1529,now?0xff69aaff:0xff31527a,1));
   h.slots.addView(t,new LinearLayout.LayoutParams(0,-1,(float)duration/GUIDE_SLOT));
  }
  void bindGuide(H h,Item i,boolean force){bindGuide(h,i,force,true);}
  void bindGuide(H h,Item i,boolean force,boolean requestNetwork){
   if(h.job!=null){h.job.cancel(true);h.job=null;rowGuideIo.purge();}int request=++h.request;h.item=i;h.name.setText(i.name);
   String key=guideKey(i,false),zone=prefs.getString("epg_zone",""),fullUrl=api("get_simple_data_table","&stream_id="+enc(i.id)),shortUrl=api("get_short_epg","&stream_id="+enc(i.id)+"&limit=16");
   GuideData d=guideCache.get(key);if(timeline){GuideData ext=guideCache.get(guideKey(i,true));if(ext!=null)d=ext;}
   final boolean extended=timeline&&(from>System.currentTimeMillis()+2*GUIDE_SLOT||from+6*GUIDE_SLOT<System.currentTimeMillis());
   if(!force&&d!=null&&System.currentTimeMillis()-d.fetched<180000&&(!extended||d.extended)){paint(h,d,false);return;}
   paint(h,d,d==null);if(!requestNetwork)return;int token=screenToken;final GuideData previous=d;
   try{h.job=submitGuide(rowGuideIo,()->{try{GuideData recent=guideCache.get(extended?guideKey(i,true):key);GuideData result=!force&&recent!=null&&System.currentTimeMillis()-recent.fetched<180000&&(!extended||recent.extended)?recent:fetchGuide(i,fullUrl,shortUrl,zone,extended);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(disposed||token!=screenToken||h.item!=i||h.request!=request||isFinishing())return;guideCache.put(extended?guideKey(i,true):key,result);paint(h,result,false);});}
   catch(Exception e){ui.post(()->{if(!disposed&&token==screenToken&&h.item==i&&h.request==request&&!isFinishing())paint(h,previous,false);});}});}catch(RejectedExecutionException e){h.job=null;paint(h,previous,false);}
  }
  @Override public void onBindViewHolder(H h,int position){bindGuide(h,data.get(position),false,h.root.isAttachedToWindow());}
  @Override public void onViewRecycled(H h){h.request++;if(h.job!=null){h.job.cancel(true);h.job=null;rowGuideIo.purge();}h.item=null;h.guide=null;super.onViewRecycled(h);}
  @Override public void onViewDetachedFromWindow(H h){h.request++;if(h.job!=null){h.job.cancel(true);rowGuideIo.purge();}h.job=null;super.onViewDetachedFromWindow(h);}
  @Override public void onViewAttachedToWindow(H h){super.onViewAttachedToWindow(h);if(h.item!=null&&(h.job==null||h.job.isCancelled())){boolean extended=timeline&&(from>System.currentTimeMillis()+2*GUIDE_SLOT||from+6*GUIDE_SLOT<System.currentTimeMillis());if(h.guide==null||System.currentTimeMillis()-h.guide.fetched>=180000||(extended&&!h.guide.extended))bindGuide(h,h.item,false);}}
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
  regionalGuideRefresh=()->{if(dialog.isShowing())adapter.refreshVisible(channels);};headerUpdate.run();dialog.setContentView(layout);dialog.setOnDismissListener(d->{regionalGuideRefresh=null;adapter.disposed=true;channels.setAdapter(null);if(guideDialog==dialog)guideDialog=null;});dialog.show();
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
  regionalGuideRefresh=()->{if(dialog.isShowing())loadFullGuide(dialog,choice[0],label,entries,scroll,true);};dialog.setContentView(layout);dialog.setOnDismissListener(d->{regionalGuideRefresh=null;guideRequest++;cancelFullGuideJob();if(guideDialog==dialog)guideDialog=null;});dialog.show();
  if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);}
  int index=0;for(int n=0;n<channelList.size();n++)if(channelList.get(n).id.equals(choice[0].id)){index=n;break;}
  focusAt(channels,index);loadFullGuide(dialog,choice[0],label,entries,scroll,false);
 }
 void showFullGuide(Item item,GuideData data,TextView label,LinearLayout entries,ScrollView scroll){
  long now=System.currentTimeMillis();int first=0;while(first<data.shows.size()&&data.shows.get(first).end>0&&data.shows.get(first).end<=now)first++;
  int offset=Math.min(first/80*80,Math.max(0,(data.shows.size()-1)/80*80));showFullGuidePage(item,data,label,entries,scroll,offset,true);
 }
 void showFullGuidePage(Item item,GuideData data,TextView label,LinearLayout entries,ScrollView scroll,int offset,boolean seekCurrent){
  int end=Math.min(offset+80,data.shows.size());
  label.setText(item.name+"  •  "+(data.extended?"Full available schedule":"Available listings")+"  •  "+(data.shows.isEmpty()?"0":(offset+1)+"–"+end)+"/"+data.shows.size());
  entries.removeAllViews();long now=System.currentTimeMillis();String lastDate="";View firstCurrent=null;
  if(offset>0){LinearLayout nav=row();button(nav,"Earlier programmes",()->showFullGuidePage(item,data,label,entries,scroll,Math.max(0,offset-80),false));entries.addView(nav);}
  for(Programme p:data.shows.subList(offset,end)){String day=guideDate(p);if(!day.equals(lastDate)){TextView header=text(day,15);header.setTextColor(UI_ACCENT);header.setTypeface(uiMedium);entries.addView(header,new LinearLayout.LayoutParams(-1,dp(29)));lastDate=day;}
   boolean current=p.start>0&&p.end>0&&p.start<=now&&now<p.end;
   TextView entry=text((current?"NOW  ":"")+guideTimes(p)+"  "+p.title+(p.description.isEmpty()?"":"\n"+p.description),14);
   entry.setMaxLines(5);entry.setPadding(dp(10),dp(7),dp(10),dp(7));entry.setFocusable(true);entry.setBackground(focus());premiumHomeFocus(entry);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(2),0,dp(2));entries.addView(entry,lp);
   if(firstCurrent==null&&(current||p.start>now))firstCurrent=entry;
  }
  if(data.shows.isEmpty())entries.addView(text("No programme schedule provided for this channel.",15));
  if(end<data.shows.size()){LinearLayout nav=row();button(nav,"Later programmes",()->showFullGuidePage(item,data,label,entries,scroll,end,false));entries.addView(nav);}
  View target=seekCurrent?firstCurrent:null;scroll.post(()->scroll.scrollTo(0,target==null?0:target.getTop()));
 }
 void loadFullGuide(Dialog dialog,Item item,TextView label,LinearLayout entries,ScrollView scroll,boolean force){
  int req=++guideRequest;cancelFullGuideJob();
  String key=guideKey(item,true),fullUrl=api("get_simple_data_table","&stream_id="+enc(item.id)),shortUrl=api("get_short_epg","&stream_id="+enc(item.id)+"&limit=24"),zone=prefs.getString("epg_zone","");
  GuideData cached=guideCache.get(key);if(!force&&cached!=null&&System.currentTimeMillis()-cached.fetched<180000){showFullGuide(item,cached,label,entries,scroll);return;}
  label.setText(item.name+"  •  Loading schedule…");entries.removeAllViews();
  fullGuideJob=submitGuide(epgIo,()->{try{GuideData data=fetchGuide(item,fullUrl,shortUrl,zone,true);if(Thread.currentThread().isInterrupted())return;ui.post(()->{if(req!=guideRequest||!dialog.isShowing()||isFinishing())return;guideCache.put(key,data);showFullGuide(item,data,label,entries,scroll);});}
   catch(Exception e){ui.post(()->{if(req!=guideRequest||!dialog.isShowing())return;if(cached!=null)showFullGuide(item,cached,label,entries,scroll);else label.setText(item.name+"  •  Schedule unavailable. Try Refresh guide.");});}});
 }

 void fav(){if(selected==null)return;Set<String>s=new HashSet<>(prefs.getStringSet("fav",Collections.emptySet()));if(!s.add(selected.id)){s.remove(selected.id);msg("Removed favourite");}else msg("Favourite saved");prefs.edit().putStringSet("fav",s).apply();}
 String stream(Item i){String k=page.equals("Live TV")?"live":page.equals("Movies")?"movie":"series";String e=k.equals("live")?liveExt:i.ext;if(!e.matches("[a-zA-Z0-9]{1,8}"))e="mp4";return server+"/"+k+"/"+enc(user)+"/"+enc(pass)+"/"+enc(i.id)+"."+e;}
 String decoderKey(Item i){return UUID.nameUUIDFromBytes((server+"|"+page+"|"+i.id+"|"+i.ext).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
 android.content.SharedPreferences decoderPrefs(){return getSharedPreferences("decoder_recovery_v1",0);}
 boolean rememberedDecoder(Item i){long when=decoderPrefs().getLong(decoderKey(i),0);long age=System.currentTimeMillis()-when;return when>0&&age>=0&&age<7L*24*60*60*1000;}
 void rememberDecoder(Item i){android.content.SharedPreferences d=decoderPrefs();android.content.SharedPreferences.Editor e=d.edit();Map<String,?> all=d.getAll();if(all.size()>=128){String oldest=null;long time=Long.MAX_VALUE;for(Map.Entry<String,?> entry:all.entrySet())if(entry.getValue() instanceof Long&&(Long)entry.getValue()<time){oldest=entry.getKey();time=(Long)entry.getValue();}if(oldest!=null)e.remove(oldest);}e.putLong(decoderKey(i),System.currentTimeMillis()).apply();}
 void attach(FrameLayout host,Item i,boolean fullscreen){if(fullscreen&&!page.equals("Live TV")&&getIntent().getBooleanExtra("ci_native_fixture",false)&&("ranchu".equals(Build.HARDWARE)||Build.FINGERPRINT.startsWith("generic"))&&server.equals("http://127.0.0.1:18765")&&user.equals("fixture")&&pass.equals("fixture")){attachNative(host,i,0);return;}boolean cached=fullscreen&&!page.equals("Live TV")&&rememberedDecoder(i);attachExo(host,i,fullscreen,cached?1:0,0,cached);}
 static boolean isHdr(Format f){return f!=null&&(MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType)||(f.colorInfo!=null&&(f.colorInfo.colorTransfer==C.COLOR_TRANSFER_ST2084||f.colorInfo.colorTransfer==C.COLOR_TRANSFER_HLG||f.colorInfo.hdrStaticInfo!=null)));}
 void attachExo(FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst){
  String mode=rangeFor(page.equals("Live TV"));
  int route=mode.equals("SDR Full")?0:-1;
  attachExo(host,i,fullscreen,attempt,position,cachedFirst,route);
 }
 Item matchingSdrFeed(Item original){
  Map<String,Item> found=new LinkedHashMap<>();
  for(Item candidate:items)if(!candidate.id.equals(original.id)&&SdrStreamMatch.matches(original.name,original.epgId,candidate.name,candidate.epgId,true))found.put(candidate.id,candidate);
  synchronized(catalogueLock){for(Map.Entry<String,CatalogueEntry> entry:catalogue.snapshot().entrySet())if(entry.getKey().startsWith("items:live:")&&SystemClock.elapsedRealtime()-entry.getValue().created<STREAM_TTL)for(Item candidate:entry.getValue().items)if(!candidate.id.equals(original.id)&&SdrStreamMatch.matches(original.name,original.epgId,candidate.name,candidate.epgId,false))found.put(candidate.id,candidate);}
  return found.size()==1?found.values().iterator().next():null;
 }
 PlayerView createPlaybackView(FrameLayout host,boolean texture){
  return texture?(PlayerView)getLayoutInflater().inflate(R.layout.shark_sdr_player,host,false):new PlayerView(this);
 }
 // Swap only the presentation target; never stop, prepare or replace the live media item.
 void switchLiveSdrOutput(boolean toFull){
  if(activeSdrPath!=0||liveSurfaceHost==null||player==null||video==null)return;
  boolean texture=video.getVideoSurfaceView() instanceof TextureView;
  if(texture==!toFull)return;
  PlayerView previous=video,next=createPlaybackView(liveSurfaceHost,!toFull);
  next.setUseController(false);next.setControllerAutoShow(false);next.setFocusable(toFull);
  next.setResizeMode(toFull?prefs.getInt("resize_mode",0):4);
  liveSurfaceHost.addView(next,0,new FrameLayout.LayoutParams(-1,-1));
  PlayerView.switchTargetView(player,previous,next);
  video=next;liveSurfaceHost.removeView(previous);
 }
 void attachExo(FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst,int sdrPath){attachExo(host,i,fullscreen,attempt,position,cachedFirst,sdrPath,i);}
 void attachExo(FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst,int sdrPath,Item sourceItem){
  long started=android.os.SystemClock.elapsedRealtime();
  // Recreate the player for each new channel. 5.0.49/50 reuse did not pass the first-frame regression test.
  release();
  final boolean stock=defaultLiveEngine();
  applyDisplayMode();
  startupBeginMs=started;startupFirstMs=startupLoadMs=startupDecoderMs=startupDecoderCostMs=startupReadyMs=-1;droppedVideoFrames=0;host.removeAllViews();activeSdrPath=sdrPath;playbackDiagnostics="Waiting for first video frame";
  // Restore the better-performing texture compositor for forced SDR preview only.
  PlayerView v=createPlaybackView(host,!fullscreen&&!stock&&sdrPath==0&&page.equals("Live TV"));
  v.setUseController(fullscreen);v.setControllerAutoShow(fullscreen);
  if(!fullscreen&&page.equals("Live TV")&&host==preview&&findViewById(android.R.id.content) instanceof FrameLayout){
   FrameLayout root=(FrameLayout)findViewById(android.R.id.content);
   liveSurfaceHost=new FrameLayout(this);liveSurfaceHost.setBackgroundColor(Color.BLACK);liveSurfaceHost.setFocusable(false);liveSurfaceHost.setClickable(false);liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
   v.setFocusable(false);liveSurfaceHost.addView(v,new FrameLayout.LayoutParams(-1,-1));
   root.addView(liveSurfaceHost,new FrameLayout.LayoutParams(1,1,Gravity.TOP|Gravity.LEFT));
   Drawable hiddenRim=preview.getForeground();if(hiddenRim!=null)hiddenRim.setVisible(false,false);preview.setForeground(null);
   livePreviewLayout=(view,l,t,r,b,ol,ot,or,ob)->{if(!full)positionLiveSurface();};
   preview.addOnLayoutChangeListener(livePreviewLayout);positionLiveSurface();preview.post(()->{if(!full)positionLiveSurface();});
  }else host.addView(v,new FrameLayout.LayoutParams(-1,-1));
  final String range=rangeFor(page.equals("Live TV"));
  final boolean fast=!stock&&page.equals("Live TV");
  final PlaybackProbe probe=new PlaybackProbe(started,fast,fast?(sdrPath==0?Math.max(1000,liveStartupBufferMs()):liveStartupBufferMs()):-1);probe.sourceItem=sourceItem;probe.alternate=!sourceItem.id.equals(i.id);probe.sampleMemory(SystemClock.elapsedRealtime());playbackProbe=probe;
  final boolean gpu=sdrPath==0&&!stock;
  // Preserve draw-on-release in preview and enable pre-draw only when the same session is fullscreen.
  final boolean liveGraph=page.equals("Live TV");
  DefaultRenderersFactory factory=new DefaultRenderersFactory(this){
   @Override protected void buildVideoRenderers(Context context,int extensionRendererMode,MediaCodecSelector selector,boolean fallback,Handler handler,androidx.media3.exoplayer.video.VideoRendererEventListener listener,long joining,ArrayList<androidx.media3.exoplayer.Renderer> out){
    if(!gpu){super.buildVideoRenderers(context,extensionRendererMode,selector,fallback,handler,listener,joining,out);return;}
    MediaCodecSelector hardwareVideoSelector=(mime,secure,tunnel)->{List<MediaCodecInfo> candidates=new ArrayList<>(selector.getDecoderInfos(mime,secure,tunnel));candidates.removeIf(codec->!codec.hardwareAccelerated||codec.softwareOnly);return candidates;};
    androidx.media3.exoplayer.video.MediaCodecVideoRenderer.Builder b=new androidx.media3.exoplayer.video.MediaCodecVideoRenderer.Builder(context)
     .setCodecAdapterFactory(getCodecAdapterFactory()).setMediaCodecSelector(hardwareVideoSelector).setEnableDecoderFallback(fallback).setAllowedJoiningTimeMs(joining).setEventHandler(handler).setEventListener(listener).setMaxDroppedFramesToNotify(50).setEarlySchedulingThresholdUs(150_000);
    out.add(new androidx.media3.exoplayer.video.MediaCodecVideoRenderer(b){
     private void requestNextDirectFrame(){androidx.media3.exoplayer.Renderer.WakeupListener wake=getWakeupListener();if(wake!=null)wake.onWakeup();}
     @Override protected androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper createPlaybackVideoGraphWrapper(Context context,androidx.media3.exoplayer.video.VideoFrameReleaseControl control){
      androidx.media3.effect.DefaultVideoFrameProcessor.Factory processor=new androidx.media3.effect.DefaultVideoFrameProcessor.Factory.Builder()
       .setSdrWorkingColorSpace(androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
       .setEnableReplayableCache(false).build();
      androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper graph=new androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper.Builder(context,control)
       .setVideoGraphFactory(new androidx.media3.common.VideoGraph.Factory(){
        @Override public boolean supportsMultipleInputs(){return false;}
        @Override public androidx.media3.common.VideoGraph create(Context ctx,androidx.media3.common.ColorInfo color,androidx.media3.common.DebugViewProvider debug,androidx.media3.common.VideoGraph.Listener downstream,java.util.concurrent.Executor executor,long offset,boolean automatic){
         androidx.media3.common.VideoGraph.Listener monitored=new androidx.media3.common.VideoGraph.Listener(){
          @Override public void onOutputSizeChanged(int w,int h){downstream.onOutputSizeChanged(w,h);}
          @Override public void onOutputFrameRateChanged(float rate){downstream.onOutputFrameRateChanged(rate);}
          @Override public void onOutputFrameAvailableForRendering(long pts,boolean redraw){if(!redraw){probe.graphReady.record();probe.decodedPts.record(pts,probe.expectedFrameUs());}downstream.onOutputFrameAvailableForRendering(pts,redraw);}
          @Override public void onEnded(long pts){downstream.onEnded(pts);}
          @Override public void onError(androidx.media3.common.VideoFrameProcessingException e){downstream.onError(e);}
         };
         if(fast&&android.os.Build.VERSION.SDK_INT>=29&&color.colorTransfer==C.COLOR_TRANSFER_SDR&&!automatic){
          probe.directGraph=true;
          return new DirectSdrVideoGraph(monitored,executor,probe.graphInput::record,probe::submitted,probe::graphDrop,probe.directLatch::record,probe::drawn,probe::swapped,probe::late,probe.directQueue::record,ns->probe.targetPts.record(ns/1000,probe.expectedFrameUs()),probe::submissionInterval,name->probe.directShader=name,()->requestNextDirectFrame(),probe.directDrawCall::record,probe.directPresentCall::record,info->probe.directGraphics=info,()->!liveGraph||full);
         }
         return new androidx.media3.effect.SingleInputVideoGraph(ctx,processor,color,monitored,debug,executor,automatic){
          @Override public boolean registerInputFrame(int index){boolean accepted=super.registerInputFrame(index);if(accepted)probe.graphInput.record();return accepted;}
          @Override public void renderOutputFrame(long timeNs){super.renderOutputFrame(timeNs);if(timeNs==androidx.media3.common.VideoFrameProcessor.DROP_OUTPUT_FRAME)probe.graphOutputDrops++;else probe.graphSubmitted.record();}
         };
        }
       })
       .setEnablePlaylistMode(true).setClock(getClock()).experimentalSetLateThresholdToDropInputUs(40_000).build();
      graph.setRequestOpenGlToneMapping(true);return graph;
     }
     @Override protected void onOutputFormatChanged(Format format,android.media.MediaFormat media){
      if(RefreshMatch.valid(format.frameRate))probe.sourceFps=format.frameRate;
      if(format.colorInfo!=null)probe.sourceTransfer=format.colorInfo.colorTransfer;
      if(media!=null){if(media.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER))probe.decodedTransfer=media.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER);
       if(!RefreshMatch.valid(probe.sourceFps)&&media.containsKey(android.media.MediaFormat.KEY_FRAME_RATE))try{probe.sourceFps=media.getNumber(android.media.MediaFormat.KEY_FRAME_RATE).floatValue();}catch(RuntimeException ignored){}
      }
      super.onOutputFormatChanged(format,media);
     }
    });
   }
  }.setEnableDecoderFallback(true);
  factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
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
  // Keep the stock engine a genuine comparison/fallback. Only the custom live path starts sooner.
  DefaultDataSource.Factory source=stock?new DefaultDataSource.Factory(this):new DefaultDataSource.Factory(this,new OkHttpDataSource.Factory(STREAM_HTTP));
  source.setTransferListener(probe);
  ExoPlayer.Builder builder=stock?new ExoPlayer.Builder(this):new ExoPlayer.Builder(this,factory);
  builder.setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(source));
  if(fast){
   DefaultLoadControl.Builder load=new DefaultLoadControl.Builder();
   // Optional reference profile: explicit buffer values observed in IBO 4.3; all existing options remain unchanged.
   if(probe.startupBufferMs==2500)load.setBufferDurationsMs(50_000,50_000,2_500,5_000).setBackBuffer(0,false).setTargetBufferBytes(C.LENGTH_UNSET);
   else load.setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,gpu?Math.max(1000,probe.startupBufferMs):probe.startupBufferMs,DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
   builder.setLoadControl(load.build());
  }
  ExoPlayer p=builder.build();
  if(gpu)p.setVideoEffects(Collections.emptyList());

  video=v;player=p;playbackItem=i;activeStreamId=i.id;v.setPlayer(p);v.setResizeMode(!fullscreen&&page.equals("Live TV")?4:prefs.getInt("resize_mode",0));
  if(!stock)p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
   .setPreferredAudioLanguage(prefs.getString("audio_lang",null))
   .setPreferredTextLanguage(prefs.getString("text_lang",null))
   .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!prefs.getBoolean("subtitles_on",true))
   .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false).build());
  if(!stock||movieDisplayPolicy())p.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF);
  if(!fullscreen)p.setVolume(0);
  startStream(p,v,host,i,fullscreen,attempt,position,cachedFirst,sdrPath,started,probe);
 }
 void detachStreamListeners(){if(player!=null){if(streamListener!=null)player.removeListener(streamListener);if(streamAnalytics!=null)player.removeAnalyticsListener(streamAnalytics);}streamListener=null;streamAnalytics=null;}
 void startStream(ExoPlayer p,PlayerView v,FrameLayout host,Item i,boolean fullscreen,int attempt,long position,boolean cachedFirst,int sdrPath,long started,PlaybackProbe probe){
  final boolean stock=defaultLiveEngine(),fast=!stock&&page.equals("Live TV");
  final String range=rangeFor(page.equals("Live TV"));
  final boolean vod=fullscreen&&!page.equals("Live TV");final int token=screenToken;
  clearRefreshMatch(false);
  if(fast)epgQuietUntil=started+1500;
  FrameRateSample sample=new FrameRateSample();frameRateSample=sample;p.setVideoFrameMetadataListener((pts,releaseNs,format,mediaFormat)->{sample.accept(pts);probe.lastFrame.set(SystemClock.elapsedRealtime());if(sdrPath==0&&releaseNs!=C.TIME_UNSET){long now=System.nanoTime();if(probe.graphFrames==0)probe.graphFirstNs=now;probe.graphLastNs=now;probe.graphFrames++;}if(mediaFormat!=null&&mediaFormat.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER)){int transfer=mediaFormat.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER);if(!probe.outputKnown||probe.outputTransfer!=transfer){probe.outputTransfer=transfer;probe.outputKnown=transfer>0;android.util.Log.i("SharkPlayback","Decoder output color transfer="+transfer);}}});
  final String mediaId="stream-"+started+"-"+i.id;
  streamAnalytics=new AnalyticsListener(){
   boolean current(AnalyticsListener.EventTime time){return player==p&&playbackProbe==probe&&time.windowIndex>=0&&time.windowIndex<time.timeline.getWindowCount()&&mediaId.equals(time.timeline.getWindow(time.windowIndex,new Timeline.Window()).mediaItem.mediaId);}
   @Override public void onLoadStarted(AnalyticsListener.EventTime time,androidx.media3.exoplayer.source.LoadEventInfo info,androidx.media3.exoplayer.source.MediaLoadData data){if(current(time)&&startupLoadMs<0)startupLoadMs=Math.max(0,info.elapsedRealtimeMs-startupBeginMs);}
   @Override public void onDroppedVideoFrames(AnalyticsListener.EventTime time,int count,long elapsedMs){if(current(time)){droppedVideoFrames+=count;probe.modeStats.reportedDrops.addAndGet(count);}}
   @Override public void onAudioUnderrun(AnalyticsListener.EventTime time,int size,long sizeMs,long elapsedMs){if(current(time))probe.audioUnderruns++;}
   @Override public void onVideoFrameProcessingOffset(AnalyticsListener.EventTime time,long offsetUs,int frameCount){if(current(time)){probe.videoOffsetSumUs+=offsetUs;probe.videoOffsetFrames+=frameCount;}}
   @Override public void onAudioDecoderInitialized(AnalyticsListener.EventTime time,String name,long ts,long duration){if(player==p)android.util.Log.i("SharkPlayback","audio decoder="+name);}
   @Override public void onVideoDecoderInitialized(AnalyticsListener.EventTime time,String name,long ts,long duration){
    if(!current(time))return;if(startupDecoderMs<0){startupDecoderMs=Math.max(0,ts-startupBeginMs);startupDecoderCostMs=duration;}activeDecoder=name;activeDecoderKind=videoDecoderKind(name);android.util.Log.i("SharkPlayback","decoder="+name+" attempt="+attempt+" initMs="+duration);
   }
  };p.addAnalyticsListener(streamAnalytics);
  MediaItem.Builder m=new MediaItem.Builder().setUri(stream(probe.sourceItem)).setMediaId(mediaId);
  if(!stock&&page.equals("Live TV"))m.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build());
  class RecoveryListener implements Player.Listener {
   boolean recovering,firstFrame;
   long networkErrorAt=-1,firstRenderedAt=-1;
   String networkError="";
   boolean liveEligible(){return player==p&&playbackProbe==probe&&!recovering&&!stopped&&token==screenToken&&page.equals("Live TV");}
   final Runnable liveWatch=new Runnable(){@Override public void run(){
    if(!liveEligible())return;
    long now=SystemClock.elapsedRealtime();
    probe.sampleMemory(now);
    int state=p.getPlaybackState();
    boolean eligible=full&&p.getPlayWhenReady()&&p.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    boolean networkFailure=networkErrorAt>=0&&p.getPlayerError()!=null;
    boolean stalled=probe.health.observe(now,p.getCurrentPosition(),eligible&&!networkFailure&&p.getPlayerError()==null&&(state==Player.STATE_READY||state==Player.STATE_BUFFERING||state==Player.STATE_ENDED));
    if(eligible&&((networkFailure&&now-networkErrorAt>=3000)||stalled)&&probe.health.takeRetry(now)){
     probe.recoveryReason=networkFailure?networkError:"Live position stalled for 30s";
     android.util.Log.w("SharkPlayback","Live reconnect "+probe.health.attempts+"/2: "+probe.recoveryReason);
     networkErrorAt=-1;probe.closeStall();probe.lastByte.set(-1);probe.lastFrame.set(-1);
     MediaItem current=p.getCurrentMediaItem();
     if(current!=null){p.stop();p.setMediaItem(current);p.prepare();p.play();}
    }
    if(liveEligible())ui.postDelayed(this,2000);
   }};
   long audioStartedAt=-1;
   final Runnable watch=new Runnable(){@Override public void run(){
    if(player!=p||playbackProbe!=probe||recovering||firstFrame||stopped||token!=screenToken)return;
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
   void startWatch(){if(!stock&&(vod||sdrPath==0)){decoderWatch=watch;ui.postDelayed(watch,750);}if(page.equals("Live TV")){stopLiveHealth();liveHealthWatch=liveWatch;ui.postDelayed(liveWatch,2000);}}


    @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK){probe.closeStall();probe.health.reset();}}
   @Override public void onPlaybackStateChanged(int state){if(player!=p||playbackProbe!=probe)return;if(state==Player.STATE_READY&&startupReadyMs<0)startupReadyMs=SystemClock.elapsedRealtime()-startupBeginMs;if(state==Player.STATE_BUFFERING&&firstFrame&&p.getPlayWhenReady()&&!vod&&probe.stallStart<0){probe.rebuffers++;probe.stallStart=SystemClock.elapsedRealtime();}else if(state!=Player.STATE_BUFFERING)probe.closeStall();}
   @Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(!ready){probe.closeStall();probe.health.reset();}}
   @Override public void onRenderedFirstFrame(){
    if(player!=p||playbackProbe!=probe||recovering)return;
    firstFrame=true;if(firstRenderedAt<0)firstRenderedAt=SystemClock.elapsedRealtime();if(startupFirstMs<0){startupFirstMs=SystemClock.elapsedRealtime()-startupBeginMs;android.util.Log.i("SharkPlayback","startup profile="+(fast?"fast-live":"stock-buffer")+" firstFrameMs="+startupFirstMs+" firstByteMs="+probe.firstByte.get()+" decoderMs="+startupDecoderMs+" startupBufferMs="+probe.startupBufferMs);if(!stock&&refreshInCurrentView()&&autoRefresh())scheduleRefreshMatch(p,sample,0);}ui.removeCallbacks(watch);
    if(!stock&&vod&&attempt==1)rememberDecoder(i);
    Format f=p.getVideoFormat();
    playbackDiagnostics=(activeDecoder.isEmpty()?"Decoder unavailable":activeDecoder)+(f==null?"":" · "+f.width+"×"+f.height+(f.bitrate>0?" · "+String.format(Locale.US,"%.1f Mb/s",f.bitrate/1000000.0):""))
     +"\n"+(isHdr(f)?"HDR source":"SDR / unknown source")+" · "+(sdrPath==0?"Full-resolution GPU SDR requested":"Native source output");
    android.util.Log.i("SharkPlayback",playbackDiagnostics);
   }
   @Override public void onPlayerError(PlaybackException e){
    if(player!=p||playbackProbe!=probe||recovering)return;
    Format fmt=p.getVideoFormat();
    String detail=fmt==null?"format unknown":fmt.sampleMimeType+" "+fmt.codecs+" "+fmt.width+"x"+fmt.height+" bitrate="+fmt.bitrate;
    android.util.Log.w("SharkPlayback",e.getErrorCodeName()+" "+detail+" decoder="+activeDecoder+" attempt="+attempt);
    // Do not log source URLs, usernames, passwords, or exception text.
    for(Throwable cause=e.getCause();cause!=null;cause=cause.getCause()){
     if(cause instanceof android.media.MediaCodec.CodecException){android.util.Log.w("SharkPlayback","codec diagnostic="+((android.media.MediaCodec.CodecException)cause).getDiagnosticInfo());break;}
    }
    boolean decoderError=e.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED||e.errorCode==PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED;
    playbackDiagnostics="Playback error: "+e.getErrorCodeName()+" · "+activeDecoder;
    boolean transientNetwork=e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED||e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
    if(transientNetwork&&liveEligible()&&full&&p.getPlayWhenReady()&&probe.health.attempts<2){networkErrorAt=SystemClock.elapsedRealtime();networkError=e.getErrorCodeName();return;}
    if(probe.alternate&&!stock&&!stopped){recover("SDR alternative unavailable",0);return;}
    if(stock||stopped||!decoderError||(!vod&&sdrPath!=0)){ui.removeCallbacks(watch);msg("Playback failed: "+e.getErrorCodeName());return;}
    recover(e.getErrorCodeName(),firstFrame?Math.max(0,p.getCurrentPosition()):position);
   }
   void recover(String reason,long resume){
    if(sdrPath==0){ui.removeCallbacks(watch);recovering=true;playbackDiagnostics="Forced SDR stopped: "+reason+" · Hardware video decoder required";msg("Forced SDR unavailable; select Auto to restore native playback");return;}
    if(probe.alternate){restart(reason,resume,-1,i);return;}

    if(player!=p||playbackProbe!=probe||recovering||stopped)return;
    recovering=true;ui.removeCallbacks(watch);
    if(attempt==1)decoderPrefs().edit().remove(decoderKey(i)).apply();
    final boolean live=page.equals("Live TV"),wasFull=full;
    android.util.Log.i("SharkPlayback","Silent decoder recovery; attempt="+attempt+" SDR path="+sdrPath+" reason="+reason);
    if(live&&wasFull&&liveSurfaceHost!=null)exitLiveFullscreen();
    release();
    decoderRetry=()->{
     decoderRetry=null;
     if(token!=screenToken||stopped||isFinishing()||player!=null||nativeVideo!=null)return;
     if(live&&selected!=i)return;
     if(!live&&!full)return;

     if(attempt==0&&!cachedFirst)attachExo(host,i,true,1,resume,false,sdrPath);
     else if(attempt==1&&cachedFirst)attachExo(host,i,true,0,resume,true,sdrPath);
     else if(sdrPath>=0){msg("SDR playback unavailable. Select Auto to retry without conversion.");}
     else attachNative(host,i,resume);
    };ui.postDelayed(decoderRetry,500);
   }
   void restart(String reason,long resume,int route,Item source){
    if(player!=p||playbackProbe!=probe||recovering||stopped)return;
    recovering=true;boolean wasFull=full;
    android.util.Log.i("SharkPlayback","SDR route switch: "+reason);
    if(wasFull&&liveSurfaceHost!=null)exitLiveFullscreen();
    release();
    decoderRetry=()->{decoderRetry=null;if(token!=screenToken||stopped||isFinishing()||player!=null||nativeVideo!=null||(page.equals("Live TV")&&(selected==null||!selected.id.equals(i.id))))return;
     attachExo(host,i,fullscreen,0,resume,false,route,source);
     if(source.id.equals(i.id)&&playbackProbe!=null)playbackProbe.sdrRejected=true;
     if(wasFull&&!fullscreen&&liveSurfaceHost!=null)enterLiveFullscreen();
    };ui.postDelayed(decoderRetry,250);
   }
  }
  RecoveryListener listener=new RecoveryListener();streamListener=listener;p.addListener(listener);
  p.setMediaItem(m.build());if(vod&&position>0)p.seekTo(position);if(!stock)p.setPlaybackSpeed(1f);p.prepare();p.play();listener.startWatch();if(fullscreen)addRangeButton(host,i,v);
 }
 void attachNative(FrameLayout host,Item i,long position){
  release();applyDisplayMode();host.removeAllViews();
  VideoView n=new VideoView(this){@Override protected void onMeasure(int ws,int hs){int mode=prefs.getInt("resize_mode",0),w=View.MeasureSpec.getSize(ws),h=View.MeasureSpec.getSize(hs);android.media.MediaPlayer media=nativeMedia;if(mode==3&&w>0&&h>0){setMeasuredDimension(w,h);return;}if(mode==4&&media!=null&&w>0&&h>0&&media.getVideoWidth()>0&&media.getVideoHeight()>0){float scale=Math.max(w/(float)media.getVideoWidth(),h/(float)media.getVideoHeight());setMeasuredDimension(Math.round(media.getVideoWidth()*scale),Math.round(media.getVideoHeight()*scale));return;}super.onMeasure(ws,hs);}};nativeVideo=n;n.setFocusable(false);n.setFocusableInTouchMode(false);n.setClickable(false);n.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);playbackItem=i;playbackDiagnostics="Android native player fallback · source picture output";
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);host.addView(n,lp);
  // The native engine uses the same premium overlay; never attach a stock MediaController.
  host.setClipChildren(true);
  final boolean[] rendered={false};
  n.setOnInfoListener((mp,what,extra)->{if(nativeVideo==n&&what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){rendered[0]=true;android.util.Log.i("SharkPlayback","Native first frame");}return false;});
  n.setOnPreparedListener(mp->{if(nativeVideo!=n)return;nativeMedia=mp;n.requestLayout();if(position>0)n.seekTo((int)Math.min(position,Integer.MAX_VALUE));android.util.Log.i("SharkPlayback","Android native prepared "+mp.getVideoWidth()+"x"+mp.getVideoHeight());if(premiumPlayer!=null)premiumPlayer.refresh();
   nativeWatch=new Runnable(){int checks=0;@Override public void run(){if(nativeVideo!=n||rendered[0]||stopped||isFinishing())return;
    if(n.isPlaying()&&n.getCurrentPosition()>position+2000){if(++checks>=7){playbackDiagnostics="Native player: audio playing, video not confirmed";msg("Video unavailable on this decoder. Try a compatible 4K version.");return;}}
    else checks=0;ui.postDelayed(this,1000);}};ui.postDelayed(nativeWatch,1000);
  });
  n.setOnCompletionListener(mp->{if(nativeVideo==n&&premiumPlayer!=null){android.util.Log.i("SharkPlayerUI","native playback completed");premiumPlayer.nativeEnded();if(!stopped)premiumPlayer.show();}});
  n.setOnErrorListener((mp,what,extra)->{if(nativeVideo==n){nativeMedia=null;if(premiumPlayer!=null)premiumPlayer.nativeEnded();android.util.Log.w("SharkPlayback","Android native error="+what+"/"+extra);msg("Playback failed on both players ("+what+"/"+extra+")");}return true;});
  n.setVideoURI(Uri.parse(stream(i)));n.start();addRangeButton(host,i,null);
 }
 void removeRangeButton(){
  if(premiumPlayer!=null){premiumPlayer.dispose();premiumPlayer=null;}
  if(playerDetails!=null){ViewGroup group=(ViewGroup)playerDetails.getParent();if(group!=null)group.removeView(playerDetails);playerDetails=null;}
  if(playerHeader!=null){ViewGroup group=(ViewGroup)playerHeader.getParent();if(group!=null)group.removeView(playerHeader);playerHeader=null;}
  if(rangeButton!=null){ViewGroup group=(ViewGroup)rangeButton.getParent();if(group!=null)group.removeView(rangeButton);rangeButton=null;}
  if(video!=null)video.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)null);
 }
 String rangeLabel(){return rangeName(rangeFor(page.equals("Live TV")));}
 TextView playerAction(LinearLayout bar,String label,Runnable action){
  TextView b=text(label,13);b.setTypeface(uiMedium);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),0,dp(12),0);b.setBackground(playerGlass());premiumHomeFocus(b);b.setFocusable(true);b.setClickable(true);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(37));lp.setMargins(dp(3),0,dp(3),0);bar.addView(b,lp);b.setOnClickListener(v->action.run());return b;
 }
 void chooseTrack(int type){
  ExoPlayer p=player;if(p==null){chooseNativeTrack(type);return;}
  List<String> labels=new ArrayList<>();List<TrackSelectionOverride> overrides=new ArrayList<>();
  labels.add("Automatic");overrides.add(null);
  if(type==C.TRACK_TYPE_TEXT){labels.add("Off");overrides.add(null);}
  for(Tracks.Group group:p.getCurrentTracks().getGroups())if(group.getType()==type)for(int n=0;n<group.length;n++)if(group.isTrackSupported(n)){
   Format f=group.getTrackFormat(n);String lang=f.language==null?"Undetermined":f.language;
   labels.add((f.label==null||f.label.isEmpty()?lang:f.label)+(type==C.TRACK_TYPE_AUDIO&&f.channelCount>0?" · "+f.channelCount+" ch":"")+(group.isTrackSelected(n)?" ✓":""));overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(),n));
  }
  new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_AUDIO?"Audio tracks":"Subtitles").setItems(labels.toArray(new String[0]),(d,n)->{
   if(player!=p)return;TrackSelectionParameters.Builder b=p.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT&&n==1);
   if(overrides.get(n)!=null)b.setOverrideForType(overrides.get(n));p.setTrackSelectionParameters(b.build());if(video!=null)video.showController();
  }).setNegativeButton("Cancel",null).show();
 }
 void chooseNativeTrack(int type){
  android.media.MediaPlayer mp=nativeMedia;if(mp==null){new AlertDialog.Builder(this).setMessage("Playback is preparing. Try again once the video starts.").setPositiveButton("OK",null).show();return;}
  try{android.media.MediaPlayer.TrackInfo[] tracks=mp.getTrackInfo();List<String> labels=new ArrayList<>();List<Integer> indices=new ArrayList<>();boolean audio=type==C.TRACK_TYPE_AUDIO;int wanted=audio?android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO:android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE;
   if(!audio){labels.add("Off");indices.add(-1);}for(int j=0;j<tracks.length;j++){int kind=tracks[j].getTrackType();if(kind==wanted||(!audio&&kind==android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)){labels.add((tracks[j].getLanguage()==null?"Undetermined":tracks[j].getLanguage())+" · Track "+(j+1));indices.add(j);}}
   if(labels.isEmpty()){new AlertDialog.Builder(this).setMessage("No selectable tracks reported by this decoder.").setPositiveButton("OK",null).show();return;}
   new AlertDialog.Builder(this).setTitle(audio?"Audio tracks":"Subtitles").setItems(labels.toArray(new String[0]),(d,n)->{if(nativeMedia!=mp)return;try{int index=indices.get(n);if(index>=0)mp.selectTrack(index);else{for(int kind:new int[]{android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE,android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT}){int selected=mp.getSelectedTrack(kind);if(selected>=0)mp.deselectTrack(selected);}}}catch(RuntimeException e){Toast.makeText(this,"Track unavailable on this decoder",Toast.LENGTH_SHORT).show();}}).setNegativeButton("Cancel",null).show();
  }catch(RuntimeException e){Toast.makeText(this,"Tracks unavailable on this decoder",Toast.LENGTH_SHORT).show();}
 }
 void aspectDialog(){
  if(video==null&&nativeVideo==null)return;String[] options={"Fit","Stretch","Zoom"};int[] modes={0,3,4};
  new AlertDialog.Builder(this).setTitle("Aspect ratio").setItems(options,(d,n)->{prefs.edit().putInt("resize_mode",modes[n]).apply();if(video!=null){video.setResizeMode(modes[n]);video.showController();}else if(nativeVideo!=null)nativeVideo.requestLayout();}).setNegativeButton("Cancel",null).show();
 }
 Drawable playerGlass(){
  GradientDrawable d=bg(0xcf254e8d,0xc4071737,0xff479af1,1);return d;
 }
 void paintPlayerDetails(LinearLayout panel,Item item,GuideData data){
  panel.removeAllViews();TextView name=text(item.name,18);name.setTypeface(uiMedium);name.setMaxLines(2);panel.addView(name,new LinearLayout.LayoutParams(-1,-2));
  TextView info=text(playbackDiagnostics,12);info.setMaxLines(3);panel.addView(info);
  long now=System.currentTimeMillis();Programme current=currentProgramme(data,now),next=nextProgramme(data,now);
  String[] heads={"Now playing","Next"};String[] titles={current==null?"Programme information unavailable":current.title,next==null?"No upcoming listing":next.title};
  for(int i=0;i<2;i++){LinearLayout card=col();card.setPadding(dp(10),dp(6),dp(10),dp(8));card.setBackground(playerGlass());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);panel.addView(card,lp);TextView heading=text(heads[i],13);heading.setTextColor(i==0?UI_GOLD:UI_ACCENT);card.addView(heading);TextView value=text(titles[i],15);value.setMaxLines(3);card.addView(value);}
 }
 void addPlayerDetails(FrameLayout host,Item item,PlayerView pv){
  if(!page.equals("Live TV")||getResources().getConfiguration().screenWidthDp<650)return;
  LinearLayout panel=col();panel.setPadding(dp(12),dp(12),dp(12),dp(12));panel.setBackground(bg(0xea122d5d,0xf2061330,0xff50a7ff,1));panel.setFocusable(false);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(238),-2,Gravity.RIGHT|Gravity.CENTER_VERTICAL);lp.rightMargin=dp(18);host.addView(panel,lp);playerDetails=panel;
  GuideData cached=guideCache.get(guideKey(item,false));paintPlayerDetails(panel,item,cached);panel.setVisibility(pv.isControllerFullyVisible()?View.VISIBLE:View.GONE);
  if(cached==null||System.currentTimeMillis()-cached.fetched>180000){
   final String a=api("get_simple_data_table","&stream_id="+enc(item.id)),b=api("get_short_epg","&stream_id="+enc(item.id)+"&limit=12"),zone=prefs.getString("epg_zone",""),key=guideKey(item,false);
   submitGuide(epgIo,()->{try{GuideData result=fetchGuide(item,a,b,zone,false);ui.post(()->{if(playerDetails==panel&&video==pv&&full){guideCache.put(key,result);paintPlayerDetails(panel,item,result);}});}catch(Exception ignored){}});
  }
 }
 void stylePlayer(PlayerView pv){
  View bottom=pv.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);if(bottom!=null)bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00132d60,0xf0081b40}));
  View center=pv.findViewById(androidx.media3.ui.R.id.exo_center_controls);
  if(center!=null&&center.getLayoutParams() instanceof FrameLayout.LayoutParams){
   FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)center.getLayoutParams();lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;lp.bottomMargin=dp(70);center.setLayoutParams(lp);
  }
  View progress=pv.findViewById(androidx.media3.ui.R.id.exo_progress);if(progress instanceof androidx.media3.ui.DefaultTimeBar){
   androidx.media3.ui.DefaultTimeBar bar=(androidx.media3.ui.DefaultTimeBar)progress;bar.setPlayedColor(UI_GOLD);bar.setScrubberColor(UI_GOLD);bar.setBufferedColor(0xff6a7890);bar.setUnplayedColor(0xff394458);
  }
  int[] controls={androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_prev,androidx.media3.ui.R.id.exo_next,androidx.media3.ui.R.id.exo_settings,androidx.media3.ui.R.id.exo_subtitle};
  for(int id:controls){View v=pv.findViewById(id);if(v!=null){v.setBackground(playerGlass());premiumHomeFocus(v);if(v instanceof ImageView)((ImageView)v).setColorFilter(UI_TEXT);}}
 }
 void addRangeButton(FrameLayout host,Item item,PlayerView playerView){
  removeRangeButton();
  if(playerView==null&&nativeVideo==null)return;
  if(playerView!=null){playerView.hideController();playerView.setUseController(false);playerView.setControllerAutoShow(false);}
  premiumPlayer=new PremiumPlayerOverlay(this,host,playerView,item);
  premiumPlayer.show();
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(full&&premiumPlayer!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)android.util.Log.i("SharkPlayerUI","remote key="+event.getKeyCode()+" source="+event.getSource());if(premiumPlayer.handleKey(event))return true;}
  if(!full&&(page.equals("Live TV")||page.equals("Movies")||page.equals("Series"))&&event.getKeyCode()==KeyEvent.KEYCODE_MENU){
   // Open on release: otherwise the newly focused dialog receives the trailing Menu key-up.
   if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){if(page.equals("Live TV"))liveOptions();else catalogueOptions();}return true;
  }
  if(full&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&rangeButton!=null&&playbackItem!=null){
   if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){choosePlayerRange(playbackItem);return true;}
   if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&getCurrentFocus()!=rangeButton&&(playerHeader==null||getCurrentFocus()==null||getCurrentFocus().getParent()!=rangeButton.getParent())){
    if(video!=null)video.showController();if(playerHeader!=null)playerHeader.setVisibility(View.VISIBLE);rangeButton.setVisibility(View.VISIBLE);rangeButton.requestFocus();return true;
   }
   if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&video!=null&&getCurrentFocus()!=null&&(getCurrentFocus()==rangeButton||getCurrentFocus().getParent()==rangeButton.getParent())){video.requestFocus();video.showController();return true;}
  }return super.dispatchKeyEvent(event);
 }
 void choosePlayerRange(Item item){
  String[] choices=Build.VERSION.SDK_INT>=29?new String[]{"Auto / native (full resolution)","Force SDR (full resolution)"}:new String[]{"Auto / native (full resolution)"};
  String[] values=Build.VERSION.SDK_INT>=29?new String[]{"Auto","SDR Full"}:new String[]{"Auto"};
  boolean live=page.equals("Live TV");if(defaultLiveEngine()){new AlertDialog.Builder(this).setTitle("Default ExoPlayer").setMessage("Native picture output. To change SDR/HDR, select Custom in Playback settings.").setPositiveButton("Close",null).show();return;}int current=Arrays.asList(values).indexOf(rangeFor(live));
  new AlertDialog.Builder(this).setTitle("Video dynamic range").setSingleChoiceItems(choices,current,(dialog,n)->{
   dialog.dismiss();String range=values[n];if(range.equals(rangeFor(live)))return;
   prefs.edit().putString(live?"range_live":"range_vod",range).apply();
   if(!full||playbackItem!=item)return;
   if(liveSurfaceHost!=null&&page.equals("Live TV")){
    if(!exitLiveFullscreen())return;
    // A picture-mode change requires a new renderer; ordinary screen-size changes do not.
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
  }).setNeutralButton("Video info",(d,w)->new AlertDialog.Builder(this).setTitle("Video information").setMessage(currentPlaybackDiagnostics()).setPositiveButton("Close",null).show()).setNegativeButton("Cancel",null).show();
 }
 void rememberCataloguePosition(){
  returnCatalogue=page;returnAnchor=returnCategory=-1;
  if(right!=null&&right.getLayoutManager() instanceof LinearLayoutManager){
   LinearLayoutManager lm=(LinearLayoutManager)right.getLayoutManager();returnAnchor=lm.findFirstVisibleItemPosition();
   View first=lm.findViewByPosition(returnAnchor);returnOffset=first==null?0:lm.getDecoratedTop(first)-right.getPaddingTop();
  }
  if(left!=null&&left.getLayoutManager() instanceof LinearLayoutManager){
   LinearLayoutManager lm=(LinearLayoutManager)left.getLayoutManager();returnCategory=lm.findFirstVisibleItemPosition();
   View first=lm.findViewByPosition(returnCategory);returnCategoryOffset=first==null?0:lm.getDecoratedTop(first)-left.getPaddingTop();
  }
 }
 void restoreCataloguePosition(){
  final RecyclerView grid=right, categories=left;final int anchor=returnAnchor,offset=returnOffset,category=returnCategory,categoryOffset=returnCategoryOffset,selection=itemPos;
  returnAnchor=returnCategory=-1;returnCatalogue="";
  // Apply the saved anchor only after the real poster dimensions are measured.
  grid.post(()->{
   if(grid!=right||full)return;
   updatePosterGeometry(grid);
   if(category>=0&&categories.getLayoutManager() instanceof LinearLayoutManager)((LinearLayoutManager)categories.getLayoutManager()).scrollToPositionWithOffset(category,categoryOffset);
   if(grid.getLayoutManager() instanceof LinearLayoutManager)((LinearLayoutManager)grid.getLayoutManager()).scrollToPositionWithOffset(anchor,offset);
   grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
    public boolean onPreDraw(){
     if(grid.getViewTreeObserver().isAlive())grid.getViewTreeObserver().removeOnPreDrawListener(this);
     if(grid!=right||full)return true;
     RecyclerView.ViewHolder holder=grid.findViewHolderForAdapterPosition(selection);
     if(holder!=null)holder.itemView.requestFocus();else focusAt(grid,selection);
     return true;
    }
   });
  });
 }
 void playFull(Item i){
  if(!full&&(page.equals("Movies")||page.equals("Series")))rememberCataloguePosition();
  // Reuse the same live player for both native and forced SDR when the selected channel is unchanged.
  if(page.equals("Live TV")&&!full&&((activeSdrPath==0)=="SDR Full".equals(rangeFor(true)))&&player!=null&&video!=null&&preview!=null&&liveSurfaceHost!=null&&video.getParent()==liveSurfaceHost&&i.id.equals(activeStreamId)&&player.getPlayerError()==null&&player.getPlaybackState()!=Player.STATE_IDLE&&player.getPlaybackState()!=Player.STATE_ENDED&&enterLiveFullscreen())return;
  // Capture only a declared rate from the identical feed. Never guess from a different channel or alternative SDR feed.
  float previewFps=-1;
  if(!full&&autoRefresh()&&player!=null&&playbackProbe!=null&&playbackProbe.sourceItem!=null&&i.id.equals(activeStreamId)&&i.id.equals(playbackProbe.sourceItem.id)){
   Format known=player.getVideoFormat();if(known!=null&&RefreshMatch.valid(known.frameRate))previewFps=known.frameRate;
  }
  release();screenToken++;loadToken++;epgToken++;full=true;if(regionalGuide!=null)regionalGuide.setBusy(true);status=null;FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.BLACK);setContentView(f);immersive();attach(f,i,true);
  if(RefreshMatch.valid(previewFps)&&autoRefresh()&&playbackProbe!=null&&playbackProbe.sourceItem!=null&&i.id.equals(playbackProbe.sourceItem.id)){
   android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode cur=d.getMode();
   ArrayList<Float> available=new ArrayList<>();for(android.view.Display.Mode mode:d.getSupportedModes())if(mode.getPhysicalWidth()==cur.getPhysicalWidth()&&mode.getPhysicalHeight()==cur.getPhysicalHeight())available.add(mode.getRefreshRate());
   float[] rates=new float[available.size()];for(int n=0;n<rates.length;n++)rates[n]=available.get(n);
   if(RefreshMatch.select(cur.getRefreshRate(),previewFps,rates)>=0||RefreshMatch.compatible(cur.getRefreshRate(),previewFps)){
    requestRefresh(playbackProbe,previewFps);
    // Recheck the new player format normally after first frame; identical mode requests are no-ops.
    matchProbe=null;
   }
  }
  if(video!=null&&premiumPlayer==null)video.requestFocus();
 }
 void positionLiveSurface(){
  if(liveSurfaceHost==null||preview==null||full||preview.getWidth()<=0||preview.getHeight()<=0)return;
  View root=(View)liveSurfaceHost.getParent();if(root==null)return;
  int[] target=new int[2],origin=new int[2];preview.getLocationInWindow(target);root.getLocationInWindow(origin);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(preview.getWidth(),preview.getHeight(),Gravity.TOP|Gravity.LEFT);
  lp.leftMargin=target[0]-origin[0];lp.topMargin=target[1]-origin[1];
  ViewGroup.LayoutParams current=liveSurfaceHost.getLayoutParams();
  if(!(current instanceof FrameLayout.LayoutParams)||current.width!=lp.width||current.height!=lp.height||((FrameLayout.LayoutParams)current).leftMargin!=lp.leftMargin||((FrameLayout.LayoutParams)current).topMargin!=lp.topMargin||((FrameLayout.LayoutParams)current).gravity!=lp.gravity)liveSurfaceHost.setLayoutParams(lp);
  int pad=dp(4);if(liveSurfaceHost.getPaddingLeft()!=pad||liveSurfaceHost.getPaddingRight()!=pad||liveSurfaceHost.getPaddingTop()!=pad||liveSurfaceHost.getPaddingBottom()!=pad)liveSurfaceHost.setPadding(pad,pad,pad,pad);if(video!=null&&video.getResizeMode()!=4)video.setResizeMode(4);if(!(liveSurfaceHost.getForeground() instanceof PulseFrame))liveSurfaceHost.setForeground(previewPulse());
 }
 boolean enterLiveFullscreen(){
  if(liveSurfaceHost==null||video==null||player==null)return false;
  liveReturnFocus=getCurrentFocus();liveReturnStatus=status;
  suspendPreviewGuide();full=true;if(regionalGuide!=null)regionalGuide.setBusy(true);if(playbackProbe!=null)playbackProbe.health.reset();status=null;
  if(body!=null){liveBodyFocusMode=body.getDescendantFocusability();liveBodyAccessibility=body.getImportantForAccessibility();body.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
  // Fullscreen uses the original SurfaceView and pre-draw pipeline, not the preview texture.
  liveSurfaceHost.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));liveSurfaceHost.setPadding(0,0,0,0);switchLiveSdrOutput(true);if(playbackProbe!=null)playbackProbe.resetModeStats();video.setResizeMode(prefs.getInt("resize_mode",0));liveSurfaceHost.setForeground(null);
  liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
  video.setFocusable(true);video.setUseController(true);video.setControllerAutoShow(true);
  player.setVolume(1f);if(!player.getPlayWhenReady())player.play();video.requestFocus();if(playbackItem!=null)addRangeButton(liveSurfaceHost,playbackItem,video);
  if(autoRefresh()&&playbackProbe!=null){
   // The identical live session already knows its FPS: request the mode as fullscreen opens.
   Format known=player.getVideoFormat();float fps=known==null?-1:known.frameRate;
   if(!RefreshMatch.valid(fps))fps=playbackProbe.sourceFps;
   if(RefreshMatch.valid(fps)){
    android.view.Display d=getWindowManager().getDefaultDisplay();android.view.Display.Mode current=d.getMode();
    ArrayList<Float> available=new ArrayList<>();for(android.view.Display.Mode mode:d.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())available.add(mode.getRefreshRate());
    float[] rates=new float[available.size()];for(int n=0;n<rates.length;n++)rates[n]=available.get(n);
    if(RefreshMatch.select(current.getRefreshRate(),fps,rates)>=0||RefreshMatch.compatible(current.getRefreshRate(),fps))requestRefresh(playbackProbe,fps);
    else scheduleRefreshMatch(player,frameRateSample,0);
   }else scheduleRefreshMatch(player,frameRateSample,0);
  }
  android.util.Log.i("SharkPlayback","Live fullscreen: same player, output target ready for fullscreen");return true;
 }
 boolean exitLiveFullscreen(){
  if(liveSurfaceHost==null||player==null||video==null||preview==null)return false;
  // Return to the normal system/manual display mode when leaving fullscreen, and cancel queued matches.
  clearRefreshMatch(true);removeRangeButton();video.hideController();video.setUseController(false);video.setFocusable(false);
  liveSurfaceHost.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
  player.setVolume(0f);full=false;if(regionalGuide!=null)regionalGuide.setBusy(stopped);if(playbackProbe!=null)playbackProbe.health.reset();status=liveReturnStatus;liveReturnStatus=null;
  switchLiveSdrOutput(false);if(playbackProbe!=null)playbackProbe.resetModeStats();positionLiveSurface();if(highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);else if(selected!=null&&epg!=null)loadEpg(selected);
  if(body!=null){body.setDescendantFocusability(liveBodyFocusMode);body.setImportantForAccessibility(liveBodyAccessibility);}
  View focus=liveReturnFocus;liveReturnFocus=null;if(focus!=null&&focus.isAttachedToWindow())focus.requestFocus();else if(right!=null)focusAt(right,itemPos);
  android.util.Log.i("SharkPlayback","Live preview: same player, output target ready for preview");return true;
 }
 @Override public void onBackPressed(){if(full){if(exitLiveFullscreen())return;Item resumePreview=page.equals("Live TV")?playbackItem:null;release();browser(true);if(resumePreview!=null&&preview!=null){selected=resumePreview;attach(preview,resumePreview,false);loadEpg(resumePreview);}return;}if(page.equals("Series")&&!seriesId.isEmpty()){seriesId="";loadItems();return;}if(!page.equals("Home")){home();return;}super.onBackPressed();}
 @Override protected void onStart(){super.onStart();stopped=false;if(regionalGuide!=null)regionalGuide.setBusy(full);if(resumeExo!=null&&resumeExo==player){resumeExo=null;applyDisplayMode();player.play();if(autoRefresh()&&refreshInCurrentView())scheduleRefreshMatch(player,frameRateSample,0);}if(liveHealthWatch!=null){if(playbackProbe!=null)playbackProbe.health.reset();stopLiveHealth();ui.postDelayed(liveHealthWatch,2000);}ui.removeCallbacks(tickRows);if(page.equals("Live TV"))ui.postDelayed(tickRows,60000);if(page.equals("Live TV")&&!full&&highlightedGuideItem!=null&&epg!=null)loadEpg(highlightedGuideItem);}
 @Override protected void onStop(){suspendPreviewGuide();ui.removeCallbacks(tickRows);stopped=true;if(regionalGuide!=null)regionalGuide.setBusy(true);resumeExo=player!=null&&player.getPlayWhenReady()&&player.getPlayerError()==null&&player.getPlaybackState()!=Player.STATE_ENDED?player:null;stopLiveHealth();if(playbackProbe!=null)playbackProbe.health.reset();cancelDecoderTasks();clearRefreshMatch(true);if(premiumPlayer!=null)premiumPlayer.hide();super.onStop();if(player!=null)player.pause();if(nativeVideo!=null){nativeVideo.pause();if(premiumPlayer!=null)premiumPlayer.nativeEnded();}}
 @Override protected void onDestroy(){if(regionalGuide!=null)regionalGuide.close();screenToken++;loadToken++;epgToken++;guideRequest++;if(guideDialog!=null&&guideDialog.isShowing())guideDialog.dismiss();release();detachCatalogueViews();if(catalogueJob!=null)catalogueJob.cancel(true);cancelPreviewGuideJob();cancelFullGuideJob();io.shutdownNow();epgIo.shutdownNow();rowGuideIo.shutdownNow();updateIo.shutdownNow();posterIo.shutdownNow();posterCache.evictAll();posterUrls.evictAll();guideCache.evictAll();clearCatalogue();PremiumMockupHome.trimArtwork();ui.removeCallbacksAndMessages(null);super.onDestroy();}
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
   JSONObject j=releaseMetadata();
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
 JSONObject releaseMetadata()throws Exception{
  // Direct GitHub release asset does not depend on the unauthenticated REST quota.
  Exception directFailure;
  try{
   HttpURLConnection con=openUpdate("https://github.com/sharkmantv/SharkNailTV/releases/latest/download/update.json");
   try{
    JSONObject j=new JSONObject(readUpdateMetadata(con));
    String version=j.optString("tag_name");
    JSONArray assets=j.optJSONArray("assets");
    if(!version.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")||assets==null||assets.length()!=1)throw new IOException("Invalid update manifest");
    JSONObject apk=assets.getJSONObject(0);
    if(!"SharkNailTV.apk".equals(apk.optString("name"))||!apk.optString("digest").matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("Invalid manifest checksum");
    return j;
   }finally{con.disconnect();}
  }catch(Exception e){directFailure=e;}
  HttpURLConnection con=null;
  try{
   con=(HttpURLConnection)new URL("https://api.github.com/repos/sharkmantv/SharkNailTV/releases/latest").openConnection();
   con.setInstanceFollowRedirects(false);con.setConnectTimeout(10000);con.setReadTimeout(15000);
   con.setRequestProperty("User-Agent","SharkSky/"+BuildConfig.VERSION_NAME);
   con.setRequestProperty("Accept","application/vnd.github+json");
   int code=con.getResponseCode();
   if(code!=200)throw new IOException("GitHub API HTTP "+code);
   return new JSONObject(readUpdateMetadata(con));
  }catch(Exception e){throw new IOException("Manifest "+updateError(directFailure)+" / "+updateError(e));}
  finally{if(con!=null)con.disconnect();}
 }
 static String readUpdateMetadata(HttpURLConnection con)throws Exception{
  try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>512*1024)throw new IOException("Update metadata too large");out.write(b,0,n);}return out.toString("UTF-8");
  }
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
