package com.example.xtreamtv;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.Locale;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PremiumPlayerOverlay extends FrameLayout {
 MainActivity a; FrameLayout host; PlayerView surface; MainActivity.Item item; ExoPlayer p;
 boolean live,compact,disposed,scrubbing,consumeUp;
 static final int GOLD=0xffffca22,WHITE=0xfff7fbff;
 SeekBar progress; TextView elapsed,total,metadata; Control play;
 final Runnable timeout=()->{if(!p.getPlayWhenReady())return;if(a.hasWindowFocus())hide();else arm();};
 final Runnable ticker=new Runnable(){public void run(){if(!disposed&&isShown()&&a.player==p){refresh();postDelayed(this,500);}}};
 final Player.Listener listener=new Player.Listener(){@Override public void onEvents(Player player,Player.Events events){if(isShown())refresh();}@Override public void onPlayWhenReadyChanged(boolean ready,int reason){android.util.Log.i("SharkPlayerUI","playWhenReady="+ready);if(isShown())arm();}};
 PremiumPlayerOverlay(MainActivity activity,FrameLayout parent,PlayerView video,MainActivity.Item current){
  super(activity);a=activity;host=parent;surface=video;item=current;p=a.player;
  live=a.page.equals("Live TV");compact=a.getResources().getConfiguration().screenWidthDp<650;
  setFocusable(false);setClickable(false);setClipChildren(false);setClipToPadding(false);
  LinearLayout top=a.row();top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(20),d(5),d(20),d(7));top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xdf071631,0x99091b40,0x00091b40}));
  ImageView logo=new ImageView(a);logo.setImageResource(R.drawable.player_sky_colour);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("Sky");top.addView(logo,new LinearLayout.LayoutParams(d(compact?62:100),d(compact?30:48)));
  LinearLayout titles=a.col();titles.setPadding(d(13),0,d(6),0);top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView title=a.text(item.name,compact?16:21);title.setTextColor(WHITE);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title);
  metadata=a.text("",compact?11:13);metadata.setSingleLine();metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(metadata);
  TextClock clock=new TextClock(a);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("h:mm a");clock.setTextSize(compact?15:19);clock.setTypeface(a.uiMedium);clock.setTextColor(WHITE);top.addView(clock,new LinearLayout.LayoutParams(-2,-2));addView(top,new FrameLayout.LayoutParams(-1,d(compact?50:68),Gravity.TOP));
  LinearLayout bottom=a.col();bottom.setClipChildren(false);bottom.setClipToPadding(false);bottom.setPadding(d(12),0,d(12),d(8));addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  LinearLayout dock=a.col();dock.setClipChildren(false);dock.setClipToPadding(false);dock.setPadding(d(compact?18:36),d(compact?12:22),d(compact?18:36),d(compact?8:13));dock.setBackground(new Dock());bottom.addView(dock,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout timeline=a.row();timeline.setGravity(Gravity.CENTER_VERTICAL);dock.addView(timeline,new LinearLayout.LayoutParams(-1,d(compact?29:35)));
  elapsed=a.text("00:00",compact?12:15);elapsed.setGravity(Gravity.CENTER);elapsed.setTextColor(WHITE);timeline.addView(elapsed,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress=new SeekBar(a);progress.setId(View.generateViewId());progress.setMax(10000);progress.setPadding(d(10),0,d(10),0);tint(progress);progress.setSecondaryProgressTintList(ColorStateList.valueOf(0xffa4b1c9));progress.setContentDescription("Playback progress");progress.setBackground(new Ring(false));timeline.addView(progress,new LinearLayout.LayoutParams(0,-1,1));
  total=a.text("00:00",compact?12:15);total.setGravity(Gravity.CENTER);total.setTextColor(WHITE);timeline.addView(total,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){scrubbing=true;removeCallbacks(timeout);}public void onStopTrackingTouch(SeekBar s){seek(s.getProgress());scrubbing=false;arm();}public void onProgressChanged(SeekBar s,int v,boolean user){if(user){if(!scrubbing)seek(v);else if(p.getDuration()>0)elapsed.setText(time((long)(p.getDuration()*(v/10000d))));if(!scrubbing)arm();}}});
  LinearLayout transport=a.row();transport.setGravity(Gravity.CENTER);transport.setClipChildren(false);transport.setClipToPadding(false);dock.addView(transport,new LinearLayout.LayoutParams(-1,d(compact?47:64)));
  Control prev=button(transport,0,"Previous",()->adjacent(-1));Control rew=button(transport,1,"Rewind",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();});
  play=button(transport,2,"Play or pause",()->{if(p.getPlayWhenReady())p.pause();else p.play();refresh();});
  Control ff=button(transport,3,"Forward",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();});Control next=button(transport,4,"Next",()->adjacent(1));
  View[] keys={prev,rew,play,ff,next};link(keys);
  LinearLayout opts=a.row();opts.setGravity(Gravity.CENTER);opts.setPadding(d(12),d(4),d(12),0);opts.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xcc091b3c,0x8805152f,0x44051028}));bottom.addView(opts,new LinearLayout.LayoutParams(-1,d(compact?35:46)));
  View[] actions={option(opts,"Audio",()->new AlertDialog.Builder(a).setTitle("Audio").setItems(new String[]{"Audio tracks","Volume"},(dialog,i)->{if(i==0)a.chooseTrack(C.TRACK_TYPE_AUDIO);else volume();}).setNegativeButton("Cancel",null).show()),option(opts,"Subtitles",()->a.chooseTrack(C.TRACK_TYPE_TEXT)),option(opts,"Aspect",a::aspectDialog),option(opts,"Picture",()->a.choosePlayerRange(item)),option(opts,"Info",()->new AlertDialog.Builder(a).setTitle(item.name).setMessage(a.playbackDiagnostics).setPositiveButton("Close",null).show())};
  link(actions);for(int i=0;i<keys.length;i++){keys[i].setNextFocusUpId(progress.getId());keys[i].setNextFocusDownId(actions[i].getId());actions[i].setNextFocusUpId(keys[i].getId());}progress.setNextFocusDownId(play.getId());
  host.addView(this,new FrameLayout.LayoutParams(-1,-1));surface.setFocusable(true);surface.setFocusableInTouchMode(true);surface.setContentDescription("Show playback controls");surface.setOnClickListener(v->{if(getVisibility()==VISIBLE)hide();else show();});surface.setOnKeyListener((v,k,e)->handleKey(e));p.addListener(listener);
 }
 int d(int n){return a.dp(n);}
 void tint(SeekBar s){s.setProgressTintList(ColorStateList.valueOf(GOLD));s.setThumbTintList(ColorStateList.valueOf(GOLD));s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff677a9a));}
 void link(View[] views){for(int i=0;i<views.length;i++){views[i].setNextFocusLeftId(views[Math.max(i-1,0)].getId());views[i].setNextFocusRightId(views[Math.min(i+1,views.length-1)].getId());}}
 Control button(LinearLayout row,int type,String label,Runnable action){Control v=new Control(type);v.setId(View.generateViewId());v.setContentDescription(label);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new Ring(true));v.setOnClickListener(x->{action.run();arm();});int size=d(compact?43:58);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,size);lp.setMargins(d(compact?8:13),0,d(compact?8:13),0);row.addView(v,lp);return v;}
 TextView option(LinearLayout row,String label,Runnable action){TextView v=a.text(label,compact?12:16);v.setId(View.generateViewId());v.setGravity(Gravity.CENTER);v.setTextColor(WHITE);v.setSingleLine();v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new Ring(false));row.addView(v,new LinearLayout.LayoutParams(0,-1,1));v.setOnClickListener(x->{arm();action.run();});return v;}
 void seek(int value){if(p.isCurrentMediaItemSeekable()&&p.getDuration()>0)p.seekTo((long)(p.getDuration()*(value/10000d)));}
 void adjacent(int delta){int index=-1;for(int i=0;i<a.items.size();i++)if(a.items.get(i).id.equals(item.id)){index=i;break;}int n=index+delta;if(index>=0&&n>=0&&n<a.items.size()){a.itemPos=n;MainActivity.Item target=a.items.get(n);if(live)a.selected=target;a.playFull(target);}}
 void volume(){AudioManager audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);if(audio==null)return;LinearLayout panel=a.col();panel.setPadding(d(24),d(16),d(24),d(16));SeekBar bar=new SeekBar(a);bar.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));bar.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));tint(bar);bar.setContentDescription("Volume");bar.setEnabled(!audio.isVolumeFixed());panel.addView(bar,new LinearLayout.LayoutParams(-1,d(44)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user&&!audio.isVolumeFixed())try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);}catch(SecurityException ignored){}}});new AlertDialog.Builder(a).setTitle(audio.isVolumeFixed()?"Use TV volume control":"Volume").setView(panel).setPositiveButton("Done",null).show();}
 String time(long millis){long s=Math.max(0,millis/1000);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60);}
 void refresh(){if(disposed||a.player!=p)return;Format f=p.getVideoFormat(),audio=p.getAudioFormat();StringBuilder s=new StringBuilder(live?"LIVE":"");if(f!=null&&f.height>0){if(s.length()>0)s.append(" · ");s.append(f.height>=2160?"4K UHD":f.height>=1080?"1080p":f.height+"p");if(MainActivity.isHdr(f))s.append(" · HDR");}if(audio!=null&&audio.channelCount>0){if(s.length()>0)s.append(" · ");s.append(audio.channelCount==6?"5.1 audio":audio.channelCount==2?"Stereo":audio.channelCount+" ch audio");}metadata.setText(s.toString());long duration=p.getDuration();boolean canSeek=p.isCurrentMediaItemSeekable()&&duration>0;progress.setEnabled(canSeek);progress.setFocusable(canSeek);if(!scrubbing){elapsed.setText(live&&!canSeek?"LIVE":time(p.getCurrentPosition()));total.setText(duration>0?time(duration):"—");progress.setProgress(canSeek?(int)Math.min(10000,Math.max(0,10000d*p.getCurrentPosition()/duration)):0);progress.setSecondaryProgress(canSeek?(int)Math.min(10000,10000d*p.getBufferedPosition()/duration):0);}play.setContentDescription(p.getPlayWhenReady()?"Pause":"Play");play.invalidate();}
 void arm(){removeCallbacks(timeout);if(isShown()&&!scrubbing&&!disposed&&p.getPlayWhenReady())postDelayed(timeout,6000);}
 void show(){if(disposed||a.player!=p)return;setVisibility(VISIBLE);android.util.Log.i("SharkPlayerUI","overlay shown");refresh();if(!hasFocus())play.requestFocus();removeCallbacks(ticker);post(ticker);arm();}
 void hide(){if(disposed)return;removeCallbacks(timeout);removeCallbacks(ticker);setVisibility(GONE);surface.requestFocus();android.util.Log.i("SharkPlayerUI","overlay hidden");}
 boolean handleKey(KeyEvent e){if(disposed)return false;int k=e.getKeyCode();boolean nav=k==19||k==20||k==21||k==22||k==23||k==66||k==82||k==KeyEvent.KEYCODE_BUTTON_A||k==KeyEvent.KEYCODE_NUMPAD_ENTER;if(e.getAction()==KeyEvent.ACTION_UP&&consumeUp){consumeUp=false;return true;}if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE){if(k==KeyEvent.KEYCODE_MEDIA_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&p.getPlayWhenReady())p.pause();else p.play();show();consumeUp=true;return true;}if(k==KeyEvent.KEYCODE_MEDIA_REWIND||k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){if(k==KeyEvent.KEYCODE_MEDIA_REWIND&&p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD&&p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();show();consumeUp=true;return true;}if(nav&&getVisibility()!=VISIBLE){show();consumeUp=true;return true;}if(nav)arm();if(k==82){a.choosePlayerRange(item);consumeUp=true;return true;}return false;}
 void dispose(){if(disposed)return;disposed=true;removeCallbacks(timeout);removeCallbacks(ticker);p.removeListener(listener);surface.setOnKeyListener(null);surface.setOnClickListener(null);surface.setContentDescription(null);surface.setFocusableInTouchMode(false);host.removeView(this);}
 final class Control extends View {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final int type;
  Control(int t){super(PremiumPlayerOverlay.this.a);type=t;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth(),getHeight())/64f;c.scale(scale,scale);pen.setColor(WHITE);pen.setStyle(Paint.Style.FILL);pen.setStrokeCap(Paint.Cap.ROUND);
   if(type==2){if(p.getPlayWhenReady()){c.drawRoundRect(-11,-14,-3,14,2,2,pen);c.drawRoundRect(3,-14,11,14,2,2,pen);}else triangle(c,-10,-15,15,0,-10,15);}
   else if(type==0||type==4){if(type==0){c.drawRect(-15,-12,-11,12,pen);triangle(c,12,-13,-10,0,12,13);}else{c.drawRect(11,-12,15,12,pen);triangle(c,-12,-13,10,0,-12,13);}}
   else{boolean back=type==1;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);c.drawArc(-15,-15,15,15,back?-50:-130,300,false,pen);pen.setStyle(Paint.Style.FILL);if(back)triangle(c,-13,-17,-3,-22,-3,-9);else triangle(c,13,-17,3,-22,3,-9);pen.setTextSize(12);pen.setTypeface(a.uiMedium);pen.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf((back?p.getSeekBackIncrement():p.getSeekForwardIncrement())/1000),0,4,pen);}
   c.restore();
  }
  void triangle(Canvas c,float x,float y,float X,float Y,float z,float Z){Path path=new Path();path.moveTo(x,y);path.lineTo(X,Y);path.lineTo(z,Z);path.close();c.drawPath(path,pen);}
 }
 final class Ring extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean circle;boolean focused;final Runnable tick=()->invalidateSelf();
  Ring(boolean round){circle=round;}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){boolean next=false;for(int s:states)if(s==android.R.attr.state_focused)next=true;if(next==focused)return false;focused=next;if(!next)unscheduleSelf(tick);invalidateSelf();return true;}
  @Override public void draw(Canvas c){if(!focused||!isVisible())return;RectF r=new RectF(getBounds());r.inset(d(4),d(4));float phase=(float)(.5+.5*Math.cos(2*Math.PI*(SystemClock.uptimeMillis()%1600)/1600d));pen.setStyle(Paint.Style.STROKE);float radius=circle?r.height()/2:d(12);for(int i=4;i>0;i--){pen.setColor(Color.argb((int)(12+phase*18),255,190,0));pen.setStrokeWidth(d(i*2));c.drawRoundRect(r,radius,radius,pen);}pen.setColor(GOLD);pen.setStrokeWidth(d(2));c.drawRoundRect(r,radius,radius,pen);scheduleSelf(tick,SystemClock.uptimeMillis()+40);}
  @Override public boolean setVisible(boolean v,boolean restart){if(!v)unscheduleSelf(tick);return super.setVisible(v,restart);}
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 final class Dock extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
  @Override public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height();if(w<=0||h<=0)return;c.save();c.translate(b.left,b.top);path.reset();path.moveTo(w*.015f,h*.48f);path.cubicTo(w*.014f,-h*.17f,w*.16f,h*.05f,w*.29f,h*.13f);path.cubicTo(w*.46f,h*.29f,w*.55f,h*.29f,w*.71f,h*.13f);path.cubicTo(w*.84f,h*.05f,w*.986f,-h*.17f,w*.985f,h*.48f);path.cubicTo(w*1.005f,h*.91f,w*.88f,h*.99f,w*.5f,h*.99f);path.cubicTo(w*.12f,h*.99f,-w*.005f,h*.91f,w*.015f,h*.48f);path.close();pen.setStyle(Paint.Style.FILL);pen.setShader(new LinearGradient(0,0,0,h,new int[]{0x995583bd,0xdf0b2852,0xf2061742,0xdd0055bd},new float[]{0,.32f,.8f,1},Shader.TileMode.CLAMP));c.drawPath(path,pen);pen.setShader(null);pen.setStyle(Paint.Style.STROKE);for(int i=4;i>0;i--){pen.setColor(0x1510aaff);pen.setStrokeWidth(d(i*2));c.drawPath(path,pen);}pen.setShader(new LinearGradient(0,0,w,h,new int[]{0xff39dcff,0xff3971c6,0xffb4ecff,0xff10bbff},null,Shader.TileMode.CLAMP));pen.setStrokeWidth(d(2));c.drawPath(path,pen);pen.setShader(null);c.restore();}
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
