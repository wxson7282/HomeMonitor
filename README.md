# android手机远程视频移动检测的实践
家中老人年高，为防止意外跌倒，需要时刻看护，于是想到用视频监控代替部分注意力。远程视频移动监测的方案有很多种，因为以前在手机上做了类似工作，参见[用安卓手机实现视频监控](https://blog.csdn.net/wxson/article/details/91987709)，在此基础之上增加移动监测报警功能。
### 服务端修改
移动监测功能在服务端（camera）实现，在以前架构基础上，做出如下修改。

 - 原以为createCameraPreviewSession()（创建显示预览界面）时，用createCaptureSession()（创建画面捕获会话）可以使用任意数量surface作为照相机图像数据的输出口，以前架构中用到了3个surface：视频预览、视频编码器、照片文件各用一个。这次增加第四个，用于视频移动监测，但反复调试仍无法创建画面捕获会话，SDK文档中也没有找到有关surface数量限制的记述。只好将第一个surface交给ImageReader，用ImageReader同时实现视频预览和移动监测功能。
```kotlin
// 创建ImageReader
openCvImageReader = ImageReader.newInstance(imageWidth, imageHeight, openCvFormat, 3)
// 创建imageAvailableListener
imageAvailableListener = ImageAvailableListener(openCvFormat, imageWidth, imageHeight, false, backgroundMat, previewThread)
// 在imageAvailableListener中注入movingAlarmListener（移动报警监听器）
imageAvailableListener?.setMovingAlarmListener(movingAlarmListener)
// 打开ImageReader
openCvImageReader.setOnImageAvailableListener(imageAvailableListener, null)
// 定义ImageReader.surface
val openCvSurface = openCvImageReader.surface
// 创建作为预览的CaptureRequest.Builder
previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
// 将openCvImageReader的surface作为CaptureRequest.Builder的目标
previewRequestBuilder.addTarget(openCvSurface)
```
- 创建画面捕获会话
```kotlin
// 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求，以及传输请求。最多只能容纳3个surface!
cameraDevice!!.createCaptureSession(listOf(openCvSurface, encoderInputSurface, imageReader.surface), object :
                    CameraCaptureSession.StateCallback() {
                    ...}
```
 - 根据相机的分辨率，获取最佳的预览尺寸，具体算法请参考代码中的注释

```kotlin
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
```

 - 在stringTransferListener中增加接受客户端开关移动监测指令的功能，直接控制imageAvailableListener的外部开关变量motionDetectOn。

```kotlin
    private val stringTransferListener = object : IStringTransferListener {
        override fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(TAG, "onStringArrived")
            localMsgLiveData.postValue("arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
            when (arrivedString){
            	...
                "Start Motion Detect" ->{
                    imageAvailableListener?.motionDetectOn = true
                }
                "Stop Motion Detect" ->{
                    imageAvailableListener?.motionDetectOn = false
                }
            }
        }
```

 - 需要定义一个预览用的线程PreviewThread

```kotlin
/**
 *  The thread used to display a content stream on
 *  @param textureView
 */
class PreviewThread(private val textureView: TextureView) : Runnable {
    lateinit var canvasRect: Rect
    // 定义接收preview image的Handler对象
    lateinit var revHandler: Handler
    class MyHandler(private val textureView: TextureView, private val dstRect: Rect) : Handler(){
        override fun handleMessage(msg: Message?) {
            val canvas = textureView.lockCanvas() ?: return
            if (msg != null){
                val bitmap: Bitmap? = msg.data.getParcelable("bitmap")
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, dstRect, null)
                }
            }
            textureView.unlockCanvasAndPost(canvas)
        }
    }

    override fun run() {
        Looper.prepare()
        revHandler = MyHandler(textureView, canvasRect)
        Looper.loop()
    }
}
```

 * 在相机打开时启动PreviewThread

```kotlin
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
```

 - 在ImageReader中实现OnImageAvailableListener，这是实现移动监测的关键部分，主要动作有
 	- 读取图像数据
 	- 格式检查
 	- 图像数据转为mat
 	- 用图像亮度mat作为移动监测用的帧数据
 	- 首帧处理
 		- 用高斯滤波抑制噪声
 		- 提取轮廓
 		- 存为背景帧
 	-  后续帧处理
 		- 用高斯滤波抑制噪声
 		- 提取轮廓
 		- 计算当前帧与背景帧的差值
 		- 如果差值大于指定的阈值，通过movingAlarmListener向外部发出移动报警文字。 
 		- 存为背景帧
 	- 用图像亮度mat和色度mat合成彩色mat
 	- 彩色mat顺时针旋转90°
 	- 彩色mat转换为 bitmap
 	- 把bitmap发送给预览线程previewThread，预览线程用canvas.drawBitmap方法显示图像。

```kotlin
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
```

### 客户端修改
客户端（monitor）修改比较简单。

 - 在界面上增加一个移动监测按钮
```xml
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_motion_detect"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_margin="16dp"
        android:layout_marginBottom="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toStartOf="@+id/fab_transmit"
        app:layout_constraintStart_toEndOf="@+id/fab_capture"
        app:srcCompat="@drawable/ic_motion_detect" />
```
 - 在程序中监听这个按钮，通过已经建立的socket连接，发送字符串指令到服务端。

```kotlin
private var isMotionDetectOn = false

        //定义移动探测浮动按钮
        fab_motion_detect.setOnClickListener{
            view ->
            if (isMotionDetectOn){
                viewModel.sendMsgToServer("Stop Motion Detect")    // notify server to Stop Motion Detect
                Snackbar.make(view, "停止移动探测", Snackbar.LENGTH_SHORT).show()
                fab_motion_detect.backgroundTintList = ContextCompat.getColorStateList(this.activity!!.baseContext, R.color.button_light)
                isMotionDetectOn = false
            }
            else{
                viewModel.sendMsgToServer("Start Motion Detect")    // notify server to Start Motion Detect
                Snackbar.make(view, "开始移动探测", Snackbar.LENGTH_SHORT).show()
                fab_motion_detect.backgroundTintList = ContextCompat.getColorStateList(this.activity!!.baseContext, R.color.colorAccent)
                isMotionDetectOn = true
            }
        }
```

 - 在客户端增加对服务端信息的响应，如果服务端发出的是移动警告信息，则启动系统铃声作为警告铃声。

```kotlin
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "onActivityCreated")
        ...
        val serverMsgObserver: Observer<String> = Observer { serverMsg -> remoteMsgHandler(serverMsg.toString()) }
        viewModel.getServerMsg().observe(this, serverMsgObserver)
        ...      
    }

    private fun remoteMsgHandler(remoteMsg: String){
        when (remoteMsg){
            "Moving Alarm" -> {
                showMsg(remoteMsg)
                viewModel.defaultMediaPlayer()
            }
        }
    }

    fun defaultMediaPlayer(){
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone: Ringtone = RingtoneManager.getRingtone(app, ringtoneUri)
        if (ringtone.isPlaying)
            ringtone.stop()
        else
            ringtone.play()
    }
```
其它细节，请参考源代码。
实践中，发现由于不同手机镜头有差异，移动监测的灵敏度会有不同，需要调整移动监测处理的相关参数。
如果有问题，请联系我（wxson@126.com）。





# **用安卓手机实现视频监控**
现代手机更新换代如此之快，以至于家中往往有闲置不用的手机。本APP用一部闲置手机作为监控相机，在另一部手机上实现远程监控。

作为监控摄像机的手机称为**服务器端**，观看监控视频的手机称为**客户端**。

对于使用环境的要求是服务器端通过无线路由器WIFI接入互联网，客户端通过互联网远程访问服务器端。
路由器需要进行简单设置：
* DHCP静态IP分配，使服务端在局域网内的地址固定下来。
* 端口转发，将路由器的指定端口与服务端的内网IP地址绑定，使客户端可以从外部访问服务器端。

![图1 系统构成](https://github.com/wxson7282/HomeMonitor/blob/master/images/HomeMonitor_1.png)

使用时，需要在客户端输入路由器的外网IP地址，家庭用户路由器的IP地址通常是电信运营商动态分配的，时时会发生变化，客户端获取路由器IP地址的方法有两种：
* 在客户端安装路由器管理APP，路由器厂商通常都会提供路由器管理APP。通过路由器管理APP可以实时查看路由器的IP地址。
* 在花生壳上用内网计算机注册私有域名，私有域名与路由器绑定。在客户端安装花生壳管理APP，通过APP可以实时查看私有域名的IP地址，这个IP地址就是路由器的地址。如果不使用花生壳，其他域名解析提供者也有类似工具。

由于服务器端和客户端都需要发送信息到对方，因此用TCP协议实现服务器端和客户端的双工通信。
## **服务器端构成**

![图2 服务器端构成](https://github.com/wxson7282/HomeMonitor/blob/master/images/HomeMonitor_CameraClasses.png)

图中所示为构成服务器端的主要类：
* **MainActivity** 用户交互主页面
* **SettingsActivity** 参数设置页面，可以设置编码器的图像分辨率和视频编码标准。
* **AutoFitTextureView** 服务器端的视频显示组件
* **MainViewModel** Android官方推荐使用MVVM架构，ViewModel是MVVM架构的重要组件，它负责为UI/View准备数据，它与外部通信通过LiveData进行。
* **CameraIntentService** 提供与客户端的通信服务，接收客户端的通信请求。通信连接成功后，建立服务器通信线程。
* **ServerThread** 服务器通信线程，发送编码后的视频数据流，接收客户端的字符信息。
* **MediaCodecCallback** 编码器采用异步工作模式，必须对编码器的各个回调函数重载，以实现本系统所需视频编码功能。
* **ByteBufferTransfer** 承载视频编码后得到的数据以及解码器需要的相关情报，它的每个实例代表一帧图像，经过通信连接，以数据流发送到客户端。

服务器端将镜头拍摄的视频信号经编码后逐帧传送给客户端，由于视频拍摄和信号传输各自具有不同的时序，因此使用异步方式实现协同动作。

![图3 服务器端时序](https://github.com/wxson7282/HomeMonitor/blob/master/images/HomeMonitor_CameraSequence.png)

**服务器端的技术要点**
* **视频编码数据获取** <br> 在android camera2的基础上，使用所推荐的流程控制相机。
  为了取得相机帧数据，以下代码把编码器的InputSurface添加到CameraRequest的targets列表中，
  在预览过程中，CameraDevice返回的帧数据就能够送到编码器中。<br>
  ```kotlin
  val encoderInputSurface = MediaCodec.createPersistentInputSurface()
  mediaCodec.setInputSurface(encoderInputSurface)
  mediaCodec.start()
  previewRequestBuilder.addTarget(encoderInputSurface)
  ```
* **视频编码器参数** <br>
视频编码器需要设置的参数如下：
>* videoCodecMime(编码格式) 可以在SettingsActivity中选择，本系统仅有H264和H265可以选择，注意！老的手机往往不支持H265。
>* videoCodecSize(采样分辨率) 可以在SettingsActivity中选择，是手机常用的分辨率。
>* KEY_FRAME_RATE(帧率) 这个参数在程序中固定为30。
>* KEY_COLOR_FORMAT(颜色格式) 这个参数在程序中固定为MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface。
>* KEY_I_FRAME_INTERVAL(关键帧间隔) 单位是秒，这个参数用于指定帧间预测所需关键帧在码流中的间隔，间隔越大，数据压缩率越高。但为了画面流畅，设为零，也就是没有用帧间预测。
>* KEY_BIT_RATE(比特率) 比特率 = 分辨率宽 x 分辨率高 x 比特率系数。比特率系数是固定值，H264为14，H265为10，因为H265有更高的压缩率。

* **双工通信** <br>
收到客户端的请求后，建立服务器通信线程。在线程中建立并行的两个循环，一个用Loop+Handler实现，Handler负责接收视频编码器的输出，送入objectOutputStream，发送到客户端。
另一个用While实现，从objectInputStream中读取来自客户端的数据，根据数据的类别进行相应处理。

## **客户器端构成**

![图4 客户端构成](https://github.com/wxson7282/HomeMonitor/blob/master/images/HomeMonitor_MonitorClasses.png)

图中所示为构成客户端的主要类：
* **MainActivity** 主页面容器
* **SettingsActivity**参数设置页面，仅用来设置服务器端的IP地址。
* **MainFragment** 用户交互主页面
* **MainViewModel** 是MVVM架构的重要组件，它负责为UI/View准备数据，它与外部通信通过LiveData进行。
* **ClientThread** 通信用客户端线程
* **MonitorTextureView** 客户端的视频显示组件
* **MediaCodecAction** 包装了解码器的静态操作方法
* **DecoderCallback** 解码器的回调函数
* **ByteBufferTransfer** 服务器端发送过来的实例，解码后得到帧图像。

接收到服务器端发送的视频码流，经解码后把视频信号交给视频显示组件。

![图5 客户端时序](https://github.com/wxson7282/HomeMonitor/blob/master/images/HomeMonitor_MonitorSequence.png)

**客户端的技术要点**
* **指定解码器输出** <br>
取得显示View的surface，
```kotlin
val surface = Surface(super@MonitorTextureView.getSurfaceTexture())
```
设置解码器时，把surface作为参数传给mediaCodec.configure()方法即可。

* **视频解码器参数** <br>
>* mime(编码格式) 服务器发送过来的ByteBufferTransfer的实例中，包含此参数，不需单独设置。
>* size(采样分辨率) 服务器发送过来的ByteBufferTransfer的实例中，包含此参数，不需单独设置。
>* csd(Codec-specific数据) 服务器端编码时产生该数据，包含在ByteBufferTransfer的实例中。
* **双工通信** <br>
与服务器端相似，在线程中建立并行的两个循环，一个用来接收，另一个用来发送。

在测试中，服务器端和客户端均使用联通的网络时，视频传输比较流畅。
但是服务器端使用联通网络，客户端使用移动网络时，卡顿非常严重，原因不得而知，也许运营商之间有壁垒。

代码公开，欢迎同行的指摘、建议，如有需要改进之处，我当尽力为之。
邮箱：wxson@126.com

