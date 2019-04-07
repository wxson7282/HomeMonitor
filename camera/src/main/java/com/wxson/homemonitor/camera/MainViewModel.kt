package com.wxson.homemonitor.camera

import android.annotation.SuppressLint
import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.wxson.homemonitor.camera.connect.CameraIntentService
import com.wxson.homemonitor.camera.connect.IStringTransferListener
import com.wxson.homemonitor.camera.connect.IsTcpSocketServiceOn
import com.wxson.homemonitor.camera.mediacodec.MediaCodecCallback
import com.wxson.homemonitor.commlib.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = this.javaClass.simpleName
    private val app = application
    private var connectStatusListener: IConnectStatusListener? = null

    //region for LiveData
    private var localMsgLiveData = MutableLiveData<String>()
    fun getLocalMsg(): LiveData<String> {
        return localMsgLiveData
    }

    // 预览尺寸
    private var previewSizeLiveData = MutableLiveData<Size>()
    fun getPreviewSize(): LiveData<Size>{
        return previewSizeLiveData
    }

    //连接状态
    private var isClientConnectedLiveData = MutableLiveData<Boolean>()
    fun getClientConnected(): LiveData<Boolean>{
        return isClientConnectedLiveData
    }

//     //handler for server thread
//    private var handler: Handler
//    class MyHandler(private var mainViewModel : WeakReference<MainViewModel>) : Handler(){
//        private val TAG = this.javaClass.simpleName
//        override fun handleMessage(msg: Message) {
//            Log.i(TAG, "handleMessage")
//            when (msg.what){
//                0x124 -> mainViewModel.get()?.localMsgLiveData!!.postValue(msg.obj.toString())
//                0x125 -> mainViewModel.get()?.previewSizeLiveData!!.postValue(msg.obj as Size)
//                0x126 -> mainViewModel.get()?.isClientConnectedLiveData!!.postValue(msg.obj as Boolean)
//            }
//        }
//    }
    //endregion

    init {
        Log.i(TAG, "init")
//        handler = MyHandler(WeakReference(this))
    }

    fun setConnectStatusListener(connectStatusListener: IConnectStatusListener) {
        this.connectStatusListener = connectStatusListener
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
        super.onCleared()
    }

    //region for camera
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mMediaCodec: MediaCodec            //编解码器
    private var cameraWidth: Int = 0
    private var cameraHigh: Int = 0
    // 摄像头ID（通常0代表后置摄像头，1代表前置摄像头）
    private val cameraId = "0"
    private var cameraDevice1: CameraDevice? = null
    private lateinit var imageReader: ImageReader
    private lateinit var byteBufferTransfer: ByteBufferTransfer
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var previewRequest: CaptureRequest
    private var surfaceTexture: SurfaceTexture? = null

    private val stateCallback = object : CameraDevice.StateCallback() {
        //  摄像头被打开时激发该方法
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.i(TAG, "onOpened")
            cameraDevice1 = cameraDevice
//            //WifiP2pConnectStatus监听器取得连接状态
//            setWifiP2pConnectStatus()
            // 开始预览
            createCameraPreviewSession()
        }

        // 摄像头断开连接时激发该方法
        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.i(TAG, "onDisconnected")
            closeCamera()
        }

        // 打开摄像头出现错误时激发该方法
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Log.i(TAG, "onError")
            closeCamera()
            System.exit(1)
        }
    }

    fun openCamera() {
        Log.i(TAG, "openCamera")
        setUpCameraOutputs(cameraWidth, cameraHigh)
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
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
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
                Arrays.asList(*map!!.getOutputSizes(ImageFormat.JPEG)),
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
            // 获取最佳的预览尺寸 通知MainActivity根据选中的预览尺寸来调整预览组件（TextureView的）的长宽比
            previewSizeLiveData.postValue(chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, largest))
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            println("出现错误。")
        }

    }

    private fun createCameraPreviewSession() {
        Log.i(TAG, "createCameraPreviewSession")
        try {
//            val surfaceTexture = mMainView.getTextureView().getSurfaceTexture()
//            surfaceTexture.setDefaultBufferSize(previewSizeLiveData.getWidth(), previewSizeLiveData.getHeight())
            surfaceTexture?.setDefaultBufferSize(previewSizeLiveData.value!!.width, previewSizeLiveData.value!!.height)

            // Set up Surface for the camera preview
            val previewSurface = Surface(surfaceTexture)

            // 创建作为预览的CaptureRequest.Builder
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            // 将textureView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(Surface(surfaceTexture))

            //region added by wan
            //取得预设的编码格式
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(app.applicationContext)
            val mime = sharedPreferences.getString("mime_list", "")
            // 根据视频编码类型创建编码器
            mMediaCodec = MediaCodec.createEncoderByType(mime!!)
            // Set up Callback for the Encoder
            val port = app.resources.getInteger(R.integer.ServerSocketPort)
            val mediaCodecCallback = MediaCodecCallback(byteBufferTransfer, port, this)
            mMediaCodec.setCallback(mediaCodecCallback.getCallback())

            //取得预设的分辨率
            val size = sharedPreferences.getString("size_list", "")
            val width = Integer.parseInt(size!!.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            val height = Integer.parseInt(size.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1])

            //set up output mediaFormat
            val codecFormat: IFormatModel
            if (mime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                codecFormat = H265Format(width, height)
            } else {
                codecFormat = H264Format(width, height)
            }

            // configure mMediaCodec
            mMediaCodec.configure(codecFormat.getEncodeFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // Set up Surface for the Encoder
            val encoderInputSurface = MediaCodec.createPersistentInputSurface()
            mMediaCodec.setInputSurface(encoderInputSurface)
            mMediaCodec.start()
            previewRequestBuilder.addTarget(encoderInputSurface)

            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求，以及传输请求
            cameraDevice.createCaptureSession(
                Arrays.asList(previewSurface, encoderInputSurface, imageReader.getSurface()), object :
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
        } catch (ie: IOException) {
            ie.printStackTrace()
        }
    }

    fun closeCamera() {
        Log.i(TAG, "closeCamera")
        cameraDevice1?.close()
        cameraDevice1 = null
    }

    // 为Size定义一个比较器Comparator
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // 强转为long保证不会发生溢出
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int, aspectRatio: Size): Size {
        // 收集摄像头支持的打过预览Surface的分辨率
        val bigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.height == option.width * h / w &&
                option.width >= width && option.height >= height
            ) {
                bigEnough.add(option)
            }
        }
        // 如果找到多个预览尺寸，获取其中面积最小的。
        if (bigEnough.size > 0) {
            return Collections.min(bigEnough, CompareSizesByArea())
        } else {
            println("找不到合适的预览尺寸！！！")
            return choices[0]
        }
    }

    val surfaceTextureListener = object : TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureSizeChanged")
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            Log.i(TAG, "onSurfaceTextureUpdated")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed")
            return true
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable")
            surfaceTexture = surface
            cameraWidth = width
            cameraHigh = height
            // notify MainActivity to requestCameraPermission
            localMsgLiveData.postValue(app.getString(R.string.to_requestCameraPermission))
        }
    }

    //endregion

    //region for service
    @SuppressLint("StaticFieldLeak")
    var cameraIntentService: CameraIntentService? = null

    private val stringTransferListener = object : IStringTransferListener {
        override fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(TAG, "onStringArrived")
            localMsgLiveData.postValue("arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
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
                        else -> {
                            localMsgLiveData.postValue("TcpSocketService invalid status")
                        }
                    }
                }
                else -> {
                    localMsgLiveData.postValue("other message")
                }
            }
        }
    }

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

    fun bindService() {
        val intent = Intent(app, CameraIntentService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    //endregion
}