package com.example.xtreamtv;

/** ST 2084 reference EOTF in nits. Lookup only replaces the preview EOTF, not gamut or tone mapping. */
final class PqPreviewLut {
 static final int SIZE=2048;
 static float nits(double value){
  double m1=2610.0/16384.0,m2=2523.0/4096.0*128.0,c1=3424.0/4096.0,c2=2413.0/4096.0*32.0,c3=2392.0/4096.0*32.0;
  double t=Math.pow(Math.max(0,Math.min(1,value)),1/m2);
  return (float)(10000*Math.pow(Math.max(t-c1,0)/(c2-c3*t),1/m1));
 }
 private PqPreviewLut(){}
}
