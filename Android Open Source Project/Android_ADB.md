# **$\color{red}{Android之ADB命令大全}$**

------

1、系统命令

```ubuntu
// Ubuntu安装adb工具
sudo apt install android-tools-adb
// 查看adb版本
adb version

// 列出连接计算机的设备和模拟器实例
adb devices

// 进入Android设备的shell环境
adb shell
// 获取root权限（需要设备已经root）
adb root
// 重新挂载系统分区为可读写
adb remount
// 获取root权限以及重新挂载系统的分区，使其可读写
adb root && adb remount
// 重启设备
adb reboot
// 重启设备到bootloader模式。
adb reboot bootloader
```

2、日志和调试

```ubuntu
// 显示设备的日志信息
adb logcat
// 显示包含特定标签的日志信息
adb logcat -s <TAG>
adb logcat -s WifiTetheringHandler

```
3、文件操作
```ubuntu
// 将文件从电脑复制到设备
adb push <local> <remote>
adb push out/target/product/uis7870sc_sailingcore20/system/app/CarPanConnectionService/CarPanConnectionService.apk system/app/CarPanConnectionService/CarPanConnectionService.apk
// 将文件从设备复制到电脑
adb pull <remote> <local>
// 列出设备的目录内容
adb shell ls <路径>
// 删除设备上的文件
adb shell rm <文件>
rm /system/app/CarPanConnectionService/CarPanConnectionService.apk
// 在设备上创建新目录
adb shell mkdir <目录>
```


