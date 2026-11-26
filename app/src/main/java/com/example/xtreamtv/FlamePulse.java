package com.example.xtreamtv;

import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.animation.ValueAnimator;

/** Shared Sky spectrum focus pulse; original geometry and animation timing retained. */
final class FlamePulse {
 private FlamePulse(){}
 static boolean animates(){return Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();}
 static float phase(){return animates()?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1600*Math.PI*2/1600.0)):1f;}
 // Clockwise from right: blue, cyan, magenta, coral, amber, gold, magenta, violet.
 static final int[] SPECTRUM={0xff2877ff,0xff48eaff,0xffb32dff,0xffff526d,0xffff9e42,0xffffd65a,0xffed3dbe,0xff6446ff,0xff2877ff};
 static final float[] STOPS={0,.125f,.25f,.375f,.5f,.625f,.75f,.875f,1};
 static final class Spectrum {
  Shader halo,body,transition,core;float x,y,w,h;
  Spectrum(RectF r){x=r.centerX();y=r.centerY();w=r.width();h=r.height();halo=shader(r,0);body=shader(r,.02f);transition=shader(r,.24f);core=shader(r,.52f);}
 }
 static final java.util.WeakHashMap<Paint,Spectrum> CACHE=new java.util.WeakHashMap<>();
 static Shader shader(RectF r,float white){
  int[] colors=new int[SPECTRUM.length];for(int i=0;i<colors.length;i++){int c=SPECTRUM[i];colors[i]=Color.rgb(Math.round(Color.red(c)+(255-Color.red(c))*white),Math.round(Color.green(c)+(255-Color.green(c))*white),Math.round(Color.blue(c)+(255-Color.blue(c))*white));}
  SweepGradient gradient=new SweepGradient(r.centerX(),r.centerY(),colors,STOPS);
  Matrix m=new Matrix();m.setScale(r.width()/r.height(),1,r.centerX(),r.centerY());gradient.setLocalMatrix(m);return gradient;
 }
 static void draw(Canvas c,Paint glow,RectF rim,float radius,float scale,float phase){
  if(rim.width()<=0||rim.height()<=0||scale<=0)return;
  Spectrum s=CACHE.get(glow);
  if(s==null||s.x!=rim.centerX()||s.y!=rim.centerY()||s.w!=rim.width()||s.h!=rim.height()){s=new Spectrum(rim);CACHE.put(glow,s);}
  glow.setStyle(Paint.Style.STROKE);glow.setStrokeJoin(radius>0?Paint.Join.ROUND:Paint.Join.MITER);
  glow.setShader(s.halo);
  for(int layer=6;layer>=1;layer--){glow.setStrokeWidth((24+phase*4+layer*3)*scale);glow.setAlpha((int)((7+phase*6)*(7-layer)));c.drawRoundRect(rim,radius,radius,glow);}
  glow.setShader(s.body);glow.setAlpha(255);
  for(int step=0;step<=24;step++){
   float mix=step/24f;
   glow.setStrokeWidth(((25+phase*5)*(1-mix)+3*mix)*scale);
   // Retain saturated spectrum through most of the band; brighten only its narrow centre.
   if(step>=22)glow.setShader(s.core);else if(step>=17)glow.setShader(s.transition);
   glow.setAlpha(255);c.drawRoundRect(rim,radius,radius,glow);
  }
  glow.setShader(s.core);glow.setAlpha(255);glow.setStrokeWidth((2+phase)*scale);
  c.drawRoundRect(rim,radius,radius,glow);
  glow.setShader(null);glow.setStyle(Paint.Style.FILL);glow.setAlpha(255);
 }
}
