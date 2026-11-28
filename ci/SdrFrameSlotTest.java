package com.example.xtreamtv;

final class SdrFrameSlotTest {
 static void require(boolean value){if(!value)throw new AssertionError("SDR decoder slot invariant");}
 public static void main(String[] args){
  SdrFrameSlot slot=new SdrFrameSlot();
  require(slot.tryAcquire());require(!slot.tryAcquire());
  boolean early=slot.releaseForSwap(true,false);require(early);require(slot.tryAcquire());require(!slot.tryAcquire());
  slot.finishSwap(early);require(slot.isPending());require(!slot.tryAcquire());
  require(!slot.releaseForSwap(false,false));slot.finishSwap(false);require(!slot.isPending());
  require(slot.tryAcquire());require(!slot.releaseForSwap(true,true));slot.finishSwap(false);require(!slot.isPending());
  require(slot.tryAcquire());slot.reset();require(slot.tryAcquire());
 }
}
