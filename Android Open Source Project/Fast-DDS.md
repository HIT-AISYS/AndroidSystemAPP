# **$\color{red}{Fast-DDS环境搭建}$**

------

**基本要求：**

- Linux三种安装方式：Fast DDS library installation (其一)
- ubuntu-22.04.4-desktop-amd64
  
## 1、工具：CMake、g++、pip3、wget 和 git

```ubuntu
sudo apt install cmake g++ python3-pip wget git
```

## 2、Dependencies

### （1）Asio和TinyXML2

```ubuntu
sudo apt install libasio-dev libtinyxml2-dev
```

### （2）OpenSSL

```ubuntu
sudo apt install libssl-dev
```

## 3、CMake安装

### （1）创建目录

用于下载和构建Fast DDS及其依赖项

```ubuntu
mkdir ~/Fast-DDS
cd ~/Fast-DDS
```

### （2）安装Foonathan memory

```ubuntu
cd ~/Fast-DDS
git clone https://github.com/eProsima/foonathan_memory_vendor.git
mkdir foonathan_memory_vendor/build
cd foonathan_memory_vendor/build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr/local/ -DBUILD_SHARED_LIBS=ON
sudo cmake --build . --target install
```

### （3）安装Fast CDR

```ubuntu
cd ~/Fast-DDS
git clone https://github.com/eProsima/Fast-CDR.git
mkdir Fast-CDR/build
cd Fast-CDR/build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr/local/ -DBUILD_SHARED_LIBS=ON
sudo cmake --build . --target install
```

### （4）安装Fast DDS

```ubuntu
cd ~/Fast-DDS
git clone https://github.com/eProsima/Fast-DDS.git
mkdir Fast-DDS/build
cd Fast-DDS/build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr/local/ -DBUILD_SHARED_LIBS=ON
sudo cmake --build . --target install
```

### （5）Run an application

```ubuntu
echo 'export LD_LIBRARY_PATH=/usr/local/lib/' >> ~/.bashrc
```
