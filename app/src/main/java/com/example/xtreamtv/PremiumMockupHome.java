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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Four-screen candidate: existing hero artwork with four native, translucent Home tiles. */
final class PremiumMockupHome {
 private static Bitmap atlas;
 private static Bitmap crop(int x,int y,int w,int h){return Bitmap.createBitmap(atlas,x,y,w,h);}
 private static ImageView image(MainActivity a,Bitmap bitmap){ImageView v=new ImageView(a);v.setImageBitmap(bitmap);v.setScaleType(ImageView.ScaleType.FIT_XY);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
 // Focus on DOWN, rather than letting Android consume the first tap solely to focus on UP.
 private static void bindNavigation(View view,MainActivity a,String dest){
  view.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.requestFocus();return false;});
  view.setOnClickListener(v->a.navigateHome(dest));
 }
 static void show(MainActivity a){
  if(atlas==null){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;atlas=BitmapFactory.decodeResource(a.getResources(),R.drawable.premium_mockup3,opts);}
  if(atlas==null||atlas.getWidth()!=1400||atlas.getHeight()!=1000)throw new IllegalStateException("Design artwork atlas missing or incorrect");
  a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));
  root.setBackground(a.screenBackground());
  ImageView hero=image(a,crop(0,0,1388,612));hero.setScaleType(ImageView.ScaleType.FIT_CENTER);root.place(hero,70,0,1508,742);
  ImageView logo=image(a,crop(0,620,537,98));logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);logo.setContentDescription("SHARK SKY");root.place(logo,28,18,340,66);
  TextClock clock=new TextClock(a);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);clock.setTextColor(Color.WHITE);clock.setTextSize(22);root.place(clock,1260,15,350,54);
  String[] dests={"Live TV","Movies","Series","Settings"};
  int[] icons={R.drawable.metal48_live,R.drawable.metal48_movies,R.drawable.metal48_series,R.drawable.metal48_settings};
  View[] tiles=new View[4];
  for(int i=0;i<4;i++){
   final String dest=dests[i];LinearLayout tile=a.col();tile.setId(View.generateViewId());tile.setContentDescription(dest);tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);tile.setClickable(true);tile.setGravity(Gravity.CENTER);tile.setPadding(a.dp(10),a.dp(8),a.dp(10),a.dp(4));tile.setBackground(a.focus());
   ImageView icon=a.art(icons[i]);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(icon,new LinearLayout.LayoutParams(-1,0,1));
   TextView name=a.text(dest.toUpperCase(java.util.Locale.ROOT),19);name.setGravity(Gravity.CENTER);name.setTypeface(a.uiMedium);name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(name,new LinearLayout.LayoutParams(-1,a.dp(32)));
   a.premiumHomeFocus(tile);bindNavigation(tile,a,dest);root.place(tile,52+i*390,650,374,232);tiles[i]=tile;
  }
  for(int i=0;i<tiles.length;i++){tiles[i].setNextFocusLeftId(tiles[Math.max(0,i-1)].getId());tiles[i].setNextFocusRightId(tiles[Math.min(i+1,tiles.length-1)].getId());}
  tiles[0].requestFocus();root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
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
