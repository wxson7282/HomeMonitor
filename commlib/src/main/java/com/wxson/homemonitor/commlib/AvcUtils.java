package com.wxson.homemonitor.commlib;

import android.util.Log;

import java.nio.ByteBuffer;

import static com.wxson.homemonitor.commlib.CommonTools.bytesToHex;


public class AvcUtils {
    private static final String TAG = "AvcUtils";
	public static final int START_PREFIX_CODE = 0x00000001;
	public static final int START_PREFIX_LENGTH = 4;
	public static final int NAL_UNIT_HEADER_LENGTH = 1;
	public static final int NAL_TYPE_CODED_SLICE 			= 0x01;
	public static final int NAL_TYPE_CODED_SLICE_IDR 		= 0x05;
	public static final int NAL_TYPE_SEI 					= 0x06;
	public static final int NAL_TYPE_SPS 					= 0x07;
	public static final int NAL_TYPE_PPS 					= 0x08;
	public static final int NAL_TYPE_SUBSET_SPS 			= 0x0f;

	public static boolean goToPrefix(final ByteBuffer buffer) {
		int presudo_prefix = 0xffffffff;
		while (buffer.hasRemaining()) {
			presudo_prefix = (presudo_prefix << 8) | (buffer.get() & 0xff);
			if (presudo_prefix == START_PREFIX_CODE) {
				return true;
			}
		}
		return false;
	}

	public static int getNalType(final ByteBuffer buffer)
	{
		return buffer.get() & 0x1f;
	}
	
	public static int getGolombUE(final BitBufferLite bitb) {
		int leadingZeroBits = 0;
		while (!bitb.getBit()) {
			leadingZeroBits++;
		}
		final int suffix = bitb.getBits(leadingZeroBits);
		final int minimum = (1 << leadingZeroBits) - 1;
		return minimum + suffix;
	}
	
	//TODO: need support extra profile_idc and pic_order_cnt_type
	//usage: int[] width = new int[1];
	//sps should contains 00 00 00 01 67 ......
	public static void parseSPS(/*in*/byte[] sps, /*out*/int[] width, /*out*/int[] height)	//sps buffer doesn't include nal-type byte
	{
		ByteBuffer byteb = ByteBuffer.wrap(sps);
		if (false == goToPrefix(byteb) || NAL_TYPE_SPS != getNalType(byteb))
			return;
		
		BitBufferLite bitb = new BitBufferLite(byteb);
		
		int profile_idc = bitb.getBits(8);				//profile idc
		bitb.getBits(16);								//constraint_set0...,
		getGolombUE(bitb);
		if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122
			|| profile_idc == 244 || profile_idc == 44 || profile_idc == 83
			|| profile_idc == 86 || profile_idc == 118 || profile_idc == 128) 
		{
			Log.e("AvcUtils", "SPS parsing do not support such profile idc, "+profile_idc);
			throw new UnsupportedOperationException("Profile idc NOT supported yet.");
		}
		int log2_max_frame_num_minus4 = getGolombUE(bitb);
		int pic_order_cnt_type = getGolombUE(bitb);
		if (pic_order_cnt_type == 0)
		{
			int log2_max_pic_order_cnt_lsb_minus4 = getGolombUE(bitb);
		}
		else if (pic_order_cnt_type == 1)
		{
			Log.e("AvcUtils", "SPS parsing do not support such pic_order_cnt_type, "+pic_order_cnt_type);
			throw new UnsupportedOperationException("pic_order_cnt_type NOT supported yet.");
		}
		else
		{
			//pic_order_cnt_type shall be "2", do nothing
		}
		
		int num_ref_frames = getGolombUE(bitb);
		int gaps_in_frame_num_value_allowed_flag = bitb.getBits(1);	//1 bit
		
		//KEY POINT
		int pic_width_in_mbs_minus1 = getGolombUE(bitb);
		width[0] = (pic_width_in_mbs_minus1 + 1) * 16;
		int pic_height_in_map_units_minus1 = getGolombUE(bitb);
		height[0] = (pic_height_in_map_units_minus1 + 1) * 16;
		
		//over
		return;
	}

	static byte[] getSps(final ByteBuffer buffer){
		buffer.clear();
		int i;
		for (i = 5; i < buffer.capacity(); i++) {
			if ((buffer.get(i) & 0xff) == 0){	//sps head以后遇到0结束
				break;
			}
		}
		byte[] returnValue = new byte[i];
		buffer.get(returnValue, 0, i);
		return returnValue;
	}

	static byte[] getPps(final ByteBuffer buffer){
		int len = buffer.capacity() - buffer.position();
		byte[] returnValue = new byte[len];
		buffer.get(returnValue);
		return returnValue;
	}

	/**
	 *  get csd from video ByteBuffer for decode
	 * @param byteBuffer video ByteBuffer
	 * @return byte[] csd
	 */
	public static byte[] GetCsd(ByteBuffer byteBuffer){
		byte[] csd;
		// for h264
		if ((byteBuffer.get(4) & 0x1f) == NAL_TYPE_SPS){ //如果byteBuffer第四个字节的后5位等于7
			csd = new byte[byteBuffer.remaining()];
			byteBuffer.get(csd);
            Log.i(TAG, "GetCsd for h264 csd=" + bytesToHex(csd));
		}
		else {
			//for h265
			int nalType = (byteBuffer.get(4) >> 1) & 0x3f;
			if (nalType == 32){
				csd = new byte[byteBuffer.remaining()];
				byteBuffer.get(csd);
                Log.i(TAG, "GetCsd for h265 csd=" + bytesToHex(csd));
			}
			else{
				csd = null;
                Log.i(TAG, "csd= null");
			}
		}
		return csd;
	}

}