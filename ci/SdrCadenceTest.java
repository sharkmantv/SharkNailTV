package com.example.xtreamtv;
public final class SdrCadenceTest {
 static void near(float actual,float expected){if(Math.abs(actual-expected)>.2f)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  SdrCadence a=new SdrCadence();
  for(int i=0;i<60;i+=3){a.accept(i*20000L);a.accept((i+2)*20000L);a.accept((i+1)*20000L);}
  near(a.estimate(),50);
  a.accept(9000000L);if(a.estimate()!=-1)throw new AssertionError("discontinuity");
  for(int i=1;i<64;i++)a.accept(9000000L+i*16683L);near(a.estimate(),59.94f);
  if(SdrCadence.shouldTryGpu(false,true,6,5000)||SdrCadence.shouldTryGpu(true,true,3,5000)||SdrCadence.shouldTryGpu(true,true,6,100)||!SdrCadence.shouldTryGpu(true,true,6,2000)||!SdrCadence.shouldTryGpu(true,false,-1,5000)||SdrCadence.shouldTryGpu(true,false,-1,2000))throw new AssertionError("SDR fallback policy");
  System.out.println("PASS: GPU source cadence with reordered PTS and HDR-only bounded conversion fallback");
 }
}
