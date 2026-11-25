package com.example.xtreamtv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;

/** Draw-only TV graphics. No boxed icon bitmaps, layout animation or video filters. */
final class PremiumVisuals {
 static final int GOLD=0xffffd34e, BLUE=0xff66b8ff;
 static final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 static Bitmap hero,logo,fourSharks;
 static final Bitmap[] homeArt=new Bitmap[4];
 static void prepare(Context c){
  if(hero!=null&&logo!=null)return;
  BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;
  Bitmap a=BitmapFactory.decodeResource(c.getResources(),R.drawable.premium_mockup3,o);
  if(a==null||a.getWidth()!=1400||a.getHeight()!=1000)return;
  hero=Bitmap.createBitmap(a,0,0,1388,612).copy(Bitmap.Config.ARGB_8888,true);
  Canvas cv=new Canvas(hero);Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
  mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
  mask.setShader(new LinearGradient(0,0,1388,0,new int[]{0x00ffffff,0xffffffff,0xffffffff,0x00ffffff},new float[]{0,.085f,.915f,1},Shader.TileMode.CLAMP));cv.drawRect(0,0,1388,612,mask);
  mask.setShader(new LinearGradient(0,0,0,612,new int[]{0x00ffffff,0xffffffff,0xffffffff,0x00ffffff},new float[]{0,.022f,.86f,1},Shader.TileMode.CLAMP));cv.drawRect(0,0,1388,612,mask);
  logo=BitmapFactory.decodeResource(c.getResources(),R.drawable.premium_cut_brand,o);
  int[] ids={R.drawable.premium_cut_live,R.drawable.premium_cut_movies,R.drawable.premium_cut_series,R.drawable.premium_cut_settings};
  for(int i=0;i<ids.length;i++)homeArt[i]=BitmapFactory.decodeResource(c.getResources(),ids[i],o);
  int atlasId=c.getResources().getIdentifier("four_sharks_atlas","drawable",c.getPackageName());
  if(atlasId!=0)fourSharks=BitmapFactory.decodeResource(c.getResources(),atlasId,o);
  a.recycle();
 }
 static View brand(MainActivity a){prepare(a);ImageView v=new ImageView(a);v.setImageBitmap(logo);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setContentDescription("SHARK SKY");return v;}
 static final class Backdrop extends Drawable{
  final Paint p=new Paint(3);final boolean withHero;
  Backdrop(boolean h){withHero=h;}
  @Override public void draw(Canvas c){Rect r=getBounds();float w=r.width(),h=r.height();if(w<=0||h<=0)return;int save=c.save();c.translate(r.left,r.top);
   if(withHero){
    // Sky Stream inspired: near-black top, rich cobalt at the lower edge, no ribbon.
    p.setShader(new LinearGradient(0,0,0,h,new int[]{0xff070811,0xff090c2e,0xff10105b,0xff111ba0,0xff10177e},new float[]{0,.26f,.52f,.80f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
    p.setShader(new RadialGradient(w*.13f,h*.77f,w*.76f,new int[]{0x700040ef,0x330027cb,0x000027cb},new float[]{0,.42f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
   }else{
   p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff061021,0xff071b40,0xff040c20},new float[]{0,.52f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
   p.setShader(new RadialGradient(w*.53f,h*.38f,Math.max(w,h)*.51f,new int[]{0x8c155ed6,0x35133f99,0x00133f99},new float[]{0,.52f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
   p.setShader(new LinearGradient(0,0,w,0,new int[]{0x000c61ce,0x603168ca,0x000c61ce},null,Shader.TileMode.CLAMP));Path ribbon=new Path();ribbon.moveTo(0,h*.53f);ribbon.cubicTo(w*.38f,h*.22f,w*.65f,h*.66f,w,h*.18f);ribbon.lineTo(w,h*.25f);ribbon.cubicTo(w*.62f,h*.73f,w*.32f,h*.31f,0,h*.58f);ribbon.close();c.drawPath(ribbon,p);
   }
   p.setShader(null);if(withHero&&fourSharks!=null){c.drawBitmap(fourSharks,new Rect(0,0,fourSharks.getWidth(),Math.round(fourSharks.getHeight()*.723f)),new RectF(0,0,w,h*.723f),bitmapPaint);}else if(withHero&&hero!=null){float bw=w*.98f,bh=bw*hero.getHeight()/hero.getWidth();float max=h*.69f;if(bh>max){bw*=max/bh;bh=max;}c.drawBitmap(hero,null,new RectF((w-bw)/2,0,(w+bw)/2,bh),bitmapPaint);}
   if(!withHero){p.setShader(new LinearGradient(0,h*.76f,0,h,new int[]{0x00030b1d,0xcb06142e,0xff030a18},new float[]{0,.53f,1},Shader.TileMode.CLAMP));c.drawRect(0,h*.76f,w,h,p);}p.setShader(null);c.restoreToCount(save);
  }
  @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
 }
 static Path shape(float l,float t,float r,float b,float corner){Path p=new Path();p.moveTo(l+corner,t);p.lineTo(r-corner,t);p.lineTo(r,t+corner);p.lineTo(r,b-corner);p.lineTo(r-corner,b);p.lineTo(l+corner,b);p.lineTo(l,b-corner);p.lineTo(l,t+corner);p.close();return p;}
 static void glass(Canvas c,Paint p,float l,float t,float r,float b,boolean focus,float phase,boolean large){
  float h=b-t,w=r-l;if(w<5||h<5)return;float s=Math.max(1,Math.min(w,h)*(large?.018f:.025f)),bevel=0;
  Path outline=shape(l+s,t+s,r-s,b-s,bevel);
  p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(l,t,r,b,new int[]{0x703b69b6,0x4810254b,0x7210203a,0x90101832},new float[]{0,.28f,.62f,1},Shader.TileMode.CLAMP));c.drawPath(outline,p);
  p.setShader(null);int saved=c.save();c.clipPath(outline);
  p.setShader(new LinearGradient(0,t,0,t+h*.39f,new int[]{0x35a9d5ff,0x1268a9ff,0x0068a9ff},null,Shader.TileMode.CLAMP));c.drawRect(l,t,r,t+h*.39f,p);
  Path gloss=new Path();gloss.moveTo(l,t);gloss.lineTo(r,t);gloss.lineTo(r,t+h*.10f);gloss.lineTo(l,t+h*.53f);gloss.close();p.setShader(new LinearGradient(l,t,r,b,new int[]{0x18eff6ff,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawPath(gloss,p);p.setShader(null);c.restoreToCount(saved);
  p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.MITER);
  if(focus){for(int i=6;i>=1;i--){p.setColor(Color.argb((int)((11+phase*11)*(7-i)/2.5f),255,187,15));p.setStrokeWidth(s*(1+i*.65f));c.drawPath(outline,p);}}
  p.setStrokeWidth(s*1.15f);p.setShader(new LinearGradient(l,t,r,b,focus?new int[]{0xfffff9cc,0xffffbd21,0xff845008,0xffffed82,0xffffc32e}:new int[]{0xffd1e9ff,0xff3178c8,0xff213b68,0xff9cdbff,0xff225aa2},new float[]{0,.20f,.48f,.76f,1},Shader.TileMode.CLAMP));c.drawPath(outline,p);
  p.setShader(null);p.setStrokeWidth(s*.4f);p.setColor(focus?0xfffff3b5:0xb8c4e7ff);c.drawPath(shape(l+s*2.1f,t+s*2.1f,r-s*2.1f,b-s*2.1f,bevel),p);p.setStyle(Paint.Style.FILL);
 }
 static final class Glass extends Drawable{
  final Paint p=new Paint(3);final boolean selected,focused;
  Glass(boolean f,boolean s){focused=f;selected=s;}
  @Override public void draw(Canvas c){Rect r=getBounds();glass(c,p,r.left,r.top,r.right,r.bottom,focused,1,false);}
  @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 static void metal(Canvas c,Paint p,Path q,boolean focus){
  p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(2.8f);p.setColor(0xcc020713);c.drawPath(q,p);
  p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,12,0,88,focus?new int[]{0xfffff9e0,0xfff8d36c,0xff7c510b,0xffffeaa1,0xffcf8e18}:new int[]{0xfff5fbff,0xffb5d0e7,0xff40536a,0xfff1f5fd,0xff718da7},new float[]{0,.26f,.51f,.74f,1},Shader.TileMode.CLAMP));c.drawPath(q,p);p.setShader(null);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(focus?0xfffff1be:0xffeff7ff);c.drawPath(q,p);p.setStyle(Paint.Style.FILL);
 }
 static void box(Path q,float l,float t,float r,float b){q.addRect(l,t,r,b,Path.Direction.CW);}
 static Path ring(float x,float y,float r,float inner){Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);p.addCircle(x,y,r,Path.Direction.CW);p.addCircle(x,y,inner,Path.Direction.CW);return p;}
 static void triangle(Path q,float x,float y,float w,float h){q.moveTo(x,y);q.lineTo(x+w,y+h/2);q.lineTo(x,y+h);q.close();}
 static void icon(Canvas c,Paint p,int type,boolean focus,float x,float y,float size){int n=c.save();c.translate(x,y);c.scale(size/100,size/100);
  Path q=new Path();q.setFillType(Path.FillType.EVEN_ODD);
  switch(type){
   case 0: case 2: case 6:
    box(q,12,26,88,78);box(q,20,34,80,70);metal(c,p,q,focus);
    q=new Path();triangle(q,41,41,22,25);if(type!=6)metal(c,p,q,focus);
    q=new Path();if(type==0){q.moveTo(29,11);q.lineTo(50,25);q.lineTo(71,11);q.lineTo(74,15);q.lineTo(50,30);q.lineTo(26,15);q.close();box(q,35,81,65,85);}else if(type==6){box(q,45,77,55,87);box(q,30,86,70,91);}else{for(int i=0;i<4;i++){box(q,15,29+i*12,18,34+i*12);box(q,82,29+i*12,85,34+i*12);}}metal(c,p,q,focus);break;
   case 1:
    box(q,14,39,86,84);box(q,23,48,77,75);metal(c,p,q,focus);q=new Path();triangle(q,42,51,20,19);metal(c,p,q,focus);
    int k=c.save();c.rotate(-13,15,34);q=new Path();q.setFillType(Path.FillType.EVEN_ODD);box(q,12,20,88,35);for(int i=0;i<3;i++){float a=21+i*23;q.moveTo(a,22);q.lineTo(a+10,22);q.lineTo(a+3,33);q.lineTo(a-7,33);q.close();}metal(c,p,q,focus);c.restoreToCount(k);break;
   case 3:
    for(int i=0;i<24;i++){double a=Math.PI*2*i/24;float r=(i%3==0||i%3==1)?39:30;float xx=50+(float)Math.cos(a)*r,yy=50+(float)Math.sin(a)*r;if(i==0)q.moveTo(xx,yy);else q.lineTo(xx,yy);}q.close();q.addCircle(50,50,16,Path.Direction.CW);metal(c,p,q,focus);break;
   case 4:
    for(int i=0;i<3;i++){q=new Path();q.setFillType(Path.FillType.EVEN_ODD);box(q,15,19+i*23,85,37+i*23);q.addCircle(25,28+i*23,3,Path.Direction.CW);box(q,61,26+i*23,77,30+i*23);metal(c,p,q,focus);}break;
   case 5:metal(c,p,ring(50,50,36,29),focus);triangle(q,42,34,25,32);metal(c,p,q,focus);break;
   case 7:
    box(q,13,39,30,62);q.moveTo(30,39);q.lineTo(52,22);q.lineTo(52,79);q.lineTo(30,62);q.close();metal(c,p,q,focus);
    for(int i=0;i<2;i++){q=new Path();float rr=24+i*11;q.arcTo(52-rr,50-rr,52+rr,50+rr,-52,104,true);q.arcTo(52-rr+6,50-rr+6,52+rr-6,50+rr-6,52,-104,false);q.close();metal(c,p,q,focus);}break;
   case 8:
    box(q,12,19,88,72);box(q,20,27,80,64);metal(c,p,q,focus);q=new Path();box(q,28,36,68,41);box(q,28,49,46,54);box(q,52,49,73,54);metal(c,p,q,focus);break;
   case 9: case 11:
    q.arcTo(15,15,85,85,-44,286,true);q.arcTo(23,23,77,77,242,-286,false);q.close();metal(c,p,q,focus);q=new Path();q.moveTo(77,11);q.lineTo(90,35);q.lineTo(63,35);q.close();metal(c,p,q,focus);break;
   case 10:metal(c,p,ring(50,50,37,30),focus);q.addCircle(50,33,5,Path.Direction.CW);box(q,46,44,54,71);metal(c,p,q,focus);break;
  }c.restoreToCount(n);
 }
 static void skyTile(Canvas c,Paint p,float w,float h,boolean f,float phase,int icon){
    float inset=Math.max(3,h*.045f);
    RectF panel=new RectF(inset,inset,w-inset,h-inset);
    p.setStyle(Paint.Style.FILL);
    p.setShader(new LinearGradient(0,panel.top,0,panel.bottom,new int[]{f?0xd321215c:0xbc151640,0xe4111243},null,Shader.TileMode.CLAMP));
    c.drawRect(panel,p);p.setShader(null);
    if(f){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,h*.009f));p.setColor(0x995b74ce);c.drawRect(panel,p);p.setStyle(Paint.Style.FILL);}
    int[] left={0xffff8d00,0xffff394d,0xfff4289e,0xffad51ff};
    int[] right={0xffff385e,0xffd92191,0xff9c41ff,0xff438dff};
    int k=Math.floorMod(icon,4);
    float band=Math.max(3,h*.027f);
    int saved=c.save();c.clipRect(panel);
    p.setShader(new LinearGradient(panel.left,0,panel.right,0,left[k],right[k],Shader.TileMode.CLAMP));
    p.setAlpha((int)(f?205+50*phase:155+65*phase));c.drawRect(panel.left,panel.top,panel.right,panel.top+band,p);
    p.setAlpha((int)(f?22+38*phase:10+18*phase));c.drawRect(panel.left,panel.top+band,panel.right,panel.top+band*2.2f,p);
    p.setAlpha(255);p.setShader(null);c.restoreToCount(saved);
 }
 static final class Tile extends View{
  final Paint p=new Paint(3);final String title;final int icon;final boolean reflection;
  Tile(Context c,String title,int icon,boolean reflection){super(c);this.title=title;this.icon=icon;this.reflection=reflection;setContentDescription(title);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
  @Override protected void onFocusChanged(boolean f,int dir,Rect prev){super.onFocusChanged(f,dir,prev);invalidate();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),body=h;float margin=Math.max(3,Math.min(w,body)*.035f);boolean f=isFocused();boolean animate=android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();float phase=animate?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1900*Math.PI*2/1900.0)):1;
   if(reflection){
    skyTile(c,p,w,h,f,phase,icon);
    int k=Math.floorMod(icon,4);
    if(fourSharks!=null){
     int[][] bounds={{167,701,312,792},{562,701,715,792},{964,701,1100,792},{1366,701,1517,792}};
     int[] a=bounds[k];Rect src=new Rect(a[0],a[1],a[2],a[3]);
     float ih=h*.42f,iw=ih*src.width()/src.height();
     c.drawBitmap(fourSharks,src,new RectF((w-iw)/2,h*.12f,(w+iw)/2,h*.12f+ih),bitmapPaint);
    }
    p.setTypeface(Typeface.create(f?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
    p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(h*.21f,w*.105f));
    p.setColor(0xfff4f5ff);Paint.FontMetrics fm=p.getFontMetrics();c.drawText(title,w*.5f,h*.76f-(fm.ascent+fm.descent)*.5f,p);
    if(animate&&isShown())postInvalidateDelayed(50);
    return;
   }
   if(!reflection){
    skyTile(c,p,w,h,f,phase,icon);
    float is=Math.min(h*.59f,w*.18f);icon(c,p,icon,false,w*.07f,(h-is)/2,is);
    p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(Paint.Align.LEFT);p.setTextSize(Math.min(h*.27f,w*.09f));while(p.measureText(title)>w*.64f)p.setTextSize(p.getTextSize()*.94f);p.setColor(Color.WHITE);Paint.FontMetrics fm=p.getFontMetrics();c.drawText(title,w*.29f,h*.5f-(fm.ascent+fm.descent)*.5f,p);
    if(animate&&isShown())postInvalidateDelayed(50);return;
   }
   glass(c,p,margin,margin,w-margin,body-margin,f,phase,true);
   float iconSize=Math.min(w*.55f,body*.57f);float iy=body*.08f;
   Bitmap art=reflection&&icon>=0&&icon<homeArt.length?homeArt[icon]:null;
   if(art!=null){float aw=iconSize,ah=aw*art.getHeight()/art.getWidth();if(ah>iconSize){aw*=iconSize/ah;ah=iconSize;}c.drawBitmap(art,null,new RectF((w-aw)/2,iy+(iconSize-ah)/2,(w+aw)/2,iy+(iconSize+ah)/2),bitmapPaint);}
   else icon(c,p,icon,f,(w-iconSize)/2,iy,iconSize);
   p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(body*.17f,w*.115f));while(p.measureText(title)>w*.82f)p.setTextSize(p.getTextSize()*.94f);
   p.setShader(null);p.setColor(Color.WHITE);p.setShadowLayer(2,0,2,0xff000000);float baseline=body*.83f;c.drawText(title,w/2,baseline,p);p.clearShadowLayer();

   if(f&&animate&&isShown())postInvalidateDelayed(40);
  }
 }
}
