package com.wxson.homemonitor.monitor.connect

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.wxson.homemonitor.monitor.R
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

class ClientThread(private val handler: Handler, context: Context) : Runnable {

    private val TAG = this.javaClass.simpleName
    private val res = context.resources
    private var socket: Socket? = null
    // 定义接收UI线程的消息的Handler对象
    var revHandler = Handler(Handler.Callback { false })

    // 该线程所处理的Socket所对应的输出流
    private var outputStream: OutputStream? = null
    private var objectOutputStream: ObjectOutputStream? = null

    override fun run() {
        Log.i(TAG, "run")
        try{
//            socket = Socket("192.168.31.63", 30000) //MiNote address
            socket = Socket(res.getString(R.string.server_ip_address), res.getInteger(R.integer.ServerSocketPort))
            outputStream = socket?.outputStream
            objectOutputStream = if (outputStream != null) ObjectOutputStream(outputStream) else null
            // 启动一条子线程来读取服务器响应的数据
            object : Thread(){
                override fun run() {
                    val inputStream = socket?.getInputStream()
                    val objectInputStream = if (inputStream != null) ObjectInputStream(inputStream) else null
                    var inputObject = objectInputStream?.readObject()
                    // 不断读取Socket输入流中的内容
                    while (inputObject != null){
                        // 每当读到来自服务器的数据之后，发送消息通知  程序界面显示该数据
                        val msg = Message()
                        msg.what = 0x123
                        msg.obj = inputObject
                        handler.sendMessage(msg)
                        // read next from socket
                        inputObject = objectInputStream?.readObject()
                    }
                }
            }.start()
            // 为当前线程初始化Looper
            Looper.prepare()
            // 创建revHandler对象
            revHandler = @SuppressLint("HandlerLeak") object : Handler(){
                override fun handleMessage(msg: Message) {
                    if (msg.what == 0x345){
                        // 将用户的文字信息写入网络
                        objectOutputStream?.writeObject((msg.obj.toString()).toByteArray())
                        Log.i(TAG, "handleMessage " + msg.obj.toString())
                    }
                }
            }
            // 启动Looper
            Looper.loop()
        }
        catch(e: NoRouteToHostException){
            Log.e(TAG, "服务器连接失败！！")
            //把失败信息向上传递
            val msg = Message()
            msg.what = 0x124
            msg.obj = "服务器连接失败！！"
            handler.sendMessage(msg)
        }
        catch (e1: SocketTimeoutException){
            e1.printStackTrace()
            Log.e(TAG, "网络连接超时！！")
        }
        catch (e: ConnectException){
            Log.e(TAG, "服务器未启动！！")
            //把失败信息向上传递
            val msg = Message()
            msg.what = 0x124
            msg.obj = "服务器未启动！！"
            handler.sendMessage(msg)
        }
        catch (e: IOException){
            e.printStackTrace()
            System.exit(1)
        }
        catch(e:Exception){
            e.printStackTrace()
            System.exit(1)
        }

    }
}