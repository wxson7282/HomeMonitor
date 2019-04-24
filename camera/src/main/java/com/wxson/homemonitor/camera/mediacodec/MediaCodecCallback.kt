package com.wxson.homemonitor.camera.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.wxson.homemonitor.camera.MainViewModel
import com.wxson.homemonitor.commlib.AvcUtils.GetCsd
import com.wxson.homemonitor.commlib.ByteBufferTransfer
import com.wxson.homemonitor.commlib.IConnectStatusListener

class MediaCodecCallback(byteBufferTransfer: ByteBufferTransfer, mainViewModel: MainViewModel) {
    private val TAG = this.javaClass.simpleName
    private var isClientConnected = false
    //to inform MainViewModel of being onOutputBufferAvailable in MediaCodecCallback
    private lateinit var byteBufferListener: IByteBufferListener

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, i: Int) {
            Log.i(TAG, "onInputBufferAvailable")
        }

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            Log.i(TAG, "onOutputBufferAvailable")
            //region for debug only
            //取得ByteBuffer
            val outputBuffer = mediaCodec.getOutputBuffer(index)

            val csd: ByteArray?
            if (outputBuffer != null) {
                csd = GetCsd(outputBuffer)
                if (csd != null) {
                    byteBufferTransfer.setCsd(csd)
                }
            }
            //endregion

            //取得ByteBuffer
            if (outputBuffer != null && isClientConnected) {
                //启动帧数据传输
                Log.i(TAG, "onOutputBufferAvailable  start to send byteBufferTransfer")
                //从outputBuffer中取出byte[]
                val bytes = ByteArray(outputBuffer!!.remaining())
                outputBuffer!!.get(bytes)
                byteBufferTransfer.byteArray = bytes
                byteBufferTransfer.bufferInfoFlags = bufferInfo.flags
                byteBufferTransfer.bufferInfoOffset = bufferInfo.offset
                byteBufferTransfer.bufferInfoPresentationTimeUs = bufferInfo.presentationTimeUs
                byteBufferTransfer.bufferInfoSize = bufferInfo.size

                // send byteBufferTransfer to MainViewModel -> CameraIntentService
                byteBufferListener.onByteBufferReady(byteBufferTransfer)

                Log.i(TAG, "onOutputBufferAvailable finish to send byteBufferTransfer")
            }
//            else {
//                Log.e(TAG, "onOutputBufferAvailable outputBuffer：null")
//            }
            mediaCodec.releaseOutputBuffer(index, false)
        }

        override fun onError(mediaCodec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "onError")
        }

        override fun onOutputFormatChanged(mediaCodec: MediaCodec, mediaFormat: MediaFormat) {
            Log.i(TAG, "onOutputFormatChanged")
        }
    }

    /**
     * primary constructor
     */
    init{
        //连接状态监听器
        val connectStatusListener = object  : IConnectStatusListener{
            override fun onConnectStatusChanged(connected: Boolean) {
                isClientConnected = connected
            }
        }
        mainViewModel.setConnectStatusListener(connectStatusListener)
    }

    fun getCallback(): MediaCodec.Callback {
        return mediaCodecCallback
    }

    fun setByteBufferListener(listener: IByteBufferListener){
        byteBufferListener = listener
    }

}