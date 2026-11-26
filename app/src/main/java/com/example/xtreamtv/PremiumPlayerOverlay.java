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
 final View wakeTarget;
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
  LinearLayout bottom=a.col();bottom.setClipChildren(false);bottom.setClipToPadding(false);bottom.setPadding(d(compact?12:24),0,d(compact?12:24),d(compact?8:14));addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  LinearLayout dock=a.col();dock.setClipChildren(false);dock.setClipToPadding(false);dock.setPadding(d(compact?12:24),d(compact?10:16),d(compact?12:24),d(compact?8:12));dock.setBackground(new Dock());bottom.addView(dock,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout timeline=a.row();timeline.setGravity(Gravity.CENTER_VERTICAL);dock.addView(timeline,new LinearLayout.LayoutParams(-1,d(compact?29:35)));
  elapsed=a.text("00:00",compact?12:15);elapsed.setGravity(Gravity.CENTER);elapsed.setTextColor(WHITE);timeline.addView(elapsed,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress=new SeekBar(a);progress.setId(View.generateViewId());progress.setMax(10000);progress.setPadding(d(10),0,d(10),0);glassTimeline(progress);progress.setContentDescription("Playback progress");progress.setBackground(new Ring(false));timeline.addView(progress,new LinearLayout.LayoutParams(0,-1,1));
  total=a.text("00:00",compact?12:15);total.setGravity(Gravity.CENTER);total.setTextColor(WHITE);timeline.addView(total,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){scrubbing=true;removeCallbacks(timeout);}public void onStopTrackingTouch(SeekBar s){seek(s.getProgress());scrubbing=false;arm();}public void onProgressChanged(SeekBar s,int v,boolean user){if(user){if(!scrubbing)seek(v);else if(p.getDuration()>0)elapsed.setText(time((long)(p.getDuration()*(v/10000d))));if(!scrubbing)arm();}}});
  LinearLayout transport=a.row();transport.setGravity(Gravity.CENTER);transport.setPadding(d(compact?8:24),0,d(compact?8:24),0);transport.setClipChildren(false);transport.setClipToPadding(false);dock.addView(transport,new LinearLayout.LayoutParams(-1,d(compact?54:78)));
  Control prev=button(transport,0,"Previous",()->adjacent(-1));Control rew=button(transport,1,"Rewind",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();});
  play=button(transport,2,"Play or pause",()->{if(p.getPlayWhenReady())p.pause();else p.play();refresh();});
  Control ff=button(transport,3,"Forward",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();});Control next=button(transport,4,"Next",()->adjacent(1));
  View[] keys={prev,rew,play,ff,next};link(keys);
  LinearLayout opts=a.row();opts.setGravity(Gravity.CENTER);opts.setPadding(d(12),d(4),d(12),0);dock.addView(opts,new LinearLayout.LayoutParams(-1,d(compact?35:46)));
  View[] actions={option(opts,"Audio",()->new AlertDialog.Builder(a).setTitle("Audio").setItems(new String[]{"Audio tracks","Volume"},(dialog,i)->{if(i==0)a.chooseTrack(C.TRACK_TYPE_AUDIO);else volume();}).setNegativeButton("Cancel",null).show()),option(opts,"Subtitles",()->a.chooseTrack(C.TRACK_TYPE_TEXT)),option(opts,"Aspect",a::aspectDialog),option(opts,"Picture",()->a.choosePlayerRange(item)),option(opts,"Info",()->new AlertDialog.Builder(a).setTitle(item.name).setMessage(a.playbackDiagnostics).setPositiveButton("Close",null).show())};
  link(actions);for(int i=0;i<keys.length;i++){keys[i].setNextFocusUpId(progress.getId());keys[i].setNextFocusDownId(actions[i].getId());actions[i].setNextFocusUpId(keys[i].getId());}progress.setNextFocusDownId(play.getId());
  // A dedicated transparent native view owns focus when controls are hidden.
  // Media3 may replace its own accessibility description and focus behaviour.
  wakeTarget=new View(a);
  wakeTarget.setId(View.generateViewId());wakeTarget.setFocusable(true);wakeTarget.setFocusableInTouchMode(true);
  wakeTarget.setClickable(true);wakeTarget.setContentDescription("Show playback controls");
  wakeTarget.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  wakeTarget.setOnClickListener(v->show());
  wakeTarget.setOnKeyListener((v,k,e)->handleKey(e));
  host.addView(wakeTarget,new FrameLayout.LayoutParams(-1,-1));
  host.addView(this,new FrameLayout.LayoutParams(-1,-1));
  p.addListener(listener);
 }
 int d(int n){return a.dp(n);}
 void tint(SeekBar s){s.setProgressTintList(ColorStateList.valueOf(GOLD));s.setThumbTintList(ColorStateList.valueOf(GOLD));s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff677a9a));}
 GradientDrawable track(int[] colors,int height){
  GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,colors);
  shape.setShape(GradientDrawable.RECTANGLE);shape.setSize(d(10),d(height));return shape;
 }
 void glassTimeline(SeekBar bar){
  GradientDrawable base=track(new int[]{0xff203d67,0xff406895,0xff263f66},5);
  ClipDrawable buffered=new ClipDrawable(track(new int[]{0xff567ba0,0xff87a9ca},5),Gravity.LEFT,ClipDrawable.HORIZONTAL);
  ClipDrawable played=new ClipDrawable(track(new int[]{0xff15a6e7,0xff82ebff,0xffb9f6ff},5),Gravity.LEFT,ClipDrawable.HORIZONTAL);
  LayerDrawable layers=new LayerDrawable(new Drawable[]{base,buffered,played});
  int[] ids={android.R.id.background,android.R.id.secondaryProgress,android.R.id.progress};
  for(int i=0;i<3;i++){layers.setId(i,ids[i]);layers.setLayerHeight(i,d(5));layers.setLayerGravity(i,Gravity.CENTER_VERTICAL|Gravity.FILL_HORIZONTAL);}
  bar.setProgressTintList(null);bar.setSecondaryProgressTintList(null);bar.setProgressBackgroundTintList(null);bar.setThumbTintList(null);
  bar.setProgressDrawable(layers);
  GradientDrawable thumb=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xfffcffff,0xffb9e9ff,0xff42a4dc});
  thumb.setShape(GradientDrawable.OVAL);thumb.setSize(d(15),d(15));bar.setThumb(thumb);bar.setThumbOffset(d(7));
 }
 void link(View[] views){for(int i=0;i<views.length;i++){views[i].setNextFocusLeftId(views[Math.max(i-1,0)].getId());views[i].setNextFocusRightId(views[Math.min(i+1,views.length-1)].getId());}}
 Control button(LinearLayout row,int type,String label,Runnable action){Control v=new Control(type);v.setId(View.generateViewId());v.setContentDescription(label);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new Ring(false));v.setOnClickListener(x->{action.run();arm();});int size=d(compact?48:72);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,size);lp.setMargins(d(compact?8:18),0,d(compact?8:18),0);row.addView(v,lp);return v;}
 TextView option(LinearLayout row,String label,Runnable action){TextView v=a.text(label,compact?12:16);v.setId(View.generateViewId());v.setGravity(Gravity.CENTER);v.setTextColor(WHITE);v.setSingleLine();v.setCompoundDrawablePadding(d(5));Drawable glyph=new OptionGlyph(label);glyph.setBounds(0,0,d(compact?15:19),d(compact?15:19));v.setCompoundDrawables(glyph,null,null,null);v.setPadding(d(compact?4:10),0,d(compact?4:10),0);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new Ring(false));row.addView(v,new LinearLayout.LayoutParams(0,-1,1));v.setOnClickListener(x->{arm();action.run();});return v;}
 void seek(int value){if(p.isCurrentMediaItemSeekable()&&p.getDuration()>0)p.seekTo((long)(p.getDuration()*(value/10000d)));}
 void adjacent(int delta){int index=-1;for(int i=0;i<a.items.size();i++)if(a.items.get(i).id.equals(item.id)){index=i;break;}int n=index+delta;if(index>=0&&n>=0&&n<a.items.size()){a.itemPos=n;MainActivity.Item target=a.items.get(n);if(live)a.selected=target;a.playFull(target);}}
 void volume(){AudioManager audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);if(audio==null)return;LinearLayout panel=a.col();panel.setPadding(d(24),d(16),d(24),d(16));SeekBar bar=new SeekBar(a);bar.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));bar.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));tint(bar);bar.setContentDescription("Volume");bar.setEnabled(!audio.isVolumeFixed());panel.addView(bar,new LinearLayout.LayoutParams(-1,d(44)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user&&!audio.isVolumeFixed())try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);}catch(SecurityException ignored){}}});new AlertDialog.Builder(a).setTitle(audio.isVolumeFixed()?"Use TV volume control":"Volume").setView(panel).setPositiveButton("Done",null).show();}
 String time(long millis){long s=Math.max(0,millis/1000);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60);}
 void refresh(){if(disposed||a.player!=p)return;Format f=p.getVideoFormat(),audio=p.getAudioFormat();StringBuilder s=new StringBuilder(live?"LIVE":"");if(f!=null&&f.height>0){if(s.length()>0)s.append(" · ");s.append(f.height>=2160?"4K UHD":f.height>=1080?"1080p":f.height+"p");if(MainActivity.isHdr(f))s.append(" · HDR");}if(audio!=null&&audio.channelCount>0){if(s.length()>0)s.append(" · ");s.append(audio.channelCount==6?"5.1 audio":audio.channelCount==2?"Stereo":audio.channelCount+" ch audio");}metadata.setText(s.toString());long duration=p.getDuration();boolean canSeek=p.isCurrentMediaItemSeekable()&&duration>0;progress.setEnabled(canSeek);progress.setFocusable(canSeek);if(!scrubbing){elapsed.setText(live&&!canSeek?"LIVE":time(p.getCurrentPosition()));total.setText(duration>0?time(duration):"—");progress.setProgress(canSeek?(int)Math.min(10000,Math.max(0,10000d*p.getCurrentPosition()/duration)):0);progress.setSecondaryProgress(canSeek?(int)Math.min(10000,10000d*p.getBufferedPosition()/duration):0);}play.setContentDescription(p.getPlayWhenReady()?"Pause":"Play");play.invalidate();}
 void arm(){removeCallbacks(timeout);if(isShown()&&!scrubbing&&!disposed&&p.getPlayWhenReady())postDelayed(timeout,6000);}
 void show(){
  if(disposed||a.player!=p)return;
  boolean opening=getVisibility()!=VISIBLE||wakeTarget.getVisibility()==VISIBLE;
  setVisibility(VISIBLE);wakeTarget.setVisibility(GONE);refresh();
  if(opening||!hasFocus()){
   play.requestFocus();
   post(()->{if(!disposed&&getVisibility()==VISIBLE&&!hasFocus())play.requestFocus();});
  }
  android.util.Log.i("SharkPlayerUI","overlay shown");
  removeCallbacks(ticker);post(ticker);arm();
 }
 void hide(){
  if(disposed)return;
  removeCallbacks(timeout);removeCallbacks(ticker);
  wakeTarget.setVisibility(VISIBLE);setVisibility(GONE);
  boolean focused=wakeTarget.requestFocus();
  if(!focused)wakeTarget.post(()->{if(!disposed&&getVisibility()!=VISIBLE)wakeTarget.requestFocus();});
  android.util.Log.i("SharkPlayerUI","overlay hidden; wake focus="+focused);
 }
 boolean handleKey(KeyEvent e){if(disposed)return false;int k=e.getKeyCode();boolean nav=k==19||k==20||k==21||k==22||k==23||k==66||k==82||k==KeyEvent.KEYCODE_BUTTON_A||k==KeyEvent.KEYCODE_NUMPAD_ENTER;if(e.getAction()==KeyEvent.ACTION_UP&&consumeUp){consumeUp=false;return true;}if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE){if(k==KeyEvent.KEYCODE_MEDIA_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&p.getPlayWhenReady())p.pause();else p.play();show();consumeUp=true;return true;}if(k==KeyEvent.KEYCODE_MEDIA_REWIND||k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){if(k==KeyEvent.KEYCODE_MEDIA_REWIND&&p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD&&p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();show();consumeUp=true;return true;}if(nav&&getVisibility()!=VISIBLE){show();consumeUp=true;return true;}if(nav)arm();if(k==82){a.choosePlayerRange(item);consumeUp=true;return true;}return false;}
 void dispose(){if(disposed)return;disposed=true;removeCallbacks(timeout);removeCallbacks(ticker);p.removeListener(listener);wakeTarget.setOnKeyListener(null);wakeTarget.setOnClickListener(null);host.removeView(wakeTarget);host.removeView(this);}
 final class Control extends View {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final int type;
  Control(int t){super(PremiumPlayerOverlay.this.a);type=t;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth(),getHeight())/64f;c.scale(scale*1.12f,scale*1.12f);pen.setColor(WHITE);pen.setStyle(Paint.Style.FILL);pen.setStrokeCap(Paint.Cap.ROUND);pen.setShader(new LinearGradient(0,-22,0,22,new int[]{0xfffcffff,0xffe5f0ff,0xff7498be,0xffc9e1fc,0xfff7fbff},new float[]{0,.40f,.51f,.78f,1},Shader.TileMode.CLAMP));
   if(type==2){if(p.getPlayWhenReady()){c.drawRect(-12,-15,-3,15,pen);c.drawRect(3,-15,12,15,pen);}else triangle(c,-10,-15,15,0,-10,15);}
   else if(type==0||type==4){if(type==0){c.drawRect(-15,-12,-11,12,pen);triangle(c,12,-13,-10,0,12,13);}else{c.drawRect(11,-12,15,12,pen);triangle(c,-12,-13,10,0,-12,13);}}
   else{boolean back=type==1;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);c.drawArc(-15,-15,15,15,back?-50:-130,300,false,pen);pen.setStyle(Paint.Style.FILL);if(back)triangle(c,-13,-17,-3,-22,-3,-9);else triangle(c,13,-17,3,-22,3,-9);pen.setTextSize(12);pen.setTypeface(a.uiMedium);pen.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf((back?p.getSeekBackIncrement():p.getSeekForwardIncrement())/1000),0,4,pen);}
   pen.setShader(null);c.restore();
  }
  void triangle(Canvas c,float x,float y,float X,float Y,float z,float Z){Path path=new Path();path.moveTo(x,y);path.lineTo(X,Y);path.lineTo(z,Z);path.close();c.drawPath(path,pen);}
 }
 final class Ring extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean circle;boolean focused;final Runnable tick=()->invalidateSelf();
  Ring(boolean round){circle=round;}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){boolean next=false;for(int s:states)if(s==android.R.attr.state_focused)next=true;if(next==focused)return false;focused=next;if(!next)unscheduleSelf(tick);invalidateSelf();return true;}
  @Override public void draw(Canvas c){
   if(!focused||!isVisible())return;
   RectF r=new RectF(getBounds());float side=Math.min(r.width(),r.height());
   float scale=Math.max(d(4),side*.105f)/25f;
   r.inset(scale*19,scale*19);
   FlamePulse.draw(c,pen,r,0,scale,FlamePulse.phase());
   if(FlamePulse.animates()){unscheduleSelf(tick);scheduleSelf(tick,SystemClock.uptimeMillis()+40);}
  }
  @Override public boolean setVisible(boolean v,boolean restart){if(!v)unscheduleSelf(tick);return super.setVisible(v,restart);}
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 final class OptionGlyph extends Drawable {
  final String name;final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
  OptionGlyph(String n){name=n;}
  @Override public void draw(Canvas c){Rect b=getBounds();if(b.isEmpty())return;c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);ink.setColor(0xffdcecff);ink.setStrokeWidth(1.65f);ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);Path q=new Path();
   switch(name){
    case "Audio":q.moveTo(3,9);q.lineTo(7,9);q.lineTo(12,5);q.lineTo(12,19);q.lineTo(7,15);q.lineTo(3,15);q.close();c.drawPath(q,ink);c.drawArc(9,7,20,17,-55,110,false,ink);c.drawArc(8,3,25,21,-52,104,false,ink);break;
    case "Subtitles":c.drawRoundRect(2,5,22,19,2,2,ink);c.drawLine(5,11,10,11,ink);c.drawLine(13,11,19,11,ink);c.drawLine(5,15,19,15,ink);break;
    case "Aspect":c.drawRect(3,5,21,19,ink);c.drawLine(6,12,6,8,ink);c.drawLine(6,8,10,8,ink);c.drawLine(18,12,18,16,ink);c.drawLine(18,16,14,16,ink);break;
    case "Picture":c.drawRoundRect(3,4,21,20,2,2,ink);c.drawCircle(16,8,1.5f,ink);q.moveTo(4,17);q.lineTo(10,10);q.lineTo(15,16);q.lineTo(18,13);q.lineTo(21,17);c.drawPath(q,ink);break;
    default:c.drawCircle(12,12,9,ink);c.drawLine(12,11,12,17,ink);c.drawCircle(12,7,0.8f,ink);break;
   }c.restore();
  }
  @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 final class Dock extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Shader glass,topGlow,bottomGlow,leftGlow,rightGlow,reflection;
  @Override protected void onBoundsChange(Rect b){
   super.onBoundsChange(b);
   float w=b.width(),h=b.height();if(w<=0||h<=0)return;
   float edge=Math.max(d(6),h*.095f), side=Math.max(d(9),w*.018f);
   // Dark glass body with continuous optical falloff from the illuminated edges.
   glass=new LinearGradient(0,0,0,h,new int[]{0xff123a70,0xff091f47,0xff061731,0xff081b3b,0xff0d2b59},new float[]{0,.20f,.45f,.78f,1},Shader.TileMode.CLAMP);
   topGlow=new LinearGradient(0,0,0,edge,new int[]{0xc04adfff,0x7039b6ff,0x202c8de4,0x002c8de4},new float[]{0,.14f,.46f,1},Shader.TileMode.CLAMP);
   bottomGlow=new LinearGradient(0,h-edge,0,h,new int[]{0x001d7ddb,0x203399ff,0x7053bfff,0xb05bdfff},new float[]{0,.54f,.86f,1},Shader.TileMode.CLAMP);
   leftGlow=new LinearGradient(0,0,side,0,new int[]{0xb24cd9ff,0x64379eff,0x00379eff},new float[]{0,.14f,1},Shader.TileMode.CLAMP);
   rightGlow=new LinearGradient(w-side,0,w,0,new int[]{0x00379eff,0x64379eff,0xb24cd9ff},new float[]{0,.86f,1},Shader.TileMode.CLAMP);
   reflection=new RadialGradient(w*.30f,0,Math.max(1,w*.28f),new int[]{0x55328fff,0x16328fff,0x00328fff},new float[]{0,.25f,1},Shader.TileMode.CLAMP);
  }
  @Override public void draw(Canvas c){
   Rect b=getBounds();float w=b.width(),h=b.height();if(w<=0||h<=0||glass==null)return;
   c.save();c.translate(b.left,b.top);
   pen.setStyle(Paint.Style.FILL);pen.setAlpha(255);
   // All edges are square; the illumination is blended rather than a hard outline.
   pen.setShader(glass);c.drawRect(0,0,w,h,pen);
   pen.setShader(reflection);c.drawRect(0,0,w,h,pen);
   pen.setShader(topGlow);c.drawRect(0,0,w,h,pen);
   pen.setShader(bottomGlow);c.drawRect(0,0,w,h,pen);
   pen.setShader(leftGlow);c.drawRect(0,0,w,h,pen);
   pen.setShader(rightGlow);c.drawRect(0,0,w,h,pen);
   pen.setShader(null);pen.setAlpha(255);c.restore();
  }
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
 }
}
