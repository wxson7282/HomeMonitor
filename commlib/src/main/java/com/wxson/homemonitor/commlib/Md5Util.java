package com.wxson.homemonitor.commlib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

/**
 * Created by wxson on 2018/3/6.
 * Package com.wxson.remote_camera.connection.
 */

public class Md5Util {
    public static String getMd5(File file) {
        InputStream inputStream = null;
        byte[] buffer = new byte[2048];
        int numRead;
        MessageDigest md5;
        try {
            inputStream = new FileInputStream(file);
            md5 = MessageDigest.getInstance("MD5");
            while ((numRead = inputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            inputStream.close();
            inputStream = null;
            return md5ToString(md5.digest());
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getMd5ForByteBuffer(ByteBuffer byteBuffer){
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(byteBuffer);
            return md5ToString(md5.digest());
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static String getMd5ForByteArray(byte[] byteArray){
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(byteArray);
            return md5ToString(md5.digest());
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private static String md5ToString(byte[] md5Bytes) {
        StringBuilder hexValue = new StringBuilder();
        for (byte b : md5Bytes) {
            int val = ((int) b) & 0xff;
            if (val < 16) {
                hexValue.append("0");
            }
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();
    }

}
