package com.example.xtreamtv;

import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.animation.ValueAnimator;

/** A single feathered gold light, no opaque outline or red/orange frame. */
final class FlamePulse {
 private FlamePulse(){}
 static boolean animates(){return Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();}
 static float phase(){return animates()?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1800*Math.PI*2/1800.0)):1f;}
 static void draw(Canvas c,Paint glow,RectF rim,float radius,float scale,float phase){
  if(rim.width()<=0||rim.height()<=0||scale<=0)return;
  glow.setShader(null);glow.setStyle(Paint.Style.STROKE);
  glow.setStrokeJoin(radius>0?Paint.Join.ROUND:Paint.Join.MITER);
  // Composite translucent nested samples of a Gaussian light profile.
  // No opaque strokes: the blue artwork remains visible through the falloff.
  float sigma=(8.5f+phase*1.7f)*scale,peak=.87f+.10f*phase,previous=0f;
  for(int i=30;i>=1;i--){
   float distance=3f*sigma*i/30f;
   float target=peak*(float)Math.exp(-.5f*distance*distance/(sigma*sigma));
   float alpha=(target-previous)/(1f-previous);
   glow.setStrokeWidth(2f*distance);
   glow.setColor(Color.argb(Math.round(255*Math.max(0,Math.min(1,alpha))),255,224,92));
   c.drawRoundRect(rim,radius,radius,glow);
   previous=target;
  }
  glow.setStyle(Paint.Style.FILL);glow.setAlpha(255);
 }
}
