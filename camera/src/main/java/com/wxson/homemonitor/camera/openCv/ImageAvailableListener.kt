package com.wxson.homemonitor.camera.openCv

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Message
import android.util.Log
import com.wxson.homemonitor.camera.preview.PreviewThread
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.system.exitProcess

class ImageAvailableListener(private val openCvFormat: Int,
                             private val width: Int,
                             private val height: Int,
                             var motionDetectOn: Boolean,
                             private var backgroundMat: Mat?,
                             private val previewThread: PreviewThread) : ImageReader.OnImageAvailableListener {
    private val TAG = this.javaClass.simpleName
    private var isTimeOut = true
    private val timer = Timer(true)     // The timer for restraining contiguous alarms
    private lateinit var movingAlarmListener: IMovingAlarmListener

    override fun onImageAvailable(reader: ImageReader) {
        try{
            val image = reader.acquireLatestImage() ?: return
            // sanity checks - 3 planes
            val planes = image.planes
            assert(planes.size == 3)
            assert(image.format == openCvFormat)
            // https://developer.android.com/reference/android/graphics/ImageFormat.html#YUV_420_888
            // Y plane (0) non-interleaved => stride == 1; U/V plane interleaved => stride == 2
            assert(planes[0].pixelStride == 1)
            assert(planes[1].pixelStride == 2)
            assert(planes[2].pixelStride == 2)

            val yPlane = planes[0].buffer
            val uvPlane = planes[1].buffer
            val yMat = Mat(height, width, CvType.CV_8UC1, yPlane)
            val uvMat = Mat(height / 2, width / 2, CvType.CV_8UC2, uvPlane)
            val cacheMat = yMat.clone()
            // start of motion detection
            if (motionDetectOn){
                if (backgroundMat == null){
                    // first image noise reduction
                    Imgproc.GaussianBlur(cacheMat, cacheMat, org.opencv.core.Size(13.0, 13.0), 0.0, 0.0)
                    backgroundMat = Mat()
                    Imgproc.Canny(cacheMat, backgroundMat, 80.0, 100.0)
                    return
                }
                else{
                    // skip interval frames
                    // next image noise reduction
                    Imgproc.GaussianBlur(cacheMat, cacheMat, org.opencv.core.Size(13.0, 13.0), 0.0, 0.0)
                    // get contours
                    val contoursMat = Mat()
                    Imgproc.Canny(cacheMat, contoursMat, 80.0, 100.0)
                    // get difference between two images
                    val diffMat = Mat()
                    Core.absdiff(backgroundMat, contoursMat, diffMat)
                    // Counts non-zero array elements.
                    val diffElements = Core.countNonZero(diffMat)
                    val matSize = diffMat.rows() * diffMat.cols()
                    val diff = diffElements.toFloat() / matSize
                    if (diff > 0.004) {
//                        Log.e(TAG, "object moving !! diff=$diff")
                        if (isTimeOut){
                            Log.i(TAG, "send MovingAlarm message out ")
                            movingAlarmListener.onMovingAlarm()
                            isTimeOut = false
                            timer.schedule(object : TimerTask(){
                                override fun run() {
                                    isTimeOut = true
                                }
                            }, 1000)
                        }
                    }
                    // save background image
                    backgroundMat = contoursMat.clone()
//                    // ***************** debug start *********************
//                    sendImageMsg(contoursMat)
//                    // ***************** debug end   *********************
                }
            }
            //**************************************************************************************
            // send image to previewThread
            val tempFrame = JavaCamera2Frame(yMat, uvMat, width, height, openCvFormat)
            val modified = tempFrame.rgba()
            sendImageMsg(modified)
            tempFrame.release()
            //**************************************************************************************
            image.close()
        }
        catch (e: Exception){
            Log.e(TAG, "onImageAvailable ", e)
        }
    }

    /**
     * This class interface is abstract representation of single frame from camera for onCameraFrame callback
     * Attention: Do not use objects, that represents this interface out of onCameraFrame callback!
     */
    interface CvCameraViewFrame {
        /**
         * This method returns RGBA Mat with frame
         */
        fun rgba(): Mat
        /**
         * This method returns single channel gray scale Mat with frame
         */
        fun gray(): Mat
    }

    private inner class JavaCamera2Frame() : CvCameraViewFrame {
        private var openCvFormat: Int = 0
        private var width: Int = 0
        private var height: Int = 0
        private var yuvFrameData: Mat? = null
        private var uvFrameData: Mat? = null
        private var rgba = Mat()

        constructor(Yuv420sp: Mat, w: Int, h: Int, format: Int): this() {
            yuvFrameData = Yuv420sp
            uvFrameData = null
            width = w
            height = h
            openCvFormat = format
        }

        constructor(Y: Mat, UV: Mat, w: Int, h: Int, format: Int) : this() {
            yuvFrameData = Y
            uvFrameData = UV
            width = w
            height = h
            openCvFormat = format
        }


        override fun gray(): Mat {
            return yuvFrameData!!.submat(0, height, 0, width)
        }

        override fun rgba(): Mat {
            if (openCvFormat == ImageFormat.NV21)
                Imgproc.cvtColor(yuvFrameData!!, rgba, Imgproc.COLOR_YUV2RGBA_NV21, 4)
            else if (openCvFormat == ImageFormat.YV12)
                Imgproc.cvtColor( yuvFrameData!!, rgba, Imgproc.COLOR_YUV2RGB_I420,4) // COLOR_YUV2RGBA_YV12 produces inverted colors
            else if (openCvFormat == ImageFormat.YUV_420_888) {
                assert(uvFrameData != null)
                //                Imgproc.cvtColorTwoPlane(yuvFrameData, uvFrameData, rgba, Imgproc.COLOR_YUV2RGBA_NV21);    //modified by wan
                Imgproc.cvtColorTwoPlane(yuvFrameData!!, uvFrameData!!, rgba, Imgproc.COLOR_YUV2BGRA_NV21)  //modified by wan
            } else
                throw IllegalArgumentException("Preview Format can be NV21 or YV12")

            return rgba
        }

        fun release() {
            rgba.release()
        }
    }

    private fun sendImageMsg(inputMat: Mat){
        val showMat = inputMat.clone()
        Core.rotate(showMat, showMat, Core.ROTATE_90_CLOCKWISE)
        // make bitmap for display
        val bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888)
        try{
            Utils.matToBitmap(showMat, bitmap)
        }
        catch (e: java.lang.Exception){
            Log.e(TAG, "Mat type: $showMat")
            Log.e(TAG, "modified.dims:" + showMat.dims() + " rows:" + showMat.rows() + " cols:" + showMat.cols())
            Log.e(TAG, "Bitmap type: " + bitmap.width + "*" + bitmap.height)
            Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.message)
            exitProcess(1)
        }
        val msg = Message()
        msg.data.putParcelable("bitmap", bitmap)
        previewThread.revHandler.sendMessage(msg)
    }

    private fun sendMovingMsg(){
    }

    fun setMovingAlarmListener(listener : IMovingAlarmListener){
        movingAlarmListener = listener
    }

}


