package com.wxson.homemonitor.camera

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.homemonitor.camera.connect.CameraIntentService
import com.wxson.homemonitor.camera.connect.IStringTransferListener
import com.wxson.homemonitor.camera.connect.IsTcpSocketServiceOn
import com.wxson.homemonitor.camera.mediacodec.CodecCallback
import com.wxson.homemonitor.camera.mediacodec.IByteBufferListener
import com.wxson.homemonitor.camera.openCv.ImageAvailableListener
import com.wxson.homemonitor.camera.preview.PreviewThread
import com.wxson.homemonitor.commlib.*
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModel(application: Application) : AndroidViewModel(application){
    private val TAG = this.javaClass.simpleName
    private val app = application
    private lateinit var connectStatusListener: IConnectStatusListener
    private lateinit var transmitInstructionListener: ITransmitInstructionListener

    /**
     *  on service connected, start CameraIntentService.
     */
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "onServiceConnected")
            val binder = service as CameraIntentService.MyBinder
            cameraIntentService = binder.cameraIntentService
            cameraIntentService?.setStringTransferListener(stringTransferListener)
            CameraIntentService.startActionTcp(app)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected")
        }
    }


    //region for LiveData
    private var localMsgLiveData = MutableLiveData<String>()
    fun getLocalMsg(): LiveData<String> {
        return localMsgLiveData
    }

    //连接状态
    private var isClientConnectedLiveData = MutableLiveData<Boolean>()
    fun getClientConnected(): LiveData<Boolean> {
        return isClientConnectedLiveData
    }

    //surfaceTexture
    private var surfaceTextureStatusLiveData = MutableLiveData<String>()
    fun getSurfaceTextureStatus(): LiveData<String> {
        return surfaceTextureStatusLiveData
    }

    //endregion

    init {
        Log.i(TAG, "init")
        bindService()
    }

    fun setConnectStatusListener(connectStatusListener: IConnectStatusListener) {
        this.connectStatusListener = connectStatusListener
    }

    fun setTransmitInstructionListener(transmitInstructionListener: ITransmitInstructionListener){
        this.transmitInstructionListener = transmitInstructionListener
    }

    /**
     * Triggered when ViewModel's owner finishes
     */
    override fun onCleared() {
        Log.i(TAG, "onCleared")
        if(IsTcpSocketServiceOn){
            cameraIntentService?.stopTcpSocketService()
        }
        app.unbindService(serviceConnection)
        cameraIntentService = null

        previewThread.revHandler.removeCallbacksAndMessages(null)

        super.onCleared()
    }

    //region for camera
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    //    private lateinit var cameraDevice: CameraDevice
    lateinit var mediaCodec: MediaCodec            //编解码器
    private var previewSurfaceWidth: Int = 0
    private var previewSurfaceHigh: Int = 0
    // 摄像头ID（通常0代表后置摄像头，1代表前置摄像头）
    private val cameraId = "0"
    private var cameraDevice: CameraDevice? = null
    private lateinit var imageReader: ImageReader
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var previewRequest: CaptureRequest
//    private var surfaceTexture: SurfaceTexture? = null
    var rotation = Surface.ROTATION_0   // 显示设备方向
    private val openCvFormat = ImageFormat.YUV_420_888
    private lateinit var openCvImageReader: ImageReader
    private var backgroundMat: Mat? = null

    lateinit var textureView: TextureView
    private lateinit var previewThread: PreviewThread
    private var imageSize = Size(-1, -1)
    private var previewScale: Float = 1F
    lateinit var viewRect: Rect

    private val stateCallback = object : CameraDevice.StateCallback() {
        //  摄像头被打开时激发该方法
        override fun onOpened(device: CameraDevice) {
            Log.i(TAG, "onOpened")
            cameraDevice = device
            // 开始预览
            createCameraPreviewSession()
        }

        // 摄像头断开连接时激发该方法
        override fun onDisconnected(device: CameraDevice) {
            Log.i(TAG, "onDisconnected")
            closeCamera()
        }

        // 摄像头出现错误时激发该方法
        override fun onError(device: CameraDevice, error: Int) {
            Log.i(TAG, "onError $error")
            closeCamera()
            System.exit(1)
        }
    }

    fun openCamera() {
        Log.i(TAG, "openCamera")
        previewThread = PreviewThread(textureView)
        setUpCameraOutputs(previewSurfaceWidth, previewSurfaceHigh)

        val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // 打开摄像头
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
            // to start previewThread
            Thread(previewThread).start()

    }

    fun closeCamera() {
        Log.i(TAG, "closeCamera")
        captureSession.close()
        cameraDevice?.close()
        cameraDevice = null
        imageReader.close()
        openCvImageReader.close()
    }

    private fun setUpCameraOutputs(surfaceWidth: Int, surfaceHeight: Int) {
        Log.i(TAG, "setUpCameraOutputs")
        val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // 获取指定摄像头的特性
            val characteristics = manager.getCameraCharacteristics(cameraId)
            // 获取摄像头支持的配置属性
            val map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )

            // 获取摄像头支持的最大尺寸
            val largest = Collections.max(
                listOf(*map!!.getOutputSizes(ImageFormat.JPEG)),
                CompareSizesByArea()
            )
            // 创建一个ImageReader对象，用于获取摄像头的图像数据
            imageReader = ImageReader.newInstance(
                largest.width, largest.height,
                ImageFormat.JPEG, 2
            )
            imageReader.setOnImageAvailableListener(
                { reader ->
                    // 当照片数据可用时激发该方法
                    Log.i(TAG, "imageReader.onImageAvailable")
                    // 获取捕获的照片数据
                    val image = reader.acquireNextImage()
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    // 使用IO流将照片写入指定文件
                    @SuppressLint("SimpleDateFormat") val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(
                        Date(System.currentTimeMillis())
                    )
                    val file = File(app.getExternalFilesDir(null), "img$timeStamp.jpg")
                    buffer.get(bytes)
                    try {
                        FileOutputStream(file).use { output -> output.write(bytes) }
                        localMsgLiveData.postValue("保存: $file")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        image.close()
                    }
                }, null
            )
//            // 根据相机的分辨率，获取最佳的预览尺寸 通知MainActivity根据选中的预览尺寸来调整预览组件（TextureView的）的长宽比
//            previewSizeLiveData.postValue(chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), surfaceWidth, surfaceHeight, largest))
            if (!calcPreviewSize(surfaceHeight, surfaceWidth)){
                localMsgLiveData.postValue("PreviewSize = bestSize")
            }


        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            Log.e(TAG,"setUpCameraOutputs: NullPointerException" )
        }

    }

    /**
     * Target1 : openCvSurface
     * Target2 : encoderInputSurface
     * Target3 : imageReader.surface
     */
    private fun createCameraPreviewSession() {
        Log.i(TAG, "createCameraPreviewSession")
        try {
            if (cameraDevice == null){
                return
            }
//            val previewWidth = previewSizeLiveData.value!!.width
//            val previewHeight = previewSizeLiveData.value!!.height
            val imageWidth = imageSize.width
            val imageHeight = imageSize.height
//            surfaceTexture?.setDefaultBufferSize(previewWidth, previewHeight)

//            // Set up Surface for the camera preview
//            val previewSurface = Surface(surfaceTexture)
            // Set up openCvImageReader
            openCvImageReader = ImageReader.newInstance(imageWidth, imageHeight, openCvFormat, 3)
            openCvImageReader.setOnImageAvailableListener(ImageAvailableListener(openCvFormat, imageWidth, imageHeight, backgroundMat, previewThread), null)
            val openCvSurface = openCvImageReader.surface

            // 创建作为预览的CaptureRequest.Builder
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//            // 将textureView的surface作为CaptureRequest.Builder的目标
//            previewRequestBuilder.addTarget(previewSurface)
            // 将openCvImageReader的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(openCvSurface)

            //region added by wan
            //首次运行时设置默认值
            PreferenceManager.setDefaultValues(app, R.xml.pref_codec, false)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)
            //取得预设的编码格式
            val videoCodecMime = sharedPreferences.getString("mime_list", "")
            //取得预设的分辨率
            val videoCodecSize = sharedPreferences.getString("size_list", "")
            // 根据视频编码类型创建编码器
            mediaCodec = MediaCodec.createEncoderByType(videoCodecMime!!)
            // Set up Callback for the Encoder
            val mediaCodecCallback = CodecCallback(videoCodecMime, videoCodecSize!!, this)
            mediaCodec.setCallback(mediaCodecCallback.getCallback())
            //设置监听器
            // to inform MainViewModel onOutputBufferAvailable in MediaCodecCallback
            mediaCodecCallback.setByteBufferListener( object : IByteBufferListener {
                override fun onByteBufferReady(byteBufferTransfer: ByteBufferTransfer) {
                    // inform ServerOutputThread of ByteBufferReady by handler
                    val msg = Message()
                    msg.what = 0x333
                    msg.obj = byteBufferTransfer
                    cameraIntentService?.serverThread?.handler?.sendMessage(msg)
                }
            })
            //取得预设的分辨率
            val width = Integer.parseInt(videoCodecSize.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            val height = Integer.parseInt(videoCodecSize.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1])

            //set up output mediaFormat
            val codecFormat: IFormatModel
            if (videoCodecMime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                codecFormat = H265Format(width, height)
            } else {
                codecFormat = H264Format(width, height)
            }

            // configure mediaCodec
            mediaCodec.configure(codecFormat.encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // Set up Surface for the Encoder
            val encoderInputSurface = MediaCodec.createPersistentInputSurface()
            mediaCodec.setInputSurface(encoderInputSurface)
            mediaCodec.start()
            previewRequestBuilder.addTarget(encoderInputSurface)

            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求，以及传输请求。最多只能容纳3个surface!
            cameraDevice!!.createCaptureSession(
                listOf(openCvSurface, encoderInputSurface, imageReader.surface), object :
                    CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        Log.i(TAG, "onConfigured")
                        // 当摄像头已经准备好时，开始显示预览
                        captureSession = cameraCaptureSession
                        try {
                            // 设置自动对焦模式
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // 设置自动曝光模式
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )
                            // 开始显示相机预览
                            previewRequest = previewRequestBuilder.build()

                            // 设置预览时连续捕获图像数据
                            captureSession.setRepeatingRequest(previewRequest, null, null)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }

                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed")
                        localMsgLiveData.postValue("配置失败！")

                    }
                }, null
            )
            //endregion
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun captureStillPicture() {
        Log.i(TAG, "captureStillPicture")
        try {
            if (cameraDevice == null) {
                return
            }
            // 创建作为拍照的CaptureRequest.Builder
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(imageReader.surface)
            // 设置自动对焦模式
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            // 设置自动曝光模式
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
            // 根据设备方向计算设置照片的方向
            setORIENTATIONS()
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
            // 停止连续取景
            captureSession.stopRepeating()
            // 捕获静态图像
            captureSession.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback()
            {
                // 拍照完成时激发该方法
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    try {
                        // 重设自动对焦模式
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                        )
                        // 设置自动曝光模式
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                        )
                        // 打开连续取景模式
                        captureSession.setRepeatingRequest(previewRequest, null, null)
                    }
                    catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }
            }, null)
        }
        catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    //方向数组
    private val ORIENTATIONS = SparseIntArray()
    private fun setORIENTATIONS(){
        ORIENTATIONS.clear()
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    // 为Size定义一个比较器Comparator
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // 强转为long保证不会发生溢出
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    val surfaceTextureListener = object : TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureSizeChanged")
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
//            Log.i(TAG, "onSurfaceTextureUpdated")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed")
            closeCamera()
            return true
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable")
//            surfaceTexture = surface
            previewSurfaceWidth = width
            previewSurfaceHigh = height

//            // to start previewThread
//            previewThread = PreviewThread(textureView)
//            Thread(previewThread).start()

            // notify MainActivity to requestCameraPermission
            surfaceTextureStatusLiveData.postValue("onSurfaceTextureAvailable")
        }
    }

    /**
     * 根据view的物理尺寸，参照相机支持的分辨率，使用最接近的长宽比，确定预览画面的尺寸
     * 循环测试相机支持的分辨率
     * 同时计算预览时图像放大比例
     * 测试条件：最佳宽度 = 0
     *           最佳高度 = 0
     *           如果view的宽度>= 相机分辨率宽度 且 view的高度 >= 相机分辨率高度  且
     *               最佳宽度  <= 相机分辨率宽度 且 最佳高度   <= 相机分辨率高度  且
     *               view的长宽比与相机分辨率长宽比之差 < 0.1
     *           则  最佳宽度 = 相机分辨率宽度
     *               最佳高度 = 相机分辨率高度
     * @author wxson
     * @param
     * viewWidth : view's viewWidth
     * viewHeight : view's viewHeight
     * @return false: fail   true: success
     */
    internal fun calcPreviewSize(viewWidth: Int, viewHeight: Int): Boolean {
        Log.i(TAG, "calcPreviewSize: " + viewWidth + "x" + viewHeight)
        val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            var bestWidth = 0
            var bestHeight = 0
            val aspect = viewWidth.toFloat() / viewHeight       // view的长宽比
            val sizes = map!!.getOutputSizes(ImageReader::class.java)   // 相机支持的分辨率
            for (i in sizes.size downTo 1) {
                val sz = sizes[i - 1]
                val w = sz.width
                val h = sz.height
                Log.i(TAG, "trying size: " + w + "x" + h)
                if (viewWidth >= w && viewHeight >= h && bestWidth <= w && bestHeight <= h
                    && Math.abs(aspect - w.toFloat() / h) < 0.1
                ) {
                    bestWidth = w
                    bestHeight = h
                }
            }
            Log.i(TAG, "best size: " + bestWidth + "x" + bestHeight)
            assert(!(bestWidth == 0 || bestHeight == 0))
            if (imageSize.width == bestWidth && imageSize.height == bestHeight)
                return false
            else {
                imageSize = Size(bestWidth, bestHeight)
                // 图像放大率
                previewScale = Math.min(viewWidth.toFloat()/bestWidth, viewHeight.toFloat()/bestHeight)
                // 图像显示范围
                viewRect = Rect(0, 0, (previewScale * bestHeight).toInt(), (previewScale * bestWidth).toInt())
                previewThread.canvasRect = viewRect
                return true
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "calcPreviewSize - Camera Access Exception", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "calcPreviewSize - Illegal Argument Exception", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "calcPreviewSize - Security Exception", e)
        }
        return false
    }


    //endregion

    //region for service
    @SuppressLint("StaticFieldLeak")
    var cameraIntentService: CameraIntentService? = null

    private val stringTransferListener = object : IStringTransferListener {
        override fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(TAG, "onStringArrived")
            localMsgLiveData.postValue("arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
            when (arrivedString){
                "Capture Still Picture" ->{
                    // Take a photo on command of client
                    // to do in main thread
                    Handler(Looper.getMainLooper()).post { captureStillPicture() }
                }
                "Stop Video Transmit" ->{
                    transmitInstructionListener.onTransmitInstructionArrived(false)
                }
                "Start Video Transmit" ->{
                    transmitInstructionListener.onTransmitInstructionArrived(true)
                }
            }
        }

        override fun onMsgTransfer(msgType: String, msg: String) {
            Log.i(TAG, "onMsgTransfer $msgType : $msg")
            when (msgType) {
                "TcpSocketServiceStatus" -> {
                    when (msg) {
                        "ON" -> localMsgLiveData.postValue("TcpSocketService is ON")
                        "OFF" -> localMsgLiveData.postValue("TcpSocketService is OFF")
                        "TIMEOUT" -> {
                            app.unbindService(serviceConnection)
                            System.exit(0)
                        }
                    }
                }
                "TcpSocketClientStatus" -> {
                    when (msg) {
                        "ON" -> {
                            isClientConnectedLiveData.postValue(true)
                            connectStatusListener.onConnectStatusChanged(true)
                        }
                        "OFF" -> {
                            isClientConnectedLiveData.postValue(false)
                            connectStatusListener.onConnectStatusChanged(false)
                        }
                    }
                }
                else -> {
                    localMsgLiveData.postValue("other message")
                }
            }
        }
    }

    private fun bindService() {
        val intent = Intent(app, CameraIntentService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    //endregion
}