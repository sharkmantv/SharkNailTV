package com.example.xtreamtv;
final class PqPreviewLutTest {
 static void require(boolean value){if(!value)throw new AssertionError("PQ lookup accuracy");}
 public static void main(String[] args){
  require(PqPreviewLut.nits(0)==0);require(Math.abs(PqPreviewLut.nits(1)-10000)<.01);require(Math.abs(PqPreviewLut.nits(.5080784)-100)<.01);
  float[] samples=new float[PqPreviewLut.SIZE];for(int i=0;i<samples.length;i++){samples[i]=PqPreviewLut.nits((double)i/(samples.length-1));if(i>0)require(samples[i]>=samples[i-1]);}
  for(int i=0;i<=100000;i++){double x=(double)i/100000,index=x*(samples.length-1);int lo=Math.min(samples.length-2,(int)index);double f=index-lo,actual=samples[lo]*(1-f)+samples[lo+1]*f,ref=PqPreviewLut.nits(x);require(Math.abs(actual-ref)<Math.max(.0001,ref*.001));}
  require(DirectSdrShaders.pqPreviewToSdr().contains("texture(uPqLut"));require(!DirectSdrShaders.pqToSdr().contains("uPqLut"));
 }
}
