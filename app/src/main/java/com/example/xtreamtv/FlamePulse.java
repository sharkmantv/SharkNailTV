package com.example.xtreamtv;

import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.animation.ValueAnimator;

/** The original approved Home flame, shared by all custom app focus controls. */
final class FlamePulse {
 private FlamePulse(){}
 static boolean animates(){return Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();}
 static float phase(){return animates()?(float)(.5+.5*Math.cos(SystemClock.uptimeMillis()%1600*Math.PI*2/1600.0)):1f;}
 // Unit scale 1 preserves the exact original 1648-design-coordinate Home renderer.
 static void draw(Canvas c,Paint glow,RectF rim,float radius,float scale,float phase){
  if(rim.width()<=0||rim.height()<=0||scale<=0)return;
  glow.setShader(null);glow.setStyle(Paint.Style.STROKE);glow.setStrokeJoin(radius>0?Paint.Join.ROUND:Paint.Join.MITER);
  for(int layer=6;layer>=1;layer--){
   glow.setStrokeWidth((24+phase*4+layer*3)*scale);
   glow.setColor(Color.argb((int)((7+phase*6)*(7-layer)),255,25+layer*4,8));
   c.drawRoundRect(rim,radius,radius,glow);
  }
  // Every stroke shares one centreline: no separate red and yellow borders.
  for(int step=0;step<=24;step++){
   float mix=step/24f;
   float width=(25+phase*5)*(1-mix)+3*mix;
   int green=Math.round(28+212*mix+phase*10*mix);
   int blue=Math.round(7+25*mix);
   glow.setStrokeWidth(width*scale);
   glow.setColor(Color.rgb(255,Math.min(255,green),blue));
   c.drawRoundRect(rim,radius,radius,glow);
  }
  glow.setStrokeWidth((2+phase)*scale);glow.setColor(0xfffff4a0);
  c.drawRoundRect(rim,radius,radius,glow);
  glow.setStyle(Paint.Style.FILL);glow.setAlpha(255);
 }
}
