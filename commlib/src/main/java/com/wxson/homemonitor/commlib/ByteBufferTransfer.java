package com.wxson.homemonitor.commlib;

import java.io.Serializable;

/**
 * Created by wxson on 2018/4/27.
 * Package com.wxson.common_lib.
 */
public class ByteBufferTransfer implements Serializable {
    //MD5码
    private String mMd5;
    private byte[] mByteArray;
    private int mBufferInfoFlags;
    private int mBufferInfoOffset;
    private long mBufferInfoPresentationTimeUs;
    private int mBufferInfoSize;
    private byte[] mCsd = null;

    public void setByteArray(byte[] byteArray) {
        this.mByteArray = byteArray;
    }

    public byte[] getByteArray() {
        return mByteArray;
    }

//    public void setBufferInfo(MediaCodec.BufferInfo bufferInfo) {
//        this.mBufferInfo = bufferInfo;
//    }

//    public MediaCodec.BufferInfo getBufferInfo() {
//        return mBufferInfo;
//    }

    public int getBufferInfoFlags() {
        return mBufferInfoFlags;
    }

    public void setBufferInfoFlags(int bufferInfoFlags) {
        this.mBufferInfoFlags = bufferInfoFlags;
    }

    public int getBufferInfoOffset() {
        return mBufferInfoOffset;
    }

    public void setBufferInfoOffset(int bufferInfoOffset) {
        this.mBufferInfoOffset = bufferInfoOffset;
    }

    public long getBufferInfoPresentationTimeUs() {
        return mBufferInfoPresentationTimeUs;
    }

    public void setBufferInfoPresentationTimeUs(long bufferInfoPresentationTimeUs) {
        this.mBufferInfoPresentationTimeUs = bufferInfoPresentationTimeUs;
    }

    public int getBufferInfoSize() {
        return mBufferInfoSize;
    }

    public void setBufferInfoSize(int bufferInfoSize) {
        this.mBufferInfoSize = bufferInfoSize;
    }

    public String getMd5() {
        return mMd5;
    }

    public void setMd5(String mMd5) {
        this.mMd5 = mMd5;
    }

    public byte[] getCsd() {
        return mCsd;
    }

    public void setCsd(byte[] csd) {
        this.mCsd = csd;
    }

    @Override
    public String toString() {
        return "ByteBufferTransfer{ ByteBufferSize=" + mByteArray.length + ", md5='" + mMd5 + "' }" ;
    }
}
