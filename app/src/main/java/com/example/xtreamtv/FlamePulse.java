package com.example.xtreamtv;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.animation.ValueAnimator;
/** Red shoulders, yellow core, continuous soft falloff and pronounced breathing. */
final class FlamePulse {
 private FlamePulse(){}
 static boolean animates(){return Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();}
 static float phase(){return animates()?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1350*Math.PI*2/1350.0)):1f;}
 static void layer(Canvas c,Paint glow,RectF rim,float radius,float sigma,float peak,int red,int green,int blue){
  float previous=0;
  for(int i=20;i>=1;i--){
   float distance=3f*sigma*i/20f;
   float target=peak*(float)Math.exp(-.5f*distance*distance/(sigma*sigma));
   float alpha=(target-previous)/(1f-previous);
   glow.setStrokeWidth(2f*distance);
   glow.setColor(Color.argb(Math.round(255*Math.max(0,Math.min(1,alpha))),red,green,blue));
   c.drawRoundRect(rim,radius,radius,glow);previous=target;
  }
 }
 static void draw(Canvas c,Paint glow,RectF rim,float radius,float scale,float phase){
  if(rim.width()<=0||rim.height()<=0||scale<=0)return;
  glow.setShader(null);glow.setStyle(Paint.Style.STROKE);glow.setStrokeJoin(radius>0?Paint.Join.ROUND:Paint.Join.MITER);
  layer(c,glow,rim,radius,(7.2f+3.0f*phase)*scale,.67f+.28f*phase,255,23,12);
  layer(c,glow,rim,radius,(4.2f+1.8f*phase)*scale,.87f+.10f*phase,255,246,45);
  glow.setStyle(Paint.Style.FILL);glow.setAlpha(255);
 }
}
