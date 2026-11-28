package com.example.xtreamtv;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;
final class RegionalGuideNames {
 static final Pattern PREFIX=Pattern.compile("^\\s*(?:[\\[({|]\\s*)?(UNITED KINGDOM|UNITED STATES|NEW ZEALAND|AUSTRALIA|CANADA|USA|UK|GB|US|CA|CAN|AU|AUS|NZ)(?:\\s*[\\])}:|\\-]+\\s*|\\s+)",Pattern.CASE_INSENSITIVE);
 static String region(String name,String id){
  Matcher m=PREFIX.matcher(name==null?"":name);
  if(m.find())switch(m.group(1).toUpperCase(Locale.ROOT)){case "GB":case "UK":case "UNITED KINGDOM":return "UK";case "US":case "USA":case "UNITED STATES":return "US";case "CA":case "CAN":case "CANADA":return "CA";case "AU":case "AUS":case "AUSTRALIA":return "AU";default:return "NZ";}
  String v=id==null?"":id.toLowerCase(Locale.ROOT);
  if(v.matches(".*\\.(uk|gb)[0-9]*$"))return "UK";
  for(String c:new String[]{"US","CA","AU","NZ"})if(v.matches(".*\\."+c.toLowerCase(Locale.ROOT)+"[0-9]*$"))return c;
  return "";
 }
 static String normalize(String value){
  if(value==null)return "";
  String s=PREFIX.matcher(value).replaceFirst("").toLowerCase(Locale.ROOT);
  s=s.replaceFirst("\\.(uk|gb|us|ca|au|nz)[0-9]*$","");
  s=s.replaceAll("[•●★☆._]+"," ");
  s=s.replaceAll("\\b(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p|hevc|h265|h264|raw)\\b"," ");
  s=s.replaceAll("\\bplus\\s*1\\b","+1");
  return s.replaceAll("[^a-z0-9+]","");
 }
 static String file(String region){switch(region){case "UK":return "UK1";case "US":return "US2";case "US_SPORTS":return "US_SPORTS1";case "CA":return "CA2";case "AU":return "AU1";case "NZ":return "NZ1";default:return "";}}
 static final int GUIDE_DAYS=14;
 static final int FULL_LIMIT=1200;
 static final int PREVIEW_LIMIT=120;
 static String[] sources(String region){return "US".equals(region)?new String[]{"US","US_SPORTS"}:file(region).isEmpty()?new String[0]:new String[]{region};}
 static boolean withinWindow(long start,long end,long now){return start>0&&end>start&&end>now-6*3600000L&&start<now+GUIDE_DAYS*86400000L;}
 static long epoch(String s){
  if(s==null||!s.matches("\\d{14}\\s*[+-]\\d{4}"))return 0;
  try{SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);f.setLenient(false);return f.parse(s.substring(0,14)+" "+s.substring(14).trim()).getTime();}catch(Exception e){return 0;}
 }
}
