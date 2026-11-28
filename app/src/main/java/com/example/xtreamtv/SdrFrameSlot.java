package com.example.xtreamtv;

import java.util.concurrent.atomic.AtomicBoolean;

/** One decoder slot, with an optional early release after drawing and before swapping. */
final class SdrFrameSlot {
 private final AtomicBoolean pending=new AtomicBoolean();
 boolean tryAcquire(){return pending.compareAndSet(false,true);}
 boolean isPending(){return pending.get();}
 void reset(){pending.set(false);}
 boolean releaseForSwap(boolean preview,boolean ended){if(!preview||ended)return false;reset();return true;}
 void finishSwap(boolean releasedBeforeSwap){if(!releasedBeforeSwap)reset();}
}
