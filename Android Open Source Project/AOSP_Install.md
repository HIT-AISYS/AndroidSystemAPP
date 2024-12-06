# **$\color{red}{AOSP13.0环境搭建}$**

------

**基本要求：**

- 电脑配置越高，编译速度越快；
- 磁盘空间>500G；
- 最低内存要求>32GB 的系统内存RAM。 (或增加32G swap);
- Ubuntu 20.04（ubuntu-20.04-desktop-amd64.iso）；
- 32G == 32768 MB；

Ubuntu默认2G左右的swap空间，（可以提供过 free -m 命令查看虚拟内存与交换空间大小）使用以下命令查看swap详情：
（1）停用交换文件

```ubuntu
sudo swapoff /swapfile
```

（2）删除文件

```ubuntu
sudo rm /swapfile
```

（3）创建20G的Swap空间

```ubuntu
sudo fallocate -l 20G /swapfile
```

（4）权限和挂载

```ubuntu
sudo chmod 600 /swapfile
sudo mkswap /swapfile
```

（5）激活启用

```ubuntu
sudo swapon /swapfile
```

可以通过free -m命令查看Swap空间

------

## 1、构建编译环境

### （1）配置清华镜像（避免翻墙）

打开源列表

```ubuntu
sudo gedit /etc/apt/sources.list 
```

把清华源复制到源列表（放前面），之前的源不要动

```ubuntu
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal main restricted universe multiverse
deb-src https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-updates main restricted universe multiverse
deb-src https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-updates main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-backports main restricted universe multiverse
deb-src https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-backports main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-security main restricted universe multiverse
deb-src https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ focal-security main restricted universe multiverse
```

保存之后更新源

```ubuntu
sudo apt-get update
sudo apt-get upgrade
```

### （2）安装基本依赖（这些Ubuntu系统可能没有自带）

```ubuntu
sudo apt-get install git git-core gnupg flex bison gperf build-essential -y
sudo apt-get install tofrodos python-markdown -y
sudo apt-get install zip curl gcc-multilib g++-multilib -y
sudo apt-get install lib32ncurses5-dev x11proto-core-dev -y
sudo apt-get install libgl1-mesa-dev libxml2-utils xsltproc unzip m4 -y
sudo apt-get install ccache lib32z1-dev -y
sudo apt-get install libssl-dev fontconfig -y
sudo apt-get install libffi-dev libz-dev openjdk-11-jdk -y

sudo apt-get install libc6-dev libc6-dev-i386 -y
sudo apt-get install libncurses5 libncurses5:i386 -y
sudo apt-get install libx11-dev libx11-dev:i386 -y
sudo apt-get install zlib1g-dev zlib1g-dev:i386 -y
sudo apt-get install libreadline6-dev libreadline6-dev:i386 -y
```

### （3）安装git工具

```ubuntu
sudo apt-get install git
git config --global user.email "zhangxiaoxiao@qq.com"
git config --global user.name "zhangxiaoxiao"
```

### （4）安装python

Ubuntu20.04默认自带的是python3，因为后面的repo命令需要依赖python命令，所以用python建立一个python3的软链接：

```ubuntu
sudo ln -s /usr/bin/python3 /usr/bin/python
```

相当于用python命令指代python3命令。

------

## 2、构建编译环境

### （1）安装repo并设置权限

 配置PATH环境变量

```ubuntu
mkdir ~/bin
echo "PATH=~/bin:\$PATH" >> ~/.bashrc
source ~/.bashrc
```

下载repo

```ubuntu
PATH=~/bin:$PATH
curl https://mirrors.tuna.tsinghua.edu.cn/git/git-repo -o ~/bin/repo
chmod a+x ~/bin/repo

export REPO_URL='https://mirrors.tuna.tsinghua.edu.cn/git/git-repo'
source ~/.bashrc
```

### （2）下载源码

```ubuntu
mkdir aosp
cd aosp
repo init -u https://mirrors.tuna.tsinghua.edu.cn/git/AOSP/platform/manifest -b android-13.0.0_r7
repo sync    
```

如果你的机器足够好，可以指定更多的任务，如下命令演示使用8个任务同步代码

```ubuntu
repo sync -c -j8
```

------

## 3、Android 源代码编译

### （1）初始化编译环境，源码整编

**【说明】:**（1）make clobber 命令用于清除缓存；
（2）source build/envsetup.sh 命令将必要的环境变量和函数添加到当前 shell 中。或者执行指令 . build/envsetup.sh；
（3）lunch 命令用于用来选择构型，并且会读取 build 目录下的 product 相关配置文件，以便后续的 make 命令可正确编译 AOSP 系统。
（4）make 命令启动 AOSP 编译过程。编译失败则使用 make clean 进行清理之前的编译结果，然后重新编译项目；
（5）export DISABLE_ARTIFACT_PATH_REQUIREMENTS="true" 命令禁用 AOSP 编译系统路径检查，以在后续启动模拟器时可跳过限制；
（6）emulator 命令启动 AOSP 模拟器，并加载编译生成的系统镜像；

**【说明】:**（1）编译目标的格式:BUILD-BUILDTYPE，比如上面的aosp_x86_64-eng的BUILD是aosp_x86_64，BUILDTYPE是eng。
（2）BUILD：特定功能的组合的特定名称，即表示编译出的镜像可以运行在什么环境。
（3）BUILD TYPE则指的是编译类型，通常有三种：

| 构建类型 | 使用情况 |
| -------- | -------- |
| user | 权限受限；适用于生产环境 |
| userdebug | 与“user”类似，但具有 root 权限和调试功能；是进行调试时的首选编译类型 |
| eng | 具有额外调试工具的开发配置，最大权限 |

```ubuntu
cd ~/aosp
make clobber
     
source build/envsetup.sh    
lunch sdk_phone_x86_64-userdebug      
make -j8    
export DISABLE_ARTIFACT_PATH_REQUIREMENTS="true"  
emulator   
```

**【说明】:**（1）make -j8中的后面数字表示同时运行的编译任务数;
（2）所有编译出的结果都在 /out 目录中可以找到。其中三个重要的镜像文件：
 
- system.img：系统镜像，里面包含了 Android 系统主要的目录和文件，通过 init.rc 进行解析并挂载到 /system 目录下。
- userdata.img：用户镜像，是 Android 系统中存放用户数据的，通过 init.rc 进行解析并挂载到 /data 目录下。
- ramdisk.img：根文件系统镜像，包含一些启动 Android 系统的重要文件，比如 init.rc。

### （2）模块编译

```ubuntu
cd ~/aosp/package/apps/Setting
source build/envsetup.sh      
mm  
```

------

## 5、运行模拟器

在编译完成后，通过下面指令运行模拟器，lunch选择刚才设置的目标版本

```ubuntu
source build/envsetup.sh
lunch sdk_phone_x86_64-userdebug  
emulator
```

------

## 6、常见错误

### （1）VMware workstation安装 ubuntu虚拟机编译AOSP源码，启动emulator失败，报错emulator: ERROR: x86_64 emulation currently requires hardware acceleration!（模拟器需要硬件加速）

解决办法：先关闭虚拟机，编辑虚拟器位置，选择处理器，勾选虚拟化Intel VT-x/EPT和AMD-V/RVI(V)，启动虚拟机，重新执行source build/envsetup.sh 和lunch命令，接下来就能正常启动虚拟机。

### （2）Command 'emulator' not found, did you mean: command 'emulator' from snap emulator. See 'snap info snapname' for additional versions

解决办法：肯定是在没有导入环境的窗口执行 emulator！
这里要先导入环境，如下：

```ubuntu
source build/envsetup.sh
lunch sdk_phone_x86_64
emulator
```

### （3） error while loading shared libraries: libncurses.so.5: cannot open shared object. file: No such file or directory

解决办法：sudo apt-get install libncurses5 -y

### （4） emulator: WARNING: Couldn't find crash service executable /home/xq/aosp/prebuilts/android-emulator/linux-x86_64/emulator64-crash-service. emulator: WARNING: system partition size adjusted to match image file (3083 MB > 800 MB).  emulator: ERROR: x86_64 emulation currently requires hardware acceleration! Please ensure KVM is properly installed and usable. CPU acceleration status: KVM requires a CPU that supports vmx or svm

解决办法：emulator -gpu off -partition-size 4096

### （5） aosp编译报错：ninja failed with: exit status 137

解决办法：原因 swap只有2G，增大交换分区空间(swap)：改为8G

### （6） Dex2oat错误：ERROR: Dex2oat failed to compile a boot image.It is likely that the boot classpath is inconsistent.Rebuild with ART_BOOT_IMAGE_EXTRA_ARGS="--runtime-arg -verbose:verifier" to see verification errors
 
解决办法：（1）方法1：#编译前先设置一下关闭dex优化设置
export WITH_DEXPREOPT=false；（2）方法2：#make的时候带上参数
make WITH_DEXPREOPT=false；

// 将现有swap移动到主内存，可能需要几分钟
sudo swapoff -a
// 创建新的swap文件，bs×count=最后生成的swap大小，这里设置8G
sudo dd if=/dev/zero of=/swapfile bs=1G count=8
// 设置权限
sudo chmod 0600 /swapfile
// 设置swap
sudo mkswap /swapfile
// 打开swap
sudo swapon /swapfile
// 检查设置是否有效,或者htop看一下
grep Swap /proc/meminfo
// 设置永久有效
sudo gedit /etc/fstab
// 在弹出的页面末尾行加上 
/swapfile swap swap sw 0 0

------

## 附录：AOSP常用编译和查找命令

- m ：等同于 make ，进行一次完整编译。
- mm：在当前目录下编译所有模块，但不包括依赖项。
- mmm [目录]：编译指定目录下的所有模块，但不包括依赖项。只编译指定模块：mmm [dir/:target1,target2]
- mma：在当前目录下编译所有模块，包括依赖项。
- mmma [目录]：编译指定目录下的所有模块，包括依赖项。
- make clean：删除整个 out/ 目录。
- make clobber：用于清除编译缓存。
- make snod：当重新编译部分模块完成后，快速生成img文件。
- cgrep：对C/C++文件执行 grep (只搜寻C/C++文件类型，也包括.h类型)。
- jgrep：只对Java文件执行 grep (只搜寻Java文件类型)。
- resgrep：只匹配 /res 目录下的 xml 文件。
- sepgrep：只查找sepolicy文件。