package com.example.xtreamtv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Design 3: original approved art in independently laid-out, accessible interactive views. */
final class PremiumMockupHome {
 private static Bitmap atlas;
 private static Bitmap crop(int x,int y,int w,int h){return Bitmap.createBitmap(atlas,x,y,w,h);}
 private static ImageView image(MainActivity a,Bitmap bitmap){ImageView v=new ImageView(a);v.setImageBitmap(bitmap);v.setScaleType(ImageView.ScaleType.FIT_XY);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
 static void show(MainActivity a){
  if(atlas==null){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;atlas=BitmapFactory.decodeResource(a.getResources(),R.drawable.premium_mockup3,opts);}
  if(atlas==null||atlas.getWidth()!=1400||atlas.getHeight()!=1000)throw new IllegalStateException("Design 3 artwork atlas missing or incorrect");
  a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));
  root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff030919,0xff061b43,0xff050b19}));
  root.place(image(a,crop(0,0,1388,612)),260,0,1388,612);
  ImageView logo=image(a,crop(0,620,537,98));logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);logo.setContentDescription("SHARK SKY");root.place(logo,28,13,537,98);
  TextClock clock=new TextClock(a);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);clock.setTextColor(Color.WHITE);clock.setTextSize(22);root.place(clock,1260,15,300,54);
  TextView version=a.text("PREMIUM  •  "+BuildConfig.VERSION_NAME,12);version.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);root.place(version,1250,75,310,36);
  String[] nav={"Home","Live TV","Movies","Series","Settings"};
  String[] glyph={"⌂","▣","▤","▥","⚙"};
  View[] rail=new View[nav.length];View[] tiles=new View[4];
  for(int i=0;i<nav.length;i++){
   String dest=nav[i];
   LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(12),0,a.dp(8),0);row.setBackground(a.focus());row.setFocusable(true);row.setFocusableInTouchMode(true);row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);row.setClickable(true);row.setId(View.generateViewId());row.setContentDescription(dest);
   TextView icon=a.text(glyph[i],22);icon.setTextColor(Color.WHITE);icon.setGravity(Gravity.CENTER);row.addView(icon,new LinearLayout.LayoutParams(a.dp(30),-1));
   TextView label=a.text(dest,18);label.setTypeface(a.uiMedium);row.addView(label,new LinearLayout.LayoutParams(0,-1,1));
   if(i==0)row.setSelected(true);a.premiumHomeFocus(row);row.setOnClickListener(v->a.navigateHome(dest));
   root.place(row,20,202+i*82,265,70);rail[i]=row;
  }
  View floor=new View(a);floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff0e2343,0xff020712,0xff071024}));root.place(floor,260,829,1388,99);
  String[] dests={"Live TV","Movies","Series","Settings"};
  for(int i=0;i<4;i++){
   final String dest=dests[i];
   FrameLayout tile=new FrameLayout(a);tile.setId(View.generateViewId());tile.setContentDescription(dest);tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);tile.setClickable(true);
   Bitmap artwork=crop(i*350,750,320,220);
   root.place(new FloorReflection(a,artwork),325+i*323,833,304,88);
   tile.addView(image(a,artwork),new FrameLayout.LayoutParams(-1,-1));a.premiumHomeFocus(tile);
   tile.setOnClickListener(v->a.navigateHome(dest));root.place(tile,325+i*323,613,304,218);tiles[i]=tile;
  }
  for(int i=0;i<rail.length;i++){rail[i].setNextFocusRightId(tiles[Math.max(0,i-1)].getId());if(i>0)rail[i].setNextFocusUpId(rail[i-1].getId());if(i+1<rail.length)rail[i].setNextFocusDownId(rail[i+1].getId());}
  for(int i=0;i<tiles.length;i++){tiles[i].setNextFocusLeftId(i==0?rail[1].getId():tiles[i-1].getId());if(i+1<tiles.length)tiles[i].setNextFocusRightId(tiles[i+1].getId());tiles[i].setNextFocusUpId(rail[i+1].getId());}
  root.rail=rail;root.tiles=tiles;
  tiles[0].requestFocus();
  root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
 }
 /** Reflect only the real tile pixels, with a soft opacity fade. No layout animation. */
 private static final class FloorReflection extends View{
  final Bitmap source;final Paint pixels=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Paint fade=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF reflected=new RectF();final RectF layer=new RectF();
  FloorReflection(MainActivity a,Bitmap b){super(a);source=b;pixels.setAlpha(112);fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);layer.set(0,0,w,h);reflected.set(0,-w*source.getHeight()/(float)source.getWidth(),w,0);fade.setShader(new LinearGradient(0,0,0,Math.max(1,h),new int[]{0xbbffffff,0x55ffffff,0x00ffffff},new float[]{0,.35f,1},Shader.TileMode.CLAMP));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(getWidth()==0||getHeight()==0)return;int layerId=canvas.saveLayer(layer,null);int id=canvas.save();canvas.scale(1,-1);canvas.drawBitmap(source,null,reflected,pixels);canvas.restoreToCount(id);canvas.drawRect(layer,fade);canvas.restoreToCount(layerId);}
 }
 private static final class DesignLayout extends ViewGroup{
  final List<int[]> coords=new ArrayList<>();
  View[] rail,tiles;
  @Override public boolean dispatchKeyEvent(KeyEvent event){
   int key=event.getKeyCode();if(rail==null||tiles==null||(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN))return super.dispatchKeyEvent(event);
   if(event.getAction()==KeyEvent.ACTION_UP)return true;if(event.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(event);
   View focused=findFocus(),target=null;
   for(int i=0;i<tiles.length;i++)if(focused==tiles[i]){
    if(key==KeyEvent.KEYCODE_DPAD_LEFT)target=i>0?tiles[i-1]:rail[1];
    else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)target=tiles[Math.min(i+1,tiles.length-1)];
    else if(key==KeyEvent.KEYCODE_DPAD_UP)target=rail[i+1];else target=tiles[i];break;
   }
   for(int i=0;i<rail.length;i++)if(focused==rail[i]){
    if(key==KeyEvent.KEYCODE_DPAD_RIGHT)target=tiles[Math.max(0,i-1)];
    else if(key==KeyEvent.KEYCODE_DPAD_UP)target=rail[Math.max(0,i-1)];
    else if(key==KeyEvent.KEYCODE_DPAD_DOWN)target=rail[Math.min(i+1,rail.length-1)];else target=rail[i];break;
   }
   if(target==null)target=tiles[0];return target.requestFocus()||super.dispatchKeyEvent(event);
  }
  DesignLayout(MainActivity a){super(a);setClipChildren(false);setClipToPadding(false);}
  void place(View v,int x,int y,int w,int h){coords.add(new int[]{x,y,w,h});addView(v,new LayoutParams(w,h));}
  int left(int[] p,int w){return Math.round(p[0]*w/1648f);}
  int top(int[] p,int h){return Math.round(p[1]*h/928f);}
  int right(int[] p,int w){return Math.round((p[0]+p[2])*w/1648f);}
  int bottom(int[] p,int h){return Math.round((p[1]+p[3])*h/928f);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1,right(p,w)-left(p,w)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,bottom(p,h)-top(p,h)),MeasureSpec.EXACTLY));}}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=r-l,h=b-t;for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).layout(left(p,w),top(p,h),right(p,w),bottom(p,h));}}
 }
}
