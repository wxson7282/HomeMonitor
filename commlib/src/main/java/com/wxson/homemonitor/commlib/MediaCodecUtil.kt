@file:Suppress("DEPRECATION")

package com.wxson.homemonitor.commlib

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import android.util.Range
import java.util.*

class MediaCodecUtil {
    companion object{
        @Suppress("JAVA_CLASS_ON_COMPANION")
        val TAG = this.javaClass.simpleName

        fun getMimeList(mimeListValues: Array<CharSequence>): Array<CharSequence> {
            Log.i(TAG, "getMimeList")
            val availableMimes = ArrayList<CharSequence>()
            for (mimeValue in mimeListValues) {
                val mediaCodecInfo = selectCodec(mimeValue.toString())
                if (mediaCodecInfo != null) {
                    //                MediaCodecInfo.CodecCapabilities codecCapabilities = getCodecCapabilities(mimeValue.toString(),mediaCodecInfo);
                    //                MediaCodecInfo.VideoCapabilities videoCapabilities = getVideoCapabilities(codecCapabilities);
                    availableMimes.add(mimeValue)
                }
            }
            return availableMimes.toTypedArray()
        }

        private fun selectCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        return codecInfo
                    }
                }
            }
            return null
        }

        fun getCodecCapabilities(mimeType: String, mediaCodecInfo: MediaCodecInfo): MediaCodecInfo.CodecCapabilities {
            return mediaCodecInfo.getCapabilitiesForType(mimeType)
        }

        fun getVideoCapabilities(codecCapabilities: MediaCodecInfo.CodecCapabilities): MediaCodecInfo.VideoCapabilities {
            return codecCapabilities.videoCapabilities
        }

        fun getSupportedFrameRates(videoCapabilities: MediaCodecInfo.VideoCapabilities, width: Int, height: Int )
                : Range<Double> {
            return videoCapabilities.getSupportedFrameRatesFor(width, height)
        }
    }

//    public static Range<Double> getAchievableFrameRates(@NonNull MediaCodecInfo.VideoCapabilities videoCapabilities, int width, int height){
//        return videoCapabilities.getAchievableFrameRatesFor(width,height);
//    }

}