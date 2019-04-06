package com.wxson.homemonitor.commlib;

import android.media.MediaFormat;

public interface IFormatModel {
    //获取编码器格式
    MediaFormat getEncodeFormat();
    //获取解码器格式
    MediaFormat getDecodeFormat(byte[] csd);
}
