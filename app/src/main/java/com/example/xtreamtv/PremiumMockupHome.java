package com.example.xtreamtv;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** Native interactive, four-tile landscape home; artwork is blended into full-screen background. */
final class PremiumMockupHome {
 static void show(MainActivity a){
  PremiumVisuals.prepare(a);a.body.removeAllViews();a.body.setPadding(0,0,0,0);
  DesignLayout root=new DesignLayout(a);a.body.addView(root,new LinearLayout.LayoutParams(-1,-1));
  try(java.io.InputStream in=a.getAssets().open("design2_neutral.png")){
   android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeStream(in);
   android.graphics.drawable.BitmapDrawable bg=new android.graphics.drawable.BitmapDrawable(a.getResources(),image);bg.setGravity(Gravity.FILL);bg.setFilterBitmap(true);root.setBackground(bg);
  }catch(java.io.IOException ex){throw new IllegalStateException("Missing approved design artwork",ex);}
  String[] dests={"Live TV","Movies","Series","Settings"};
  int[][] bounds={{68,514,366,311},{481,524,322,295},{858,524,330,295},{1260,524,337,295}};
  View[] tiles=new View[4];
  for(int i=0;i<4;i++){String dest=dests[i];FocusTile tile=new FocusTile(a,dest);tile.setId(View.generateViewId());tile.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.requestFocus();return false;});tile.setOnClickListener(v->a.navigateHome(dest));int[] b=bounds[i];root.place(tile,b[0],b[1],b[2],b[3]);tiles[i]=tile;}
  for(int i=0;i<4;i++){tiles[i].setNextFocusLeftId(tiles[Math.max(0,i-1)].getId());tiles[i].setNextFocusRightId(tiles[Math.min(3,i+1)].getId());}
  tiles[0].requestFocus();root.post(()->{if(root.isAttachedToWindow()&&!root.hasFocus())tiles[0].requestFocus();});
 }
 static final class FocusTile extends View{
  final android.graphics.Paint p=new android.graphics.Paint(3);
  FocusTile(MainActivity a,String title){super(a);setContentDescription(title);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
  @Override protected void onFocusChanged(boolean f,int d,android.graphics.Rect r){super.onFocusChanged(f,d,r);invalidate();}
  @Override protected void onDraw(android.graphics.Canvas c){
   if(!isFocused())return;
   boolean animate=android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();
   float phase=animate?(float)(.5+.5*Math.cos(android.os.SystemClock.uptimeMillis()%1900*Math.PI*2/1900.0)):1;
   float w=getWidth(),h=getHeight(),line=h*(.012f+.010f*phase);
   p.setStyle(android.graphics.Paint.Style.STROKE);
   for(int i=3;i>=0;i--){float stroke=line+i*h*.009f;float inset=stroke/2;p.setStrokeWidth(stroke);p.setColor(i==0?android.graphics.Color.rgb(255,217+Math.round(38*phase),75):android.graphics.Color.argb(15+(3-i)*14+Math.round(20*phase),255,183,13));c.drawRoundRect(inset,inset,w-inset,h-inset,h*.10f,h*.10f,p);}
   if(animate&&isShown())postInvalidateDelayed(40);
  }
 }
 static final class DesignLayout extends ViewGroup{
  final List<int[]> coords=new ArrayList<>();DesignLayout(MainActivity a){super(a);}
  void place(View v,int x,int y,int w,int h){coords.add(new int[]{x,y,w,h});addView(v,new LayoutParams(w,h));}
  int xx(int x,int w){return Math.round(x*w/1648f);}int yy(int y,int h){return Math.round(y*h/928f);}
  @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1,xx(p[0]+p[2],w)-xx(p[0],w)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,yy(p[1]+p[3],h)-yy(p[1],h)),MeasureSpec.EXACTLY));}}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){int[] p=coords.get(i);getChildAt(i).layout(xx(p[0],r-l),yy(p[1],b-t),xx(p[0]+p[2],r-l),yy(p[1]+p[3],b-t));}}
 }
}
