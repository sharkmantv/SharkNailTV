package com.example.xtreamtv;

import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.GlUtil;
import androidx.media3.effect.DefaultGlObjectsProvider;

/**
 * Used only by the SDR-output video graph, with no extra video effects and an
 * electrical SDR working color space. A full-size RGBA16F render target is
 * replaceable by RGB10_A2 because the sampler shader has already tone-mapped
 * and gamma-encoded/clamped its RGB output to [0,1] and emits opaque alpha.
 * Keep the original texture if the device cannot support the packed format.
 */
final class SdrPackedGlProvider implements GlObjectsProvider {
    private final DefaultGlObjectsProvider delegate=new DefaultGlObjectsProvider();
    private boolean logged;
    @Override public EGLContext createEglContext(EGLDisplay d,int v,int[] a)throws GlUtil.GlException{
        return delegate.createEglContext(d,v,a);
    }
    @Override public EGLSurface createEglSurface(EGLDisplay d,Object s,int t,boolean e)throws GlUtil.GlException{
        return delegate.createEglSurface(d,s,t,e);
    }
    @Override public EGLSurface createFocusedPlaceholderEglSurface(EGLContext c,EGLDisplay d)throws GlUtil.GlException{
        return delegate.createFocusedPlaceholderEglSurface(c,d);
    }
    @Override public GlTextureInfo createBuffersForTexture(int texId,int width,int height)throws GlUtil.GlException{
        int[] major=new int[1],minor=new int[1],previous=new int[1],format=new int[1],fbo=new int[1];
        boolean packed=false;
        GLES20.glGetIntegerv(GLES30.GL_MAJOR_VERSION,major,0);
        if(major[0]>=3)GLES20.glGetIntegerv(GLES30.GL_MINOR_VERSION,minor,0);
        if(width>=1920&&height>=1080&&(major[0]>3||(major[0]==3&&minor[0]>=1))){
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D,previous,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texId);
            GLES31.glGetTexLevelParameteriv(GLES20.GL_TEXTURE_2D,0,0x1003,format,0);
            if(GLES20.glGetError()==GLES20.GL_NO_ERROR&&format[0]==GLES30.GL_RGBA16F){
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES30.GL_RGB10_A2,width,height,0,GLES20.GL_RGBA,GLES30.GL_UNSIGNED_INT_2_10_10_10_REV,null);
                if(GLES20.glGetError()==GLES20.GL_NO_ERROR){
                    int[] oldFbo=new int[1];
                    GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,oldFbo,0);
                    GLES20.glGenFramebuffers(1,fbo,0);
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo[0]);
                    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texId,0);
                    packed=GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE;
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,oldFbo[0]);
                    GLES20.glDeleteFramebuffers(1,fbo,0);
                }
                if(!packed){
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES30.GL_RGBA16F,width,height,0,GLES20.GL_RGBA,GLES30.GL_HALF_FLOAT,null);
                    GlUtil.checkGlError();
                }
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previous[0]);
        }
        if(!logged||packed){android.util.Log.i("SharkPlayback","SDR intermediate "+width+"x"+height+" packed10="+packed);logged=true;}
        return delegate.createBuffersForTexture(texId,width,height);
    }
    @Override public void release(EGLDisplay d)throws GlUtil.GlException{delegate.release(d);}
}
