# **用安卓手机实现视频监控**
现代手机更新换代如此之快，以至于家中往往有闲置不用的手机。本APP用一部闲置手机作为监控相机，在另一部手机上实现远程监控。

作为监控摄像机的手机称为**服务端**，观看监控视频的手机称为**客户端**。

对于使用环境的要求是服务端通过无线路由器WIFI接入互联网，客户端通过互联网远程访问服务端。
路由器需要进行简单设置：
1. DHCP静态IP分配，使服务端在局域网内的地址固定下来。
2. 端口转发，将路由器的指定端口与服务端的内网IP地址绑定，使客户端可以从外部访问服务端。
[](D:\AndroidStudioProjects\HomeMonitor\HomeMonitor_1.png)

未完
