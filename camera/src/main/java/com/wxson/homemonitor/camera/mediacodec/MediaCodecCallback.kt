package com.wxson.homemonitor.camera.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.wxson.homemonitor.camera.MainViewModel
import com.wxson.homemonitor.commlib.AvcUtils.GetCsd
import com.wxson.homemonitor.commlib.ByteBufferTransfer
import com.wxson.homemonitor.commlib.ByteBufferTransferTask
import com.wxson.homemonitor.commlib.IConnectStatusListener

class MediaCodecCallback(byteBufferTransfer: ByteBufferTransfer, PORT: Int, mainViewModel: MainViewModel) {
    private val TAG = this.javaClass.simpleName
    private var byteBufferTransferTask: ByteBufferTransferTask
    private var isPreTaskCompleted: Boolean = false
    private var port: Int = 0
    private var isClientConnected = false

    private val MediaCodecCallback = object : MediaCodec.Callback() {
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

            //如果前一帧传输任务完成
            if (isPreTaskCompleted) {
                //获取客户端地址
                val mInetAddress = ByteBufferTransferTask.getInetAddress()
                // 如果已经获得客户端地址
                if (mInetAddress != null) {
                    //取得ByteBuffer
                    if (outputBuffer != null && isClientConnected) {
                        //启动帧数据传输ByteBufferTransferTask
                        Log.i(TAG, "onOutputBufferAvailable 客户端地址：" + mInetAddress.hostAddress)
                        //从outputBuffer中取出byte[]
                        val bytes = ByteArray(outputBuffer.remaining())
                        outputBuffer.get(bytes)
                        byteBufferTransfer.byteArray = bytes
                        byteBufferTransfer.bufferInfoFlags = bufferInfo.flags
                        byteBufferTransfer.bufferInfoOffset = bufferInfo.offset
                        byteBufferTransfer.bufferInfoPresentationTimeUs = bufferInfo.presentationTimeUs
                        byteBufferTransfer.bufferInfoSize = bufferInfo.size
                        //AsyncTask实例只能运行一次
                        byteBufferTransferTask = ByteBufferTransferTask(port)
                        //定义AsyncTask完成监听器
                        val taskCompletedListener = ByteBufferTransferTask.TaskCompletedListener { isPreTaskCompleted = true }
                        byteBufferTransferTask.setTaskCompletedListener(taskCompletedListener)
                        byteBufferTransferTask.setByteBufferTransfer(byteBufferTransfer)
                        byteBufferTransferTask.execute(mInetAddress.hostAddress)
                        isPreTaskCompleted = false  //任务完成标志
                        Log.i(TAG, "onOutputBufferAvailable byteBufferTransferTask.execute")
                    } else {
                        Log.e(TAG, "onOutputBufferAvailable outputBuffer：null")
                    }
                } else {
                    Log.e(TAG, "onOutputBufferAvailable 客户端地址：null")
                }
            } else {
                Log.i(TAG, "onOutputBufferAvailable PreTaskNotCompleted")
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
        port = PORT
        byteBufferTransferTask = ByteBufferTransferTask(port)
        isPreTaskCompleted = true
        //连接状态监听器
        val connectStatusListener = object  : IConnectStatusListener{
            override fun onConnectStatusChanged(connected: Boolean) {
                isClientConnected = connected
            }
        }
        mainViewModel.setConnectStatusListener(connectStatusListener)
    }

    fun getCallback(): MediaCodec.Callback {
        return MediaCodecCallback
    }
}