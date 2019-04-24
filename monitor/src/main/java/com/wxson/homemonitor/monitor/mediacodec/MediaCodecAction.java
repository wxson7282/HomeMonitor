package com.wxson.homemonitor.monitor.mediacodec;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import com.wxson.homemonitor.commlib.H264Format;
import com.wxson.homemonitor.commlib.H265Format;
import com.wxson.homemonitor.commlib.IFormatModel;

import java.io.IOException;

/**
 * Created by wxson on 2018/7/2.
 * Package com.wxson.mobilecontroller.camera_manager.
 */
public class MediaCodecAction {
    private static final String TAG = "MediaCodecAction";

    public static MediaCodec PrepareDecoder(Surface surface, byte[] csd, Context context){
        //取得预设的编码格式
        SharedPreferences sharedPreferences =PreferenceManager.getDefaultSharedPreferences(context);
        String mime = sharedPreferences.getString("mime_list", "");
        //取得预设的分辨率
        String size = sharedPreferences.getString("size_list", "");
        int width = Integer.parseInt(size.split("x")[0]);
        int height = Integer.parseInt(size.split("x")[1]);

        try {
            MediaCodec mediaCodec = MediaCodec.createDecoderByType(mime);
            //注册解码器回调
            DecoderCallback decoderCallback = new DecoderCallback();
            mediaCodec.setCallback(decoderCallback.getCallback());
            //设定格式
            IFormatModel codecFormat;
            if (mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)){
                codecFormat = new H265Format(width, height);
            }
            else{
                codecFormat = new H264Format(width, height);
            }
            mediaCodec.configure(codecFormat.getDecodeFormat(csd), surface, null, 0);
            Log.i(TAG, "DecoderCallback mMediaCodec.configure");
            return mediaCodec;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void StartDecoder(MediaCodec mediaCodec){
        mediaCodec.start();
    }

    public static void ReleaseDecoder(MediaCodec mediaCodec){
        mediaCodec.stop();
        mediaCodec.release();
    }
}
