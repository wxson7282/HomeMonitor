package com.wxson.homemonitor.monitor.connect

import android.content.Context
import android.os.Message
import android.util.Log
import com.wxson.homemonitor.monitor.R

class HeartBeatThread(context: Context, private val clientThread: ClientThread) : Runnable {
    private val TAG = this.javaClass.simpleName
    private val res = context.resources
    var isHeartBeatOn: Boolean = true

    override fun run() {
        Log.i(TAG, "run")
        val heartBeatInterval = res.getInteger(R.integer.HeartBeatInterval)
        while (isHeartBeatOn) {
            for (i in 0..heartBeatInterval) {
                if (isHeartBeatOn)
                    Thread.sleep(1000)
                else
                    break
            }
            if (isHeartBeatOn){
                val msg = Message()
                msg.what = 0x345
                msg.obj = "Heart Beat"
                clientThread.revHandler.sendMessage(msg)    // output to server
                clientThread.sendLocalMsg("Send Heart Beat") //output to local
            }
        }
    }
}