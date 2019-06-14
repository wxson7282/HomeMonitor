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

