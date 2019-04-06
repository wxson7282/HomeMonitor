package com.wxson.homemonitor.camera.connect

import java.net.InetAddress

interface IStringTransferListener {
    fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress)
    fun onMsgTransfer(msgType: String, msg: String)
}