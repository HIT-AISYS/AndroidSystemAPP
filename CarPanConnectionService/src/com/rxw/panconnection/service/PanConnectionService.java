package com.rxw.panconnection.service;

import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.net.wifi.WifiClient;
import android.net.MacAddress;
import android.net.wifi.WifiManager;
import android.net.TetheringManager;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.LinkedHashSet;
import java.util.concurrent.Executor;

import com.rxw.panconnection.service.aidl.IUniDeviceConnection;
import com.rxw.panconnection.service.aidl.IUniDeviceConnectionCallback;
import com.rxw.panconnection.service.bluetooth.BtUtils;
import com.rxw.panconnection.service.camera.PanCameraManager;
import com.rxw.panconnection.service.command.CommandManager;
import com.rxw.panconnection.service.hvac.CarHvacController;
import com.rxw.panconnection.service.unidevice.DeviceFilter;
import com.rxw.panconnection.service.unidevice.UniDevice;
import com.rxw.panconnection.service.utils.WhitelistUtils;
import com.rxw.panconnection.service.wifi.WifiTetheringHandler;
import com.rxw.panconnection.service.wifi.WifiConnectionConstants;
import com.rxw.panconnection.service.wifi.WifiConnectionUtils;
import com.rxw.panconnection.service.wifi.WifiTetheringAvailabilityListener;
import com.rxw.panconnection.service.wifi.WifiTetheringObserver;
import com.rxw.dds.connect.service.IUniConnectionServiceControl;
import com.rxw.dds.connect.service.IUniConnectionServiceCallback;
import com.rxw.dds.connect.service.UniConnectionServiceManager;
import com.rxw.dds.control.UniDeviceController;
import com.rxw.dds.control.UniDeviceInfoCallback;
import com.rxw.panconnection.service.wifi.WifiDevice;

public class PanConnectionService extends Service {
    private static final String TAG = PanConnectionService.class.getSimpleName();

    private static final boolean DEBUG_ALL          = true;
    private static final boolean DEBUG_CALL         = DEBUG_ALL | false;
    private static final boolean DEBUG_CALLBACK     = DEBUG_ALL | false;
    private static final boolean DEBUG_PERMISSION   = DEBUG_ALL | false;
    private static final boolean DEBUG_DISCOVERY    = DEBUG_ALL | false;
    private static final boolean DEBUG_CONNECT      = DEBUG_ALL | false;
    private static final boolean DEBUG_REMOVE_BOND  = DEBUG_ALL | false;
    private static final boolean DEBUG_AUTO_CONNECT = DEBUG_ALL | false;

    private BtManager mBtManager;
    private WifiTetheringHandler mWifiTetheringHandler;
    private List<WifiDevice> connectedWifiDevicesList = new ArrayList<>(); 
    private List<WifiDevice> discoveredWifiDevicesList = new ArrayList<>(); 
    private final Map<String, UniDevice> mDevices = new HashMap<>();

    private DeviceFilter mDeviceFilter;
    private WhitelistUtils mWhitelistUtils;
    private Set<UniDevice> mAutoConnectDevices;

    /*===================*
     * Getters           *
     *===================*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastFoundUniDeviceInfo() throws JSONException, RemoteException {
        Set<UniDevice> set = new HashSet<>();
        this.mBtManager.updateLastFoundBcDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        set.add(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()));
                    }
                }
        );

        this.mBtManager.updateLastFoundBleDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        set.add(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()));
                    }
                }
        );
        // Convert the set of UniDevice objects to a JSON string
        String info = UniDevice.getDeviceInfoJsonArray(set).toString();

        // Log the JSON string of device information
        Log.d(TAG, "JSON string of device information: " + info);

        return info;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastFoundUniDeviceInfoAndBroadcast() throws JSONException, RemoteException {
        String info = this.getLastFoundUniDeviceInfo();

        Intent intent = new Intent();
        intent.setAction("com.rxw.ACTION_DISCOVERED_DEVICES");
        intent.setPackage("com.rxw.car.panconnection");
        intent.putExtra("discovered_devices", info);
        PanConnectionService.this.sendBroadcast(intent);

        return info;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String updateLastFoundUniDeviceInfo() throws JSONException, RemoteException {
        Log.d(TAG, "updateLastFoundUniDeviceInfo()");
        this.mBtManager.updateLastFoundBcDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        String address = device.getAddress();
                        if (!this.mDevices.containsKey(address)) {
                            this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()));
                        }
                    }
                }
        );
        this.mBtManager.updateLastFoundBleDeviceInfo(
                devices -> {
                    for (BluetoothDevice device : devices) {
                        String address = device.getAddress();
                        if (!this.mDevices.containsKey(address)) {
                            this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()));
                        }
                    }
                }
        );

        // Exclude connected devices

        Set<UniDevice> devices = new HashSet<>(this.mDevices.values());
        Log.d(TAG, "Last Found Devices: " + devices);

        Set<UniDevice> toRemove = this.getLastConnectedUniDevices(null);
        Log.d(TAG, "To Remove Devices:" + toRemove);

        devices.removeAll(toRemove);
        Log.d(TAG, "After Removing Devices:" + devices);

        String info = UniDevice.getDeviceInfoJsonArray(devices).toString();
        Log.d(TAG, "Broadcast Info:" + info);

        // String info = UniDevice.getDeviceInfoJsonArray(this.mDevices.values()).toString();

        return info;
    }

    // @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    // private String updateLastFoundUniDeviceInfoAndBroadcast() throws JSONException, RemoteException {
    //     String info = this.updateLastFoundUniDeviceInfo();

    //     if (!(TextUtils.isEmpty(info) && TextUtils.equals(info, "[]"))) {
    //         Intent intent = new Intent();
    //         intent.setAction("com.rxw.ACTION_DISCOVERED_DEVICES");
    //         intent.setPackage("com.rxw.car.panconnection");
    //         intent.putExtra("discovered_devices", info);
    //         PanConnectionService.this.sendBroadcast(intent);
    //     }


    //     return info;
    // }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private Set<UniDevice> getLastConnectedUniDevices(String deviceClass) {
        Set<UniDevice> devices = new HashSet<>();
        UniDevice device;

        // Classic Bluetooth
        for (Map.Entry<String, UniDevice> entry : this.mBtManager.getLastBondedBcDevices().entrySet()) {
            device = entry.getValue();
            if (!devices.contains(device) && (deviceClass == null || TextUtils.equals(device.getDeviceClass(), deviceClass))) {
                devices.add(device);
            }
        }

        // Bluetooth Low Energy
        for (Map.Entry<String, UniDevice> entry : this.mBtManager.getLastBondedBleDevices().entrySet()) {
            device = entry.getValue();
            if (!devices.contains(device) && (deviceClass == null || TextUtils.equals(device.getDeviceClass(), deviceClass))) {
                devices.add(device);
            }
        }

        // Wi-Fi
        for (Map.Entry<String, UniDevice> entry : getLastBondedWifiDevices().entrySet()) {
            device = entry.getValue();
            if (!devices.contains(device) && (deviceClass == null || TextUtils.equals(device.getDeviceClass(), deviceClass))) {
                devices.add(device);
            }
        }

        return devices;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String getLastConnectedUniDeviceInfo(String deviceClass) throws JSONException {
        return UniDevice.getDeviceInfoJsonArray(
            this.getLastConnectedUniDevices(deviceClass)
        ).toString();
    }



    /*===================*
     * State Observer    *
     *===================*/

    public static final int STATE_DISCOVERY_SEARCHING   = 1;
    public static final int STATE_DISCOVERY_END         = 2;
    public static final int STATE_CONNECTING            = 3;
    public static final int STATE_CONNECTED             = 4;
    public static final int STATE_DISCONNECTING         = 5;
    public static final int STATE_DISCONNECTED          = 6;
    public static final int STATE_UNBONDING             = 7;
    public static final int STATE_UNBONDED              = 8;

    public static final int STATE_ERROR_UNKNOWN         = -1;

    private final RemoteCallbackList<IUniDeviceConnectionCallback> mCallbackList = new RemoteCallbackList<>();
    private final ReentrantLock mCallbackListLock = new ReentrantLock();

    private void registerCallback(IUniDeviceConnectionCallback callback) {
        this.mCallbackList.register(callback);
    }

    private void unregisterCallback(IUniDeviceConnectionCallback callback) {
        this.mCallbackList.unregister(callback);
    }

    private void callbackAllOnDiscoveryStateChanged(String deviceList, int state) throws RemoteException {
        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    callback.onDiscoveryStateChanged(deviceList, state);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error when callback all on discovery state changed.", e);
            e.printStackTrace();
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();
        }
    }

    private void callbackAllOnDiscoveryStateChangedAndBroadcastOnlyOnce(String deviceList, int state) throws RemoteException {
        Log.d(TAG, "To broadcast by on discovery state changed: " + deviceList);

        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    callback.onDiscoveryStateChanged(deviceList, state);
                }
            }
        } catch (RemoteException eRemote) {
            Log.e(TAG, "Error when callback all on discovery state changed.", eRemote);
            eRemote.printStackTrace();
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();

            this.broadcastInfo(deviceList);
        }
    }

    private void callbackAllOnConnectionStateChanged(String deviceInfo, int state) throws RemoteException {
        if (DEBUG_CONNECT) {
            Log.d(TAG, "callback all on connection state changed");
        }
        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();

        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback size: " + count);
        }
        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    if (DEBUG_CONNECT) {
                        Log.d(TAG, "callback: on connection state changed:{deviceInfo=" + deviceInfo + ", state=" + state + "}");
                    }
                    callback.onConnectionStateChanged(deviceInfo, state);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error when callback all on connection state changed.", e);
            e.printStackTrace();
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();
        }
    }

    private void callbackAllOnConnectionStateChanged(UniDevice device, int state) throws RemoteException {

        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback all on connection state changed (input uni-device)");
        }

        String info = "";
        try {
            JSONObject object = UniDevice.toJSONObject(device);
            info = object.toString();

        } catch (JSONException e) {
            Log.e(TAG, "Error when callback all on connection state changed.", e);
            e.printStackTrace();
        } finally {
            this.callbackAllOnConnectionStateChanged(info, state);
        }

    }

    private void callbackAllOnBondStateChanged(String deviceInfo, int state) throws RemoteException {
        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback all on bond state changed");
        }

        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();

        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback size: " + count);
        }

        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    if (DEBUG_REMOVE_BOND) {
                        Log.d(TAG, "callback: on unbond state changed:{deviceInfo=" + deviceInfo + ", state=" + state + "}");
                    }
                    callback.onUnbondStateChanged(deviceInfo, state);
                }
            }
        } catch (RemoteException e) {
            throw e;
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();
        }
    }

    private void callbackAllOnBondStateChanged(UniDevice device, int state) throws RemoteException {
        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback all on bond state changed (input uni-device)");
        }

        try {
            JSONObject object = UniDevice.toJSONObject(device);

            this.callbackAllOnBondStateChanged(object.toString(), state);
        } catch (JSONException e) {
            Log.e(TAG, "Error when callback all on bond state changed.", e);
            e.printStackTrace();
        }
    }

    private void callbackAllOnConnectionFailed(String deviceInfo, int errorCode) throws RemoteException {
        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    callback.onConnectionFailed(deviceInfo, errorCode);
                }
            }
        } catch (RemoteException e) {
            throw e;
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();
        }
    }

    private void callbackAllOnConnectionFailed(UniDevice device, int errorCode) throws RemoteException {

        try {
            JSONObject object = UniDevice.toJSONObject(device);

            this.callbackAllOnConnectionFailed(object.toString(), errorCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error when callback all on connection failed.", e);
            e.printStackTrace();
        }

    }

    private void callbackAllOnUnbondFailed(String deviceInfo, int errorCode) throws RemoteException {
        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "callback all on unbond failed");
        }
        this.mCallbackListLock.lock();

        int count = this.mCallbackList.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                IUniDeviceConnectionCallback callback = this.mCallbackList.getBroadcastItem(i);
                if (callback != null) {
                    if (DEBUG_REMOVE_BOND) {
                        Log.d(TAG, "callback: on unbond failed:{deviceInfo=" + deviceInfo + ", errorCode=" + errorCode + "}");
                    }
                    callback.onUnbondFailed(deviceInfo, errorCode);
                }
            }
        } catch (RemoteException e) {
            throw e;
        } finally {
            this.mCallbackList.finishBroadcast();
            this.mCallbackListLock.unlock();
        }
    }

    private void callbackAllOnUnbondFailed(UniDevice device, int errorCode) throws RemoteException {

        try {
            JSONObject object = UniDevice.toJSONObject(device);

            this.callbackAllOnUnbondFailed(object.toString(), errorCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error when callback all on unbond failed.", e);
            e.printStackTrace();
        }

    }

    /*===================*
     * Discovery         *
     *===================*/

    private boolean mIsBcDiscovering;
    private boolean mIsBleDiscovering;
    private boolean mIsWifiDiscovering;

    private boolean isDiscoveryEnd() {
        return (!this.mIsBcDiscovering
                && !this.mIsBleDiscovering
                && !this.mIsWifiDiscovering
        );
    }

    private void clear() {
        this.mDevices.clear();
        this.mBtManager.clear();
        // TODO: Wi-Fi
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void startDiscoverUniDevice(IUniDeviceConnectionCallback callback) {
        if (DEBUG_DISCOVERY) {
            Log.d(TAG, "Start discover uni-device.");
            Log.d(TAG, "callback == null? " + (callback == null));
        }

        if (callback != null) {
            this.registerCallback(callback);
            // Broadcast
            try {
                this.callbackAllOnDiscoveryStateChangedAndBroadcastOnlyOnce(
                    this.updateLastFoundUniDeviceInfo(),
                    STATE_DISCOVERY_SEARCHING
                );
            } catch (RemoteException | JSONException e) {
                Log.e(TAG, "Error when start discover uni-device.", e);
                e.printStackTrace();
            }
        }

        // Bluetooth
        this.mBtManager.startBtDiscovery();

        // Wi-Fi
        startWifiDiscovery();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void stopDiscoverUniDevice(IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        // Bluetooth
        this.mBtManager.stopBluetoothDiscovery();

        // Wi-Fi
        stopWifiDiscovery();
    }



    /*==============*
     * Connect      *
     *==============*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final String address) {
        if (DEBUG_CONNECT) {
            Log.d(TAG, "Try to connect uni-device.");
        }

        UniDevice device = this.mDevices.get(address);

        if (DEBUG_CONNECT) {
            Log.d(TAG, "Address: " + address);
            Log.d(TAG, "Is device null ? " + (device == null));
            Log.d(TAG, "Devices: " + this.mDevices);
        }

        if (device != null) {
            int type = device.getProtocolType();

            if (DEBUG_CONNECT) {
                Log.d(TAG, String.format("Device info: {name=%s, protocol=%d, address=%s}", device.getDeviceName(), type, address));
            }

            // Classic Bluetooth
            if (UniDevice.isBcDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect classic bluetooth.");
                }

                this.mBtManager.connectBc(address);
                return true;
            }

            // Bluetooth Low Energy
            else if (UniDevice.isBleDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect bluetooth low erengy.");
                }

                this.mBtManager.connectBle(address);
                return true;
            }

            // Wi-Fi
            else if (UniDevice.isWifiDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect Wi-Fi.");
                }

                connectWifiClient(address);
                return true;
            }
        }

        return false;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final String deviceClass, final String address) {
        if (DEBUG_CONNECT) {
            Log.d(TAG, "Try to connect uni-device.");
        }

        UniDevice device = this.mDevices.get(address);

        if (DEBUG_CONNECT) {
            Log.d(TAG, "Is device null ? " + (device == null));
        }

        if (device != null && TextUtils.equals(device.getDeviceClass(), deviceClass)) {
            int type = device.getProtocolType();

            if (DEBUG_CONNECT) {
                Log.d(TAG, String.format("Device info: {name=%s, protocol=%d, address=%s}", device.getDeviceName(), type, address));
            }

            if (UniDevice.isBcDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect classic bluetooth.");
                }

                this.mBtManager.connectBc(address);
                return true;
            }

            if (UniDevice.isBleDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect bluetooth low erengy.");
                }

                this.mBtManager.connectBle(address);
                return true;
            }

            if (UniDevice.isWifiDevice(type)) {
                if (DEBUG_CONNECT) {
                    Log.d(TAG, "Try to connect Wi-Fi.");
                }

                connectWifiClient(address);
                return true;
            }
        }

        return false;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean connectUniDevice(final String deviceClass, final String deviceAddress, IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        return this.connectUniDevice(deviceClass, deviceAddress);
    }



    /*==============*
     * Remove Bond  *
     *==============*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final String address) {
        boolean flag = false;

        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "Try to remove bond uni-device.");
            Log.d(TAG, "Input address: " + address);
            Log.d(TAG, "Last bonded classic bluetooth address set: " + this.mBtManager.getLastBondedBcDeviceAddressSet());
            Log.d(TAG, "Last bonded bluetooth low erengy address set: " + this.mBtManager.getLastBondedBleDeviceAddressSet());
        }


        if (this.mBtManager.getLastBondedBcDeviceAddressSet().contains(address)) {
            this.mBtManager.removeBondBc(address);
            flag = true;
        }

        if (this.mBtManager.getLastBondedBleDeviceAddressSet().contains(address)) {
            this.mBtManager.removeBondBle(address);
            flag = true;
        }

        // Wi-Fi
        if (getLastBondedWifiDeviceAddressSet().contains(address)) {
            removeWifiClient(address);
            flag = true;
        }

        return flag;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final String deviceClass, final String address) {
        boolean flag = false;
        Map<String, UniDevice> map;
        UniDevice device;

        if (DEBUG_REMOVE_BOND) {
            Log.d(TAG, "Try to remove bond uni-device.");
            Log.d(TAG, "Input address: " + address);
            Log.d(TAG, "Input device class: " + deviceClass);
            Log.d(TAG, "Last bonded classic bluetooth address set: " + this.mBtManager.getLastBondedBcDeviceAddressSet());
            Log.d(TAG, "Last bonded bluetooth low erengy address set: " + this.mBtManager.getLastBondedBleDeviceAddressSet());
        }

        // Classic Bluetooth
        map = this.mBtManager.getLastBondedBcDevices();
        if (map.containsKey(address)) {
            device = map.get(address);
            if (TextUtils.equals(device.getDeviceClass(), deviceClass)) {
                this.mBtManager.removeBondBc(address);
                flag = true;
            }
        }

        // Bluetooth Low Energy
        map = this.mBtManager.getLastBondedBleDevices();
        if (map.containsKey(address)) {
            device = map.get(address);
            if (TextUtils.equals(device.getDeviceClass(), deviceClass)) {
                this.mBtManager.removeBondBle(address);
                flag = true;
            }
        }

        // Wi-Fi
        if (getLastBondedWifiDeviceAddressSet().contains(address)) {
            removeWifiClient(address);
            flag = true;
        }

        return flag;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private boolean removeBondedUniDevice(final String deviceClass, final String deviceAddress, IUniDeviceConnectionCallback callback) {
        this.registerCallback(callback);

        return this.removeBondedUniDevice(deviceClass, deviceAddress);
    }



    /*===================*
     * IPC               *
     *===================*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private String broadcastInfo(final String info) {

        if (!(TextUtils.isEmpty(info) || TextUtils.equals(info, "[]"))) {
            Intent intent = new Intent();
            intent.setAction("com.rxw.ACTION_DISCOVERED_DEVICES");
            intent.setPackage("com.rxw.car.panconnection");
            intent.putExtra("discovered_devices", info);
            PanConnectionService.this.sendBroadcast(intent);
        }

        return info;
    }

    private final class PanConnectionServiceBinder extends IUniDeviceConnection.Stub {

        /**
         * 发现泛设备
         *
         * @param uniConnectionCallback 发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_SEARCHING 给外部
         */
        @Override
        public void discoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            if (DEBUG_CALL) {
                Log.d(TAG, "call: discoverUniDevice");
            }

            PanConnectionService.this.startDiscoverUniDevice(uniConnectionCallback);

        }

        /**
         * 停止发现泛设备
         *
         * @param uniConnectionCallback 停止发现泛设备后，通过此callback回调已发现的设备列表和 DISCOVERY_END 给外部
         */
        @Override
        public void stopDiscoverUniDevice(IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            PanConnectionService.this.stopDiscoverUniDevice(uniConnectionCallback);
        }

        /**
         * 连接泛设备
         *
         * @param deviceType            泛设备的类型 ：摄像头:camera,香氛机:aroma
         * @param deviceId              泛设备的唯一标识id
         * @param uniConnectionCallback 连接泛设备的回调接口，连接状态通过此callback回调给外部
         */
        @Override
        public void connectUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            Log.d(TAG, "connect uni-device: {deviceType=" + deviceType + ", deviceId=" + deviceId + "}");
            PanConnectionService.this.connectUniDevice(deviceType, deviceId, uniConnectionCallback);
        }

        /**
         * 解绑泛设备
         *
         * @param deviceType            泛设备的类型: 摄像头:camera,香氛机:aroma
         * @param deviceId              泛设备的唯一标识id
         * @param uniConnectionCallback 解绑泛设备的回调接口，解绑状态通过此callback回调给外部
         */
        @Override
        public void removeBondedUniDevice(String deviceType, String deviceId, IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            PanConnectionService.this.removeBondedUniDevice(deviceType, deviceId, uniConnectionCallback);
        }


        /**
         * 获取当前某个类型下已经连接上的泛设备
         *
         * @param deviceType 泛设备的类型，
         * @return 当前连接上的泛设备列表
         */
        @Override
        public String getConnectedUniDevices(String deviceType) throws RemoteException {
            try {
                return PanConnectionService.this.getLastConnectedUniDeviceInfo(deviceType);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * 移除已经注册的Callback；
         * @param uniConnectionCallback 回调接口
         */
        @Override
        public void unregisterCallback(IUniDeviceConnectionCallback uniConnectionCallback) throws RemoteException {
            PanConnectionService.this.unregisterCallback(uniConnectionCallback);
        }

        /**
         * 注册虚拟摄像头
         * @param cameraId 泛设备摄像头id
         * @param registerAsFrontCamera 是否注册为后置摄像头
         */
        @Override
        public int registerVirtualCamera(String cameraId, boolean registerAsRearCamera) {
            return PanCameraManager.getInstance(PanConnectionService.this).registerPanCamera(cameraId, registerAsRearCamera);
        }

        /**
         * 反注册虚拟摄像头
         * @param cameraId 泛设备摄像头id
         */
        @Override
        public int unregisterVirtualCamera(String cameraId, boolean unregisterRearCamera) {
            return PanCameraManager.getInstance(PanConnectionService.this).unregisterPanCamera(cameraId, unregisterRearCamera);
        }

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: " + intent);

        // Start
        this.startDiscoverUniDevice(null);

        return new PanConnectionServiceBinder();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        // Initialize and start WiFi AP
        startWifiAp();

        // Wi-Fi
        mWifiTetheringHandler.registerObserver(new WifiTetheringObserver() {
            @Override
            public void onDiscoveryStateChanged(String deviceList, int discoveryState) {
                Log.d(TAG, "wifi--onDiscoveryStateChanged, deviceList: " + deviceList + ", discoveryState: " + discoveryState);
                try {
                    PanConnectionService.this.callbackAllOnDiscoveryStateChanged(
                            PanConnectionService.this.updateLastFoundUniDeviceInfo(),
                            discoveryState
                    );
                } catch (JSONException | RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onConnectionStateChanged(String device, int connectionState)
            {
                Log.d(TAG, "wifi--onConnectionStateChanged, device: " + device + ", connectionState: " + connectionState);

                try {
                    PanConnectionService.this.callbackAllOnConnectionStateChanged(device, connectionState);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error notifying connection state changed", e);
                }
            }

            @Override
            public void onConnectionFailed(String device, int errorCode)
            {
                Log.d(TAG, "wifi--onConnectionFailed, device: " + device + ", errorCode: " + errorCode);

                try {
                    PanConnectionService.this.callbackAllOnConnectionFailed(device, errorCode);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error notifying connection failed", e);
                }
            }

            @Override
            public void onUnbondStateChanged(String device, int bondState)
            {
                Log.d(TAG, "wifi--onUnbondStateChanged, device: " + device + ", bondState: " + bondState);

                try {
                    PanConnectionService.this.callbackAllOnBondStateChanged(device, bondState);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error notifying connection state changed", e);
                }
            }

            @Override
            public void onUnbondFailed(String device, int errorCode)
            {
                Log.d(TAG, "wifi--onUnbondFailed, device: " + device + ", errorCode: " + errorCode);

                try {
                    PanConnectionService.this.callbackAllOnUnbondFailed(device, errorCode);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error notifying unbond failed", e);
                }
            }
        });

        // Whitelist
        this.mWhitelistUtils = new WhitelistUtils(this);
        /// Allowed Devices
        this.mWhitelistUtils.saveAllowedDeviceNames(
            new String[] {
                "ai-thinker",
                "yinxiang"
            }
        );
        /// Auto Connect Device
        try {
            this.mAutoConnectDevices = this.mWhitelistUtils.loadAutoConnectDevices();
            Log.d(TAG, "mWhitelistUtils.loadAutoConnectDevices():" + this.mAutoConnectDevices);
        } catch (JSONException e) {
            Log.e(TAG, "JsonException when load device set.", e);
            e.printStackTrace();
            this.mAutoConnectDevices = new HashSet<>();
        }



        // Bluetooth
        this.mBtManager = new BtManager(this);
        this.mBtManager.registerObserver(new BtUtils.BcStateObserver() {
            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {
                BtUtils.BcStateObserver.super.onDiscoveryStateChanged(list, state);
                if (DEBUG_DISCOVERY) {
                    Log.d(TAG, "onDiscoveryStateChanged (bc)" + list.toString());
                }

                for (BluetoothDevice device : list) {
                    String address = device.getAddress();
                    if (!PanConnectionService.this.mDevices.containsKey(address)) {
                        PanConnectionService.this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()));   // TODO: Multiple Mode.
                    }
                }
                int callbackState = STATE_DISCOVERY_SEARCHING;

                switch (state) {
                    case STATE_DISCOVERY_SEARCHING:
                        PanConnectionService.this.mIsBcDiscovering = true;
                        break;
                    case STATE_DISCOVERY_END:
                        PanConnectionService.this.mIsBcDiscovering = false;
                        if (!PanConnectionService.this.mIsBleDiscovering) {
                            callbackState = STATE_DISCOVERY_END;
                        }
                        break;
                    default:
                        return;
                }

                try {
                    PanConnectionService.this.callbackAllOnDiscoveryStateChangedAndBroadcastOnlyOnce(
                            PanConnectionService.this.updateLastFoundUniDeviceInfo(),
                            callbackState
                    );
                } catch (JSONException | RemoteException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                BtUtils.BcStateObserver.super.onConnectionStateChanged(device, state);
                try {
                    PanConnectionService.this.callbackAllOnConnectionStateChanged(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onBondStateChanged(BluetoothDevice device, int state) {
                BtUtils.BcStateObserver.super.onBondStateChanged(device, state);

                try {
                    PanConnectionService.this.callbackAllOnBondStateChanged(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()), state);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BcStateObserver.super.onConnectionFailed(device, errorCode);
                try {
                    PanConnectionService.this.callbackAllOnConnectionFailed(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onUnbondFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BcStateObserver.super.onUnbondFailed(device, errorCode);
                try {
                    PanConnectionService.this.callbackAllOnUnbondFailed(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName()), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        this.mBtManager.registerObserver(new BtUtils.BleStateObserver() {
            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {
                if (DEBUG_DISCOVERY) {
                    Log.d(TAG, "onDiscoveryStateChanged (ble)" + list.toString());
                }
                BtUtils.BleStateObserver.super.onDiscoveryStateChanged(list, state);

                for (BluetoothDevice device : list) {
                    String address = device.getAddress();
                    String name = device.getName();


                    if (!PanConnectionService.this.mDevices.containsKey(address)) {
                        if (DEBUG_DISCOVERY) {
                            Log.d(TAG, "mDevices not contain key");
                        }
                        PanConnectionService.this.mDevices.put(address, new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, name));   // TODO: Multiple Mode.
                    }

                    if (DEBUG_AUTO_CONNECT) {
                        Log.d(TAG, "Device: {address=" + address + ", name=" + name + "}");
                        Log.d(TAG, "mAutoConnectDevices:" + PanConnectionService.this.mAutoConnectDevices);
                    }


                    for (UniDevice uniDevice : PanConnectionService.this.mAutoConnectDevices) {
                        if (
                            TextUtils.equals(uniDevice.getDeviceName(), name) &&
                            TextUtils.equals(uniDevice.getAddress(), address) &&
                            UniDevice.isBleDevice(uniDevice.getProtocolType())
                        ) {
                            PanConnectionService.this.mBtManager.connectBle(address);
                        }
                    }
                }

                if (DEBUG_DISCOVERY) {
                    Log.d(TAG, "mDevices.keySet " + PanConnectionService.this.mDevices.keySet().toString());
                }


                int callbackState = STATE_DISCOVERY_SEARCHING;

                switch (state) {
                    case STATE_DISCOVERY_SEARCHING:
                        PanConnectionService.this.mIsBleDiscovering = true;


                        break;
                    case STATE_DISCOVERY_END:
                        PanConnectionService.this.mIsBleDiscovering = false;
                        if (!PanConnectionService.this.mIsBcDiscovering) {
                            callbackState = STATE_DISCOVERY_END;
                        }
                        break;
                    default:
                        return;
                }

                try {
                    PanConnectionService.this.callbackAllOnDiscoveryStateChangedAndBroadcastOnlyOnce(
                            PanConnectionService.this.updateLastFoundUniDeviceInfo(),
                            callbackState
                    );
                } catch (JSONException | RemoteException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                BtUtils.BleStateObserver.super.onConnectionStateChanged(device, state);
                try {
                    Log.d(TAG, "call: Observer onConnectionStateChanged");

                    if (state == STATE_CONNECTED) {
                        try {
                            UniDevice uniDevice = new UniDevice(
                                device.getAddress(),
                                UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE,
                                device.getName()
                            );
                            Log.d(TAG, "mWhitelistUtils.addAutoConnectDevice");
                            PanConnectionService.this.mWhitelistUtils.addAutoConnectDevice(uniDevice);
                            Log.d(TAG, "mAutoConnectDevices.add");
                            PanConnectionService.this.mAutoConnectDevices.add(uniDevice);
                        } catch (JSONException e) {
                            Log.e(TAG, "JsonException when auto connect device.", e);
                            e.printStackTrace();
                        }
                    }

                    // TODO: remove auto?

                    Log.d(TAG, "Before call: callbackAllOnConnectionStateChanged: state=" + state);

                    UniDevice uniDevice = new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName());

                    Log.d(TAG, "New uni-device: " + uniDevice);
                    PanConnectionService.this.callbackAllOnConnectionStateChanged(uniDevice, state);
                    // PanConnectionService.this.callbackAllOnConnectionStateChanged(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()), state);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error when onConnectionStateChanged", e);
                    e.printStackTrace();
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onBondStateChanged(BluetoothDevice device, int state) {
                BtUtils.BleStateObserver.super.onBondStateChanged(device, state);

                if (DEBUG_REMOVE_BOND) {
                    Log.d(TAG, "Before callbackAllOnBondStateChanged");
                }

                try {
                    PanConnectionService.this.callbackAllOnBondStateChanged(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()), state);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error when onBondStateChanged", e);
                    e.printStackTrace();
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onConnectionFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BleStateObserver.super.onConnectionFailed(device, errorCode);
                try {
                    PanConnectionService.this.callbackAllOnConnectionFailed(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onUnbondFailed(BluetoothDevice device, int errorCode) {
                BtUtils.BleStateObserver.super.onUnbondFailed(device, errorCode);

                try {
                    PanConnectionService.this.callbackAllOnUnbondFailed(new UniDevice(device.getAddress(), UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName()), errorCode);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // Device Filter
        this.mDeviceFilter = new DeviceFilter(this);
        this.mBtManager.registerDeviceFilter(this.mDeviceFilter);

        // Others
        setForegroundService();

        CommandManager.getInstance(this).subscribeDDSCommand();
        CarHvacController.getInstance(this).init();

        // DDS
        IUniConnectionServiceControl control = UniConnectionServiceManager.getInstance();
        control.start(new IUniConnectionServiceCallback() {
            @Override
            public Optional<String> getConnectedUniDevices(String deviceClass) {
                Log.d(TAG, "dds getConnectedUniDevices, deviceType: " + deviceClass);

                Set<UniDevice> devices = new HashSet<>();
                UniDevice device;

                // Classic Bluetooth
                for (Map.Entry<String, UniDevice> entry : PanConnectionService.this.mBtManager.getLastBondedBcDevices().entrySet()) {
                    device = entry.getValue();
                    Log.d(TAG, "classic bt device: " + device);
                    if (!devices.contains(device) && TextUtils.equals(device.getDeviceClass(), deviceClass) || "all".equals(deviceClass)) {
                        Log.d(TAG, "Added classic bt device: " + device);
                        devices.add(device);
                    }
                }

                // Bluetooth Low Energy
                for (Map.Entry<String, UniDevice> entry : PanConnectionService.this.mBtManager.getLastBondedBleDevices().entrySet()) {
                    device = entry.getValue();
                    Log.d(TAG, "ble device: " + device);
                    if (!devices.contains(device) && TextUtils.equals(device.getDeviceClass(), deviceClass) || "all".equals(deviceClass)) {
                        Log.d(TAG, "Added ble device: " + device);
                        devices.add(device);
                    }
                }

                // Wi-Fi
                for (Map.Entry<String, UniDevice> entry : getLastBondedWifiDevices().entrySet()) {
                    device = entry.getValue();
                    Log.d(TAG, "wifi device: " + device);
                    if (!devices.contains(device) && TextUtils.equals(device.getDeviceClass(), deviceClass)|| "all".equals(deviceClass)) {
                        Log.d(TAG, "Added wifi device: " + device);
                        devices.add(device);
                    }
                }

                Log.d(TAG, "dds getConnectedUniDevices, devices.Size: " + devices.size());

                try {
                    if (!devices.isEmpty()) {
                        return Optional.of(UniDevice.getDeviceInfoJsonArray(devices).toString());
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error when getConnectedUniDevices", e);
                }

                return Optional.empty();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start
        this.startDiscoverUniDevice(null);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        // Bluetooth
        this.mBtManager.onDestroy();

	    // Others
        super.onDestroy();
    }

    private class BtManager {
        private final BtUtils mmBtUtils;

        public BtManager(Context context) {
            this.mmBtUtils = new BtUtils(context);

        }

        /*===================*
         * Getters           *
         *===================*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Set<String> getLastBondedBcDeviceAddressSet() {
            Set<String> addressSet = new HashSet<>();
            for (BluetoothDevice device : this.mmBtUtils.getBondedBcDevices()) {
                addressSet.add(device.getAddress());
            }
            return addressSet;
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Map<String, UniDevice> getLastBondedBcDevices() {
            Map<String, UniDevice> devices = new HashMap<>();

            Consumer<Set<BluetoothDevice>> consumer = set -> {
                for (BluetoothDevice device : set) {
                    String address = device.getAddress();
                    UniDevice uniDevice = new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, device.getName());
                    devices.put(address, uniDevice);
                }
            };

            this.mmBtUtils.getBondedBcDevices(consumer);

            return devices;
        }

        private Set<String> getLastBondedBleDeviceAddressSet() {
            return this.mmBtUtils.getBondedBleDeviceAddressSet();
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private Map<String, UniDevice> getLastBondedBleDevices() {
            Map<String, UniDevice> devices = new HashMap<>();

            Consumer<Map<String, BluetoothGatt>> consumer = map -> {
                for (Map.Entry entry : map.entrySet()) {
                    String address = (String) entry.getKey();
                    BluetoothGatt gatt = (BluetoothGatt) entry.getValue();
                    BluetoothDevice device = gatt.getDevice();
                    UniDevice uniDevice = new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, device.getName());
                    devices.put(address, uniDevice);
                }
            };

            this.mmBtUtils.getBondedBleDevices(consumer);

            return devices;
        }

        private void updateLastFoundBcDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
            this.mmBtUtils.updateLastFoundBcDeviceInfo(consumer);
        }

        private void updateLastFoundBleDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
            this.mmBtUtils.updateLastFoundBleDeviceInfo(consumer);
        }

        private void clear() {
            this.mmBtUtils.clear();
        }



        /*===================*
         * State Observer    *
         *===================*/

        private void registerObserver(BtUtils.BcStateObserver observer) {
            this.mmBtUtils.registerObserver(observer);
        }

        private void registerObserver(BtUtils.BleStateObserver observer) {
            this.mmBtUtils.registerObserver(observer);
        }



        /*===================*
         * State Observer    *
         *===================*/

        private void registerDeviceFilter(DeviceFilter filter) {
            this.mmBtUtils.registerDeviceFilter(filter);
        }

        private void unregisterDeviceFilter() {
            this.mmBtUtils.unregisterDeviceFilter();
        }


        /*===================*
         * Discovery         *
         *===================*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void startBtDiscovery() {
            // this.mmBtUtils.clear();

            /* Discover BLE (Bluetooth Low Energy) Device */
            Log.d(TAG, "Start discover bluetooth low energy devices...");
            this.mmBtUtils.startDiscoveryBle();

            /* Discover BC (Bluetooth Classic) Device */
            Log.d(TAG, "Start discover classic bluetooth devices...");
            this.mmBtUtils.startDiscoveryBc();
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void stopBluetoothDiscovery() {
            /* Discover BLE (Bluetooth Low Energy) Device */
            Log.d(TAG, "Stop discover bluetooth low energy devices...");
            this.mmBtUtils.stopDiscoveryBle();

            /* Discover BC (Bluetooth Classic) Device */
            Log.d(TAG, "Stop discover classic bluetooth devices...");
            this.mmBtUtils.stopDiscoveryBc();
        }

        /*==============*
         * Connect      *
         *==============*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connectBc(final String address) {
            if (DEBUG_CONNECT) {
                Log.d(TAG, "BtUtils try to connect classic bluetooth.");
            }
            this.mmBtUtils.connectBc(address);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connectBle(final String address) {
            if (DEBUG_CONNECT) {
                Log.d(TAG, "BtUtils try to connect bluetooth low erengy.");
            }
            this.mmBtUtils.connectBle(address);
        }

        /*==============*
         * Remove       *
         *==============*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void removeBondBc(final String address) {
            this.mmBtUtils.removeBondBc(address);
            try {
                UniDevice device = this.mmBtUtils.toUniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC);
                PanConnectionService.this.mWhitelistUtils.deleteAutoConnectDevice(device);
                Log.d(TAG, "mWhitelistUtils.deleteAutoConnectDevice: " + device);
                PanConnectionService.this.mAutoConnectDevices.remove(device);
            } catch (JSONException e) {
                Log.e(TAG, "Error when delete auto connect device.", e);
                e.printStackTrace();
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void removeBondBle(final String address) {
            this.mmBtUtils.removeBondBle(address);
            try {
                UniDevice device = this.mmBtUtils.toUniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE);

                if (DEBUG_AUTO_CONNECT) {
                    Log.d(TAG, "Before remove auto connect device");
                    Log.d(TAG, "Auto connect devices: " + PanConnectionService.this.mAutoConnectDevices);
                    Log.d(TAG, "To be removed: " + device);
                    Log.d(TAG, "After remove auto connect device");
                }


                PanConnectionService.this.mWhitelistUtils.deleteAutoConnectDevice(device);
                PanConnectionService.this.mAutoConnectDevices.remove(device);
                if (DEBUG_AUTO_CONNECT) {
                    Log.d(TAG, "Auto connect devices: " + PanConnectionService.this.mAutoConnectDevices);
                }

            } catch (JSONException e) {
                Log.e(TAG, "Error when delete auto connect device.", e);
                e.printStackTrace();
            }
        }


        private void onDestroy() {
            this.mmBtUtils.onDestroy();
        }
    }



    /*===================================================================*
     * WifiUtils
     *===================================================================*/

    private void startWifiAp() {

        // Get system services for WiFi and tethering
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        TetheringManager tetheringManager = (TetheringManager) getApplicationContext().getSystemService(Context.TETHERING_SERVICE);
        // Initialization
        mWifiTetheringHandler = new WifiTetheringHandler(getApplicationContext(), wifiManager, tetheringManager, new WifiTetheringAvailabilityListener() {
            @Override
            public void onWifiTetheringAvailable() {
                Log.d(TAG, "onWifiTetheringAvailable!");
            }

            @Override
            public void onWifiTetheringUnavailable() {
                Log.d(TAG, "onWifiTetheringUnavailable!");
            }

            @Override
            public void onConnectedClientsChanged(int clientCount) {
                Log.d(TAG, "onConnectedClientsChanged, clientCount: " + clientCount);
            }

            @Override
            public void onDiscoveryWifiDevice(WifiDevice device) {
                if (device != null) {
                    if (!discoveredWifiDevicesList.contains(device)) {
                        discoveredWifiDevicesList.add(device);
                    }
                    String macAddress = device.getAddress();
                    String deviceName = device.getDeviceName();
                    String deviceType = device.getDeviceType();
                    if (!PanConnectionService.this.mDevices.containsKey(macAddress)) {
                        PanConnectionService.this.mDevices.put(macAddress, new UniDevice(macAddress, UniDevice.PROTOCOL_TYPE_WIFI, deviceName, deviceType));
                    }
                    Log.d(TAG, "onDiscoveryWifiDevice, device: " + device.toString());
                }mDevices
            }

            @Override
            public void connectedWifiDevices(List<WifiDevice> connectedWifiDevices) {
                if (connectedWifiDevices != null) {
                    connectedWifiDevicesList.clear();
                    connectedWifiDevicesList.addAll(connectedWifiDevices);
                    for (WifiDevice client : connectedWifiDevicesList) {
                        Log.d(TAG, "onConnectedClientsChanged, client: " + client.toString());
                    }
                } else {
                    connectedWifiDevicesList.clear();
                    Log.d(TAG, "onConnectedClientsChanged, client is null!");
                }
            }
        });

        // Configure and start the hotspot
        mWifiTetheringHandler.softApConfigureAndStartHotspot();
        mWifiTetheringHandler.createMulticast();
        Log.d(TAG, "The WifisoftAP is all completed for the first time!");
    }

    /*===================*
     * Getters           *
     *===================*/
    public List<WifiDevice> getConnectedClients() {
        return connectedWifiDevicesList;
    }
    public List<WifiDevice> getDiscoveredWifiDevices() {
        return discoveredWifiDevicesList;
    }

    private Map<String, UniDevice> getLastBondedWifiDevices() {
        Map<String, UniDevice> devices = new HashMap<>();
        List<WifiDevice> clients = getConnectedClients();
        if (clients != null) {
            Log.d(TAG, "getLastBondedWifiDevices, The clients isnot null!");
            Log.d(TAG, "getLastBondedWifiDevices, clients: " + clients.toString());
            for (WifiDevice client : clients) {
                String macAddress = client.getAddress();
                String deviceName = client.getDeviceName();
                String deviceType = client.getDeviceType();
                devices.put(macAddress, new UniDevice(macAddress, UniDevice.PROTOCOL_TYPE_WIFI, deviceName, deviceType));
            }
            Log.d(TAG, "getLastBondedWifiDevices, devices: " + devices.toString());
        } else {
            Log.d(TAG, "getLastBondedWifiDevices! The clients is null!");
        }

        for (Map.Entry<String, UniDevice> entry : devices.entrySet()) {
            Log.d(TAG, "getLastBondedWifiDevices! MAC Address: " + entry.getKey() + ", UniDevice: " + entry.getValue());
        }

        return devices;
    }

    private Set<String> getLastBondedWifiDeviceAddressSet() {
        Set<String> addressSet = new HashSet<>();
        List<WifiDevice> clients = getConnectedClients();
        if (clients != null) {
            Log.d(TAG, "getLastBondedWifiDeviceAddressSet! The clients isnot null!");
            Log.d(TAG, "getLastBondedWifiDeviceAddressSet!, clients: " + clients.toString());
            for (WifiDevice client : clients) {
                String macAddress = client.getAddress();
                addressSet.add(macAddress);
            }
            Log.d(TAG, "getLastBondedWifiDeviceAddressSet!, addressSet: " + addressSet.toString());
        } else {
            Log.d(TAG, "getLastBondedWifiDeviceAddressSet! The clients is null!");
        }

        return addressSet;
    }

    /*===================*
     * StartDiscovery    *
     *===================*/

    private void startWifiDiscovery() {
        Log.d(TAG, "Start discover wifi devices...");
    }

    /*===================*
     * StopDiscovery     *
     *===================*/

    private void stopWifiDiscovery() {
        Log.d(TAG, "Stop discover wifi devices...");
    }

    /*==============*
     * Connect      *
     *==============*/

    private void connectWifiClient(final String address) {
        MacAddress macAddrObj = MacAddress.fromString(address);
        mWifiTetheringHandler.bondDeviceAndSoftApConfigure(macAddrObj);
        Log.d(TAG, "The device Try to connect SoftAP!");
    }

    /*==============*
     * Remove       *
     *==============*/

    private void removeWifiClient(final String address) {
        MacAddress macAddrObj = MacAddress.fromString(address);
        mWifiTetheringHandler.unbondDeviceAndSoftApConfigure(macAddrObj);
        Log.d(TAG, "The device Try to remove SoftAP!");
    }

    private void setForegroundService() {
        NotificationChannel notificationChannel = new NotificationChannel(
                "PanConnectionService",
                "PanConnectionService",
                NotificationManager.IMPORTANCE_LOW);
        Notification.Builder notificationBuilder =
                new Notification.Builder(getApplicationContext(), "PanConnectionService");
        notificationBuilder.setSmallIcon(android.R.drawable.sym_def_app_icon);
        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);
        startForeground(-999, notificationBuilder.build());
    }
}
