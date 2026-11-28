package com.example.xtreamtv;
final class PlaybackHealth {
 static final long STALL_MS=30000, COOLDOWN_MS=30000;
 long since=-1,position=-1,lastRetry=-1;
 int attempts;
 void reset(){since=-1;position=-1;}
 boolean observe(long now,long pos,boolean eligible){
  if(!eligible){reset();return false;}
  if(since<0||pos<position||pos-position>=250){since=now;position=pos;return false;}
  return now-since>=STALL_MS;
 }
 boolean takeRetry(long now){
  if(attempts>=2||(lastRetry>=0&&now-lastRetry<COOLDOWN_MS))return false;
  attempts++;lastRetry=now;reset();return true;
 }
}
