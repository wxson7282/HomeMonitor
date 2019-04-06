package com.wxson.homemonitor.commlib;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by wxson on 2018/4/28.
 * Package com.wxson.common_lib.
 */
public class ByteBufferTransferTask extends AsyncTask<String, Void, Boolean> {
    private int mPort;
    private ByteBufferTransfer mByteBufferTransfer;
    private static InetAddress mInetAddress = null;
    private static final String TAG = "ByteBufferTransferTask";
    private TaskCompletedListener mTaskCompletedListener;

    public ByteBufferTransferTask(int port) {
        mPort = port;
    }

    public void setByteBufferTransfer(ByteBufferTransfer byteBufferTransfer) {
        mByteBufferTransfer = byteBufferTransfer;
    }

    public static void setInetAddress(InetAddress inetAddress) {
        mInetAddress = inetAddress;
    }

    public static InetAddress getInetAddress() {
        return mInetAddress;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.e(TAG, "onPreExecute");
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        mByteBufferTransfer.setMd5(Md5Util.getMd5ForByteArray(mByteBufferTransfer.getByteArray()));
        Log.e(TAG, "ByteBuffer的MD5码值是：" + mByteBufferTransfer.getMd5());
        Socket socket = null;
        OutputStream outputStream = null;
        ObjectOutputStream objectOutputStream = null;
        InputStream inputStream = null;
        try{
            socket = new Socket();
            socket.bind(null);
            Log.e(TAG, "doInBackground 客户端地址：" + strings[0] + " 端口：" + mPort);
            socket.connect((new InetSocketAddress(strings[0], mPort)), 10000);
            outputStream = socket.getOutputStream();
            objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(mByteBufferTransfer);
            byte[] byteArray = mByteBufferTransfer.getByteArray();
            outputStream.write(byteArray);

            outputStream.close();
            objectOutputStream.close();
            socket.close();
            outputStream = null;
            objectOutputStream = null;
            inputStream = null;
            socket = null;
            Log.e(TAG, "ByteBuffer发送成功");
            return true;
        }
        catch (Exception e){
            Log.e(TAG, "ByteBuffer发送异常 Exception: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        Log.e(TAG, "onPostExecute: " + aBoolean);
        mTaskCompletedListener.onPreTaskCompleted();    //通知上级程序，任务已经完成
//        super.onPostExecute(aBoolean);
    }

    //程序中有两个实例，有一个多余
    public interface TaskCompletedListener {
        void onPreTaskCompleted();  //上一个任务完成
    }

    public void setTaskCompletedListener(TaskCompletedListener taskCompletedListener){
        mTaskCompletedListener = taskCompletedListener;
    }
}
