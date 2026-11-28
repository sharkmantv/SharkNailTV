package com.example.xtreamtv;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.TimestampIterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Experimental surface-only, single-input graph. No intermediate RGBA frame or second draw.
 * ExoPlayer retains codec, audio clock, demuxing and frame release scheduling.
 * A decoder frame remains latched until the corresponding release/drop command arrives.
 */
final class DirectSdrVideoGraph implements VideoGraph {
 private static final float[] QUAD={-1,-1,0,1, 1,-1,0,1, -1,1,0,1, 1,1,0,1};
 private static final float[] LIMITED={1.1689f,1.1689f,1.1689f,0,-.1881f,2.1502f,1.6853f,-.6530f,0};
 private static final float[] FULL={1,1,1,0,-.1646f,1.8814f,1.4746f,-.5714f,0};
 private final Listener listener;
 private final Executor callback;
 private final Runnable inputCounter,submitCounter,dropCounter;
 private final AtomicBoolean pending=new AtomicBoolean();
 private final HandlerThread thread=new HandlerThread("SharkDirectSDR",android.os.Process.THREAD_PRIORITY_DISPLAY);
 private Handler gl;
 private EGLDisplay display;
 private EGLContext context;
 private EGLSurface placeholder,window;
 private SurfaceTexture texture;
 private Surface input;
 private GlProgram hdrProgram,sdrProgram;
 private int tex,width=1,height=1,transfer=C.COLOR_TRANSFER_SDR;
 private float pixelAspect=1;
 private boolean fullRange,latched,ended;
 private long pts,offset,lastPts=C.TIME_UNSET;
 private final float[] texMatrix=new float[16],position=new float[16],identity=new float[16];
 private volatile boolean ready,released,failed,zero,configured;
 private volatile int generation;
 private SurfaceInfo output;
 DirectSdrVideoGraph(Listener l,Executor cb,Runnable in,Runnable out,Runnable drop){
  listener=l;callback=cb;inputCounter=in;submitCounter=out;dropCounter=drop;Matrix.setIdentityM(identity,0);
 }
 private interface Task{void run()throws Exception;}
 private void sync(Task task)throws Exception{
  if(Thread.currentThread()==thread){task.run();return;}
  FutureTask<Void> f=new FutureTask<>(()->{task.run();return null;});
  if(!gl.post(f))throw new IllegalStateException("GL thread stopped");
  f.get(5,TimeUnit.SECONDS);
 }
 private void error(Throwable e){
  if(failed||released)return;failed=true;pending.set(false);
  callback.execute(()->{if(!released)listener.onError(new VideoFrameProcessingException(e));});
 }
 private void post(Task task){
  if(gl==null||released||failed)return;
  gl.post(()->{if(!released&&!failed)try{task.run();}catch(Exception e){error(e);}});
 }
 private void focusPlaceholder()throws Exception{GlUtil.focusEglSurface(display,context,placeholder,1,1);}
 @Override public void initialize()throws VideoFrameProcessingException{
  thread.start();gl=new Handler(thread.getLooper());
  try{sync(()->{
   display=GlUtil.getDefaultEglDisplay();
   context=GlUtil.createEglContext(EGL14.EGL_NO_CONTEXT,display,3,GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
   placeholder=GlUtil.createFocusedPlaceholderEglSurface(context,display);
   tex=GlUtil.createExternalTexture();texture=new SurfaceTexture(tex);input=new Surface(texture);
   texture.setOnFrameAvailableListener(t->onFrame(),gl);
   ready=true;
  });}catch(Exception e){throw new VideoFrameProcessingException(e);}
 }
 @Override public void registerInput(int index)throws VideoFrameProcessingException{
  if(index!=0||!ready)throw new VideoFrameProcessingException("Direct SDR input unavailable");
 }
 @Override public Surface getInputSurface(int index){return input;}
 @Override public void setOnInputSurfaceReadyListener(int index,Runnable r){if(ready)r.run();}
 @Override public void setOnInputFrameProcessedListener(int index,OnInputFrameProcessedListener l){}
 @Override public void setCompositionEffects(List<Effect> effects){if(!effects.isEmpty())throw new IllegalArgumentException("Direct graph has no effects");}
 @Override public void setCompositorSettings(VideoCompositorSettings settings){if(!VideoCompositorSettings.DEFAULT.equals(settings))throw new IllegalArgumentException("Single input only");}
 @Override public void setOutputSurfaceInfo(SurfaceInfo info){
  if(released||failed)return;
  try{sync(()->{
   if(info!=null&&info.equals(output))return;
   focusPlaceholder();
   if(window!=null)GlUtil.destroyEglSurface(display,window);
   window=null;output=info;
   if(info!=null)window=GlUtil.createEglSurface(display,info.surface,C.COLOR_TRANSFER_SDR,false);
  });}catch(Exception e){error(e);}
 }
 @Override public void registerInputStream(int index,int type,Format f,List<Effect> effects,long offsetUs){
  if(type!=VideoFrameProcessor.INPUT_TYPE_SURFACE||!effects.isEmpty()){error(new IllegalArgumentException("Direct SDR requires decoded surface frames"));return;}
  try{sync(()->{
   focusPlaceholder();
   width=f.width;height=f.height;pixelAspect=f.pixelWidthHeightRatio>0?f.pixelWidthHeightRatio:1;
   ColorInfo ci=f.colorInfo;transfer=ci==null?C.COLOR_TRANSFER_SDR:ci.colorTransfer;
   fullRange=ci!=null&&ci.colorRange==C.COLOR_RANGE_FULL;
   boolean hdr=transfer==C.COLOR_TRANSFER_ST2084||transfer==C.COLOR_TRANSFER_HLG;
   if(hdr){
    if(ci.colorSpace!=C.COLOR_SPACE_BT2020)throw new IllegalArgumentException("Unsupported HDR gamut");
    if(hdrProgram==null)hdrProgram=new GlProgram(DirectSdrShaders.VERTEX,DirectSdrShaders.HDR);
   }else if(sdrProgram==null)sdrProgram=new GlProgram(DirectSdrShaders.VERTEX,DirectSdrShaders.SDR);
   if(width<=0||height<=0)throw new IllegalArgumentException("Invalid dimensions");
   this.offset=offsetUs;texture.setDefaultBufferSize(width,height);ended=false;configured=true;
  });callback.execute(()->{if(!released){listener.onOutputSizeChanged(f.width,f.height);listener.onOutputFrameRateChanged(f.frameRate);}});
  }catch(Exception e){error(e);}
 }
 @Override public boolean registerInputFrame(int index){
  if(!configured||failed||released||!pending.compareAndSet(false,true))return false;
  inputCounter.run();return true;
 }
 @Override public int getPendingInputFrameCount(int index){return pending.get()?1:0;}
 private void onFrame(){
  if(released||failed)return;
  try{
   focusPlaceholder();
   if(latched)throw new IllegalStateException("Unexpected extra decoder frame");
   texture.updateTexImage();
   if(!pending.get())return;
   texture.getTransformMatrix(texMatrix);pts=texture.getTimestamp()/1000+offset;
   latched=true;if(pts==0)zero=true;lastPts=pts;
   final long timestamp=pts;final int token=generation;
   callback.execute(()->{if(!released&&!failed&&token==generation)listener.onOutputFrameAvailableForRendering(timestamp,false);});
  }catch(Exception e){error(e);}
 }
 @Override public void renderOutputFrame(long ns){
  final int token=generation;
  post(()->{
   if(token!=generation)return;
   if(!latched){throw new IllegalStateException("No latched decoder frame");}
   if(ns==VideoFrameProcessor.DROP_OUTPUT_FRAME){dropCounter.run();}
   else if(window!=null&&output!=null){
    GlUtil.focusEglSurface(display,context,window,output.width,output.height);
    GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    Matrix.setIdentityM(position,0);
    float src=width*pixelAspect/height,dst=(float)output.width/output.height;
    if(output.orientationDegrees==90||output.orientationDegrees==270)src=1/src;
    Matrix.scaleM(position,0,Math.min(1,src/dst),Math.min(1,dst/src),1);
    Matrix.rotateM(position,0,output.orientationDegrees,0,0,1);
    boolean hdr=transfer==C.COLOR_TRANSFER_ST2084||transfer==C.COLOR_TRANSFER_HLG;
    GlProgram p=hdr?hdrProgram:sdrProgram;p.use();
    p.setSamplerTexIdUniform("uTexSampler",tex,0);
    p.setFloatsUniform("uTexTransformationMatrix",texMatrix);
    p.setFloatsUniform("uTransformationMatrix",position);
    p.setBufferAttribute("aFramePosition",QUAD,4);
    if(hdr){
     p.setFloatsUniform("uRgbMatrix",identity);
     p.setFloatsUniform("uYuvToRgbColorTransform",fullRange?FULL:LIMITED);
     p.setIntUniform("uInputColorTransfer",transfer);
     p.setIntUniform("uApplyHdrToSdrToneMapping",1);
     p.setIntUniform("uOutputColorTransfer",C.COLOR_TRANSFER_GAMMA_2_2);
    }
    p.bindAttributesAndUniforms();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    GlUtil.checkGlError();
    long time=ns>=0?ns:ns==VideoFrameProcessor.RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME?pts*1000:System.nanoTime();
    EGLExt.eglPresentationTimeANDROID(display,window,time);
    if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("SDR surface swap failed");
    submitCounter.run();
   }
   latched=false;pending.set(false);
   if(ended)notifyEnded();
  });
 }
 private void notifyEnded(){long timestamp=lastPts;int token=generation;callback.execute(()->{if(!released&&token==generation)listener.onEnded(timestamp);});}
 @Override public boolean queueInputBitmap(int i,Bitmap b,TimestampIterator t){return false;}
 @Override public boolean queueInputTexture(int i,int id,long pts){return false;}
 @Override public void redraw(){}
 @Override public boolean hasProducedFrameWithTimestampZero(){return zero;}
 @Override public void flush(){
  if(released||failed)return;
  generation++;
  try{sync(()->{latched=false;pending.set(false);ended=false;lastPts=C.TIME_UNSET;});}catch(Exception e){error(e);}
 }
 @Override public void signalEndOfInput(int i){post(()->{ended=true;if(!pending.get())notifyEnded();});}
 @Override public void release(){
  if(released)return;released=true;ready=false;pending.set(false);
  if(gl!=null)try{sync(()->{
   if(display!=null&&context!=null&&placeholder!=null)focusPlaceholder();
   if(input!=null)input.release();if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();}
   if(hdrProgram!=null)hdrProgram.delete();if(sdrProgram!=null)sdrProgram.delete();
   if(tex!=0)GlUtil.deleteTexture(tex);
   if(window!=null)GlUtil.destroyEglSurface(display,window);
   if(placeholder!=null)GlUtil.destroyEglSurface(display,placeholder);
   if(display!=null&&context!=null)GlUtil.destroyEglContext(display,context);
  });}catch(Exception e){android.util.Log.w("SharkPlayback","Direct SDR cleanup",e);}finally{thread.quitSafely();}
 }
}
