package com.wxson.homemonitor.camera.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.wxson.homemonitor.camera.CameraViewModel
import com.wxson.homemonitor.commlib.AvcUtils.GetCsd
import com.wxson.homemonitor.commlib.ByteBufferTransfer
import com.wxson.homemonitor.commlib.IConnectStatusListener
import com.wxson.homemonitor.commlib.ITransmitInstructionListener

class CodecCallback(val mime: String, val size: String, mainViewModel: CameraViewModel) {
    private val TAG = this.javaClass.simpleName
    private var isClientConnected = false
    private var isTransmitOn = true
    //to inform MainViewModel of being onOutputBufferAvailable in MediaCodecCallback
    private lateinit var byteBufferListener: IByteBufferListener
    private var firstFrameCsd: ByteArray? = null

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, i: Int) {
            Log.i(TAG, "onInputBufferAvailable")
        }

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
//            Log.i(TAG, "onOutputBufferAvailable")
            //取得outputBuffer
            val outputBuffer = mediaCodec.getOutputBuffer(index)
            val byteBufferTransfer = ByteBufferTransfer()
            val csd: ByteArray?
            if (outputBuffer != null) {
                csd = GetCsd(outputBuffer)
                if (csd != null) {
                    // in first frame video data
                    firstFrameCsd = csd
                }
                byteBufferTransfer.csd = firstFrameCsd
                byteBufferTransfer.mime = mime.toByteArray()
                byteBufferTransfer.size = size.toByteArray()
            }

            //设置byteBufferTransfer
            if (outputBuffer != null && isClientConnected && isTransmitOn) {
                //启动帧数据传输
//                Log.i(TAG, "onOutputBufferAvailable  start to send byteBufferTransfer")
                //从outputBuffer中取出byte[]
                val bytes = ByteArray(outputBuffer.remaining())
                outputBuffer.get(bytes)
                byteBufferTransfer.byteArray = bytes
                byteBufferTransfer.bufferInfoFlags = bufferInfo.flags
                byteBufferTransfer.bufferInfoOffset = bufferInfo.offset
                byteBufferTransfer.bufferInfoPresentationTimeUs = bufferInfo.presentationTimeUs
                byteBufferTransfer.bufferInfoSize = bufferInfo.size
                // send byteBufferTransfer to MainViewModel -> CameraIntentService
                byteBufferListener.onByteBufferReady(byteBufferTransfer)
//                Log.i(TAG, "onOutputBufferAvailable finish to send byteBufferTransfer")
            }
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
        //视频传送指令监听器
        val transmitInstructionListener = object : ITransmitInstructionListener{
            override fun onTransmitInstructionArrived(transmitOn: Boolean) {
                isTransmitOn = transmitOn
            }
        }
        mainViewModel.setTransmitInstructionListener(transmitInstructionListener)
    }

    fun getCallback(): MediaCodec.Callback {
        return mediaCodecCallback
    }

    fun setByteBufferListener(listener: IByteBufferListener){
        byteBufferListener = listener
    }

}