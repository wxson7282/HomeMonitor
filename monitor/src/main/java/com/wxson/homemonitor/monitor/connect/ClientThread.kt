package com.wxson.homemonitor.monitor.connect

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import com.wxson.homemonitor.commlib.ByteBufferTransfer
import com.wxson.homemonitor.monitor.R
import com.wxson.homemonitor.monitor.mediacodec.IFirstByteBufferListener
import com.wxson.homemonitor.monitor.mediacodec.IInputDataReadyListener
import java.io.EOFException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.UnknownHostException


class ClientThread(private val handler: Handler, private val context: Context) : Runnable {

    private val TAG = this.javaClass.simpleName
    private val res = context.resources
    var socket: Socket? = null
    // 定义接收UI线程的消息的Handler对象
    var revHandler = Handler(Handler.Callback { false })

    // 该线程所处理的Socket所对应的输入出流
    private var objectOutputStream: ObjectOutputStream? = null
    private var objectInputStream: ObjectInputStream? = null
    private var firstByteBufferFlag: Int = 1


    companion object{
        @JvmStatic
        lateinit var firstByteBufferListener: IFirstByteBufferListener
        @JvmStatic
        lateinit var inputDataReadyListener: IInputDataReadyListener
    }

    override fun run() {
        Log.i(TAG, "run")
//        sendLocalMsg("Disconnected")
        // get server ip address
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val serverIp = sharedPreferences.getString("server_ip", "")
        try{
//            socket = Socket(res.getString(R.string.server_ip_address), res.getInteger(R.integer.ServerSocketPort))
            socket = Socket(serverIp, res.getInteger(R.integer.ServerSocketPort))
            sendLocalMsg("Connected")
            objectOutputStream = ObjectOutputStream(socket?.getOutputStream())
            objectInputStream = ObjectInputStream(socket?.getInputStream())
            // 启动一条子线程来读取服务器响应的数据
            object : Thread(){
                override fun run() {
                    var inputObject: Any? = objectInputStream?.readObject()
                    // 不断读取Socket输入流中的内容
                    while (inputObject != null){
//                        Log.e(TAG, "byteBufferTransfer time=${(inputObject as ByteBufferTransfer).bufferInfoPresentationTimeUs}")
                        when (inputObject.javaClass.simpleName){
                            // ByteBufferTransfer is received
                            "ByteBufferTransfer" -> {
//                                Log.i(TAG, "接收到ByteBufferTransfer类")
                                val byteBufferTransfer: ByteBufferTransfer = inputObject as ByteBufferTransfer
//                                Log.i(TAG, "byteBufferTransfer time=${byteBufferTransfer.bufferInfoPresentationTimeUs} size=${byteBufferTransfer.bufferInfoSize}")
                                // 接收到首个byteBufferTransfer既第一帧视频
                                if (firstByteBufferFlag == 1){
                                    firstByteBufferFlag++
                                    //触发FirstByteBufferListener，通知MonitorTextureView准备解码器
                                    firstByteBufferListener.onFirstByteBufferArrived(byteBufferTransfer.csd, String(byteBufferTransfer.mime), String(byteBufferTransfer.size))
                                }
                                //如果FirstByteBuffer已到 decode已经启动
                                if (firstByteBufferFlag > 1){
                                    //把ByteBuffer传给Decode
//                                    Log.i(TAG, "把ByteBuffer传给MediaCodec")
                                    val bufferInfo = MediaCodec.BufferInfo()
                                    bufferInfo.flags = byteBufferTransfer.bufferInfoFlags
                                    bufferInfo.offset = byteBufferTransfer.bufferInfoOffset
                                    bufferInfo.presentationTimeUs = byteBufferTransfer.bufferInfoPresentationTimeUs
                                    bufferInfo.size = byteBufferTransfer.bufferInfoSize
                                    inputDataReadyListener.onInputDataReady(byteBufferTransfer.byteArray, bufferInfo)
                                }
                            }
                            // Byte array is received
                            "byte[]" -> {
                                // 每当读到来自服务器的文字数据之后，发送消息通知  程序界面显示该数据
                                val arrivedString = String(inputObject as ByteArray)
                                Log.e(TAG, "接收到byte[]类 内容：${arrivedString}")
                                val msg = Message()
                                msg.what = 0x123
                                msg.obj = arrivedString
                                handler.sendMessage(msg)
                            }
                            // other is received
                            else -> {
                                Log.i(TAG, "接收到其它类 ${inputObject.javaClass.simpleName}")
                            }
                        }
                        // read next object from socket
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
                        objectOutputStream?.reset()
                        Log.i(TAG, "handleMessage " + msg.obj.toString())
                    }
                }
            }
            // 启动Looper
            Looper.loop()
        }
        catch (e: UnknownHostException){
            Log.e(TAG, "未知IP地址！！")
            sendLocalMsg("未知IP地址！！")
        }
        catch(e: NoRouteToHostException){
            Log.e(TAG, "服务器连接失败！！")
            sendLocalMsg("服务器连接失败！！")
        }
        catch (e: ConnectException){
            Log.e(TAG, "服务器未启动！！")
            sendLocalMsg("服务器未启动！！")
        }
        catch (e: EOFException){
            Log.e(TAG, "EOFException")
            sendLocalMsg("服务器关闭！！")
        }
        catch (re: RuntimeException){
            Log.e(TAG, "RuntimeException")
        }
        catch (e: IOException){
            e.printStackTrace()
            System.exit(1)
        }
        catch(e:Exception){
            e.printStackTrace()
            System.exit(1)
        }
        finally {
            objectOutputStream?.close()
            objectInputStream?.close()
//            outputStream?.close()
//            inputStream?.close()
            socket?.close()
//            socket = null
            sendLocalMsg("Disconnected")
        }
    }

    fun sendLocalMsg(localMsg: String){
        val msg = Message()
        msg.what = 0x124
        msg.obj = localMsg
        handler.sendMessage(msg)
    }

}