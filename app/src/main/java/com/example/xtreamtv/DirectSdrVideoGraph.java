package com.example.xtreamtv;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES30;
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
 private final Runnable inputCounter,submitCounter,dropCounter,slotAvailable;
 private final java.util.function.LongConsumer latchTiming,drawTiming,swapTiming,lateTiming,queueTiming,targetTiming,submissionTiming;
 private final java.util.function.Consumer<String> shaderName,graphicsInfo;
 private final java.util.function.LongConsumer drawCallTiming,presentationCallTiming;
 // Sparse, non-blocking fence observations: not a GPU execution timer or scanout timestamp.
 private long sampledFence,fenceIssuedNs,fenceSamples,fenceAtSwap,fenceAtNextFrame,fenceStillPending,fenceFailed,fenceUpperTotal,fenceUpperMax;
 private int sampleCounter,drawsUntilErrorCheck;
 private static final int DRAW_ERROR_CHECK_INTERVAL=60;
 private boolean fenceDisabled;
 private String gpuDescription="Not initialized";
 private final AtomicBoolean pending=new AtomicBoolean();
 private final HandlerThread thread=new HandlerThread("SharkDirectSDR",android.os.Process.THREAD_PRIORITY_DISPLAY);
 private Handler gl;
 private EGLDisplay display;
 private EGLContext context;
 private EGLSurface placeholder,window;
 private SurfaceTexture texture;
 private Surface input;
 private GlProgram hdrProgram,pqProgram,sdrProgram,boundProgram;
 private boolean pqSpecializationUnavailable;
 private boolean drawStateDirty=true,needsClear=true;
 private int textureMatrixLocation=-1;
 private final float[] boundTextureMatrix=new float[16];
 private int tex,width=1,height=1,transfer=C.COLOR_TRANSFER_SDR;
 private float pixelAspect=1;
 private boolean fullRange,latched,ended,windowFocused;
 private long pts,offset,lastPts=C.TIME_UNSET;
 private final float[] texMatrix=new float[16],position=new float[16],identity=new float[16];
 private volatile boolean ready,released,failed,zero,configured;
 private volatile int generation;
 private SurfaceInfo output;
 DirectSdrVideoGraph(Listener l,Executor cb,Runnable in,Runnable out,Runnable drop,java.util.function.LongConsumer latchTiming,java.util.function.LongConsumer drawTiming,java.util.function.LongConsumer swapTiming,java.util.function.LongConsumer lateTiming,java.util.function.LongConsumer queueTiming,java.util.function.LongConsumer targetTiming,java.util.function.LongConsumer submissionTiming,java.util.function.Consumer<String> shaderName,Runnable slotAvailable,java.util.function.LongConsumer drawCallTiming,java.util.function.LongConsumer presentationCallTiming,java.util.function.Consumer<String> graphicsInfo){
  listener=l;callback=cb;inputCounter=in;submitCounter=out;dropCounter=drop;this.latchTiming=latchTiming;this.drawTiming=drawTiming;this.swapTiming=swapTiming;this.lateTiming=lateTiming;this.queueTiming=queueTiming;this.targetTiming=targetTiming;this.submissionTiming=submissionTiming;this.shaderName=shaderName;this.slotAvailable=slotAvailable;this.drawCallTiming=drawCallTiming;this.presentationCallTiming=presentationCallTiming;this.graphicsInfo=graphicsInfo;Matrix.setIdentityM(identity,0);
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
 private void focusPlaceholder()throws Exception{GlUtil.focusEglSurface(display,context,placeholder,1,1);windowFocused=false;drawStateDirty=true;}
 private void focusWindow()throws Exception{if(!windowFocused){GlUtil.focusEglSurface(display,context,window,output.width,output.height);windowFocused=true;}}
 @Override public void initialize()throws VideoFrameProcessingException{
  thread.start();gl=new Handler(thread.getLooper());
  try{sync(()->{
   display=GlUtil.getDefaultEglDisplay();
   context=GlUtil.createEglContext(EGL14.EGL_NO_CONTEXT,display,3,GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
   placeholder=GlUtil.createFocusedPlaceholderEglSurface(context,display);
   tex=GlUtil.createExternalTexture();texture=new SurfaceTexture(tex);input=new Surface(texture);
   texture.setOnFrameAvailableListener(t->onFrame(),gl);
   gpuDescription=GLES20.glGetString(GLES20.GL_RENDERER);
   graphicsInfo.accept("GPU: "+gpuDescription+"; fence sampling pending; panel timing unavailable");
   ready=true;
  });}catch(Exception e){release();throw new VideoFrameProcessingException(e);}
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
   discardFence();
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
    if(transfer==C.COLOR_TRANSFER_ST2084&&pqProgram==null&&!pqSpecializationUnavailable){
     try{
      pqProgram=new GlProgram(DirectSdrShaders.VERTEX,DirectSdrShaders.pqToSdr());
      pqProgram.setBufferAttribute("aFramePosition",QUAD,4);
     }catch(Exception e){
      if(pqProgram!=null){try{pqProgram.delete();}catch(Exception ignored){}pqProgram=null;}
      pqSpecializationUnavailable=true;
      android.util.Log.w("SharkPlayback","PQ specialization unavailable; using standard HDR shader",e);
     }
    }
    if((transfer!=C.COLOR_TRANSFER_ST2084||pqProgram==null)&&hdrProgram==null){hdrProgram=new GlProgram(DirectSdrShaders.VERTEX,DirectSdrShaders.HDR);hdrProgram.setBufferAttribute("aFramePosition",QUAD,4);}
   }else if(sdrProgram==null){sdrProgram=new GlProgram(DirectSdrShaders.VERTEX,DirectSdrShaders.SDR);sdrProgram.setBufferAttribute("aFramePosition",QUAD,4);}
   shaderName.accept(transfer==C.COLOR_TRANSFER_ST2084&&pqProgram!=null?"PQ specialized":hdr?"Standard HDR":"SDR pass-through");
   drawStateDirty=true;
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
 private void discardFence(){
  if(sampledFence!=0){GLES30.glDeleteSync(sampledFence);sampledFence=0;}
 }
 private void publishFence(){
  long observed=fenceAtSwap+fenceAtNextFrame;
  graphicsInfo.accept(String.format(java.util.Locale.US,"GPU: %s; fence samples %d; signaled at swap %d, by next latch %d, still pending %d, failed %d; observed completion upper bound %s; panel timing unavailable",gpuDescription,fenceSamples,fenceAtSwap,fenceAtNextFrame,fenceStillPending,fenceFailed,observed==0?"n/a":String.format(java.util.Locale.US,"%.2f ms avg / %.2f max",fenceUpperTotal/1e6/observed,fenceUpperMax/1e6)));
 }
 private void disableFence(){
  fenceFailed++;fenceDisabled=true;discardFence();publishFence();
 }
 private void startFenceSample(){
  if(fenceDisabled||sampledFence!=0||++sampleCounter%120!=0)return;
  fenceSamples++;
  try{
   fenceIssuedNs=System.nanoTime();
   sampledFence=GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,0);
   if(sampledFence==0){GLES20.glGetError();disableFence();}
  }catch(RuntimeException e){disableFence();}
 }
 private void pollFence(boolean afterSwap){
  if(sampledFence==0)return;
  try{
   int status=GLES30.glClientWaitSync(sampledFence,0,0);
   if(status==GLES30.GL_ALREADY_SIGNALED||status==GLES30.GL_CONDITION_SATISFIED){
    long elapsed=Math.max(0,System.nanoTime()-fenceIssuedNs);
    if(afterSwap)fenceAtSwap++;else fenceAtNextFrame++;
    fenceUpperTotal+=elapsed;fenceUpperMax=Math.max(fenceUpperMax,elapsed);
    discardFence();publishFence();
   }else if(status==GLES30.GL_TIMEOUT_EXPIRED){
    if(!afterSwap){fenceStillPending++;discardFence();publishFence();}
   }else{GLES20.glGetError();disableFence();}
  }catch(RuntimeException e){disableFence();}
 }
 private void onFrame(){
  if(released||failed)return;
  try{
   // The decoder texture and window share this GL context. Keep the current
   // draw surface instead of rebinding EGL twice per video frame.
   if(latched)throw new IllegalStateException("Unexpected extra decoder frame");
   pollFence(false);
   long begin=System.nanoTime();texture.updateTexImage();latchTiming.accept(System.nanoTime()-begin);
   if(!pending.get())return;
   texture.getTransformMatrix(texMatrix);pts=texture.getTimestamp()/1000+offset;
   latched=true;if(pts==0)zero=true;lastPts=pts;
   final long timestamp=pts;final int token=generation;
   callback.execute(()->{if(!released&&!failed&&token==generation)listener.onOutputFrameAvailableForRendering(timestamp,false);});
  }catch(Exception e){error(e);}
 }
 @Override public void renderOutputFrame(long ns){
  final int token=generation;
  final long queuedAt=System.nanoTime();
  post(()->{
   if(token!=generation)return;
   if(!latched){throw new IllegalStateException("No latched decoder frame");}
   if(ns==VideoFrameProcessor.DROP_OUTPUT_FRAME){dropCounter.run();}
   else if(window!=null&&output!=null){
    long drawStart=System.nanoTime();queueTiming.accept(drawStart-queuedAt);focusWindow();
    boolean hdr=transfer==C.COLOR_TRANSFER_ST2084||transfer==C.COLOR_TRANSFER_HLG;
    boolean specialized=transfer==C.COLOR_TRANSFER_ST2084&&pqProgram!=null;
    GlProgram p=hdr?(specialized?pqProgram:hdrProgram):sdrProgram;
    if(drawStateDirty||boundProgram!=p){
     drawsUntilErrorCheck=0;
     Matrix.setIdentityM(position,0);
     float src=width*pixelAspect/height,dst=(float)output.width/output.height;
     if(output.orientationDegrees==90||output.orientationDegrees==270)src=1/src;
     float sx=Math.min(1,src/dst),sy=Math.min(1,dst/src);
     Matrix.scaleM(position,0,sx,sy,1);
     Matrix.rotateM(position,0,output.orientationDegrees,0,0,1);
     needsClear=sx<1f||sy<1f||output.orientationDegrees!=0;
     p.use();
     p.setSamplerTexIdUniform("uTexSampler",tex,0);
     p.setFloatsUniform("uTexTransformationMatrix",texMatrix);
     p.setFloatsUniform("uTransformationMatrix",position);
     if(hdr){
      p.setFloatsUniform("uRgbMatrix",identity);
      p.setFloatsUniform("uYuvToRgbColorTransform",fullRange?FULL:LIMITED);
      if(!specialized){
       p.setIntUniform("uInputColorTransfer",transfer);
       p.setIntUniform("uApplyHdrToSdrToneMapping",1);
       p.setIntUniform("uOutputColorTransfer",C.COLOR_TRANSFER_GAMMA_2_2);
      }
     }
     p.bindAttributesAndUniforms();
     textureMatrixLocation=p.getUniformLocation("uTexTransformationMatrix");
     System.arraycopy(texMatrix,0,boundTextureMatrix,0,16);
     boundProgram=p;drawStateDirty=false;
    }else{
     // updateTexImage implicitly binds the external texture in this private
     // context. The only sampler is unit zero, set during program binding.
     // No intervening operation changes the active texture or its binding.
     if(!java.util.Arrays.equals(texMatrix,boundTextureMatrix)){
      GLES20.glUniformMatrix4fv(textureMatrixLocation,1,false,texMatrix,0);
      System.arraycopy(texMatrix,0,boundTextureMatrix,0,16);
     }
    }
    if(needsClear){GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);}
    long commandStart=System.nanoTime();
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    // Keep immediate validation after state changes and periodic validation
    // during steady playback, avoiding a driver error query on every draw.
    if(drawsUntilErrorCheck==0){GlUtil.checkGlError();drawsUntilErrorCheck=DRAW_ERROR_CHECK_INTERVAL;}
    drawsUntilErrorCheck--;
    drawCallTiming.accept(System.nanoTime()-commandStart);
    startFenceSample();
    long time=ns>=0?ns:ns==VideoFrameProcessor.RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME?pts*1000:System.nanoTime();
    long presentStart=System.nanoTime();
    EGLExt.eglPresentationTimeANDROID(display,window,time);
    long swapStart=System.nanoTime();presentationCallTiming.accept(swapStart-presentStart);drawTiming.accept(swapStart-drawStart);
    if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("SDR surface swap failed");
    long finished=System.nanoTime();swapTiming.accept(finished-swapStart);if(ns>=0){lateTiming.accept(Math.max(0,finished-ns));targetTiming.accept(ns);}submissionTiming.accept(finished);submitCounter.run();pollFence(true);
   }
   latched=false;pending.set(false);
   if(ended)notifyEnded();
   else callback.execute(()->{if(!released&&!failed&&token==generation)slotAvailable.run();});
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
  try{sync(()->{discardFence();latched=false;pending.set(false);ended=false;lastPts=C.TIME_UNSET;});}catch(Exception e){error(e);}
 }
 @Override public void signalEndOfInput(int i){post(()->{ended=true;if(!pending.get())notifyEnded();});}
 @Override public void release(){
  if(released)return;released=true;ready=false;pending.set(false);
  if(gl!=null)try{sync(()->{
   if(display!=null&&context!=null&&placeholder!=null)focusPlaceholder();
   discardFence();
   if(input!=null)input.release();if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();}
   if(hdrProgram!=null)hdrProgram.delete();if(pqProgram!=null)pqProgram.delete();if(sdrProgram!=null)sdrProgram.delete();
   if(tex!=0)GlUtil.deleteTexture(tex);
   if(window!=null)GlUtil.destroyEglSurface(display,window);
   if(placeholder!=null)GlUtil.destroyEglSurface(display,placeholder);
   if(display!=null&&context!=null)GlUtil.destroyEglContext(display,context);
  });}catch(Exception e){android.util.Log.w("SharkPlayback","Direct SDR cleanup",e);}finally{thread.quitSafely();}
 }
}
