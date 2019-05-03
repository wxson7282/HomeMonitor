package com.wxson.homemonitor.camera.connect

import android.annotation.SuppressLint
import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.wxson.homemonitor.camera.R
import com.wxson.homemonitor.commlib.ByteBufferTransfer
import com.wxson.homemonitor.commlib.LocalException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket

private const val ACTION_TCP = "com.wxson.homemonitor.camera.connect.action.TCP"
var IsTcpSocketServiceOn = false  //switch for TcpSocketService ON/OFF
private lateinit var StringTransferListener: IStringTransferListener

/** * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
class CameraIntentService : IntentService("CameraIntentService") {
    private val TAG = "CameraIntentService"
    lateinit var outputThread: ServerOutputThread

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_TCP -> {
                handleActionTcp()
            }
        }
    }

    /**
     * Handle action TCP in the provided background thread with the provided
     */
    private fun handleActionTcp() {
        var clientSocket: Socket? = null
        var serverSocket: ServerSocket? = null
        var tcpSocketServiceStatus: String
        try {
            serverSocket = ServerSocket(resources.getInteger(R.integer.ServerSocketPort))
            Log.i(TAG, "handleActionTcp: create ServerSocket")
            while (IsTcpSocketServiceOn){
                Log.i(TAG, "handleActionTcp: while IsTcpSocketServiceOn")
                StringTransferListener.onMsgTransfer("TcpSocketServiceStatus", "ON")
                clientSocket = serverSocket.accept()   //blocks until a connection is made
                Log.i(TAG, "client IP address: " + clientSocket.inetAddress.hostAddress)
                StringTransferListener.onMsgTransfer("TcpSocketClientStatus", "ON")
                Thread(ServerInputThread(clientSocket)).start()     // Input thread
                outputThread = ServerOutputThread(clientSocket)
                Thread(outputThread).start()    // Output thread
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw LocalException("I/O Exception")
        } catch (e: Exception) {
            e.printStackTrace()
            throw LocalException("Undefined Exception")
        } finally {
            clientSocket?.close()
            serverSocket?.close()
            IsTcpSocketServiceOn = false
            tcpSocketServiceStatus = "OFF"
            StringTransferListener.onMsgTransfer("TcpSocketServiceStatus", tcpSocketServiceStatus)
        }
    }

    companion object {
        /**
         * Starts this service to perform action Tcp. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionTcp(context: Context) {
            val intent = Intent(context, CameraIntentService::class.java).apply {
                action = ACTION_TCP
                IsTcpSocketServiceOn = true
            }
            context.startService(intent)
        }
    }

    inner class MyBinder: Binder(){
        val cameraIntentService: CameraIntentService = this@CameraIntentService
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    private var localSocket: Socket? = null
    private var objectOutputStream: ObjectOutputStream? = null
    /**
     *  sends message to local host for stopping service
     */
    fun stopTcpSocketService() {
        object :Thread(){
            override fun run() {
                try{
                    localSocket = Socket("127.0.0.1", 30000)
                    objectOutputStream =  ObjectOutputStream(localSocket?.outputStream)
                    objectOutputStream?.writeObject("StopTcpSocketService".toByteArray())
                }
                catch (e:IOException){
                    Log.i(TAG, "stopTcpSocketService: IOException")
                }
            }
        }.start()
        IsTcpSocketServiceOn = false
    }

    fun setStringTransferListener(stringTransferListener: IStringTransferListener) {
        StringTransferListener = stringTransferListener
    }
}

class ServerInputThread(private var clientSocket: Socket) : Runnable {

    private val TAG = this.javaClass.simpleName

    // 该线程所处理的Socket所对应的输入流
    private val objectInputStream: ObjectInputStream = ObjectInputStream(clientSocket.getInputStream())

    override fun run() {
        var inputObject = readObjectFromClient()
        // 采用循环不断从Socket中读取客户端发送过来的数据
        while (inputObject != null && IsTcpSocketServiceOn){
            when (inputObject.javaClass.simpleName){
                "byte[]" ->{
                    Log.i(TAG, "byte[] class received")
                    val arrivedString = String(inputObject as ByteArray)
                    Log.i(TAG, "$arrivedString received")
                    // triggers listener
                    StringTransferListener.onStringArrived(arrivedString, clientSocket.inetAddress)
                }
                else ->{
                    Log.i(TAG, "other class received")
                }
            }
            inputObject = readObjectFromClient()
        }
    }

    // 定义读取客户端数据的方法
    private fun readObjectFromClient(): Any? {
        try{
            return objectInputStream.readObject()
        }
        // 如果捕捉到异常，表明该Socket对应的客户端已经关闭
        catch (e: IOException){
            Log.i(TAG, "readObjectFromClient: socket is closed")
            StringTransferListener.onMsgTransfer("TcpSocketClientStatus", "OFF")
        }
        return null
    }
}

/**
 * The thread for sending image data from MediaCodec.Buffer
 * Output timing is onOutputBufferAvailable
 */
class ServerOutputThread(private var clientSocket: Socket) : Runnable {
    private val TAG = this.javaClass.simpleName
    private val objectOutputStream: ObjectOutputStream = ObjectOutputStream(clientSocket.getOutputStream())
    // 定义接收外部线程的消息的Handler对象
    var handler = Handler(Handler.Callback { false })

    var frameCount: Long = 0

    override fun run() {
        while (true) {
            try {
                Looper.prepare()
                handler = @SuppressLint("HandlerLeak") object : Handler(){
                    override fun handleMessage(msg: Message) {
                        // ByteBuffer data
                        if (msg.what == 0x333){
                            writeObjectToClient(msg.obj)
//                            frameCount++
//                            writeObjectToClient(frameCount.toString().toByteArray())
//                            Log.e(TAG, "frameCount=$frameCount")
                        }
                    }
                }
                Looper.loop()
            }
            catch (e: InterruptedException) {
                Log.e(TAG, "ServerOutputThread InterruptedException")
            }
        }
    }

    private fun writeObjectToClient(obj: Any){
        try {
            objectOutputStream.writeObject(obj)
            val testObject: ByteBufferTransfer = obj as ByteBufferTransfer
            Log.e(TAG, "writeObjectToClient ByteBufferTransfer time= ${testObject.bufferInfoPresentationTimeUs} size= ${testObject.bufferInfoSize}")
        }
        catch (e: IOException){
            Log.i(TAG, "writeObjectToClient: socket is closed")
            StringTransferListener.onMsgTransfer("TcpSocketClientStatus", "OFF")
        }
    }

}

