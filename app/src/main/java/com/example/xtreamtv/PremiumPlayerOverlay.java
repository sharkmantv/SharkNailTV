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
 LinearLayout topBar, bottomBar, dockBar, timelineBar, transportBar, optionBar;
 LinearLayout infoPanel,titlePanel;
 TextView movieTitle,programmeDetails,liveStatus,resolutionStatus,audioStatus,infoDetails; TextClock playerClock;
 long lastInfoRefresh;
 View logoView;
 Control[] transportKeys;
 View infoButton, infoReturnFocus;
 int dockFocusMode, dockAccessibility;
 final int NAVY=0xff071331;
 int sizedW=-1,sizedH=-1;
 final Runnable timeout=()->{if(!p.getPlayWhenReady())return;if(a.hasWindowFocus())hide();else arm();};
 final Runnable ticker=new Runnable(){public void run(){if(!disposed&&isShown()&&a.player==p){refresh();postDelayed(this,500);}}};
 final Player.Listener listener=new Player.Listener(){@Override public void onEvents(Player player,Player.Events events){if(isShown())refresh();}@Override public void onPlayWhenReadyChanged(boolean ready,int reason){android.util.Log.i("SharkPlayerUI","playWhenReady="+ready);if(isShown())arm();}};
 PremiumPlayerOverlay(MainActivity activity,FrameLayout parent,PlayerView video,MainActivity.Item current){
  super(activity);a=activity;host=parent;surface=video;item=current;p=a.player;
  live=a.page.equals("Live TV");compact=a.getResources().getConfiguration().screenWidthDp<650;
  setFocusable(false);setClickable(false);setClipChildren(false);setClipToPadding(false);
  setBackground(new SkyAtmosphere());
  LinearLayout top=a.row();topBar=top;top.setGravity(Gravity.CENTER_VERTICAL);
  logoView=PremiumVisuals.brand(a);logoView.setContentDescription("Sky");logoView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  top.addView(logoView,new LinearLayout.LayoutParams(0,-1,.22f));
  top.addView(new View(a),new LinearLayout.LayoutParams(0,-1,.39f));
  liveStatus=status(live?"LIVE":"",true);resolutionStatus=status("",false);audioStatus=status("",false);
  top.addView(liveStatus,new LinearLayout.LayoutParams(0,-1,.10f));
  top.addView(resolutionStatus,new LinearLayout.LayoutParams(0,-1,.08f));
  top.addView(audioStatus,new LinearLayout.LayoutParams(0,-1,.11f));
  playerClock=new TextClock(a);playerClock.setFormat24Hour("HH:mm");playerClock.setFormat12Hour("h:mm a");playerClock.setTypeface(a.uiMedium);playerClock.setTextColor(WHITE);playerClock.setIncludeFontPadding(false);playerClock.setGravity(Gravity.CENTER);top.addView(playerClock,new LinearLayout.LayoutParams(0,-1,.10f));
  addView(top,new FrameLayout.LayoutParams(-1,1,Gravity.TOP));
  titlePanel=a.col();titlePanel.setGravity(Gravity.TOP);
  movieTitle=a.text(item.name,21);movieTitle.setTextColor(WHITE);movieTitle.setTypeface(a.uiMedium);movieTitle.setIncludeFontPadding(false);movieTitle.setMaxLines(2);movieTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
  titlePanel.addView(movieTitle,new LinearLayout.LayoutParams(-1,-2));
  metadata=a.text("",14);metadata.setTextColor(0xffdbe8ff);metadata.setIncludeFontPadding(false);metadata.setMaxLines(2);metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);titlePanel.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
  programmeDetails=a.text("",14);programmeDetails.setTextColor(WHITE);programmeDetails.setIncludeFontPadding(false);programmeDetails.setMaxLines(3);programmeDetails.setEllipsize(android.text.TextUtils.TruncateAt.END);titlePanel.addView(programmeDetails,new LinearLayout.LayoutParams(-1,-2));
  addView(titlePanel,new FrameLayout.LayoutParams(1,1,Gravity.TOP|Gravity.LEFT));
  LinearLayout bottom=a.col();bottomBar=bottom;bottom.setClipChildren(false);bottom.setClipToPadding(false);addView(bottom,new FrameLayout.LayoutParams(-1,1,Gravity.BOTTOM));
  LinearLayout dock=a.col();dockBar=dock;dock.setClipChildren(false);dock.setClipToPadding(false);dock.setBackgroundColor(Color.TRANSPARENT);bottom.addView(dock,new LinearLayout.LayoutParams(-1,-1));
  LinearLayout timeline=a.row();timelineBar=timeline;timeline.setGravity(Gravity.CENTER_VERTICAL);
  elapsed=a.text("00:00",compact?12:15);elapsed.setGravity(Gravity.CENTER);elapsed.setTextColor(WHITE);timeline.addView(elapsed,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress=new SeekBar(a);progress.setId(View.generateViewId());progress.setMax(10000);progress.setPadding(d(10),0,d(10),0);glassTimeline(progress);progress.setContentDescription("Playback progress");progress.setBackground(new Ring(false));timeline.addView(progress,new LinearLayout.LayoutParams(0,-1,1));
  total=a.text("00:00",compact?12:15);total.setGravity(Gravity.CENTER);total.setTextColor(WHITE);timeline.addView(total,new LinearLayout.LayoutParams(d(compact?60:76),-1));
  progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){scrubbing=true;removeCallbacks(timeout);}public void onStopTrackingTouch(SeekBar s){seek(s.getProgress());scrubbing=false;arm();}public void onProgressChanged(SeekBar s,int v,boolean user){if(user){if(!scrubbing)seek(v);else if(p.getDuration()>0)elapsed.setText(time((long)(p.getDuration()*(v/10000d))));if(!scrubbing)arm();}}});
  dock.addView(timeline,new LinearLayout.LayoutParams(-1,0,.17f));
  LinearLayout transport=a.row();transportBar=transport;transport.setGravity(Gravity.CENTER);transport.setClipChildren(false);transport.setClipToPadding(false);
  LinearLayout.LayoutParams transportLp=new LinearLayout.LayoutParams(-1,0,.50f);dock.addView(transport,transportLp);
  Control prev=button(transport,0,"Previous",()->adjacent(-1));Control rew=button(transport,1,"Rewind",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();});
  play=button(transport,2,"Play or pause",()->{if(p.getPlayWhenReady())p.pause();else p.play();refresh();});
  Control ff=button(transport,3,"Forward",()->{if(p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();});Control next=button(transport,4,"Next",()->adjacent(1));
  transportKeys=new Control[]{prev,rew,play,ff,next};View[] keys=transportKeys;link(keys);
  LinearLayout opts=a.row();optionBar=opts;opts.setGravity(Gravity.CENTER);opts.setClipChildren(false);opts.setClipToPadding(false);opts.setBackground(skyPanel(3));dock.addView(opts,new LinearLayout.LayoutParams(-1,0,.33f));
  java.util.ArrayList<View> actionList=new java.util.ArrayList<>();
  infoButton=option(opts,"Info",this::showInfo);actionList.add(infoButton);
  if(live)actionList.add(option(opts,"EPG",()->a.openChannelGuide(item)));
  actionList.add(option(opts,"Subtitles",()->a.chooseTrack(C.TRACK_TYPE_TEXT)));
  actionList.add(option(opts,"Audio",()->new AlertDialog.Builder(a).setTitle("Audio").setItems(new String[]{"Audio tracks","Volume"},(dialog,i)->{if(i==0)a.chooseTrack(C.TRACK_TYPE_AUDIO);else volume();}).setNegativeButton("Cancel",null).show()));
  actionList.add(option(opts,"Aspect",a::aspectDialog));
  actionList.add(option(opts,"Picture",()->a.choosePlayerRange(item)));
  View[] actions=actionList.toArray(new View[0]);link(actions);
  for(int i=0;i<keys.length;i++){keys[i].setNextFocusUpId(progress.getId());keys[i].setNextFocusDownId(actions[Math.round(i*(actions.length-1)/4f)].getId());}
  for(int i=0;i<actions.length;i++){actions[i].setNextFocusUpId(keys[Math.round(i*4f/(actions.length-1))].getId());actions[i].setNextFocusDownId(actions[i].getId());}
  progress.setNextFocusUpId(progress.getId());progress.setNextFocusDownId(play.getId());
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
 // Native, full-resolution atmosphere inspired by the exact approved layout.
 final class SkyAtmosphere extends Drawable {
  final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path upper=new Path(),lower=new Path(),right=new Path();
  Shader topShade,leftShade,bottomShade;
  @Override protected void onBoundsChange(Rect b){
   float w=b.width(),h=b.height();if(w<=0||h<=0)return;
   topShade=new LinearGradient(0,0,0,h*.28f,new int[]{0xda07142f,0x730b2041,0x00071c45},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
   leftShade=new LinearGradient(0,0,w*.60f,0,new int[]{0xc9061535,0x740b2448,0x00071e4d},new float[]{0,.44f,1},Shader.TileMode.CLAMP);
   bottomShade=new LinearGradient(0,h*.49f,0,h,new int[]{0x00051843,0x79071431,0xe5071838,0xf0071a42},new float[]{0,.31f,.76f,1},Shader.TileMode.CLAMP);
   upper.reset();upper.moveTo(-w*.02f,h*.195f);upper.cubicTo(w*.13f,h*.11f,w*.24f,h*.17f,w*.53f,-h*.02f);
   lower.reset();lower.moveTo(-w*.03f,h*.866f);lower.cubicTo(w*.13f,h*1.025f,w*.25f,h*.99f,w*.39f,h*1.07f);
   right.reset();right.moveTo(w*.36f,h*1.02f);right.cubicTo(w*.62f,h*.91f,w*.81f,h*.99f,w*1.04f,h*.77f);
  }
  void sweep(Canvas c,Path path,float h){
   ink.setShader(null);ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);
   for(int i=8;i>=1;i--){float q=i/8f;ink.setStrokeWidth(h*.016f*q);ink.setColor(Color.argb(Math.round(3+8*(1-q)),27,115,234));c.drawPath(path,ink);}
   ink.setStrokeWidth(h*.0017f);ink.setColor(0xb7399fea);c.drawPath(path,ink);
   ink.setStrokeWidth(h*.0007f);ink.setColor(0x996ed3ff);c.drawPath(path,ink);
   ink.setStyle(Paint.Style.FILL);
  }
  @Override public void draw(Canvas c){
   Rect b=getBounds();float w=b.width(),h=b.height();if(topShade==null||w<=0||h<=0)return;
   c.save();c.translate(b.left,b.top);ink.setStyle(Paint.Style.FILL);ink.setAlpha(255);
   ink.setShader(topShade);c.drawRect(0,0,w,h*.28f,ink);
   ink.setShader(leftShade);c.drawRect(0,0,w*.60f,h*.61f,ink);
   ink.setShader(bottomShade);c.drawRect(0,h*.49f,w,h,ink);
   sweep(c,upper,h);sweep(c,lower,h);sweep(c,right,h);ink.setShader(null);c.restore();
  }
  @Override public void setAlpha(int n){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 Drawable skyPanel(int style){
  int[] colors=style==0?new int[]{0xfa142b50,0xfd08162e,0xfd071226}:style==3?new int[]{0xf02a4970,0xf2102548,0xfc08182f}:new int[]{0xff294d77,0xff0b203f};
  GradientDrawable panel=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors);
  panel.setCornerRadius(d(4));panel.setStroke(Math.max(1,d(1)),0xb37faace);return panel;
 }
 TextView status(String label,boolean accent){
  TextView v=a.text(label,15);v.setGravity(Gravity.CENTER);v.setTextColor(WHITE);v.setTypeface(a.uiMedium);v.setIncludeFontPadding(false);v.setSingleLine(true);
  GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,accent?new int[]{0xf02d699e,0xfa143d68}:new int[]{0xe32b4769,0xf00c213e});
  shape.setCornerRadius(d(3));shape.setStroke(Math.max(1,d(1)),accent?0xff7fcaf4:0x999cc3e2);v.setBackground(shape);return v;
 }
 void px(TextView v,float size){v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);}
 Drawable artwork(int id){BitmapDrawable b=(BitmapDrawable)a.getDrawable(id).mutate();b.setGravity(Gravity.FILL);b.setFilterBitmap(true);return new Drawable(){
   @Override public void draw(Canvas c){b.setBounds(getBounds());b.draw(c);}
   @Override public void setAlpha(int alpha){b.setAlpha(alpha);}
   @Override public void setColorFilter(ColorFilter f){b.setColorFilter(f);}
   @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 };}
 @Override protected void onMeasure(int ws,int hs){
  int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);
  if(w!=sizedW||h!=sizedH){sizedW=w;sizedH=h;
   FrameLayout.LayoutParams top=(FrameLayout.LayoutParams)topBar.getLayoutParams();top.width=Math.round(w*.94f);top.height=Math.round(h*.11f);top.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;top.topMargin=Math.round(h*.016f);
   for(TextView v:new TextView[]{liveStatus,resolutionStatus,audioStatus}){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.height=Math.round(h*.052f);lp.setMargins(Math.round(w*.006f),0,Math.round(w*.006f),0);px(v,h*.027f);}
   px(playerClock,h*.034f);
   FrameLayout.LayoutParams title=(FrameLayout.LayoutParams)titlePanel.getLayoutParams();title.width=Math.round(w*.52f);title.height=Math.round(h*.36f);title.topMargin=Math.round(h*.228f);title.leftMargin=Math.round(w*.033f);
   px(movieTitle,h*.044f);px(metadata,h*.029f);px(programmeDetails,h*.029f);metadata.setPadding(0,Math.round(h*.014f),0,Math.round(h*.012f));
   FrameLayout.LayoutParams bottom=(FrameLayout.LayoutParams)bottomBar.getLayoutParams();bottom.width=Math.round(w*.94f);bottom.height=Math.round(h*.298f);bottom.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;bottom.bottomMargin=Math.round(h*.083f);
   dockBar.setPadding(0,0,0,0);
   transportBar.setPadding(Math.round(w*.195f),0,Math.round(w*.195f),0);
   for(Control key:transportKeys){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)key.getLayoutParams();lp.weight=key.type==2?1.30f:1f;lp.setMargins(Math.round(w*.008f),0,Math.round(w*.008f),0);}
   for(int i=0;i<optionBar.getChildCount();i++){View v=optionBar.getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.setMargins(0,0,0,0);}
   for(TextView v:new TextView[]{elapsed,total}){px(v,h*.028f);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.width=Math.round(w*.083f);}
  }
  super.onMeasure(ws,hs);
 }
 void logVideoBounds(){post(()->{if(!disposed&&isShown())android.util.Log.i("SharkPlayerUI","video surface "+surface.getWidth()+"x"+surface.getHeight()+" overlay "+getWidth()+"x"+getHeight());});}
 void showInfo(){
  if(disposed||infoPanel!=null||getWidth()<=0||getHeight()<=0)return;
  removeCallbacks(timeout);
  infoReturnFocus=infoButton;
  LinearLayout panel=a.col();infoPanel=panel;
  panel.setPadding(d(18),d(10),d(18),d(10));
  panel.setBackground(skyPanel(0));
  panel.setClickable(true);
  panel.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
  TextView title=a.text("Playback Information",20);title.setTextColor(WHITE);title.setTypeface(a.uiMedium);
  panel.addView(title,new LinearLayout.LayoutParams(-1,d(38)));
  ScrollView scroll=new ScrollView(a);
  scroll.setId(View.generateViewId());scroll.setFillViewport(false);
  scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);
  scroll.setContentDescription("Full playback diagnostics");
  scroll.setForeground(new Ring(false));
  View rule=new View(a);rule.setBackgroundColor(0x887bafd6);panel.addView(rule,new LinearLayout.LayoutParams(-1,d(1)));
  TextView details=new TextView(a);infoDetails=details;lastInfoRefresh=SystemClock.uptimeMillis();
  details.setTextColor(WHITE);details.setTypeface(a.uiRegular);details.setTextSize(compact?13:15);
  details.setSingleLine(false);details.setMaxLines(Integer.MAX_VALUE);details.setEllipsize(null);
  details.setText(item.name+"\n"+a.currentPlaybackDiagnostics());details.setLineSpacing(d(3),1f);details.setPadding(0,d(7),0,d(5));
  scroll.addView(details,new ScrollView.LayoutParams(-1,-2));
  panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView close=a.text("Close",16);
  close.setId(View.generateViewId());close.setContentDescription("Close");
  close.setTextColor(WHITE);close.setGravity(Gravity.CENTER);close.setBackground(skyPanel(2));
  close.setForeground(new Ring(false));close.setFocusable(true);close.setFocusableInTouchMode(true);close.setClickable(true);
  panel.addView(close,new LinearLayout.LayoutParams(-1,d(40)));
  close.setNextFocusUpId(scroll.getId());close.setNextFocusDownId(close.getId());
  close.setNextFocusLeftId(close.getId());close.setNextFocusRightId(close.getId());
  scroll.setNextFocusUpId(scroll.getId());scroll.setNextFocusDownId(close.getId());
  scroll.setNextFocusLeftId(scroll.getId());scroll.setNextFocusRightId(scroll.getId());
  focusBeforeClick(close);close.setOnClickListener(v->dismissInfo(true));
  dockFocusMode=bottomBar.getDescendantFocusability();dockAccessibility=bottomBar.getImportantForAccessibility();
  bottomBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
  bottomBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.round(getWidth()*(compact?.67f:.38f)),Math.round(getHeight()*.62f),Gravity.TOP|Gravity.RIGHT);
  lp.topMargin=Math.round(getHeight()*.12f);lp.rightMargin=Math.round(getWidth()*.016f);
  addView(panel,lp);
  close.requestFocus();
  panel.post(()->{if(infoPanel==panel){close.requestFocus();android.util.Log.i("SharkPlayerUI","Info dialog shown");}});
 }
 void dismissInfo(boolean restoreFocus){
  if(infoPanel==null)return;
  removeView(infoPanel);infoPanel=null;infoDetails=null;
  bottomBar.setDescendantFocusability(dockFocusMode);bottomBar.setImportantForAccessibility(dockAccessibility);
  View restore=infoReturnFocus;infoReturnFocus=null;
  if(restoreFocus&&restore!=null){restore.requestFocus();arm();}
  android.util.Log.i("SharkPlayerUI","Info dismissed");
 }
 int d(int n){return a.dp(n);}
 void tint(SeekBar s){s.setProgressTintList(ColorStateList.valueOf(GOLD));s.setThumbTintList(ColorStateList.valueOf(GOLD));s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff677a9a));}
 GradientDrawable track(int[] colors,int height){
  GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,colors);
  shape.setCornerRadius(d(5));shape.setSize(d(10),d(height));return shape;
 }
 void glassTimeline(SeekBar bar){
  GradientDrawable base=track(new int[]{0xff203d67,0xff406895,0xff263f66},5);
  ClipDrawable buffered=new ClipDrawable(track(new int[]{0xff567ba0,0xff87a9ca},5),Gravity.LEFT,ClipDrawable.HORIZONTAL);
  ClipDrawable played=new ClipDrawable(track(new int[]{0xff1478d9,0xff00b9f5,0xff9ceaff},5),Gravity.LEFT,ClipDrawable.HORIZONTAL);
  LayerDrawable layers=new LayerDrawable(new Drawable[]{base,buffered,played});
  int[] ids={android.R.id.background,android.R.id.secondaryProgress,android.R.id.progress};
  for(int i=0;i<3;i++){layers.setId(i,ids[i]);layers.setLayerHeight(i,d(5));layers.setLayerGravity(i,Gravity.CENTER_VERTICAL|Gravity.FILL_HORIZONTAL);}
  bar.setProgressTintList(null);bar.setSecondaryProgressTintList(null);bar.setProgressBackgroundTintList(null);bar.setThumbTintList(null);
  bar.setProgressDrawable(layers);
  GradientDrawable thumb=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffffffff,0xffe2f7ff,0xff9ed8ff});
  thumb.setShape(GradientDrawable.OVAL);thumb.setSize(d(15),d(15));bar.setThumb(thumb);bar.setThumbOffset(d(7));
 }
 // Android's focusable-in-touch-mode controls can consume the first tap just
 // taking focus on ACTION_UP. Acquire focus on DOWN so that the normal View
 // click path fires exactly once on UP, even after closing the Info panel.
 void focusBeforeClick(View v){
  v.setOnTouchListener((target,event)->{
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&target.isEnabled())target.requestFocus();
   return false;
  });
 }
 void link(View[] views){for(int i=0;i<views.length;i++){views[i].setNextFocusLeftId(views[Math.max(i-1,0)].getId());views[i].setNextFocusRightId(views[Math.min(i+1,views.length-1)].getId());}}
 Control button(LinearLayout row,int type,String label,Runnable action){Control v=new Control(type);v.setId(View.generateViewId());v.setContentDescription(label);v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new SkyKey(type==2?1:0));focusBeforeClick(v);v.setOnClickListener(x->{android.util.Log.i("SharkPlayerUI","control clicked="+v.getContentDescription());action.run();arm();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(d(2),0,d(2),0);row.addView(v,lp);return v;}
 View option(LinearLayout row,String label,Runnable action){
  View v=new OptionControl(label);v.setId(View.generateViewId());v.setContentDescription(label);
  v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setBackground(new SkyKey(2));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(d(2),0,d(2),0);row.addView(v,lp);focusBeforeClick(v);v.setOnClickListener(x->{android.util.Log.i("SharkPlayerUI","control clicked="+label);arm();action.run();});return v;
 }
 final class OptionControl extends View {
  final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);final String label;final OptionGlyph glyph;
  OptionControl(String name){super(PremiumPlayerOverlay.this.a);label=name;glyph=new OptionGlyph(name);ink.setTypeface(a.uiMedium);ink.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,compact?12:16,a.getResources().getDisplayMetrics()));ink.setColor(WHITE);}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);float h=getHeight(),w=getWidth(),size=Math.min(h*.43f,w*.19f);
   ink.setTextSize(Math.min(h*.32f,w*.102f));ink.setTextAlign(Paint.Align.LEFT);
   float labelWidth=ink.measureText(label),group=size+h*.14f+labelWidth,x=(w-group)/2f,cy=h*.5f;
   glyph.setBounds(Math.round(x),Math.round(cy-size/2),Math.round(x+size),Math.round(cy+size/2));glyph.draw(c);
   ink.setColor(WHITE);Paint.FontMetrics fm=ink.getFontMetrics();c.drawText(label,x+size+h*.14f,cy-(fm.ascent+fm.descent)/2,ink);
   if(getParent() instanceof ViewGroup&&((ViewGroup)getParent()).indexOfChild(this)>0){ink.setColor(0x80619bd3);c.drawRect(0,h*.18f,Math.max(1,h*.012f),h*.82f,ink);}
  }
 }
 void seek(int value){if(p.isCurrentMediaItemSeekable()&&p.getDuration()>0)p.seekTo((long)(p.getDuration()*(value/10000d)));}
 void adjacent(int delta){int index=-1;for(int i=0;i<a.items.size();i++)if(a.items.get(i).id.equals(item.id)){index=i;break;}int n=index+delta;if(index>=0&&n>=0&&n<a.items.size()){a.itemPos=n;MainActivity.Item target=a.items.get(n);if(live)a.selected=target;a.playFull(target);}}
 void volume(){AudioManager audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);if(audio==null)return;LinearLayout panel=a.col();panel.setPadding(d(24),d(16),d(24),d(16));SeekBar bar=new SeekBar(a);bar.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));bar.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));tint(bar);bar.setContentDescription("Volume");bar.setEnabled(!audio.isVolumeFixed());panel.addView(bar,new LinearLayout.LayoutParams(-1,d(44)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user&&!audio.isVolumeFixed())try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);}catch(SecurityException ignored){}}});new AlertDialog.Builder(a).setTitle(audio.isVolumeFixed()?"Use TV volume control":"Volume").setView(panel).setPositiveButton("Done",null).show();}
 String time(long millis){long s=Math.max(0,millis/1000);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60);}
 void refresh(){if(disposed||a.player!=p)return;Format f=p.getVideoFormat(),audio=p.getAudioFormat();resolutionStatus.setText(f!=null&&f.height>0?(f.height>=2160?"4K UHD":f.height>=1080?"1080p":f.height+"p"):"");audioStatus.setText(audio!=null&&audio.channelCount>0?(audio.channelCount==6?"5.1 audio":audio.channelCount==2?"Stereo":audio.channelCount+" ch audio"):"");liveStatus.setVisibility(live?View.VISIBLE:View.INVISIBLE);resolutionStatus.setVisibility(resolutionStatus.length()>0?View.VISIBLE:View.INVISIBLE);audioStatus.setVisibility(audioStatus.length()>0?View.VISIBLE:View.INVISIBLE);MainActivity.Programme programme=live?MainActivity.currentProgramme(a.guideCache.get(a.guideKey(item,false)),System.currentTimeMillis()):null;metadata.setText(programme!=null?programme.title:(item.year==null?"":item.year));programmeDetails.setText(programme!=null&&programme.description!=null?programme.description:"");long duration=p.getDuration();boolean canSeek=p.isCurrentMediaItemSeekable()&&duration>0;progress.setEnabled(canSeek);progress.setFocusable(canSeek);if(!scrubbing){elapsed.setText(live&&!canSeek?"LIVE":time(p.getCurrentPosition()));total.setText(duration>0?time(duration):"—");progress.setProgress(canSeek?(int)Math.min(10000,Math.max(0,10000d*p.getCurrentPosition()/duration)):0);progress.setSecondaryProgress(canSeek?(int)Math.min(10000,10000d*p.getBufferedPosition()/duration):0);}String description=p.getPlayWhenReady()?"Pause":"Play";if(!description.contentEquals(play.getContentDescription())){play.setContentDescription(description);play.invalidate();}if(infoDetails!=null&&SystemClock.uptimeMillis()-lastInfoRefresh>=1000){lastInfoRefresh=SystemClock.uptimeMillis();String details=item.name+"\n"+a.currentPlaybackDiagnostics();if(!details.contentEquals(infoDetails.getText()))infoDetails.setText(details);}}
 void arm(){removeCallbacks(timeout);if(infoPanel==null&&isShown()&&!scrubbing&&!disposed&&p.getPlayWhenReady())postDelayed(timeout,6000);}
 void show(){
  if(disposed||a.player!=p)return;
  boolean opening=getVisibility()!=VISIBLE||wakeTarget.getVisibility()==VISIBLE;
  setVisibility(VISIBLE);wakeTarget.setVisibility(GONE);refresh();
  if(opening||!hasFocus()){
   play.requestFocus();
   post(()->{if(!disposed&&getVisibility()==VISIBLE&&!hasFocus())play.requestFocus();});
  }
  android.util.Log.i("SharkPlayerUI","overlay shown");logVideoBounds();
  removeCallbacks(ticker);post(ticker);arm();
 }
 void hide(){
  if(disposed)return;
  dismissInfo(false);
  removeCallbacks(timeout);removeCallbacks(ticker);
  wakeTarget.setVisibility(VISIBLE);setVisibility(GONE);
  boolean focused=wakeTarget.requestFocus();
  if(!focused)wakeTarget.post(()->{if(!disposed&&getVisibility()!=VISIBLE)wakeTarget.requestFocus();});
  android.util.Log.i("SharkPlayerUI","overlay hidden; wake focus="+focused);
 }
 boolean handleKey(KeyEvent e){if(disposed)return false;int k=e.getKeyCode();boolean nav=k==19||k==20||k==21||k==22||k==23||k==66||k==82||k==KeyEvent.KEYCODE_BUTTON_A||k==KeyEvent.KEYCODE_NUMPAD_ENTER;if(e.getAction()==KeyEvent.ACTION_UP&&consumeUp){consumeUp=false;return true;}if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(infoPanel!=null){if(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_ESCAPE){dismissInfo(true);consumeUp=true;return true;}return false;}if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE){if(k==KeyEvent.KEYCODE_MEDIA_PAUSE||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&p.getPlayWhenReady())p.pause();else p.play();show();consumeUp=true;return true;}if(k==KeyEvent.KEYCODE_MEDIA_REWIND||k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){if(k==KeyEvent.KEYCODE_MEDIA_REWIND&&p.isCommandAvailable(Player.COMMAND_SEEK_BACK))p.seekBack();if(k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD&&p.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))p.seekForward();show();consumeUp=true;return true;}if(nav&&getVisibility()!=VISIBLE){show();consumeUp=true;return true;}if(nav)arm();if(k==KeyEvent.KEYCODE_DPAD_UP&&!progress.isFocusable()&&getFocusedChild()==bottomBar){View focus=findFocus();if(focus!=null&&focus.getParent()==transportBar){consumeUp=true;return true;}}if(k==82){a.choosePlayerRange(item);consumeUp=true;return true;}return false;}
 void dispose(){if(disposed)return;dismissInfo(false);disposed=true;removeCallbacks(timeout);removeCallbacks(ticker);p.removeListener(listener);wakeTarget.setOnKeyListener(null);wakeTarget.setOnClickListener(null);host.removeView(wakeTarget);host.removeView(this);}
 final class Control extends View {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final int type;
  Control(int t){super(PremiumPlayerOverlay.this.a);type=t;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f,getHeight()*.5f);float scale=Math.min(getWidth(),getHeight())/(type==2?70f:87f);c.scale(scale,scale);pen.setColor(WHITE);pen.setStyle(Paint.Style.FILL);pen.setStrokeCap(Paint.Cap.ROUND);pen.setShader(null);
   if(type==2){if(p.getPlayWhenReady()){c.drawRect(-12,-15,-3,15,pen);c.drawRect(3,-15,12,15,pen);}else triangle(c,-10,-15,15,0,-10,15);}
   else if(type==0||type==4){if(type==0){c.drawRect(-15,-12,-11,12,pen);triangle(c,12,-13,-10,0,12,13);}else{c.drawRect(11,-12,15,12,pen);triangle(c,-12,-13,10,0,-12,13);}}
   else{boolean back=type==1;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);c.drawArc(-15,-15,15,15,back?-50:-130,300,false,pen);pen.setStyle(Paint.Style.FILL);if(back)triangle(c,-13,-17,-3,-22,-3,-9);else triangle(c,13,-17,3,-22,3,-9);pen.setTextSize(12);pen.setTypeface(a.uiMedium);pen.setTextAlign(Paint.Align.CENTER);c.drawText(String.valueOf((back?p.getSeekBackIncrement():p.getSeekForwardIncrement())/1000),0,4,pen);}
   pen.setShader(null);c.restore();
  }
  void triangle(Canvas c,float x,float y,float X,float Y,float z,float Z){Path path=new Path();path.moveTo(x,y);path.lineTo(X,Y);path.lineTo(z,Z);path.close();c.drawPath(path,pen);}
 }
 // Focus-only broad, translucent halo around a glossy blue key; no hard gold stroke.
 final class SkyKey extends Drawable {
  final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
  final RectF r=new RectF(),inner=new RectF();
  final int style;
  boolean focused;
  float rad;
  Shader edge,face,focusFace,shine;
  final Runnable tick=()->invalidateSelf();
  SkyKey(int style){this.style=style;}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){boolean next=false;for(int state:states)if(state==android.R.attr.state_focused)next=true;if(next==focused)return false;focused=next;if(!next)unscheduleSelf(tick);invalidateSelf();return true;}
  @Override protected void onBoundsChange(Rect b){
   float w=b.width(),h=b.height();if(w<=0||h<=0){edge=null;return;}
   float mx=style==2?w*.024f:w*.05f,my=style==2?h*.10f:style==1?h*.075f:h*.21f;
   r.set(b.left+mx,b.top+my,b.right-mx,b.bottom-my);rad=h*.055f;
   inner.set(r);inner.inset(Math.max(1,h*.022f),Math.max(1,h*.022f));
   edge=new LinearGradient(0,r.top,0,r.bottom,new int[]{0xffb2d7ef,0xff507b9f,0xff1b3959,0xff7dacce},new float[]{0,.20f,.72f,1},Shader.TileMode.CLAMP);
   face=new LinearGradient(0,r.top,0,r.bottom,new int[]{0xff335579,0xff1a3357,0xff091b36},new float[]{0,.34f,1},Shader.TileMode.CLAMP);
   focusFace=new LinearGradient(0,r.top,0,r.bottom,new int[]{0xff3b82bd,0xff1a4e88,0xff10264d},new float[]{0,.32f,1},Shader.TileMode.CLAMP);
   shine=new LinearGradient(0,r.top,0,r.bottom,new int[]{0x44d8efff,0x00b3d8ff,0x103a8bfa},new float[]{0,.55f,1},Shader.TileMode.CLAMP);
  }
  @Override public void draw(Canvas c){
   if(edge==null||!isVisible()||(style==2&&!focused))return;
   ink.setStyle(Paint.Style.FILL);ink.setShader(edge);c.drawRoundRect(r,rad,rad,ink);
   ink.setShader(focused?focusFace:face);c.drawRoundRect(inner,rad*.70f,rad*.70f,ink);
   ink.setShader(shine);c.drawRoundRect(inner,rad*.70f,rad*.70f,ink);
   ink.setShader(null);
   if(focused){
    float phase=FlamePulse.phase(),scale=Math.max(1,r.height()*.052f)/12f;
    // Same warm focus treatment as Home; all gradient and geometry objects cached.
    FlamePulse.draw(c,ink,r,rad,scale,phase);
    if(FlamePulse.animates()){unscheduleSelf(tick);scheduleSelf(tick,SystemClock.uptimeMillis()+40);}
   }
   ink.setStyle(Paint.Style.FILL);ink.setAlpha(255);
  }
  @Override public boolean setVisible(boolean v,boolean restart){if(!v)unscheduleSelf(tick);return super.setVisible(v,restart);}
  @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
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
    case "EPG":c.drawRect(3,4,21,20,ink);c.drawLine(3,9,21,9,ink);c.drawLine(9,9,9,20,ink);c.drawLine(9,14,21,14,ink);break;
    case "Audio":q.moveTo(3,9);q.lineTo(7,9);q.lineTo(12,5);q.lineTo(12,19);q.lineTo(7,15);q.lineTo(3,15);q.close();c.drawPath(q,ink);c.drawArc(9,7,20,17,-55,110,false,ink);c.drawArc(8,3,25,21,-52,104,false,ink);break;
    case "Subtitles":c.drawRoundRect(2,5,22,19,2,2,ink);c.drawLine(5,11,10,11,ink);c.drawLine(13,11,19,11,ink);c.drawLine(5,15,19,15,ink);break;
    case "Aspect":c.drawRect(3,5,21,19,ink);c.drawLine(6,12,6,8,ink);c.drawLine(6,8,10,8,ink);c.drawLine(18,12,18,16,ink);c.drawLine(18,16,14,16,ink);break;
    case "Picture":c.drawRoundRect(3,4,21,20,2,2,ink);c.drawCircle(16,8,1.5f,ink);q.moveTo(4,17);q.lineTo(10,10);q.lineTo(15,16);q.lineTo(18,13);q.lineTo(21,17);c.drawPath(q,ink);break;
    default:c.drawCircle(12,12,9,ink);c.drawLine(12,11,12,17,ink);c.drawCircle(12,7,0.8f,ink);break;
   }c.restore();
  }
  @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 // Raised square transport keys. Static cached shaders; only focus pulse animates.
 final class ControlPlate extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Shader edge,face,shine;
  @Override protected void onBoundsChange(Rect b){float h=b.height();if(h<=0)return;
   edge=new LinearGradient(0,0,0,h,new int[]{0xffb3d8f2,0xff335d84,0xff0b203a,0xff527c9f},new float[]{0,.18f,.65f,1},Shader.TileMode.CLAMP);
   face=new LinearGradient(0,0,0,h,new int[]{0xff294d78,0xff142e50,0xff081a34,0xff112d4d},new float[]{0,.20f,.60f,1},Shader.TileMode.CLAMP);
   shine=new LinearGradient(0,0,0,h*.45f,new int[]{0x42e1f3ff,0x00e1f3ff},null,Shader.TileMode.CLAMP);
  }
  @Override public void draw(Canvas c){
   Rect b=getBounds();float w=b.width(),h=b.height();if(edge==null||w<=0||h<=0)return;
   c.save();c.translate(b.left,b.top);float m=d(3),e=d(1);
   pen.setShader(null);pen.setColor(0xc8030b1c);c.drawRect(m,m+e,w-m,h-m,pen);
   pen.setShader(edge);c.drawRect(m,m,w-m,h-m-e,pen);
   pen.setShader(face);c.drawRect(m+e,m+e,w-m-e,h-m-2*e,pen);
   pen.setShader(shine);c.drawRect(m+e,m+e,w-m-e,h*.45f,pen);
   pen.setShader(null);c.restore();
  }
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 final class Dock extends Drawable {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Shader frame,metal,glass,gloss,sideLight,lower,topSpecular,drop;
  final Path topFacet=new Path(),leftFacet=new Path(),rightFacet=new Path(),bottomFacet=new Path(),sheen=new Path();
  float bevel;
  @Override protected void onBoundsChange(Rect b){
   super.onBoundsChange(b);float w=b.width(),h=b.height();if(w<=0||h<=0)return;
   bevel=Math.max(d(5),Math.min(d(9),h*.047f));float k=bevel;
   frame=new LinearGradient(0,0,w,h,new int[]{0xff9ccff1,0xff163d65,0xff08172e,0xff6195bd,0xff172b46},new float[]{0,.22f,.53f,.80f,1},Shader.TileMode.CLAMP);
   metal=new LinearGradient(0,0,0,h,new int[]{0xffb4d8f2,0xff3f7daa,0xff132e53,0xff08182e,0xff47759e,0xffb4d6eb},new float[]{0,.055f,.20f,.65f,.94f,1},Shader.TileMode.CLAMP);
   glass=new LinearGradient(0,k,0,h-k,new int[]{0xff234978,0xff142e53,0xff0a1a34,0xff0c213e,0xff142e50},new float[]{0,.18f,.44f,.76f,1},Shader.TileMode.CLAMP);
   gloss=new LinearGradient(0,k,0,h*.48f,new int[]{0x42b6d9ff,0x145fa9f1,0x005fa9f1},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
   sideLight=new RadialGradient(w*.18f,0,Math.max(1,w*.54f),new int[]{0x303e95ed,0x103e95ed,0x003e95ed},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
   lower=new LinearGradient(0,h*.76f,0,h-k,new int[]{0x00000000,0x94040e22},null,Shader.TileMode.CLAMP);
   topSpecular=new LinearGradient(k,0,w-k,0,new int[]{0x0066cdff,0xaac3efff,0x276ba7d3,0x7aa9d9ff,0x0066cdff},new float[]{0,.16f,.43f,.80f,1},Shader.TileMode.CLAMP);
   drop=new LinearGradient(0,0,0,h,new int[]{0xffc1dff7,0xff2e6597,0xff0c2340,0xff061428},new float[]{0,.06f,.35f,1},Shader.TileMode.CLAMP);
   quad(topFacet,0,0,w,0,w-k,k,k,k);
   quad(leftFacet,0,0,k,k,k,h-k,0,h);
   quad(rightFacet,w,0,w,h,w-k,h-k,w-k,k);
   quad(bottomFacet,0,h,k,h-k,w-k,h-k,w,h);
   quad(sheen,k,k,w-k,k,w-k,h*.15f,k,h*.40f);
  }
  void quad(Path p,float x,float y,float a,float b,float i,float j,float u,float v){p.reset();p.moveTo(x,y);p.lineTo(a,b);p.lineTo(i,j);p.lineTo(u,v);p.close();}
  @Override public void draw(Canvas c){
   Rect b=getBounds();float w=b.width(),h=b.height();if(w<=0||h<=0||glass==null)return;
   c.save();c.translate(b.left,b.top);float k=bevel;
   pen.setStyle(Paint.Style.FILL);pen.setAlpha(255);pen.setShader(frame);c.drawRect(0,0,w,h,pen);
   pen.setShader(metal);c.drawPath(topFacet,pen);c.drawPath(bottomFacet,pen);
   pen.setShader(drop);c.drawPath(leftFacet,pen);
   pen.setShader(frame);c.drawPath(rightFacet,pen);
   pen.setShader(null);pen.setColor(0xff040e20);c.drawRect(k*.78f,k*.78f,w-k*.78f,h-k*.78f,pen);
   pen.setShader(glass);c.drawRect(k,k,w-k,h-k,pen);
   pen.setShader(sideLight);c.drawRect(k,k,w-k,h-k,pen);
   pen.setShader(gloss);c.drawPath(sheen,pen);
   pen.setShader(lower);c.drawRect(k,h*.76f,w-k,h-k,pen);
   pen.setShader(topSpecular);c.drawRect(k,k,w-k,k+d(1),pen);
   pen.setShader(null);pen.setColor(0x605d8ab5);
   c.drawRect(k+d(12),h*.755f,w-k-d(12),h*.755f+d(1),pen);
   pen.setColor(0x66101a2c);c.drawRect(k+d(12),h*.755f+d(1),w-k-d(12),h*.755f+d(2),pen);
   pen.setAlpha(255);c.restore();
  }
  @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
 }
}
