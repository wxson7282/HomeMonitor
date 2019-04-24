package com.wxson.homemonitor.camera.mediacodec

import com.wxson.homemonitor.commlib.ByteBufferTransfer

interface IByteBufferListener {
    /**
     *  to inform MainViewModel onOutputBufferAvailable in MediaCodecCallback
     */
    fun onByteBufferReady(byteBufferTransfer: ByteBufferTransfer)
}