package com.wxson.homemonitor.monitor.mediacodec

interface IFirstByteBufferListener {
    fun onFirstByteBufferArrived(csd: ByteArray, mime: String, size: String)
}