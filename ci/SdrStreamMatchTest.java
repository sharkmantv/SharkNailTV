package com.example.xtreamtv;
public final class SdrStreamMatchTest {
 static void check(boolean value){if(!value)throw new AssertionError("SDR matching");}
 public static void main(String[] args){
  check(SdrStreamMatch.matches("UK: Sky Sports F1 UHD HDR","f1.uk","UK: Sky Sports F1 UHD SDR","f1.uk",false));
  check(!SdrStreamMatch.matches("UK: Sky Sports F1 UHD HDR","f1.uk","UK: Sky Sports F1 HD SDR","f1.uk",true));
  check(!SdrStreamMatch.matches("UK: Sky Sports F1 UHD HDR","f1.uk","US: Sky Sports F1 UHD SDR","f1.uk",true));
  check(!SdrStreamMatch.matches("UK: Sky Sports F1 UHD HDR","f1.uk","UK: Sky Sports F1 UHD SDR","other.uk",true));
  check(!SdrStreamMatch.matches("Sky Sports F1 UHD HDR","","Sky Sports F1 UHD","",true));
  check(!SdrStreamMatch.matches("Sky Sports F1 UHD HDR","","Sky Sports F1 UHD SDR HDR","",true));
  check(!SdrStreamMatch.matches("Sky Sports F1 UHD HDR","","Sky Sports F1 UHD SDR","",false));
  check(SdrStreamMatch.matches("Sky Sports F1 UHD HDR","","Sky Sports F1 UHD SDR","",true));
  System.out.println("PASS: explicit same-channel SDR alternatives only; reject other regions, resolutions, ambiguous HDR labels and mismatched EPG");
 }
}
