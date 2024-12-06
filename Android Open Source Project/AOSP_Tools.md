# **$\color{red}{AOSP工具安装}$**

------

## 1、SSH服务

SSH服务：使得我们在其他平台通过SSH客户端程序即可访问我们的Linux服务器，方便开发工作。

- **安装 openssh-server**

```ubuntu
sudo apt install openssh-server
# 开机自启动
sudo systemctl enable ssh
# 重启 ssh 服务
sudo systemctl restart ssh
```

- **配置固定 IP 地址**

```ubuntu
sudo apt install net-tools -y
cd /etc/netplan
# 备份旧的配置文件
sudo cp 00-installer-config.yaml  00-installer-config.yaml_before
```

```ubuntu
# 修改配置文件：
sudo vim 00-installer-config.yaml

network:
  version: 2
  renderer: NetworkManager
  ethernets:
    enp0s5:   # 网卡名称
      dhcp4: no     # 关闭dhcp
      dhcp6: no
      addresses: [10.0.0.89/24]  # 静态ip，根据自己网络情况配置
      gateway4: 10.0.0.1     # 网关，根据自己网络情况配置
      nameservers:
        addresses: [10.0.0.1, 114.114.114.114] #dns，根据自己网络情况配置
```

- **使配置生效**

```ubuntu
sudo netplan apply
```

- **查询IP地址和用户名**

（1）查IP地址

```ubuntu
hostname -I
```

（2）查用户名

```ubuntu
who
```

------

## 2、vim 基本使用

- **一般模式**

进入 vim 环境，通过上下左右方向键可以移动光标。（test.txt 可以是当前目录已存在文件，也可以是当前目录不存在文件）

```ubuntu
vim test.txt
```

- **编辑模式**

在一般模式下，输入 i 进入编辑模式，可输入文本修改文件内容。

在编辑模式下，我们点击 esc 键即可回到一般模式。

- **命令行模式**

在一般模式下，输入分号 “:”，进入命令行模式。在命令行中，最常用的功能包括2类：

（1）退出 vim；
（2）当前文本中，搜索字符串；

进入命令行模式后，有三种方式来退出 vim：

（1）当前文件没有任何改变时，输入 q 指令，退出 vim 环境；
（2）当前文件做了一些修改，如果想放弃已修改的内容，输入 q! 指令，退出 vim 环境；
（3）当前文件做了一些修改，如果想保存已修改的内容，输入 wq 指令，退出 vim 环境；

进入命令行模式后，我们还可以输入 / + 字符串，点击回车键，进入搜索模式，点击 N 键，光标就会在匹配处依次跳动。

在一般模式下，我们也可以直接输入 / + 字符串进行搜索。

------

## 3、find + grep 命令

Android 系统源码繁杂，通过 find 命令来查找文件。使用 find + grep 命令查找文件内容。

（1）找文件，比如 service_manager.c

```ubuntu
find . -name "service_manager.c"
./frameworks/native/cmds/servicemanager/service_manager.c
```

（2）找文件内容，找 recyclerview 库

```ubuntu
find . -name "Android.bp" | xargs grep "name: \".*recyclerview.*\""
./prebuilts/sdk/current/androidx/Android.bp:    name: "androidx.recyclerview_recyclerview-selection-nodeps",
./prebuilts/sdk/current/androidx/Android.bp:    name: "androidx.recyclerview_recyclerview-selection",
./prebuilts/sdk/current/androidx/Android.bp:    name: "androidx.recyclerview_recyclerview-nodeps",
./prebuilts/sdk/current/androidx/Android.bp:    name: "androidx.recyclerview_recyclerview",
./prebuilts/sdk/current/support/Android.bp:    name: "android-support-recyclerview-selection-nodeps",
./prebuilts/sdk/current/support/Android.bp:    name: "android-support-recyclerview-selection",
./prebuilts/sdk/current/support/Android.bp:    name: "android-support-v7-recyclerview-nodeps",
./prebuilts/sdk/current/support/Android.bp:    name: "android-support-v7-recyclerview",
```

## 3、Visual Studio Code安装与配置（局域网）

我们可以通过两种方式来使用 VSCode

- Ubuntu 本机使用
- Windows 通过 remote-ssh 插件远程使用 VSCode 编辑linux 上的代码


### （1）编译器安装

【1】从vscode[官网](https://code.visualstudio.com/docs?dv=linux64)下载deb包格式
【2】在安装包当前目录下，直接打开终端，然后输入：

```ubuntu
# 其中的 -i 后面的就是deb包名
sudo  dpkg  -i code_1.76.2-1678817801_amd64.deb
```

打开 vscode，然后通过 file -> open folder 选择系统源码，即可打开整个系统源码。

### （2）编译器插件

- C/C++ Extension Pack
- Extension Pack for Java
- Makefile Tools
- RC Script language
- Android System Tools
- Android Studio Color Theme

### （3）远程使用

**Ubuntux系统要求如下：**

（1）安装OpenSSH服务器软件包

```ubuntu
sudo apt install openssh-server
```

（2）确认SSH服务正在运行

```ubuntu
sudo systemctl status ssh
```

（3）如果SSH服务没有自动启动，使用以下命令手动启动它

```ubuntu
sudo systemctl start ssh
```

（4）确保SSH服务在启动时自动运行

```ubuntu
sudo systemctl enable ssh
```

**Windows系统要求如下：**

- 安装好 Remote-SSH 插件；
- 点击左侧 remote-explorer 图标，点击右侧加号；
- 在弹出的输入框中，输入 用户名@IP地址，点击回车；

------

## 4、Android Studio安装

（1）打开终端（Terminal）;

（2）首先，确保你已经安装了 Java 开发工具包（JDK）。Android Studio 需要 Java 运行环境。你可以使用以下命令来安装 OpenJDK 11

```ubuntu
sudo apt-get install openjdk-11-jdk -y
```

（3）下载 Android Studio 最新安装包。Android Studio [官网网站](https://developer.android.com/studio)


（4）解压下载的安装包。你可以在终端中使用 tar 命令解压，比如：

```ubuntu
tar -xvzf android-studio-2024.1.1.13-linux.tar.gz
```

（5）进入解压后的 Android Studio 目录。在终端中切换到 Android Studio 的目录，比如：

```ubuntu
cd android-studio/bin
```

（6）运行 studio.sh 脚本启动 Android Studio 安装向导。在终端中运行以下命令：

```ubuntu
./studio.sh
```

（7）按照安装向导的指引完成 Android Studio 的安装过程。安装完成后，你可以在应用菜单中找到 Android Studio，并创建一个启动器。

【注】在编译器中，选择菜单，Tools -> Create Destop Entry，即可在应用程序列表里面看到编译器图标。