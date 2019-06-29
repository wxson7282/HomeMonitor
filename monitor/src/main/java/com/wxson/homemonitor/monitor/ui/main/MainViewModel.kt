package com.wxson.homemonitor.monitor.ui.main

import android.app.Application
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.homemonitor.monitor.connect.ClientThread
import com.wxson.homemonitor.monitor.connect.HeartBeatThread
import java.lang.ref.WeakReference


class MainViewModel(application: Application) : AndroidViewModel(application){

    private val TAG = this.javaClass.simpleName

    private val app = getApplication<Application>()
    private val myUncheckedExceptionHandler = MyUncheckedExceptionHandler()

    private var serverMsg = MutableLiveData<Any>()
    fun getServerMsg(): LiveData<Any> {
        return serverMsg
    }

    private var localMsg = MutableLiveData<String>()
    fun getLocalMsg(): LiveData<String>{
        return localMsg
    }

    private var unchekedException = MutableLiveData<String>()
    fun getUnchededException(): LiveData<String>{
        return unchekedException
    }

    private var handler: Handler
    private var clientThread: ClientThread

    class MyHandler(private var mainViewModel : WeakReference<MainViewModel>) : Handler(){
        private val TAG = this.javaClass.simpleName
        override fun handleMessage(msg: Message) {
            Log.i(TAG, "handleMessage")
            // 如果消息来自于子线程
            when (msg.what){
                0x123 -> mainViewModel.get()?.serverMsg!!.postValue(msg.obj)        // 将读取的server内容写入serverMsg中
                0x124 -> mainViewModel.get()?.localMsg!!.postValue(msg.obj.toString())
            }
        }
    }

    private var heartBeatThread: HeartBeatThread

    private val uncaughtExceptionListener = object : MyUncheckedExceptionHandler.IUncaughtExceptionListener{
        override fun onUnUncaughtException(exceptionName: String) {
            //如果从子线程收到EOFException，表明服务器已经关闭
            if (exceptionName == "EOFException"){
                localMsg.postValue("Disconnected")
            }
        }
    }

    init{
        Log.i(TAG, "init")
        myUncheckedExceptionHandler.setUncaughtExceptionListener(uncaughtExceptionListener)
        Thread.setDefaultUncaughtExceptionHandler(myUncheckedExceptionHandler)
        handler = MyHandler(WeakReference(this))
        clientThread = ClientThread(handler, app)
        Thread(clientThread).start()
        heartBeatThread = HeartBeatThread(app, clientThread)
    }

    fun sendMsgToServer(msgText: String){
        Log.i(TAG, "sendMsgToServer")
        val msg = Message()
        msg.what = 0x345
        msg.obj = msgText
        clientThread.revHandler.sendMessage(msg)
    }

    override fun onCleared() {
        Thread(clientThread).interrupt()
        clientThread.socket?.close()
        clientThread.socket = null
        super.onCleared()
    }

    fun setHeartBeat(enabled: Boolean){
        if (enabled){
            // to start heartBeatThread
            Log.i(TAG, "Heart Beat start")
            heartBeatThread.isHeartBeatOn = true
            Thread(heartBeatThread).start()
        }
        else{
            // to stop heartBeatThread
            Log.i(TAG, "Heart Beat stop")
            heartBeatThread.isHeartBeatOn = false
        }
    }
}
