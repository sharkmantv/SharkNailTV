package com.example.xtreamtv;

import java.util.concurrent.atomic.AtomicBoolean;

/** One decoder slot, with an optional early release after drawing and before swapping. */
final class SdrFrameSlot {
 private final AtomicBoolean pending=new AtomicBoolean();
 // GL worker owns the flags during playback; release/reset can clear them.
 private volatile boolean latchedReleased,deferredArrival;
 boolean tryAcquire(){return pending.compareAndSet(false,true);}
 boolean isPending(){return pending.get();}
 void reset(){pending.set(false);latchedReleased=false;deferredArrival=false;}
 void releaseLatched(){latchedReleased=true;pending.set(false);}
 boolean isLatchedReleased(){return latchedReleased;}
 boolean deferArrival(){if(!latchedReleased||deferredArrival||!pending.get())return false;deferredArrival=true;return true;}
 boolean takeDeferredArrival(){boolean had=deferredArrival;deferredArrival=false;return had;}
 boolean releaseForSwap(boolean preview,boolean ended){if(!preview||ended)return false;releaseLatched();return true;}
 void finishSwap(boolean releasedBeforeSwap){if(!releasedBeforeSwap)pending.set(false);latchedReleased=false;}
}
