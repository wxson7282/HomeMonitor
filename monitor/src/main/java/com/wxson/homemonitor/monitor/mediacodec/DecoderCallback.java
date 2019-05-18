package com.wxson.homemonitor.monitor.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.annotation.NonNull;
import android.util.Log;
import com.wxson.homemonitor.monitor.connect.ClientThread;

import java.nio.ByteBuffer;

/**
 * Created by wxson on 2018/5/22.
 * Package com.wxson.mobilecontroller.camera_manager.
 */
class DecoderCallback {
    private static final String TAG = "DecoderCallback";
    private byte[] mInputData;
    private MediaCodec.BufferInfo mBufferInfo;
    private boolean mInputDataReady = false;

    DecoderCallback() {
        //注册输入数据准备好监听器
        ClientThread.inputDataReadyListener = new IInputDataReadyListener(){
            @Override
            public void onInputDataReady(@NonNull byte[] inputData, @NonNull MediaCodec.BufferInfo bufferInfo) {
                //如果输入数据没有处理完(mInputDataReady==true)，则丢弃新来的数据，没有缓冲区
                if (!mInputDataReady){
                    Log.i(TAG, "onInputDataReady get one frame");
                    mInputDataReady = true;
                    mInputData = inputData;
                    mBufferInfo = bufferInfo;
                }
                else{
                    Log.e(TAG, "onInputDataReady lost one frame");
                }
            }
        };
    }

    private MediaCodec.Callback mCallback = new MediaCodec.Callback(){
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inputBufferId) {
//            Log.i(TAG, "onInputBufferAvailable");
            try {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                if (inputBuffer != null){
                    int length = 0;
                    long timeStamp = 0;
                    inputBuffer.clear();
                    if (mInputDataReady){
                        //如果输入数据准备好，注入解码器
                        length = mBufferInfo.size;
                        timeStamp = mBufferInfo.presentationTimeUs;
                        inputBuffer.put(mInputData, 0, length);
                        mInputDataReady = false;
                        Log.i(TAG, "输入数据注入解码器 length=" + length + " timeStamp=" + timeStamp );
                    }
                    //把inputBuffer放回队列
                    mediaCodec.queueInputBuffer(inputBufferId,0, length, timeStamp,0);
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outputBufferId, @NonNull MediaCodec.BufferInfo bufferInfo) {
            Log.i(TAG, "onOutputBufferAvailable");
            mediaCodec.releaseOutputBuffer(outputBufferId, true);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.e(TAG, "onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            Log.i(TAG, "onOutputFormatChanged");
        }
    };

    MediaCodec.Callback getCallback(){
        return mCallback;
    }

}
