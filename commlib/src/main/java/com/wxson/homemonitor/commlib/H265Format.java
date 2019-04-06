package com.wxson.homemonitor.commlib;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public class H265Format implements IFormatModel {

    private int mWidth;
    private int mHeight;
    private static final String TAG = "H265Format";
    private String mMime = "video/hevc";

    public H265Format(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    public MediaFormat getEncodeFormat() {
        int frameRate = 30;
        int frameInterval = 0;
        int bitRateFactor = 10;

        MediaFormat encodeFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, mWidth * mHeight * bitRateFactor);
        encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        encodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval);

        return encodeFormat;
    }

    @Override
    public MediaFormat getDecodeFormat(byte[] csd) {
        MediaFormat decodeFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        decodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight);
        decodeFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd));

        return decodeFormat;
    }
}
