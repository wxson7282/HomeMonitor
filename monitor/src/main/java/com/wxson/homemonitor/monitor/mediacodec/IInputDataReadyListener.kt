package com.wxson.homemonitor.monitor.mediacodec

import android.media.MediaCodec

interface IInputDataReadyListener {
    fun onInputDataReady(inputData: ByteArray, bufferInfo: MediaCodec.BufferInfo)
}