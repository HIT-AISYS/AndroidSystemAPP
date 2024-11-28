package com.rxw.panconnection.service.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rxw.panconnection.service.unidevice.DeviceFilter;
import com.rxw.panconnection.service.unidevice.UniDevice;
import com.rxw.panconnection.service.PanConnectionService;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class BtUtils {

    private static final String TAG = BtUtils.class.getSimpleName();

    protected static final boolean DEBUG_ALL = true;                // TODO
    protected static final boolean DEBUG_BLE = DEBUG_ALL || false;
    protected static final boolean DEBUG_BC = DEBUG_ALL || false;

//    private static final UUID DEFAULT_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Context
    private Context mContext;

    // Bluetooth
    private BluetoothManager mManager;
    private BluetoothAdapter mAdapter;

    // Classic Bluetooth Only
    private BcUtils mBcUtils;
    private final Map<BluetoothDevice, Long> mBcDeviceFoundTimestamp = new HashMap<>(); // Place `BluetoothDevice` objects of bc (classic bluetooth) and ble (bluetooth low energy) separately to allow independent modification if one changes.

    // Bluetooth Low Energy Only
    private BleUtils mBleUtils;
    private final Map<BluetoothDevice, Long> mBleDeviceFoundTimestamp = new HashMap<>();    // Place `BluetoothDevice` objects of bc (classic bluetooth) and ble (bluetooth low energy) separately to allow independent modification if one changes.

    // Filter
    private DeviceFilter mDeviceFilter;


    /*===================*
     * General Methods   *
     *===================*/

    public static BluetoothAdapter initialize(@NonNull Context context) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager.getAdapter();
    }


    private boolean initialize() {
        if (this.mContext == null) {
            Log.e(TAG, "Unable to obtained a Context.");
            return false;
        }

        this.mManager = (BluetoothManager) this.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = this.mManager.getAdapter();

        if (adapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        this.mAdapter = adapter;
        return true;
    }

    public boolean hasComponent() {
        return this.mAdapter != null;
    }

    public boolean isEnabled() {
        if (this.hasComponent()) {
            return this.mAdapter.isEnabled();
        }

        return false;
    }

//    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    public void enable() {
//        if (this.mAdapter != null && !this.mAdapter.isEnabled()) {
//            this.mAdapter.enable();
//        }
//    }
//
//    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    public void disable() {
//        if (this.mAdapter != null && !this.mAdapter.isEnabled()) {
//            this.mAdapter.disable();
//        }
//    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static String getDeviceInfo(BluetoothDevice device) {
        String name = device.getName();
        String address = device.getAddress();
        String info = String.format(
                "%s (%s)",
                name,
                address
        );

        return info;
    }

    public static String getDeviceType(BluetoothDevice device) {
        String type = "unknown";
        String name = device.getName();

        if (name != null && name.contains("ai-thinker")) {
            type = "aroma";
        }

        return type;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static JSONObject getDeviceInfoJson(BluetoothDevice device) throws JSONException {
        String name = device.getName();
        String address = device.getAddress();
        int clazz = device.getBluetoothClass().getDeviceClass();

        JSONObject obj = new JSONObject();
        // obj.put("device_type", clazz);
        obj.put("device_type", BtUtils.getDeviceType(device));
        obj.put("device_name", name);
        obj.put("device_id", address);

        return obj;
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static JSONArray getDeviceInfoJsonArray(List<BluetoothDevice> list) throws JSONException {

        JSONArray array = new JSONArray();
        for (Object obj : list) {

            if (obj instanceof BluetoothDevice) {
                array.put(getDeviceInfoJson((BluetoothDevice) obj));
            }

            if (obj instanceof JSONObject) {
                array.put(obj);
            }

        }

        return array;
    }

    public void updateLastFoundBcDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
        consumer.accept(this.mBcDeviceFoundTimestamp.keySet());
    }

    public void updateLastFoundBleDeviceInfo(Consumer<Set<BluetoothDevice>> consumer) {
        consumer.accept(this.mBleDeviceFoundTimestamp.keySet());
    }

    public UniDevice toUniDevice(String address) {
        BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
        return BtUtils.toUniDevice(device);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static UniDevice toUniDevice(BluetoothDevice device) {
        String address = device.getAddress();
        String name = device.getName();                             // Manifest.permission#BLUETOOTH_CONNECT
        int type = device.getType();                                // Manifest.permission#BLUETOOTH_CONNECT

        int flag = 0;
        flag = (type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL) ? (flag | UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC) : (flag & ~UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC);
        flag = (type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL) ? (flag | UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE) : (flag & ~UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE);

        return new UniDevice(
            address,
            flag,
            name
        );
    }

    public UniDevice toUniDevice(String address, int protocol) {
        BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
        return BtUtils.toUniDevice(device, protocol);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static UniDevice toUniDevice(BluetoothDevice device, int protocol) {
        String address = device.getAddress();
        String name = device.getName();                             // Manifest.permission#BLUETOOTH_CONNECT

        return new UniDevice(
            address,
            protocol,
            name
        );
    }


    /*=============================*
     * Constructor and Destructor  *
     *=============================*/

    public BtUtils(Context context) {
        this.mContext = context;

        boolean result = this.initialize();

        // Bluetooth Classic
        this.mBcUtils = new BcUtils();
        this.mBcUtils.initialize();
        this.mBcUtils.mmStateReceiver = new BroadcastReceiver() {

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device;
                int state;

                switch (action) {
                    case BluetoothDevice.ACTION_FOUND:
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);


                        String address = device.getAddress();   // MAC address.
                        String name = device.getName();         // Device Name.
                        int type = device.getType();            // Device Type: 0: DEVICE_TYPE_UNKNOWN; 1: DEVICE_TYPE_CLASSIC; 2:DEVICE_TYPE_LE; 3:DEVICE_TYPE_DUAL;
                        int clazz = device.getBluetoothClass().getDeviceClass();


                        if (DEBUG_BC) {
                            ParcelUuid[] uuids = device.getUuids();

                            Log.d(TAG, "==========");
                            Log.d(TAG, "Address: " + address);
                            Log.d(TAG, "Name: " + name);
                            Log.d(TAG, "Name is null? : " + (name == null));
                            Log.d(TAG, "Name is \"null\"?: " + (TextUtils.equals(name, "null")));
                            Log.d(TAG, "Type: " + type);
                            if (uuids != null) {
                                for (ParcelUuid uuid : uuids) {
                                    Log.d(TAG, "UUID: " + uuid.getUuid().toString());
                                }
                            } else {
                                Log.d(TAG, "Uuids not found.");
                            }
                        }

                        if (BtUtils.this.mDeviceFilter != null) {
                            if (DEBUG_BC) {
                                Log.d(TAG, "Device Filter is not null.");
                            }
                            if (!BtUtils.this.mDeviceFilter.mFilter.test(new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_CLASSIC, name))) {
                                if (DEBUG_BC) {
                                    Log.d(TAG, "Filtered.");
                                }
                                break;
                            }
                        }

                        if (DEBUG_BC) {
                            Log.d(TAG, "Try to put device.");
                        }

                        if (BtUtils.this.mBcDeviceFoundTimestamp.containsKey(device)) {
                            if (DEBUG_BC) {
                                Log.d(TAG, "Already contain key.");
                            }
                            break;
                        }

                        BtUtils.this.mBcDeviceFoundTimestamp.put(device, System.currentTimeMillis());   // TODO: Is it necessary to detect duplication according to MAC address?

                        for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), PanConnectionService.STATE_DISCOVERY_SEARCHING);
                        }


                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), PanConnectionService.STATE_DISCOVERY_SEARCHING);
                        }

                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                            observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBcDeviceFoundTimestamp.keySet()), PanConnectionService.STATE_DISCOVERY_END);
                        }

                        break;
                    case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                        state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        switch (state) {
                            case BluetoothAdapter.STATE_CONNECTED:
                                for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                                    observer.onConnectionStateChanged(device, PanConnectionService.STATE_CONNECTED);
                                }
                                break;
                            case BluetoothAdapter.STATE_DISCONNECTED:
                                for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                                    observer.onConnectionStateChanged(device, PanConnectionService.STATE_DISCONNECTED);
                                }
                                break;
                            case BluetoothAdapter.STATE_CONNECTING:
                                for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                                    observer.onConnectionStateChanged(device, PanConnectionService.STATE_CONNECTING);
                                }
                                break;
                            case BluetoothAdapter.STATE_DISCONNECTING:
                                for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                                    observer.onConnectionStateChanged(device, PanConnectionService.STATE_DISCONNECTING);
                                }
                                break;
                            default:
                                // TODO: Error
                        }

                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        switch (state) {
                            case BluetoothDevice.BOND_NONE:
                                for (BcStateObserver observer : BtUtils.this.mBcUtils.mmObserverList) {
                                    observer.onBondStateChanged(device, PanConnectionService.STATE_UNBONDED);
                                }
                                if (DEBUG_BC) {
                                    Log.d(TAG, "On Classic Bluetooth Device Bond State Changed: {device=" + device.toString() + ", state=" + PanConnectionService.STATE_UNBONDED + "}");
                                }
                                break;
                            case BluetoothDevice.BOND_BONDING:
                            case BluetoothDevice.BOND_BONDED:
                            default:
                                break;
                        }


                        break;
                    default:
                        break;
                }
            }
        };

        // Bluetooth Low Energy
        this.mBleUtils = new BleUtils();
        this.mBleUtils.initialize();
        this.mBleUtils.mmScanCallback = new ScanCallback() {

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                BluetoothDevice device = result.getDevice();
                String address = device.getAddress();   // MAC address.
                String name = device.getName();         // Device Name.
                int type = device.getType();            // Device Type: 0: DEVICE_TYPE_UNKNOWN; 1: DEVICE_TYPE_CLASSIC; 2:DEVICE_TYPE_LE; 3:DEVICE_TYPE_DUAL;
                int clazz = device.getBluetoothClass().getDeviceClass();


                if (DEBUG_BLE) {
                    ParcelUuid[] uuids = device.getUuids();

                    Log.d(TAG, "==========");
                    Log.d(TAG, "Address: " + address);
                    Log.d(TAG, "Name: " + name);
                    Log.d(TAG, "Name is null? : " + (name == null));
                    Log.d(TAG, "Name is \"null\"?: " + (TextUtils.equals(name, "null")));
                    Log.d(TAG, "Type: " + type);
                    if (uuids != null) {
                        for (ParcelUuid uuid : uuids) {
                            Log.d(TAG, "UUID: " + uuid.getUuid().toString());
                        }
                    } else {
                        Log.d(TAG, "Uuids not found.");
                    }
                }

                if (BtUtils.this.mDeviceFilter != null) {
                    if (DEBUG_BLE) {
                        Log.d(TAG, "Device Filter is not null.");
                    }
                    if (!BtUtils.this.mDeviceFilter.mFilter.test(new UniDevice(address, UniDevice.PROTOCOL_TYPE_BLUETOOTH_LE, name))) {
                        if (DEBUG_BLE) {
                            Log.d(TAG, "Filtered.");
                        }
                        return;
                    }
                }

                if (DEBUG_BLE) {
                    Log.d(TAG, "Try to put device.");
                }


                if (BtUtils.this.mBleDeviceFoundTimestamp.containsKey(device)) {
                    if (DEBUG_BC) {
                        Log.d(TAG, "Already contain key.");
                    }
                    return;
                }

                BtUtils.this.mBleDeviceFoundTimestamp.put(device, System.currentTimeMillis());

                for (BleStateObserver observer : BtUtils.this.mBleUtils.mmObserverList) {
                    observer.onDiscoveryStateChanged(new ArrayList<>(BtUtils.this.mBleDeviceFoundTimestamp.keySet()), PanConnectionService.STATE_DISCOVERY_SEARCHING);
                }

            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                // TODO:
            }
        };


        this.mBleUtils.mmGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                BluetoothDevice device = gatt.getDevice();

                // Successfully connected to the GATT Server.
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (DEBUG_BLE) {
                        Log.d(TAG, "Successfully connected to GATT.");
                    }

                    for (BleStateObserver observer : BtUtils.this.mBleUtils.mmObserverList) {
                        observer.onConnectionStateChanged(device, PanConnectionService.STATE_CONNECTED);
                    }
                }

                // Disconnected from the GATT Server.
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (DEBUG_BLE) {
                        Log.d(TAG, "On Bluetooth Low Erengy device disconnected: {device=" + device.toString() + ", state=" + PanConnectionService.STATE_DISCONNECTED + "}");
                    }

                    // for (BleStateObserver observer : BtUtils.this.mBleUtils.mmObserverList) {
                    //     if (DEBUG_BLE) {
                    //         Log.d(TAG, "Before onConnectionStateChanged: Observer size: " + BtUtils.this.mBleUtils.mmObserverList.size());
                    //     }
                    //     observer.onConnectionStateChanged(device, PanConnectionService.STATE_DISCONNECTED);
                    // }

                    if (DEBUG_BLE) {
                        Log.d(TAG, "Before onConnectionStateChanged: Observer size: " + BtUtils.this.mBleUtils.mmObserverList.size());
                    }
                    BtUtils.this.mBleUtils.mmObserverList.get(0).onConnectionStateChanged(device, PanConnectionService.STATE_DISCONNECTED);


                    String address = device.getAddress();
                    if (BtUtils.this.mBleUtils.mCloseList.contains(address)) {
                        BtUtils.this.mBleUtils.mCloseList.remove(address);
                        BtUtils.this.mBleUtils.close(address);

                        if (DEBUG_BLE) {
                            Log.d(TAG, "Before BleStateObserver.onBondStateChanged");
                        }

                        for (BleStateObserver observer : BtUtils.this.mBleUtils.mmObserverList) {
                            if (DEBUG_BLE) {
                                Log.d(TAG, "Before observer.onBondStateChanged: Observer size: " + BtUtils.this.mBleUtils.mmObserverList.size());
                            }
                            observer.onBondStateChanged(device, PanConnectionService.STATE_UNBONDED);
                        }

                    }
                }

            }
        };
    }

    public void clear() {
        this.mBcDeviceFoundTimestamp.clear();
        this.mBleDeviceFoundTimestamp.clear();
    }


    public void onDestroy() {
        if (this.mBcUtils != null) {
            this.mBcUtils.onDestroy();
        }

        if (this.mBleUtils != null) {
            this.mBleUtils.onDestroy();
        }
    }



    /*=============================*
     * BC (Bluetooth Classic) Only *
     *=============================*/

    // Discovery
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void startDiscoveryBc() {
        if (this.mBcUtils != null) {
            this.mBcUtils.startDiscovery();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void stopDiscoveryBc() {
        if (this.mBcUtils != null) {
            this.mBcUtils.stopDiscovery();
        }
    }

    // Connect & Disconnect
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectBc(String address) {
        if (this.mBcUtils != null) {
            if (DEBUG_BC) {
                Log.d(TAG, "BcUtils try to connect classic bluetooth.");
            }
            this.mBcUtils.connect(address);
        }
    }

    public void removeBondBc(String address) {
        if (this.mBcUtils != null) {
            if (DEBUG_BC) {
                Log.d(TAG, "Try to remove bond classic bluetooth.");
            }
            this.mBcUtils.removeBond(address);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public Set<BluetoothDevice> getBondedBcDevices() {
        if (this.mBcUtils != null) {
            return this.mBcUtils.getBondedDevices();
        }

        return null;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void getBondedBcDevices(@NonNull Consumer<Set<BluetoothDevice>> consumer) {
        if (this.mBcUtils != null) {
            this.mBcUtils.getBondedDevices(consumer);
        }
    }


    /*==================================*
     * BLE (Bluetooth Low Energy) Only  *
     *==================================*/

    // Discovery
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void startDiscoveryBle() {
        if (this.mBleUtils != null) {
            this.mBleUtils.startDiscovery();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public void stopDiscoveryBle() {
        if (this.mBleUtils != null) {
            this.mBleUtils.stopDiscovery();
        }
    }

    // Connect & Disconnect
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connectBle(final String address) {
        if (this.mBleUtils != null) {
            if (DEBUG_BLE) {
                Log.d(TAG, "BleUtils try to connect bluetooth low erengy.");
            }
            this.mBleUtils.connect(address);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void disconnectBle(String address) {
        if (this.mBleUtils != null) {
            this.mBleUtils.disconnect(address);
        }
    }

    public Set<String> getBondedBleDeviceAddressSet() {
        if (this.mBleUtils != null) {
            return this.mBleUtils.getBondedDeviceAddressSet();
        }

        return null;
    }

    public void getBondedBleDevices(@NonNull Consumer<Map<String, BluetoothGatt>> consumer) {
        if (this.mBleUtils != null) {
            this.mBleUtils.getBondedDevices(consumer);
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void removeBondBle(@NonNull String address) {
        if (this.mBleUtils != null) {
            if (DEBUG_BLE) {
                Log.d(TAG, "Try to remove bond bluetooth low erengy.");
            }
            this.mBleUtils.closeAfterDisconnection(address);
            this.mBleUtils.disconnect(address);
        }
    }


    /*===================*
     * Observer          *
     *===================*/

    public interface BcStateObserver {
        default void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {}
        default void onConnectionStateChanged(BluetoothDevice device, int state) {}
        default void onBondStateChanged(BluetoothDevice device, int state) {}
        default void onConnectionFailed(BluetoothDevice device, int errorCode) {}
        default void onUnbondFailed(BluetoothDevice device, int errorCode) {}
    }

    public boolean registerObserver(BcStateObserver observer) {
        if (this.mBcUtils.mmObserverList.contains(observer)) {
            return false;
        }

        this.mBcUtils.mmObserverList.add(observer);
        return true;
    }

    public boolean unregisterObserver(BcStateObserver observer) {
        return this.mBcUtils.mmObserverList.remove(observer);
    }

    public interface BleStateObserver {
        default void onDiscoveryStateChanged(List<BluetoothDevice> list, int state) {}
        default void onConnectionStateChanged(BluetoothDevice device, int state) {}
        default void onBondStateChanged(BluetoothDevice device, int state) {}
        default void onConnectionFailed(BluetoothDevice device, int errorCode) {}
        default void onUnbondFailed(BluetoothDevice device, int errorCode) {}
    }

    public boolean registerObserver(BleStateObserver observer) {
        if (this.mBleUtils.mmObserverList.contains(observer)) {
            return false;
        }

        this.mBleUtils.mmObserverList.add(observer);
        return true;
    }

    public boolean unregisterObserver(BleStateObserver observer) {
        return this.mBleUtils.mmObserverList.remove(observer);
    }



    /*===================*
     * Device Filter     *
     *===================*/

    public boolean registerDeviceFilter(DeviceFilter filter) {
        this.mDeviceFilter = filter;
        return true;
    }

    public boolean unregisterDeviceFilter() {
        if (this.mDeviceFilter == null) {
            return false;
        }

        this.mDeviceFilter = null;
        return true;
    }

    /*===================*
     * Static methods    *
     *===================*/

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private static Set<BluetoothDevice> getBondedBcDevices(BluetoothAdapter adapter) {
        return adapter.getBondedDevices();
    }



    /*==============*
     * Utils        *
     *==============*/

    private class BcUtils {



        /*=============================*
         * Constructor and Destructor  *
         *=============================*/
        private void initialize() {
            this.registerStateReceiver();
        }

        private void onDestroy() {
            this.unregisterStateReceiver();
        }




        /*===================*
         * State Observer    *
         *===================*/

        private final List<BcStateObserver> mmObserverList = new ArrayList<>();

        private BroadcastReceiver mmStateReceiver;

        public void registerStateReceiver() {
            IntentFilter filter = new IntentFilter();

            // Action
            /// Discovery
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            /// Connect
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

            // Register.
            BtUtils.this.mContext.registerReceiver(this.mmStateReceiver, filter);
        }


        public void unregisterStateReceiver() {
            BtUtils.this.mContext.unregisterReceiver(this.mmStateReceiver);
        }



        /*==============*
         * Discovery    *
         *==============*/

        //    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void startDiscovery() {
            if (DEBUG_BC) {
                Log.d(TAG, "startDiscoveryBc");
            }

            boolean result = BtUtils.this.mAdapter.startDiscovery();
            if (DEBUG_BC) {
                Log.d(TAG, "startDiscovery result: " + result);
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void stopDiscovery() {
            BtUtils.this.mAdapter.cancelDiscovery();
        }

        /*==============*
         * Connect      *
         *==============*/

        private class ConnectThread extends Thread {
            private final BluetoothSocket mmmSocket;
            private final BluetoothDevice mmmDevice;

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            public ConnectThread(BluetoothDevice device) {
                this.mmmDevice = device;

                BluetoothSocket socket = null;

                try {
                    socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
                } catch (Exception e2) {
                    Log.e(TAG, "Socket's create() method failed", e2);
                    e2.printStackTrace();
                }

                this.mmmSocket = socket;
            }

            @Override
            @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
            public void run() {
                super.run();

                // Cancel discovery because it otherwise slows down the connection.
                Log.d(TAG, "Close discovery before connect.");
                BtUtils.this.mAdapter.cancelDiscovery();

                if (DEBUG_BC) {
                    Log.d(TAG, "To connect Name: " + this.mmmDevice.getName());
                    Log.d(TAG, "To connect Address: " + this.mmmDevice.getAddress());
                }

                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    this.mmmSocket.connect();
//                Log.d(TAG, "Connected?");   // TODO: check!
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and return.
                    try {
                        mmmSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Could not close the client socket", closeException);
                    }
                    return;
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.

                // TODO


            }

            // Closes the client socket and causes the thread to finish.
            public void cancel() {
                try {
                    mmmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close the client socket", e);
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connect(BluetoothDevice device) {
            Thread thread = new ConnectThread(device);
            thread.start();
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private void connect(final String address) {
            if (DEBUG_BC) {
                Log.d(TAG, "BcUtils try to get remote device.");
            }
            BluetoothDevice device = BtUtils.this.mAdapter.getRemoteDevice(address);
            if (DEBUG_BC) {
                Log.d(TAG, "BcUtils start a new connect thread.");
            }
            Thread thread = new ConnectThread(device);
            thread.start();
        }



        /*==============*
         * Remove Bond  *
         *==============*/

        public void removeBond(@NonNull BluetoothDevice device) {
            try {
                Method method = BluetoothDevice.class.getMethod("removeBond");
                method.invoke(device);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }

        }

        public void removeBond(final String address) {
            BluetoothDevice device = BtUtils.this.mAdapter.getRemoteDevice(address);
            this.removeBond(device);
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public Set<BluetoothDevice> getBondedDevices() {
            return BtUtils.getBondedBcDevices(BtUtils.this.mAdapter);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public void getBondedDevices(Consumer<Set<BluetoothDevice>> consumer) {
            consumer.accept(BtUtils.getBondedBcDevices(BtUtils.this.mAdapter));
        }

        /*==============*
         * Close        *
         *==============*/

    }

    private class BleUtils {

        /*=============================*
         * Constructor and Destructor  *
         *=============================*/

        private void initialize() {
            this.mmScanner = BtUtils.this.mAdapter.getBluetoothLeScanner();
        }

        private void onDestroy() {

        }


        /*==============*
         * Observer     *
         *==============*/
        private final List<BleStateObserver> mmObserverList = new ArrayList<>();



        /*==============*
         * Discovery    *
         *==============*/

        private static final long SCAN_PERIOD_DEFAULT = 30 * 1000;  // 30s
        private boolean mmIsScanning = false;
        private BluetoothLeScanner mmScanner;
        private ScanCallback mmScanCallback;

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void startDiscovery() {
            if (!this.mmIsScanning) {

                this.mmIsScanning = true;
                if (DEBUG_BLE) {
                    Log.d(TAG, "this.mBleScanCallback is null? " + (this.mmScanCallback == null));
                    Log.d(TAG, "this.mBleScanner is null? " + (this.mmScanner == null));
                }
                this.mmScanner.startScan(this.mmScanCallback);
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        private void stopDiscovery() {
            if (this.mmIsScanning) {
                this.mmIsScanning = false;
                this.mmScanner.stopScan(this.mmScanCallback);
            }
        }



        /*===================*
         * Connect           *
         *===================*/

        private int mConnectionState;

        //    private BluetoothGatt mGatt;
        private Map<String, BluetoothGatt> mmGattMap = new HashMap<>();
        private BluetoothGattCallback mmGattCallback;

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public boolean connect(final String address) {
            if (BtUtils.this.mAdapter == null || address == null) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
                return false;
            }

            try {
                final BluetoothDevice device = BtUtils.this.mAdapter.getRemoteDevice(address);
                // Connect to the GATT server on the device.
                BluetoothGatt gatt = device.connectGatt(BtUtils.this.mContext, false, this.mmGattCallback);   // Manifest.permission.BLUETOOTH_CONNECT

                this.mmGattMap.put(address, gatt);

                return true;
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Device not found with provided address.");
                return false;
            }
        }


        /*===================*
         * Disconnect        *
         *===================*/

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public boolean disconnect(String address) {
            if (!this.mmGattMap.containsKey(address)) {
                return false;
            }

            BluetoothGatt gatt = this.mmGattMap.get(address);

            gatt.disconnect();
            return true;
        }

        public Set<String> getBondedDeviceAddressSet() {
            return this.mmGattMap.keySet();
        }

        public void getBondedDevices(@NonNull Consumer<Map<String, BluetoothGatt>> consumer) {
            consumer.accept(this.mmGattMap);
        }



        /*===================*
         * Close             *
         *===================*/

        private List<String> mCloseList = new ArrayList<>();

        public void closeAfterDisconnection(@NonNull String address) {
            this.mCloseList.add(address);
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public boolean close(@NonNull String address) {
            if (address == null) {
                return false;
            }

            if (DEBUG_BLE) {
                Log.d(TAG, "Try to close bluetooth low erengy device: " + address);
            }

            if (!this.mmGattMap.containsKey(address)) {
                return false;
            }

            BluetoothGatt gatt = this.mmGattMap.remove(address);
            gatt.close();

            return true;
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        public boolean close() {
            for (Map.Entry<String, BluetoothGatt> entry : this.mmGattMap.entrySet()) {
                entry.getValue().close();
            }

            this.mmGattMap.clear();

            return true;
        }


    }
}
