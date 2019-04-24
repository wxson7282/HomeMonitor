package com.wxson.homemonitor.monitor.mediacodec

interface IFirstByteBufferListener {
    fun onFirstByteBufferArrived(csd: ByteArray)
}