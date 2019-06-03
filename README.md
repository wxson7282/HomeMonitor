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

未完
