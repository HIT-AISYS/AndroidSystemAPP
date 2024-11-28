# **$\color{red}{Camera开机自动连接WiFi}$**

------

**基本要求：** 

- 开机扫描到该热点后，自动进行连接。
- 没扫描到该热点，则进行等待，一直扫描，直到该热点出现，然后进行连接。
- 第一次连接热点后，不管是否成功，等待5s，再进行一次连接。
- 2次尝试连接后，不再进行扫描和连接。
  
## 1、安装工具 network-manager（nmcli）

```bash
sudo apt-get install network-manager
sudo apt install vim
```

## 2、创建连接脚本 AutoConnectWifi.sh

```bash
// 创建脚本文件
sudo touch AutoConnectWifi.sh
// 输入脚本文件内容
sudo vim AutoConnectWifi.sh
// 给予执行权限
sudo chmod +x AutoConnectWifi.sh
```

## 3、cron的@reboot指令

```bash
// 设置EDITOR环境变量
export EDITOR=vim
// 编辑当前用户的crontab文件
crontab -e
// 文本中添加@reboot指令
@reboot /home/pi/RaspberryConnectWifi/AutoConnectWifi.sh
// 确认crontab已更新
crontab -l
// 测试@reboot任务，重启系统
sudo reboot
```

## 4、WiFi测试

##### （1）删除Ubuntu系统配置文件

WiFi 配置信息存储在 /etc/NetworkManager/system-connections/ 目录下，手动删除这些配置文件

```bash
// 列出该目录下的文件
ls /etc/NetworkManager/system-connections/
// 手动删除
sudo rm /etc/NetworkManager/system-connections/PanConnection
// 重启 NetworkManager 以应用更改
sudo systemctl restart NetworkManager
sudo systemctl status NetworkManager
```

##### （2）检测指定wifi是否连接

```bash
// 检查网络连接状态，查找无线设备 wlan0 等
nmcli device status

DEVICE         TYPE      STATE   CONNECTION    
eth0           ethernet  已连接  有线连接 1    
wlan0          wifi      已连接  PanConnection 
p2p-dev-wlan0  wifi-p2p  已断开  --            
lo             loopback  未托管  --

// 查看所有已保存的网络
nmcli connection show

NAME             UUID                                  TYPE      DEVICE 
有线连接 1        2a6ede12-6a4a-31f0-82bf-cdf04102b850 ethernet  eth0   
PanConnection    036a4c0c-f0cb-42a4-857c-78f4e521b822  wifi      wlan0  
HIT12            76677fad-82cc-4218-8797-c6d0fdd3c1b1  wifi      --     
Rivotek-Visitor  c3427877-6e52-4d72-9ee9-044b782565c7  wifi      --     
Xiaomi 13 Pro    9b825079-12a0-4093-b93a-e7d5342e00f3  wifi      --

// 删除特定的WiFi网络配置
// 在/etc/NetworkManager/system-connections/目录下的PanConnection文件将被删除
nmcli connection delete UUID
// 例如，如果NAME是PanConnection，UUID是12345678-1234-5678-1234-567812345678，则命令将是
sudo nmcli connection delete 036a4c0c-f0cb-42a4-857c-78f4e521b822
```

##### （3）删除已连接的WiFi信息

```bash
// 列出所有已保存的WiFi网络配置，显示所有已保存的网络连接的列表(不包含已连接)
nmcli connection show

cd /etc/NetworkManager/system-connections
sudo vim PanConnection.nmconnection

[connection]
id=PanConnection
uuid=d3513810-c9ee-47c7-9c9e-e6a0849e18f7
type=wifi
interface-name=wlan0
permissions=
timestamp=1731669033

[wifi]
mac-address-blacklist=
mode=infrastructure
seen-bssids=66:2B:31:C3:05:FE;
ssid=PanConnection

[wifi-security]
auth-alg=open
key-mgmt=wpa-psk
psk=a93308c17f9a95c7e599

[ipv4]
dns-search=
method=auto

[ipv6]
addr-gen-mode=stable-privacy
dns-search=
method=auto

[proxy]


sudo rm -rf /etc/NetworkManager/system-connections/PanConnection.nmconnection

nmcli connection delete 12345678-1234-5678-1234-567812345678

sudo nmcli connection up PanConnection
sudo nmcli dev wifi connect "PanConnection" password "12345678"

// 重启网络管理器，删除配置后，需要重启网络管理器以使更改生效
sudo systemctl restart NetworkManager

指令：iw dev wlan0 link
Connected to 66:2b:31:c3:05:fe (on wlan0)
	SSID: PanConnection
	freq: 5745
	RX: 81678 bytes (147 packets)
	TX: 67711 bytes (135 packets)
	signal: -39 dBm
	rx bitrate: 390.0 MBit/s
	tx bitrate: 390.0 MBit/s

	bss flags:	
	dtim period:	3
	beacon int:	100

指令：iw dev wlan0 link
Not connected.

```