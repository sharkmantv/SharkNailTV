package com.example.xtreamtv;
final class RefreshMatch {
 private RefreshMatch(){}
 static boolean valid(float fps){return Float.isFinite(fps)&&fps>=10f&&fps<=144f;}
 static double error(float hz,float fps){
  if(!valid(fps)||!Float.isFinite(hz)||hz<=0)return Double.POSITIVE_INFINITY;
  double ratio=(double)hz/fps;long multiple=Math.round(ratio);
  return multiple<1?Double.POSITIVE_INFINITY:Math.abs(ratio-multiple)/multiple;
 }
 static boolean compatible(float hz,float fps){return error(hz,fps)<=.0015;}
 static boolean exact(float hz,float fps){return error(hz,fps)<=.00035;}
 static int select(float current,float fps,float[] rates){
  if(!valid(fps)||exact(current,fps))return -1;
  int best=-1;double bestError=Double.POSITIVE_INFINITY;
  for(int n=0;n<rates.length;n++){
   float hz=rates[n];double e=error(hz,fps);if(!compatible(hz,fps))continue;
   if(best<0||(exact(hz,fps)&&!exact(rates[best],fps))||
     (exact(hz,fps)==exact(rates[best],fps)&&(e<bestError-.00005||(Math.abs(e-bestError)<=.00005&&hz>rates[best])))){
     best=n;bestError=e;
   }
  }
  if(compatible(current,fps)&&(best<0||!exact(rates[best],fps)))return -1;
  return best;
 }
}
