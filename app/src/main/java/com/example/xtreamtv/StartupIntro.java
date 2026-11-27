package com.example.xtreamtv;

import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;

/** Single cold-launch movie over the already-built home screen. */
final class StartupIntro {
 private static boolean attempted;
 private final MainActivity activity;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private FrameLayout overlay;
 private VideoView video;
 private View home, previousFocus;
 private int homeAccessibility, consumedKey=-1;
 private boolean triedFallback;
 private final Runnable watchdog=()->stop("timeout");

 StartupIntro(MainActivity activity){this.activity=activity;}
 private int resource(String name){
  return activity.getResources().getIdentifier(name,"raw",activity.getPackageName());
 }
 private boolean canDecodeUhd(){
  android.view.Display.Mode mode=activity.getWindowManager().getDefaultDisplay().getMode();
  if(Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight())<3840||Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight())<2160)return false;
  try{
   for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
    if(info.isEncoder())continue;
    if(android.os.Build.VERSION.SDK_INT>=29&&!info.isHardwareAccelerated())continue;
    for(String mime:info.getSupportedTypes())if("video/avc".equalsIgnoreCase(mime)){
     MediaCodecInfo.VideoCapabilities cap=info.getCapabilitiesForType(mime).getVideoCapabilities();
     if(cap!=null&&cap.areSizeAndRateSupported(3840,2160,60))return true;
    }
   }
  }catch(RuntimeException e){Log.w("SharkIntro","UHD decoder detection failed",e);}
  return false;
 }
 void start(){
  if(attempted)return;
  attempted=true;
  int uhd=resource("shark_intro_uhd"), fallback=resource("shark_intro_hd");
  if(fallback==0)return;
  FrameLayout content=activity.findViewById(android.R.id.content);
  if(content==null||content.getChildCount()==0)return;
  home=content.getChildAt(0);previousFocus=home.findFocus();
  homeAccessibility=home.getImportantForAccessibility();
  home.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);
  overlay.setFocusableInTouchMode(true);overlay.setContentDescription("Skip cinematic intro");
  overlay.setOnClickListener(v->stop("skipped"));
  content.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.requestFocus();
  triedFallback=uhd==0||!canDecodeUhd();
  handler.postDelayed(watchdog,12000);
  play(triedFallback?fallback:uhd);
 }
 private void play(int id){
  if(overlay==null)return;
  if(id==0){stop("missing");return;}
  if(video!=null){video.setOnCompletionListener(null);video.setOnErrorListener(null);video.setOnPreparedListener(null);video.stopPlayback();overlay.removeView(video);}
  final VideoView current=new VideoView(activity);video=current;
  current.setFocusable(false);
  overlay.addView(current,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  current.setOnPreparedListener(mp->{if(video!=current||overlay==null)return;mp.setLooping(false);Log.i("SharkIntro","prepared "+mp.getVideoWidth()+"x"+mp.getVideoHeight());current.start();});
  current.setOnCompletionListener(mp->{if(video==current)stop("completed");});
  current.setOnErrorListener((mp,what,extra)->{
   if(video!=current||overlay==null)return true;
   Log.w("SharkIntro","decoder error "+what+"/"+extra);
   if(!triedFallback){triedFallback=true;handler.post(()->{if(video==current&&overlay!=null)play(resource("shark_intro_hd"));});}
   else stop("error");
   return true;
  });
  current.setVideoURI(Uri.parse("android.resource://"+activity.getPackageName()+"/"+id));
  current.start();
  Log.i("SharkIntro","started "+(triedFallback?"HD":"UHD"));
 }
 boolean handleKey(KeyEvent e){
  int key=e.getKeyCode();
  if(e.getAction()==KeyEvent.ACTION_UP&&key==consumedKey){consumedKey=-1;return true;}
  if(overlay==null)return false;
  if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
   if(e.getAction()==KeyEvent.ACTION_DOWN){consumedKey=key;stop("skipped");}
   return true;
  }
  return key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
 }
 void stop(String reason){
  handler.removeCallbacksAndMessages(null);
  if(overlay==null)return;
  FrameLayout old=overlay;overlay=null;
  if(video!=null){VideoView v=video;video=null;v.setOnCompletionListener(null);v.setOnErrorListener(null);v.setOnPreparedListener(null);v.stopPlayback();old.removeView(v);}
  if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
  if(home!=null){home.setImportantForAccessibility(homeAccessibility);if(previousFocus!=null&&previousFocus.isAttachedToWindow())previousFocus.requestFocus();}
  home=null;previousFocus=null;
  Log.i("SharkIntro","home revealed: "+reason);
 }
}
