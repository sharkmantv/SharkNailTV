package com.example.xtreamtv;
import java.util.Locale;
import java.util.regex.Pattern;
/** Only substitute an explicitly identified SDR equivalent, never guess from HD/UHD. */
final class SdrStreamMatch {
 private static final Pattern SDR=Pattern.compile("\\bSDR\\b",Pattern.CASE_INSENSITIVE);
 private static final Pattern HDR=Pattern.compile("\\b(?:HDR(?:10(?:\\+)?)?|HLG|DOLBY\\s+VISION)\\b",Pattern.CASE_INSENSITIVE);
 static String canonical(String name){
  return SDR.matcher(HDR.matcher(name).replaceAll(" ")).replaceAll(" ").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
 }
 static boolean matches(String original,String originalEpg,String candidate,String candidateEpg,boolean sameList){
  if(!SDR.matcher(candidate).find()||HDR.matcher(candidate).find())return false;
  String key=canonical(original);if(key.isEmpty()||!key.equals(canonical(candidate)))return false;
  boolean both=!originalEpg.isEmpty()&&!candidateEpg.isEmpty();
  return both?originalEpg.equalsIgnoreCase(candidateEpg):sameList;
 }
}
