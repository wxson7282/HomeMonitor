package com.wxson.homemonitor.commlib;

import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Created by wxson on 2018/4/27.
 * Package com.wxson.common_lib.
 */
public class ByteBufferTransfer implements Serializable {
    private byte[] mByteArray;
    private int mBufferInfoFlags;
    private int mBufferInfoOffset;
    private long mBufferInfoPresentationTimeUs;
    private int mBufferInfoSize;
    private byte[] mCsd = null;
    private byte[] mMime;
    private byte[] mSize;

    public void setByteArray(byte[] byteArray) {
        this.mByteArray = byteArray;
    }

    public byte[] getByteArray() {
        return mByteArray;
    }

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

    public byte[] getMime(){ return mMime; }

    public void setMime(byte[] mime){ this.mMime = mime; }

    public byte[] getSize(){ return mSize; }

    public void setSize(byte[] size){ this.mSize = size; }

    public byte[] getCsd() {
        return mCsd;
    }

    public void setCsd(byte[] csd) {
        this.mCsd = csd;
    }

    @NonNull
    @Override
    public String toString() {
        return "ByteBufferTransfer{ ByteBufferSize=" + mByteArray.length + " }" ;
    }
}
