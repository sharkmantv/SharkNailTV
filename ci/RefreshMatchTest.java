package com.example.xtreamtv;
public final class RefreshMatchTest {
 static void eq(int got,int want,String note){if(got!=want)throw new AssertionError(note+": "+got+" != "+want);}
 static void yes(boolean v,String note){if(!v)throw new AssertionError(note);}
 public static void main(String[] args){
  float[] modes={24,48,50,60,100,120,23.976f,59.94f,119.88f};
  eq(RefreshMatch.select(60,24,modes),5,"24 fps cinema");
  eq(RefreshMatch.select(60,25,modes),4,"UK 25 fps");
  eq(RefreshMatch.select(60,50,modes),4,"UK 50 fps");
  eq(RefreshMatch.select(100,59.94f,modes),8,"US fractional");
  eq(RefreshMatch.select(60,29.97f,modes),8,"US 29.97");
  eq(RefreshMatch.select(60,23.976f,modes),8,"fractional film");
  eq(RefreshMatch.select(60,30,modes),-1,"already matched");
  eq(RefreshMatch.select(60,25,new float[]{60}),-1,"60-only television");
  eq(RefreshMatch.select(60,Float.NaN,modes),-1,"unknown FPS");
  yes(RefreshMatch.compatible(60,29.97f)&&!RefreshMatch.exact(60,29.97f),"nominal fallback distinct from exact");
  yes(!RefreshMatch.compatible(60,24),"3:2 is not integer");
  System.out.println("PASS: automatic refresh policy, UK/US, fractional FPS, existing match and unsupported modes");
 }
}
