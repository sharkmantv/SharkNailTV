package com.example.xtreamtv;
public final class PlaybackHealthTest {
 static void check(boolean v){if(!v)throw new AssertionError("PlaybackHealth regression");}
 public static void main(String[] args){
  PlaybackHealth h=new PlaybackHealth();
  check(!h.observe(0,0,true));check(!h.observe(29000,0,true));check(h.observe(30000,0,true));check(h.takeRetry(30000));
  check(!h.takeRetry(31000));check(!h.observe(32000,0,true));check(!h.observe(61000,500,true));
  check(!h.observe(92000,500,false));check(!h.observe(95000,500,true));check(!h.observe(120000,0,true));check(h.observe(150000,0,true));check(h.takeRetry(150000));check(!h.takeRetry(200000));
  h.reset();check(h.attempts==2);
  System.out.println("PASS: live stall threshold, progress, pause, seek, cooldown and bounded reconnect");
 }
}
