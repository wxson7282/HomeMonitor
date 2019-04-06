package com.wxson.homemonitor.commlib;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

import static com.wxson.homemonitor.commlib.AvcUtils.*;

public class H264Format implements IFormatModel {

    private int mWidth;
    private int mHeight;
    private static final String TAG = "H264Format";
    private String mMime = "video/avc";

    public H264Format(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    public MediaFormat getEncodeFormat() {
        int frameRate = 30;
        int frameInterval = 0;  //每一帧都是关键帧
        int bitRateFactor = 14;

        MediaFormat encodeFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, mWidth * mHeight * bitRateFactor);
        encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        encodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);    // This format is ok.
        encodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval);

        return encodeFormat;
    }

    @Override
    public MediaFormat getDecodeFormat(byte[] csd) {
        //分割spsPps
        ByteBuffer csdByteBuffer = ByteBuffer.wrap(csd);
        if (csdByteBuffer == null){
            Log.e(TAG, "getDecodeFormat csd is null");
            return null;
        }
        csdByteBuffer.clear();
        if (!goToPrefix(csdByteBuffer)) {
            Log.e(TAG, "getDecodeFormat Prefix error");
            return null;
        }
        byte[] header_sps = getSps(csdByteBuffer);
        byte[] header_pps = getPps(csdByteBuffer);

        MediaFormat decodeFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        decodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight);
        decodeFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
        decodeFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

        return decodeFormat;

    }
}
