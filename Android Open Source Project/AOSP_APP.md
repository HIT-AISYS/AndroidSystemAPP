# **$\color{red}{AOSP之系统APP应用搭建}$**

------

- 原生应用开发（Native App Development）/应用层开发（普通APP）：用户可以从 Google Play 或其他应用商店下载并安装的应用程序。与系统功能的集成有限，专注于提供特定的用户功能和服务，如社交媒体、娱乐、生产力工具等。这是传统的安卓开发方式，使用Android Studio作为主要的开发工具。通过Android SDK直接开发，可以充分利用安卓设备的硬件和系统资源。
  
- 安卓系统框架开发（Android Framework Development）/系统级开发（框架层）（系统APP）：开发者可以修改安卓系统的核心组件和应用，实现深度定制。在AOSP环境中进行开发，通常在 Linux 系统（如 Ubuntu）上进行。例如：设置应用 (Settings), 电话应用 (Phone), 短信应用 (Messages)。（1）Android Framework是安卓操作系统提供的核心API和服务层，供应用层调用。（2）开发者直接与Android的核心系统组件和服务交互，修改或扩展系统功能。

**系统应用具有以下特点：**

- 可以调用Android SDK未公开的私有API。
- 拥有更高的系统权限。
- 直接嵌入到Android ROM中，普通方法无法卸载。

## 1、编译 Android framework（制作 API 包）

系统应用可调用隐藏的API，需要引入包含被隐藏 API 的 jar 包，需要在源码下编译 Framework 模块：

```ubuntu
source 
lunch Rice14-eng
# （1）Android10 及以前
make framework
# （2）Android11 及以后
make framework-minus-apex
# 输出目录out/target/common/obj/JAVA_LIBRARIES/framework_intermediates
```

编译完成后，在输出目录下找到 classes.jar 或classes-header.jar 文件，classes-header.jar中包含Android SDK中没有公开的API，例如：用于启用RRO机制的OverlayManager。



