package com.wxson.homemonitor.camera.connect

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.wxson.homemonitor.camera.R
import com.wxson.homemonitor.commlib.LocalException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

private const val ACTION_TCP = "com.wxson.homemonitor.camera.connect.action.TCP"
var IsTcpSocketServiceOn = false  //switch for TcpSocketService ON/OFF
private lateinit var StringTransferListener: IStringTransferListener

/** * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
class CameraIntentService : IntentService("CameraIntentService") {

    private val TAG = "CameraIntentService"

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_TCP -> {
                handleActionTcp()
            }
        }
    }

    /**
     * Handle action TCP in the provided background thread with the provided
     * parameters.
     * There are tow methods to stop handleActionTcp
     *  1. SocketTimeoutException
     *      no clientSocket accepted from client until timeout
     *  2. IsTcpSocketServiceOn controlled by stopTcpSocketService()
     *      implement by outside
     */
    private fun handleActionTcp() {
        var clientSocket: Socket? = null
        var serverSocket: ServerSocket? = null
        var tcpSocketServiceStatus = ""
        try {
            serverSocket = ServerSocket(resources.getInteger(R.integer.ServerSocketPort))
//            serverSocket.soTimeout = resources.getInteger(R.integer.ServerSocketTimeout)  //sets timeout for accept()
            Log.i(TAG, "handleActionTcp: create ServerSocket")
            while (IsTcpSocketServiceOn){
                Log.i(TAG, "handleActionTcp: while IsTcpSocketServiceOn")
                StringTransferListener.onMsgTransfer("TcpSocketServiceStatus", "ON")
                clientSocket = serverSocket.accept()   //blocks until a connection is made
                Log.i(TAG, "client IP address: " + clientSocket.inetAddress.hostAddress)
                Thread(ServerThread(clientSocket)).start()
            }
        } catch (e: SocketTimeoutException) {
            Log.i(TAG, "SocketTimeoutException")    //ServerSocket.accept timeout
            tcpSocketServiceStatus = "TIMEOUT"
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
//                    objectOutputStream?.close()
//                    localSocket?.close()
                }
                catch (e:IOException){
                    Log.i(TAG, "stopTcpSocketService: IOException")
//                    e.printStackTrace()
                }
            }
        }.start()
        IsTcpSocketServiceOn = false
    }

    fun setStringTransferListener(stringTransferListener: IStringTransferListener) {
        StringTransferListener = stringTransferListener
    }
}

class ServerThread(private var clientSocket: Socket) : Runnable {

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
        }
        return null
    }
}
