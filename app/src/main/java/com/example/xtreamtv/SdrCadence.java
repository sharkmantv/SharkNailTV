package com.example.xtreamtv;
import java.util.Arrays;
/** Bounded source cadence estimator for GPU paths without rendered-frame metadata.
 * Decoder input may arrive in decode order, not presentation order. */
final class SdrCadence {
 private final long[] pts=new long[64]; private int size,next; private long high=Long.MIN_VALUE;
 synchronized void accept(long t){
  if(high!=Long.MIN_VALUE&&(t>high+2000000L||t<high-2000000L)){size=next=0;high=Long.MIN_VALUE;}
  if(t>high)high=t;
  pts[next]=t; next=(next+1)%pts.length; if(size<pts.length)size++;
 }
 synchronized float estimate(){
  if(size<16)return -1;
  long[] sorted=Arrays.copyOf(pts,size);Arrays.sort(sorted);
  long[] deltas=new long[size-1];int n=0;
  for(int i=1;i<size;i++){long d=sorted[i]-sorted[i-1];if(d>=5000&&d<=250000)deltas[n++]=d;}
  if(n<12)return -1;
  Arrays.sort(deltas,0,n);long med=deltas[n/2];int near=0;double sum=0;
  for(int i=0;i<n;i++)if(Math.abs(deltas[i]-med)<=med*.12){near++;sum+=deltas[i];}
  return near>=n*.75?(float)(1000000.0*near/sum):-1;
 }
 // Android MediaFormat COLOR_TRANSFER_SDR_VIDEO = 3. Do not guess HDR from an unknown source.
 static boolean shouldTryGpu(boolean hdr,boolean known,int transfer,long ageMs){
  if(!hdr||ageMs<1500)return false;
  return known?transfer!=3:ageMs>=4500;
 }
}
