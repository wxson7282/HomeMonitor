package com.wxson.homemonitor.monitor.mediacodec

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.wxson.homemonitor.monitor.connect.ClientThread

class MonitorTextureView(private val mContext: Context, attrs: AttributeSet) : TextureView(mContext, attrs),
    TextureView.SurfaceTextureListener {
    private val TAG = this.javaClass.simpleName
    private var mediaCodec: MediaCodec? = null

    init {
        this.surfaceTextureListener = this
        this.rotation = 90f
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
        Log.i(TAG, "onSurfaceTextureAvailable...")
        //注册首帧数据监听器
        registerFirstByteBufferListener()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, i: Int, i1: Int) {
        Log.i(TAG, "onSurfaceTextureSizeChanged...")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.i(TAG, "onSurfaceTextureDestroyed...")
        //释放视频解码器
        MediaCodecAction.ReleaseDecoder(mediaCodec)
        return false
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
//        Log.i(TAG, "onSurfaceTextureUpdated...")
    }

    //注册首帧数据监听器
    private fun registerFirstByteBufferListener() {
        val firstByteBufferListener = object : IFirstByteBufferListener {
            override fun onFirstByteBufferArrived(csd: ByteArray, mime: String, size: String) {
                //准备解码器
                val surface = Surface(super@MonitorTextureView.getSurfaceTexture())
                mediaCodec = MediaCodecAction.PrepareDecoder(surface, csd, mime, size, mContext)
                //启动解码器
                MediaCodecAction.StartDecoder(mediaCodec)
                Log.i(TAG, "onFirstByteBufferArrived StartDecoder")
            }
        }
        ClientThread.firstByteBufferListener = firstByteBufferListener
    }

}
