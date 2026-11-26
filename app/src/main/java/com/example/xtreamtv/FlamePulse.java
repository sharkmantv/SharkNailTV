package com.example.xtreamtv;

import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.animation.ValueAnimator;

/** One continuous warm flame glow, without a hard inner or outer outline. */
final class FlamePulse {
 private FlamePulse(){}
 static boolean animates(){return Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();}
 static float phase(){return animates()?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1600*Math.PI*2/1600.0)):1f;}
 static void draw(Canvas c,Paint glow,RectF rim,float radius,float scale,float phase){
  if(rim.width()<=0||rim.height()<=0||scale<=0)return;
  glow.setShader(null);glow.setStyle(Paint.Style.STROKE);
  glow.setStrokeJoin(radius>0?Paint.Join.ROUND:Paint.Join.MITER);
  float width=25+phase*5;
  // Soft red falloff, all samples use one centreline.
  for(int step=0;step<12;step++){
   float mix=step/12f;
   glow.setStrokeWidth((width+18*(1-mix))*scale);
   glow.setColor(Color.argb(Math.round(3+15*mix*mix+phase*4*mix),255,18+Math.round(15*mix),5));
   c.drawRoundRect(rim,radius,radius,glow);
  }
  // Continuous cross-section: saturated red at the shoulders, orange
  // in between and warm yellow at the centre. No final line or outline.
  for(int step=0;step<=36;step++){
   float mix=step/36f;
   float smooth=mix*mix*(3-2*mix);
   glow.setStrokeWidth((width*(1-mix)+1.5f*mix)*scale);
   int green=Math.round(26+219*smooth);
   int blue=Math.round(5+28*smooth);
   glow.setColor(Color.rgb(255,green,blue));
   c.drawRoundRect(rim,radius,radius,glow);
  }
  glow.setStyle(Paint.Style.FILL);glow.setAlpha(255);
 }
}
