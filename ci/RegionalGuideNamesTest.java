package com.example.xtreamtv;
public final class RegionalGuideNamesTest {
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  eq(RegionalGuideNames.region("UK: ITV 1 HD",""),"UK");eq(RegionalGuideNames.region("USA | ESPN",""),"US");
  eq(RegionalGuideNames.region("CANADA CBC",""),"CA");eq(RegionalGuideNames.region("AU: ABC",""),"AU");eq(RegionalGuideNames.region("NZ: TVNZ",""),"NZ");eq(RegionalGuideNames.region("France 2",""),"");eq(RegionalGuideNames.region("ESPN","ESPN.HD.us2"),"US");
  eq(RegionalGuideNames.normalize("UK: ITV 1 4K •"),RegionalGuideNames.normalize("ITV1.HD.uk"));
  eq(RegionalGuideNames.normalize("UK: ITV 1 +1"),RegionalGuideNames.normalize("ITV1+1.uk"));
  if(RegionalGuideNames.normalize("ITV1+1.uk").equals(RegionalGuideNames.normalize("ITV1.HD.uk")))throw new AssertionError("timeshift collision");
  if(RegionalGuideNames.normalize("US: ABC East").equals(RegionalGuideNames.normalize("US: ABC West")))throw new AssertionError("regional collision");
  eq(RegionalGuideNames.epoch("20261127230000 +0000"),RegionalGuideNames.epoch("20261127180000 -0500"));
  eq(RegionalGuideNames.epoch("20261127230000"),0L);
  System.out.println("PASS: five regional EPG feeds, HD/4K matching, +1 and regional isolation, XMLTV time offsets");
 }
}
