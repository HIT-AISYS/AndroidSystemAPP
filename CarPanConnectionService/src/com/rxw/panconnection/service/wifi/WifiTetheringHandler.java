package com.rxw.panconnection.service.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.net.MacAddress;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.Queue;

import com.android.internal.util.ConcurrentUtils;
import com.rxw.panconnection.service.wifi.WifiTetheringAvailabilityListener;
import com.rxw.panconnection.service.wifi.WifiTetheringObserver;
import com.rxw.panconnection.service.wifi.WifiSHA256Generator;
import com.rxw.panconnection.service.wifi.WifiConnectionConstants;
import com.rxw.panconnection.service.wifi.WifiConnectionUtils;
import com.rxw.panconnection.service.wifi.WifiDevice;
import com.rxw.dds.control.UniDeviceController;
import com.rxw.dds.control.UniDeviceInfoCallback;


public class WifiTetheringHandler {

    // Initialization
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final WifiTetheringAvailabilityListener mWifiTetheringAvailabilityListener;

    // Define variables
    private int callbackCount;
    private List<WifiTetheringObserver> observers = new ArrayList<>();
    private List<WifiClient> previousClients = new ArrayList<>();
    private JSONArray MultiDeviceArray = new JSONArray();
    private final Map<String, WifiDevice> mWifiDevice = new HashMap<>();
    private int countConnected = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BlockingQueue<String> deviceNameQueueBroadcast = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueAddress = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueDeviceName = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueDeviceType = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueManufacturer = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueBatteryLevel = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueuePosition = new LinkedBlockingQueue<>();
    private Timer updateDeviceTimer;
    private long updateDevicedelay = 20000;
    private final BlockingQueue<String> allQueueAddressUpdate = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueDeviceNameUpdate = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueDeviceTypeUpdate = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueManufacturerUpdate = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueueBatteryLevelUpdate = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> allQueuePositionUpdate = new LinkedBlockingQueue<>();

    /*==============================================================================*
     * SoftApCallback                                                               *
     *==============================================================================*/

    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() {
        /**
         * Called when soft AP state changes.
         *
         * @param state         the new AP state. One of {@link #WIFI_AP_STATE_DISABLED},
         *                      {@link #WIFI_AP_STATE_DISABLING}, {@link #WIFI_AP_STATE_ENABLED},
         *                      {@link #WIFI_AP_STATE_ENABLING}, {@link #WIFI_AP_STATE_FAILED}
         * @param failureReason reason when in failed state. One of
         *                      {@link #SAP_START_FAILURE_GENERAL},
         *                      {@link #SAP_START_FAILURE_NO_CHANNEL},
         *                      {@link #SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION}
         */
        @Override
        public void onStateChanged(int state, int failureReason) {
            /**
             * WIFI_AP_STATE_DISABLING = 10 : Wi-Fi AP is currently being disabled.
             * WIFI_AP_STATE_DISABLED = 11 : Wi-Fi AP is disabled.
             * WIFI_AP_STATE_ENABLING = 12 : Wi-Fi AP is currently being enabled.
             * WIFI_AP_STATE_ENABLING = 13 : Wi-Fi AP is enabled.
             * WIFI_AP_STATE_FAILED = 14 : Wi-Fi AP is in a failed state.
             */
            handleStateChanged(state, failureReason);
            /*======================================*
             * [observer]
             *======================================*/
            for (WifiTetheringObserver observer : observers) {
                observer.getOnStateChanged(state, failureReason);
            }
        }

        /**
         * This method is called when there is a change in the list of connected clients to the Soft Access Point (Soft AP).
         *
         * @param info The current Soft AP information. This includes details about the Soft AP configuration and status.
         * @param currentClients A list of WifiClient objects representing the currently connected clients to the Soft AP.
         */
        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info, @NonNull List<WifiClient> currentClients) {
            handleConnectedClientsChanged(info, currentClients);
            /*======================================*
             * [Listener]
             *======================================*/
            mWifiTetheringAvailabilityListener.onConnectedClientsChanged(currentClients.size());
            /*======================================*
             * [observer]
             *======================================*/
            for (WifiTetheringObserver observer : observers) {
                observer.getOnConnectedClientsChanged(info, currentClients);
            }
        }

        /**
         * Called when client trying to connect but device blocked the client with specific reason.
         *
         * Can be used to ask user to update client to allowed list or blocked list
         * when reason is {@link SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER}, or
         * indicate the block due to maximum supported client number limitation when reason is
         * {@link SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS}.
         *
         * @param client the currently blocked client.
         * @param blockedReason one of blocked reason from {@link SapClientBlockedReason}
         */
        @Override
        public void onBlockedClientConnecting(WifiClient client, int blockedReason) {
            /**
             * blockedReason = 0 : the client doesn't exist in the specified configuration
             * blockedReason = 1 : no more clients can be associated to this AP since it reached maximum capacity.
             * blockedReason = 2 : Client disconnected for unspecified reason.
             */
            handleonBlockedClientConnecting(client, blockedReason);
            /*======================================*
            * [observer]
            *======================================*/
            for (WifiTetheringObserver observer : observers) {
                observer.getOnBlockedClientConnecting(client, blockedReason);
            }
        }

        /**
         * Called when capability of Soft AP changes.
         *
         * @param softApCapability is the Soft AP capability. {@link SoftApCapability}
         */
        public void onCapabilityChanged(@NonNull SoftApCapability softApCapability) {
            handleonCapabilityChanged(softApCapability);
        }

        /**
         * Called when the Soft AP information changes.
         */
        public void onInfoChanged(@NonNull List<SoftApInfo> softApInfoList) {
            handleInfoChanged(softApInfoList);
            /*======================================*
             * [observer]
             *======================================*/
            for (WifiTetheringObserver observer : observers) {
                observer.getOnInfoChanged(softApInfoList);
            }
        }
    };

    /*==============================================================================*
     * callback: handleInfoChanged                                                  *
     *==============================================================================*/
    private void handleInfoChanged(List<SoftApInfo> softApInfoList) {
        Log.d(WifiConnectionConstants.TAG, "onInfoChanged: " + softApInfoList);
        // Loop through each SoftApInfo object in the softApInfoList
        for (SoftApInfo info : softApInfoList) {
            // Retrieve the properties of each SoftApInfo object
            int bandwidth = info.getBandwidth();
            int frequency = info.getFrequency();
            String bssid = info.getBssid().toString();
            int wifiStandard = info.getWifiStandard();
            long mIdleShutdownTimeoutMillis = info.getAutoShutdownTimeoutMillis();
            // Log the properties of each SoftApInfo object
            Log.d(WifiConnectionConstants.TAG, "SoftApInfo, Bandwidth: " + bandwidth +
            ", Frequency: " + frequency +
            ", BSSID: " + bssid +
            ", WiFi Standard: " + wifiStandard +
            ", AutoShutdown Timeout (ms): " + mIdleShutdownTimeoutMillis);
            Log.d(WifiConnectionConstants.TAG, "-------------------------------------------------------");
        }
    }

    /*==============================================================================*
     * callback: handleStateChanged                                                 *
     *==============================================================================*/
    private void handleStateChanged(int state, int failureReason) {
        Log.d(WifiConnectionConstants.TAG, "onStateChanged, state: " + state + ", failureReason: " + failureReason);
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                getWifiApSettingInfo();
                getWifiApClientListInfo();
                printAllClientsFromFile(WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
                Log.d(WifiConnectionConstants.TAG, "The WifiAp output ending!");
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
            default:
                mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                break;
        }
    }

    /*==============================================================================*
     * callback: handleonCapabilityChanged                                          *
     *==============================================================================*/
    private void handleonCapabilityChanged(SoftApCapability softApCapability) {
        Log.d(WifiConnectionConstants.TAG, "onCapabilityChanged: " + softApCapability);
        // observer
        for (WifiTetheringObserver observer : observers) {
            observer.getOnCapabilityChanged(softApCapability);
        }
        boolean isMacAddressCustomizationSupported = softApCapability.areFeaturesSupported(WifiConnectionConstants.TARGET_SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION);
        Log.d(WifiConnectionConstants.TAG, "SoftApCapability," + " MAC Address Customization Supported: " + isMacAddressCustomizationSupported);
    }

    /*==============================================================================*
     * callback: BlockedClientConnecting                                            *
     *==============================================================================*/
    public void handleonBlockedClientConnecting(WifiClient client, int blockedReason) {
        if (client != null) {
            Log.d(WifiConnectionConstants.TAG, "onBlockedClientConnecting: " + client.getMacAddress().toString() + ", blockedReason: " + blockedReason);
            String macAddress = client.getMacAddress().toString();
            String deviceType = getDeviceTypeByMacAddress(macAddress);
            String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
            // ERRORCODE_CONNECTION_NOTEXIST = 0
            if (blockedReason == 0) {
                // observer
                for (WifiTetheringObserver observer : observers) {
                    // observer.onConnectionFailed(device, WifiConnectionConstants.ERRORCODE_CONNECTION_NOTEXIST);
                }
            }
            // ERRORCODE_CONNECTION_NOCLIENTSNUM = 1
            if (blockedReason == 1) {
                // observer
                for (WifiTetheringObserver observer : observers) {
                    // observer.onConnectionFailed(device, WifiConnectionConstants.ERRORCODE_CONNECTION_NOCLIENTSNUM);
                }
            }
            // ERRORCODE_CONNECTION_SOFTAPSHUTDOWN = 2
            if (blockedReason == 2) {
                // observer
                for (WifiTetheringObserver observer : observers) {
                    // observer.onConnectionFailed(device, WifiConnectionConstants.ERRORCODE_CONNECTION_SOFTAPSHUTDOWN);
                }
            }
        }
    }

    /*==============================================================================*
     * callback: ConnectedClientsChanged                                            *
     *==============================================================================*/

     public void handleConnectedClientsChanged(SoftApInfo info, List<WifiClient> currentClients) {
        /*==============================================================================*
         * [observer] onDiscoveryStateChanged && [Listener] onDiscoveryWifiDevice
         * send broadcast
         *==============================================================================*/
        if (currentClients != null) {
            Log.d(WifiConnectionConstants.TAG, "onConnectedClientsChanged" + currentClients + ", info: " + info);
            for (WifiClient client : currentClients) {
                // Check if the client is isAllowedClient
                boolean isAllowedClient = isAllowedClientList(client.getMacAddress(), WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
                if (!isAllowedClient) {
                    String macAddress = client.getMacAddress().toString();
                    String deviceType = getDeviceTypeByMacAddress(macAddress);
                    boolean misMacAddressInFile = isMacAddressInFile(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                    if (misMacAddressInFile) {
                        Log.d(WifiConnectionConstants.TAG, "The macAddress is MacAddressInFile!");
                        String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                        String manufacturer = getManufacturerByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                        int batteryLevel = getBatteryLevelByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                        int Position = getPositionByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                        WifiDevice device = new WifiDevice(macAddress, deviceName, deviceType, manufacturer, batteryLevel, Position);
                        /*======================================*
                         * [Listener]
                         *======================================*/
                        mWifiTetheringAvailabilityListener.onDiscoveryWifiDevice(device);
                        Log.d(WifiConnectionConstants.TAG, "handleConnectedClientsChanged! onDiscovery, device: " + device.toString());
                        /*======================================*
                         * [observer]
                         *======================================*/
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        for (WifiTetheringObserver observer : observers) {
                            String deviceInfo = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                            // observer.onDiscoveryStateChanged(device, WifiConnectionConstants.DISCOVERY_SEARCHING);
                            observer.onDiscoveryStateChanged(deviceInfo, WifiConnectionConstants.DISCOVERY_END);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        /*======================================*
                         * send broadcast
                         *======================================*/
                        // sendBroadcastToAPP(deviceType, deviceName, macAddress);
                        sendBroadcastToAPP(deviceType, deviceName, macAddress, manufacturer, batteryLevel, Position);
                        /*======================================*
                         * Add currentBlockedClientList
                         *======================================*/
                        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
                        if (originalConfig != null) {
                            MacAddress macAddrObj = client.getMacAddress();
                            List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                            if (!currentBlockedClientList.contains(macAddrObj)) {
                                originalConfig.getBlockedClientList().add(macAddrObj);
                            }
                            mWifiManager.setSoftApConfiguration(originalConfig);
                            // Output currentBlockedClientList
                            for (MacAddress macAddressOr : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                                Log.d(WifiConnectionConstants.TAG, "currentBlockedClientList: " + macAddressOr.toString());
                            }
                        }
                        Log.d(WifiConnectionConstants.TAG, "Add currentBlockedClientList successed!");
                    } else {
                        Log.d(WifiConnectionConstants.TAG, "The macAddress isnot MacAddressInFile!");
                        UniDeviceController.getInstance(mContext).requestUniDeviceInfo(deviceType, macAddress, new UniDeviceInfoCallback() {
                            @Override
                            public void onUniDeviceInfoReady(com.rxw.dds.model.UniDevice ddsUniDevice) {
                                try {
                                    allQueueAddress.put(ddsUniDevice.id);
                                    allQueueDeviceName.put(ddsUniDevice.name);
                                    allQueueDeviceType.put(ddsUniDevice.type);
                                    allQueueManufacturer.put(ddsUniDevice.manufacturer);
                                    allQueueBatteryLevel.put(String.valueOf(ddsUniDevice.batteryLevel));
                                    allQueuePosition.put(String.valueOf(ddsUniDevice.position));
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                String id = allQueueAddress.take();
                                                String name = allQueueDeviceName.take();
                                                String type = allQueueDeviceType.take();
                                                String manufacturer = allQueueManufacturer.take();
                                                String batteryLevel = allQueueBatteryLevel.take();
                                                String position = allQueuePosition.take();
                                                // Log the successful retrieval of device information
                                                String logMessage = "handleConnectedClientsChanged! onDiscovery, macAddress: " + id +
                                                ", deviceName: " + name +
                                                ", deviceType: " + type +
                                                ", manufacturer: " + manufacturer +
                                                ", batteryLevel: " + batteryLevel +
                                                ", position: " + position;
                                                Log.d(WifiConnectionConstants.TAG, logMessage);
                                                WifiDevice device = new WifiDevice(id, name, type, manufacturer, Integer.parseInt(batteryLevel), Integer.parseInt(position));
                                                /*======================================*
                                                 * Add DeviceToFile
                                                 *======================================*/
                                                boolean isAllowedDevice = isAllowedDeviceList(device, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                                                if (!isAllowedDevice) {
                                                    addDeviceToFile(device, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                                                }
                                                /*======================================*
                                                 * [PeriodicUpdate]
                                                 *======================================*/
                                                // retrieveDeviceInfoAndPeriodicUpdate(type, name)
                                                /*======================================*
                                                 * [Listener]
                                                 *======================================*/
                                                mWifiTetheringAvailabilityListener.onDiscoveryWifiDevice(device);
                                                Log.d(WifiConnectionConstants.TAG, "handleConnectedClientsChanged! onDiscovery, device: " + device.toString());
                                                /*======================================*
                                                 * [observer]
                                                 *======================================*/
                                                try {
                                                    Thread.sleep(100);
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                }
                                                for (WifiTetheringObserver observer : observers) {
                                                    String deviceInfo = WifiConnectionUtils.getDeviceToJSON(type, name, id);
                                                    // observer.onDiscoveryStateChanged(device, WifiConnectionConstants.DISCOVERY_SEARCHING);
                                                    observer.onDiscoveryStateChanged(deviceInfo, WifiConnectionConstants.DISCOVERY_END);
                                                }
                                                try {
                                                    Thread.sleep(100);
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                }
                                                /*======================================*
                                                 * send broadcast
                                                 *======================================*/
                                                // sendBroadcastToAPP(type, name, id);
                                                sendBroadcastToAPP(type, name, id, manufacturer, Integer.parseInt(batteryLevel), Integer.parseInt(position));
                                                /*======================================*
                                                 * Add currentBlockedClientList
                                                 *======================================*/
                                                SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
                                                if (originalConfig != null) {
                                                    MacAddress macAddrObj = client.getMacAddress();
                                                    List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                                                    if (!currentBlockedClientList.contains(macAddrObj)) {
                                                        originalConfig.getBlockedClientList().add(macAddrObj);
                                                    }
                                                    mWifiManager.setSoftApConfiguration(originalConfig);
                                                    // Output currentBlockedClientList
                                                    for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                                                        Log.d(WifiConnectionConstants.TAG, "currentBlockedClientList: " + macAddress.toString());
                                                    }
                                                }
                                                Log.d(WifiConnectionConstants.TAG, "Add currentBlockedClientList successed!");
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }
                                    });
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
                    }
                } else {
                    /*======================================*
                     * Package MultiDeviceArray
                     *======================================*/
                    String macAddress = client.getMacAddress().toString();
                    String deviceType = getDeviceTypeByMacAddress(macAddress);
                    String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                    createMultiDeviceArray(deviceType, deviceName, macAddress);
                    Log.d(WifiConnectionConstants.TAG, "The MultiDeviceArray: " + getMultiDeviceArray());
                }
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "onConnectedClientsChanged" + "currentClients are null!");
        }
        /*======================================*
         * Update the connectedClients list
         *======================================*/
        List<WifiClient> updatedClients = new ArrayList<>(currentClients);
        List<WifiClient> connectedClients = new ArrayList<>();
        if (currentClients != null) {
            for (WifiClient client : currentClients) {
                // Check if the client is allowed
                boolean isAllowedClient = isAllowedClientList(client.getMacAddress(), WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
                if (!isAllowedClient) {
                    // Remove the client from the currentClients list
                    updatedClients.remove(client);
                    // Add the client to the toConnectedClientList
                    boolean toConnectedClient = isAllowedClientList(client.getMacAddress(), WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
                    if (!toConnectedClient) {
                        addClientToFile(client.getMacAddress(), WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
                    }
                }
            }
        } else {
            // Handle the case where currentClients is null
            Log.d(WifiConnectionConstants.TAG, "currentClients is null");
        }
        connectedClients = new ArrayList<>(updatedClients);
        Log.d(WifiConnectionConstants.TAG, "handleConnectedClientsChanged, updatedClients: " + updatedClients.toString());
        Log.d(WifiConnectionConstants.TAG, "handleConnectedClientsChanged, connectedClients: " + connectedClients.toString());
        /*======================================*
         * [Listener]
         *======================================*/
        int totalConnectedClients = connectedClients.size();
        if (totalConnectedClients == 0) {
            mWifiTetheringAvailabilityListener.connectedWifiDevices(null);
        }
        countConnected = 0;
        List<WifiDevice> currentWifiDevices = new ArrayList<>();
        if (connectedClients != null) {
            for (WifiClient client : connectedClients) {
                String macAddress = client.getMacAddress().toString();
                String deviceType = getDeviceTypeByMacAddress(macAddress);
                String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                String manufacturer = getManufacturerByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                int batteryLevel = getBatteryLevelByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                int Position = getPositionByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                String logMessage = "handleConnectedClientsChanged, connectedMacAddress: " + macAddress + 
                ", deviceType: " + deviceType + 
                ", deviceName: " + deviceName + 
                ", manufacturer: " + manufacturer + 
                ", batteryLevel: " + String.valueOf(batteryLevel) + 
                ", Position: " + String.valueOf(Position);
                Log.d(WifiConnectionConstants.TAG, logMessage);
                // For each newDevice
                WifiDevice newDevice = new WifiDevice(macAddress, deviceName, deviceType, manufacturer, batteryLevel, Position);
                currentWifiDevices.add(newDevice);
                countConnected ++;
                /*======================================*
                 * [Listener]
                 *======================================*/
                if (totalConnectedClients == countConnected) {
                    mWifiTetheringAvailabilityListener.connectedWifiDevices(currentWifiDevices);
                    Log.d(WifiConnectionConstants.TAG, "handleConnectedClientsChanged, connectedMacAddress: " + currentWifiDevices.toString());
                }
                /*======================================*
                 * [observer]
                 *======================================*/
                boolean toConnectedClient = isAllowedClientList(client.getMacAddress(), WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
                if (toConnectedClient) {
                    // Remove the client to the toConnectedClientList
                    removeClientToFile(client.getMacAddress(), WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
                    for (WifiTetheringObserver observer : observers) {
                        Log.d(WifiConnectionConstants.TAG, "observer: onConnectionStateChanged!");
                        String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                        // observer.onConnectionStateChanged(device, WifiConnectionConstants.CONNECTING);
                        observer.onConnectionStateChanged(device, WifiConnectionConstants.CONNECTED);
                    }
                }
            }
        }
        /*======================================*
         * [observer]
         *======================================*/
        // find DisconnectedClients and refresh previousClients
        List<WifiClient> disconnectedClients = WifiConnectionUtils.findDisconnectedClients(previousClients, currentClients);
        previousClients = new ArrayList<>(currentClients);
        if (disconnectedClients != null) {
            for (WifiClient disconnectedClient : disconnectedClients) {
                for (WifiTetheringObserver observer : observers) {
                    String macAddress = disconnectedClient.getMacAddress().toString();
                    String deviceType = getDeviceTypeByMacAddress(macAddress);
                    String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
                    String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                    // observer.onConnectionStateChanged(device, WifiConnectionConstants.DISCONNECTING);
                    // observer.onConnectionStateChanged(device, WifiConnectionConstants.DISCONNECTED);
                }
            }
        }
    }

    /**
     * Adds a MacAddress to a file if it doesn't already exist in the file.
     *
     * @param macAddrObj The MacAddress object to add to the file.
     * @param fileName The name of the file where the MacAddress will be added.
     */
    private void addClientToFile(MacAddress macAddrObj, String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        boolean exists = file.exists();
        boolean isAdded = false;
        // Check if the file exists and if the MacAddress is not already in the file
        if (exists) {
            try {
                // Read existing client information
                FileInputStream fis = mContext.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    // Check if the MacAddress already exists in the file
                    if (line.equals(macAddrObj.toString())) {
                        isAdded = true;
                        break;
                    }
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
            }
        }
        // If the file does not exist or the MacAddress is not in the file, add it
        if (!isAdded) {
            try {
                // Append the MacAddress to the file
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_APPEND);
                fos.write((macAddrObj.toString() + "\n").getBytes());
                fos.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error writing to file", e);
            }
        }
    }

    /**
     * Removes a MacAddress from a file if it exists and the file contains the MacAddress.
     *
     * @param macAddrObj The MacAddress object to remove from the file.
     * @param fileName The name of the file containing the list of clients.
     */
    private void removeClientToFile(MacAddress macAddrObj, String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                // Read existing client information
                FileInputStream fis = mContext.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                HashSet<String> clientSet = new HashSet<>();
                String line;
                while ((line = br.readLine()) != null) {
                    clientSet.add(line);
                }
                br.close();
                // Remove the client if it exists
                boolean isRemoved = clientSet.remove(macAddrObj.toString());
                if (isRemoved) {
                    // Write the updated client set back to the file
                    FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                    for (String client : clientSet) {
                        fos.write((client + "\n").getBytes());
                    }
                    fos.close();
                } else {
                    // Client not found in the file
                    Log.d(WifiConnectionConstants.TAG, "removeClientToFile, No such client in file: " + macAddrObj.toString());
                }
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "removeClientToFile, Error reading or writing file", e);
            }
        } else {
            // File does not exist
            Log.d(WifiConnectionConstants.TAG, "removeClientToFile, File does not exist: " + fileName);
        }
    }

    /**
     * Checks if a client with a given MAC address is present in a specific file.
     *
     * @param macAddrObj The MAC address object to check against the file.
     * @param fileName The name of the file containing the list of clients.
     * @return true if the client is present in the file, false otherwise.
     */
    private boolean isAllowedClientList(MacAddress macAddrObj, String fileName) {
        boolean isAllowedClient = false;
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                // Read existing client information
                FileInputStream fis = mContext.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    // Check if the MacAddress is in the file
                    if (line.equals(macAddrObj.toString())) {
                        isAllowedClient = true;
                        break;
                    }
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "isAllowedClientList, Error reading file", e);
            }
        } else {
            // File does not exist, print a message
            Log.d(WifiConnectionConstants.TAG, "isAllowedClientList, File does not exist: " + fileName);
        }
        return isAllowedClient;
    }

    /**
     * Prints all MacAddresses from a file to the log if the file exists.
     *
     * @param fileName The name of the file containing the list of MacAddresses.
     */
    private void printAllClientsFromFile(String fileName) {
        // Check if the file exists
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                // Read existing client information
                FileInputStream fis = mContext.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    // Print each MacAddress
                    Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! MacAddress: " + line);
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "printAllClientsFromFile! Error reading file", e);
            }
        } else {
            // File does not exist, do not print anything
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! File does not exist: " + fileName);
        }
    }

    /**
     * Clears the content of a file if it exists, or creates a new file if it does not exist.
     * Logs the content of the file if it exists and the file is not empty.
     * 
     * @param fileName The name of the file to clear or create.
     */ 
    private void clearOrCreateFile(String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                // If the file exists, read its content to log
                FileInputStream fis = mContext.openFileInput(fileName);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder content = new StringBuilder();
                String line;
                boolean isEmpty = true;
                while ((line = br.readLine()) != null) {
                    content.append(line).append("\n");
                    isEmpty = false;
                }
                br.close();
                // Log the content of the file
                if (isEmpty) {
                    Log.d(WifiConnectionConstants.TAG, "clearOrCreateFile! File exists but is empty: " + fileName);
                } else {
                    Log.d(WifiConnectionConstants.TAG, "clearOrCreateFile! File exists with content: " + fileName);
                    Log.d(WifiConnectionConstants.TAG, "clearOrCreateFile! Content: " + content.toString());
                }
                // Clear the file content
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                fos.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "clearOrCreateFile! Error reading or clearing file", e);
            }
        } else {
            // File does not exist, create it
            try {
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                fos.close();
                Log.d(WifiConnectionConstants.TAG, "clearOrCreateFile! File did not exist, created successfully: " + fileName);
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "clearOrCreateFile! Error creating file", e);
            }
        }
    }

    /**
     * Asynchronously retrieves device information and sends a broadcast to the app.
     * @param client The WifiClient instance to retrieve device information.
     */
    private void retrieveDeviceInfoAndSendBroadcast(WifiClient client) {
        // Log the start of device information retrieval
        Log.d(WifiConnectionConstants.TAG, "Retrieving device information for client: " + client.getMacAddress().toString());
        String macAddress = client.getMacAddress().toString();
        String deviceType = getDeviceTypeByMacAddress(macAddress);
        // Asynchronously retrieve device information
        UniDeviceController.getInstance(mContext).requestUniDeviceInfo(deviceType, macAddress, new UniDeviceInfoCallback() {
            @Override
            public void onUniDeviceInfoReady(com.rxw.dds.model.UniDevice ddsUniDevice) {
                // Log the successful retrieval of device information
                String logMessage = "deviceNameQueueBroadcast, macAddress: " + ddsUniDevice.id +
                ", deviceName: " + ddsUniDevice.name +
                ", deviceType: " + ddsUniDevice.type +
                ", manufacturer: " + ddsUniDevice.manufacturer +
                ", batteryLevel: " + ddsUniDevice.batteryLevel +
                ", position: " + ddsUniDevice.position;
                Log.d(WifiConnectionConstants.TAG, logMessage);
                try {
                    deviceNameQueueBroadcast.put(ddsUniDevice.name);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                String deviceName = deviceNameQueueBroadcast.take();
                                // Use the retrieved device information to send a broadcast
                                // sendBroadcastToAPP(ddsUniDevice.type, ddsUniDevice.name, client.getMacAddress().toString());
                                sendBroadcastToAPP(deviceType, deviceName, client.getMacAddress().toString(), ddsUniDevice.manufacturer, ddsUniDevice.batteryLevel, ddsUniDevice.position);
                                // add currentBlockedClientList
                                SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
                                if (originalConfig != null) {
                                    MacAddress macAddrObj = client.getMacAddress();
                                    List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                                    if (!currentBlockedClientList.contains(macAddrObj)) {
                                        originalConfig.getBlockedClientList().add(macAddrObj);
                                    }
                                    mWifiManager.setSoftApConfiguration(originalConfig);
                                    // Output currentBlockedClientList
                                    for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                                        Log.d(WifiConnectionConstants.TAG, "currentBlockedClientList: " + macAddress.toString());
                                    }
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * Determines the device type by checking if the MAC address is in any of the device type lists.
     * @param macAddress The MAC address to check against known device types.
     * @return The device type string if found, otherwise returns DEVICE_TYPE_UNKNOWN.
     */
    public String getDeviceTypeByMacAddress(String macAddress) {
        // Check if the MAC address is in the camera devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_CAMERA.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_CAMERA;
        }
        // Check if the MAC address is in the fragrance device type list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_FRAGRANCE.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_FRAGRANCE;
        }
        // Check if the MAC address is in the atmosphere light devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_ATMOSPHERE_LIGHT.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_ATMOSPHERE_LIGHT;
        }
        // Check if the MAC address is in the mirror devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_MIRROR.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_MIRROR;
        }
        // Check if the MAC address is in the disinfection devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_DISINFECTION.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_DISINFECTION;
        }
        // Check if the MAC address is in the humidifier devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_HUMIDIFIER.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_HUMIDIFIER;
        }
        // Check if the MAC address is in the microphone devicetype list
        if (WifiConnectionConstants.DEVICE_TYPE_LIST_MICROPHONE.contains(macAddress)) {
            return WifiConnectionConstants.DEVICE_TYPE_MICROPHONE;
        }
        // Return DEVICE_TYPE_UNKNOWN if the MAC address is not found in any list
        return WifiConnectionConstants.DEVICE_TYPE_UNKNOWN;
    }

    /**
     * Sends a broadcast to the specified package with device information.
     *
     * @param deviceType The type of the device.
     * @param deviceName The name of the device.
     * @param macAddress The MAC address of the device.
     */
    public void sendBroadcastToAPP(String deviceType, String deviceName, String macAddress) {
        try {
            List<String> allowedDeviceTypes = Arrays.asList("unknown", "camera", "fragrance", "atmosphere_light",
                    "mirror", "disinfection", "humidifier", "microphone");

            if (!allowedDeviceTypes.contains(deviceType)) {
                deviceType = "unknown";
            }
            JSONArray deviceArray = new JSONArray();
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            deviceArray.put(deviceObject);
            Intent intent = new Intent(WifiConnectionConstants.ACTION_DISCOVERED_DEVICES);
            intent.setPackage(WifiConnectionConstants.PACKAGE_DISCOVERED_DEVICES);
            intent.putExtra("discovered_devices", deviceArray.toString());
            mContext.sendBroadcast(intent);
            Log.d(WifiConnectionConstants.TAG, "Broadcast sented for new device: " + deviceArray);
        } catch (Exception e) {
            Log.e(WifiConnectionConstants.TAG, "Error creating broadcast JSON: ", e);
        }
    }

    /**
     * Sends a broadcast to the specified package with device information.
     *
     * @param deviceType The type of the device.
     * @param deviceName The name of the device.
     * @param macAddress The MAC address of the device.
     * @param manufacturer The manufacturer of the device.
     * @param battery The battery level of the device.
     * @param position The position of the device.
     */
    public void sendBroadcastToAPP(String deviceType, String deviceName, String macAddress, String manufacturer, int battery, int position) {
        try {
            List<String> allowedDeviceTypes = Arrays.asList("unknown", "camera", "fragrance", "atmosphere_light",
                    "mirror", "disinfection", "humidifier", "microphone");

            if (!allowedDeviceTypes.contains(deviceType)) {
                deviceType = "unknown";
            }
            JSONArray deviceArray = new JSONArray();
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            deviceObject.put("manufacturer", manufacturer);
            deviceObject.put("battery", battery);
            deviceObject.put("position", position);
            deviceArray.put(deviceObject);
            Intent intent = new Intent(WifiConnectionConstants.ACTION_DISCOVERED_DEVICES);
            intent.setPackage(WifiConnectionConstants.PACKAGE_DISCOVERED_DEVICES);
            intent.putExtra("discovered_devices", deviceArray.toString());
            mContext.sendBroadcast(intent);
            Log.d(WifiConnectionConstants.TAG, "Broadcast sented for new device: " + deviceArray);
        } catch (Exception e) {
            Log.e(WifiConnectionConstants.TAG, "Error creating broadcast JSON: ", e);
        }
    }

    /*==============================================================================*
     * WithDelay                                                                    *
     *==============================================================================*/

    public void bondDeviceAndSoftApConfigureWithDelay(WifiClient client) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(WifiConnectionConstants.TAG, "Wait for 10 seconds!" + " bondDeviceAndSoftApConfigureWithDelay: " + client.getMacAddress().toString());
                bondDeviceAndSoftApConfigure(client.getMacAddress());
            }
        }, 10000);
    }

    public void unbondDeviceAndSoftApConfigureWithDelay(WifiClient client) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                unbondDeviceAndSoftApConfigure(client.getMacAddress());
                Log.d(WifiConnectionConstants.TAG, "Wait for 10 seconds!" + " unbondDeviceAndSoftApConfigureWithDelay: " + client.getMacAddress().toString());
            }
        }, 10000);
    }

    /**
     * Retrieve device information and send a broadcast after a delay.
     *
     * @param client The WifiClient object that contains information about the device to retrieve.
     */
    public void retrieveDeviceInfoAndSendBroadcastWithDelay(WifiClient client) {
        // Create a new Handler that uses the main looper to interact with the main thread.
        Handler handler = new Handler(Looper.getMainLooper());
        // Post a delayed Runnable to the handler, which will be executed after 10 seconds (10000 milliseconds).
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(WifiConnectionConstants.TAG, "Wait for 10 seconds!" + " retrieveDeviceInfoAndSendBroadcastWithDelay: " + client.getMacAddress().toString());
                retrieveDeviceInfoAndSendBroadcast(client);
            }
        }, 10000); 
    }

    /*==============================================================================*
     * restart WifiSoftAp
     *==============================================================================*/
    private void restartSoftAp() {
        if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
            // stopTethering
            stopTethering();
            Handler handler1 = new Handler(Looper.getMainLooper());
            handler1.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // startTethering
                    startTethering();
                }
            }, 1000);
            Log.d(WifiConnectionConstants.TAG, "Restart softApConfigureAndStartHotspot begin first!");
        }
    }

    /*==============================================================================*
     * register and unregister Observer                                             *
     *==============================================================================*/

    public void registerObserver(WifiTetheringObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(WifiTetheringObserver observer) {
        observers.remove(observer);
    }

    /*==============================================================================*
     * Getters                                                                      *
     *==============================================================================*/

    /**
     * Retrieves the string representation of the MultiDeviceArray.
     *
     * @return A String representation of the MultiDeviceArray.
     */
    public String getMultiDeviceArray() {
        // Convert the MultiDeviceArray to a String and log it for debugging purposes
        String multiDeviceArrayString = MultiDeviceArray.toString();
        Log.d(WifiConnectionConstants.TAG, "MultiDeviceArray content: " + multiDeviceArrayString);
        return multiDeviceArrayString;
    }

    /**
     * Creates a JSON object representing a device and adds it to the MultiDeviceArray.
     *
     * @param deviceType   The type of the device.
     * @param deviceName   The name of the device.
     * @param macAddress   The MAC address of the device, used as the device ID.
     */
    public void createMultiDeviceArray(String deviceType, String deviceName, String macAddress) {
        try {
            // Create a new JSONObject to represent the device
            JSONObject deviceObject = new JSONObject();
            // Populate the JSONObject with device details
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            // Add the device JSONObject to the global MultiDeviceArray
            MultiDeviceArray.put(deviceObject);
            // Log the successful creation of the device array JSON
            Log.d(WifiConnectionConstants.TAG, "Device array JSON created successfully.");
        } catch (JSONException e) {
            // Log the error if JSON object creation fails
            Log.e(WifiConnectionConstants.TAG, "Error creating device array JSON: ", e);
        }
    }

    public void getWifiApSettingInfo() {
        // Output WifiApInfo
        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
        if (originalConfig != null) {
            String SSID = originalConfig.getSsid();
            String Passphrase;
            if (originalConfig.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_OPEN) {
                Passphrase = null;
            } else {
                Passphrase = originalConfig.getPassphrase();
            }
            String Bssid;
            if (originalConfig.getBssid() != null) {
                Bssid = originalConfig.getBssid().toString();
            } else {
                Bssid = null;
            }
            // Getter
            String MaxNumberOfClients = String.valueOf(originalConfig.getMaxNumberOfClients());
            String ShutdownTimeoutMillis = String.valueOf(originalConfig.getShutdownTimeoutMillis());
            String Band = String.valueOf(originalConfig.getBand());
            String HiddenSsid = String.valueOf(originalConfig.isHiddenSsid());
            String AutoShutdown = String.valueOf(originalConfig.isAutoShutdownEnabled());
            String ClientControlByUser = String.valueOf(originalConfig.isClientControlByUserEnabled());
            String logMessage = "onTetheringStarted! SSID: " + SSID + ", Passphrase: " + Passphrase + ", Bssid: " + Bssid +
            ", MaxNumberOfClients: " + MaxNumberOfClients +
            ", ShutdownTimeoutMillis: " + ShutdownTimeoutMillis + ", Band: " + Band +
            ", AutoShutdown: " + AutoShutdown + ", HiddenSsid: " + HiddenSsid + ", ClientControlByUser: " + ClientControlByUser;
            Log.d(WifiConnectionConstants.TAG, logMessage);
            // GetSecurityType
            int mSecurityType = originalConfig.getSecurityType();
            switch (mSecurityType) {
                case SoftApConfiguration.SECURITY_TYPE_OPEN:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_OPEN");
                    break;
                case SoftApConfiguration.SECURITY_TYPE_WPA2_PSK:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_WPA2_PSK");
                    break;
                case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_WPA3_SAE_TRANSITION");
                    break;
                case SoftApConfiguration.SECURITY_TYPE_WPA3_SAE:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_WPA3_SAE");
                    break;
                case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_WPA3_OWE_TRANSITION");
                    break;
                case SoftApConfiguration.SECURITY_TYPE_WPA3_OWE:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: SECURITY_TYPE_WPA3_OWE");
                    break;
                default:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", SecurityType: Unknown security type");
                    break;
            }
            // GetMacRandomizationSetting
            int MacRandomizationSetting = originalConfig.getMacRandomizationSetting();
            switch (MacRandomizationSetting) {
                case SoftApConfiguration.RANDOMIZATION_NONE:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", MacRandomizationSetting: RANDOMIZATION_NONE");
                    break;
                case SoftApConfiguration.RANDOMIZATION_PERSISTENT:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", MacRandomizationSetting: RANDOMIZATION_PERSISTENT");
                    break;
                case SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", MacRandomizationSetting: RANDOMIZATION_NON_PERSISTENT");
                    break;
                default:
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + ", MacRandomizationSetting: Unknown MacRandomizationSetting");
                    break;
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "getWifiApSettingInfo! originalConfig is null");
        }
    }

    public void getWifiApClientListInfo() {
        // Output WifiApInfo
        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
        if (originalConfig != null) {
            // Output currentBlockedClientList
            if (!originalConfig.getBlockedClientList().isEmpty()) {
                for (MacAddress macAddress : originalConfig.getBlockedClientList()) {
                    Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + " currentBlockedClientList: " + macAddress.toString());
                }
            } else {
                Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!" + " currentBlockedClientList is null!");
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "getWifiApClientListInfo! originalConfig is null");
        }
    }

    /*==============================================================================*
     * bond UniDevice                                                               *
     *==============================================================================*/

    public void bondDeviceAndSoftApConfigure(MacAddress macAddrObj) {
        Log.d(WifiConnectionConstants.TAG, "bondDeviceAndSoftApConfigure strating!");
        try {
            // Check if the client is isAllowedClient
            boolean isAllowedClient = isAllowedClientList(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            if (!isAllowedClient) {
                // If the client is isAllowedClient, save the client's information to the file
                addClientToFile(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
                Log.d(WifiConnectionConstants.TAG, "bondDeviceAndSoftApConfigure!" + " The macAddrObj success saving!");
            }
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! strating!");
            printAllClientsFromFile(WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! ending!");
            // softApConfigure setting
            SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
            if (originalConfig != null) {
                List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                if (currentBlockedClientList.contains(macAddrObj)) {
                    originalConfig.getBlockedClientList().remove(macAddrObj);
                }
                mWifiManager.setSoftApConfiguration(originalConfig);
                // Output currentBlockedClientList and currentAllowedClientList
                for (MacAddress macAddrObj1 : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                    Log.d(WifiConnectionConstants.TAG, "bondDeviceAndSoftApConfigure!" + " The currentBlockedClientList: " + macAddrObj1.toString());
                }
            }
            Log.d(WifiConnectionConstants.TAG, "bondDeviceAndSoftApConfigure! UniDevice bonded: " + macAddrObj.toString());
        } catch (Exception e) {
            // Handle the exception, for example, log it or notify the user
            Log.e(WifiConnectionConstants.TAG, "Failed to bond device and configure Soft AP: " + e.getMessage());
            /*==============================================================================*
             * [observer]
             * onConnectionFailed
             *==============================================================================*/
            String macAddress = macAddrObj.toString();
            String deviceType = getDeviceTypeByMacAddress(macAddress);
            String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            for (WifiTetheringObserver observer : observers) {
                String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                observer.onConnectionFailed(device, WifiConnectionConstants.ERRORCODE_CONNECTION_OTHER);
            }
        }
        Log.d(WifiConnectionConstants.TAG, "bondDeviceAndSoftApConfigure ending!");
    }

    /*==============================================================================*
     * unbond UniDevice                                                              *
     *===============================================================================*/

    public void unbondDeviceAndSoftApConfigure(MacAddress macAddrObj) {
        Log.d(WifiConnectionConstants.TAG, "unbondDeviceAndSoftApConfigure strating!");
        try {
            String macAddress = macAddrObj.toString();
            String deviceType = getDeviceTypeByMacAddress(macAddress);
            String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            String manufacturer = getManufacturerByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            int batteryLevel = getBatteryLevelByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            int Position = getPositionByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            String logMessage = "unbondDeviceAndSoftApConfigure: " + macAddress + 
            ", deviceType: " + deviceType + 
            ", deviceName: " + deviceName + 
            ", manufacturer: " + manufacturer + 
            ", batteryLevel: " + String.valueOf(batteryLevel) + 
            ", Position: " + String.valueOf(Position);
            Log.d(WifiConnectionConstants.TAG, logMessage);
            /*======================================*
             * [observer]
             *======================================*/
            for (WifiTetheringObserver observer : observers) {
                String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                // observer.onUnbondStateChanged(device, WifiConnectionConstants.UNBONDING);
                observer.onUnbondStateChanged(device, WifiConnectionConstants.UNBONDED);
            }
            /*======================================*
             * [Listener]
             *======================================*/
            WifiDevice newDevice = new WifiDevice(macAddress, deviceName, deviceType, manufacturer, batteryLevel, Position);
            mWifiTetheringAvailabilityListener.onDiscoveryWifiDevice(newDevice);
            /*======================================*
             * unbondDeviceAndSoftApConfigure
             *======================================*/
            boolean isAllowedClient = isAllowedClientList(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            if (isAllowedClient) {
                // If the client is isAllowedClient, save the client's information to the file
                removeClientToFile(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
                Log.d(WifiConnectionConstants.TAG, "unbondDeviceAndSoftApConfigure!" + " The macAddrObj success removing!");
            }
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! strating!");
            printAllClientsFromFile(WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! ending!");
            // Add the client to the toConnectedClientList
            boolean toConnectedClient = isAllowedClientList(macAddrObj, WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
            if (!toConnectedClient) {
                addClientToFile(macAddrObj, WifiConnectionConstants.TO_CONNECTEDCLIENTLIST_FILE);
            }
            // softApConfigure setting
            SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
            if (originalConfig != null) {
                List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                if (!currentBlockedClientList.contains(macAddrObj)) {
                    originalConfig.getBlockedClientList().add(macAddrObj);
                }
                mWifiManager.setSoftApConfiguration(originalConfig);
                // Output currentBlockedClientList
                for (MacAddress macAddrObj1 : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                    Log.d(WifiConnectionConstants.TAG, "unbondDeviceAndSoftApConfigure!" + " The currentBlockedClientList: " + macAddrObj1.toString());
                }
            }
            Log.d(WifiConnectionConstants.TAG, "unbondDeviceAndSoftApConfigure! UniDevice unbonded: " + macAddress);
        } catch (Exception e) {
            // Handle the exception, for example, log it or notify the user
            Log.e(WifiConnectionConstants.TAG, "Failed to unbond device and configure Soft AP: " + e.getMessage());
            /*======================================*
             * [observer]
             *======================================*/
            String macAddress = macAddrObj.toString();
            String deviceType = getDeviceTypeByMacAddress(macAddress);
            String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            for (WifiTetheringObserver observer : observers) {
                String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                observer.onUnbondFailed(device, WifiConnectionConstants.ERRORCODE_UNBOND);
            }
        }
        Log.d(WifiConnectionConstants.TAG, "unbondDeviceAndSoftApConfigure ending!");
    }

    /*==============================================================================*
     * remove UniDevice                                                             *
     *==============================================================================*/

     public void removeDeviceAndSoftApConfigure(MacAddress macAddrObj) {
        Log.d(WifiConnectionConstants.TAG, "removeDeviceAndSoftApConfigure strating!");
        try {
            // Check if the client is isAllowedClient
            boolean isAllowedClient = isAllowedClientList(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            if (isAllowedClient) {
                // If the client is isAllowedClient, save the client's information to the file
                removeClientToFile(macAddrObj, WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            }
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! strating!");
            printAllClientsFromFile(WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            Log.d(WifiConnectionConstants.TAG, "printAllClientsFromFile! ending!");
            // softApConfigure setting
            SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
            if (originalConfig != null) {
                List<MacAddress> currentBlockedClientList = originalConfig.getBlockedClientList();
                if (currentBlockedClientList.contains(macAddrObj)) {
                    originalConfig.getBlockedClientList().remove(macAddrObj);
                }
                mWifiManager.setSoftApConfiguration(originalConfig);
                // Output currentBlockedClientList
                for (MacAddress macAddrObj1 : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                    Log.d(WifiConnectionConstants.TAG, "removeDeviceAndSoftApConfigure" + " currentBlockedClientList: " + macAddrObj1.toString());
                }
            }
            Log.d(WifiConnectionConstants.TAG, "UniDevice remove: " + macAddrObj.toString());
        } catch (Exception e) {
            // Handle the exception, for example, log it or notify the user
            Log.e(WifiConnectionConstants.TAG, "Failed to remove device and configure Soft AP: " + e.getMessage());
            /*======================================*
             * [observer]
             *======================================*/
            String macAddress = macAddrObj.toString();
            String deviceType = getDeviceTypeByMacAddress(macAddress);
            String deviceName = getDeviceNameByMac(macAddress, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE);
            for (WifiTetheringObserver observer : observers) {
                String device = WifiConnectionUtils.getDeviceToJSON(deviceType, deviceName, macAddress);
                observer.onUnbondFailed(device, WifiConnectionConstants.ERRORCODE_UNBOND);
            }
        }
        Log.d(WifiConnectionConstants.TAG, "removeDeviceAndSoftApConfigure ending!");
    }

    /**
     * Sets the tethered Wi-Fi AP Configuration.
     *
     * If the API is called while the tethered soft AP is enabled, the configuration will apply to
     * the current soft AP if the new configuration only includes
     * {@link SoftApConfiguration.Builder#setMaxNumberOfClients(int)}
     * or {@link SoftApConfiguration.Builder#setShutdownTimeoutMillis(long)}
     * or {@link SoftApConfiguration.Builder#setClientControlByUserEnabled(boolean)}
     * or {@link SoftApConfiguration.Builder#setBlockedClientList(List)}
     * or {@link SoftApConfiguration.Builder#setAllowedClientList(List)}
     * or {@link SoftApConfiguration.Builder#setAutoShutdownEnabled(boolean)}
     * or {@link SoftApConfiguration.Builder#setBridgedModeOpportunisticShutdownEnabled(boolean)}
     *
     * Otherwise, the configuration changes will be applied when the Soft AP is next started
     * (the framework will not stop/start the AP).
     *
     * @param softApConfig  A valid SoftApConfiguration specifying the configuration of the SAP.
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     *
     * @hide
     */

    /*==============================================================================*
     * softApConfigureAndStartHotspot begin first!                                  *
     *==============================================================================*/
    public void softApConfigureAndStartHotspot() {
        Log.d(WifiConnectionConstants.TAG, "softApConfigureAndStartHotspot starting!");
        // softApConfigure setting
        SoftApConfiguration originalConfig = mWifiManager.getSoftApConfiguration();
        if (originalConfig != null) {
            Log.d(WifiConnectionConstants.TAG, "softApConfigureAndStartHotspot!" + " originalConfig isnot null!");
            List<MacAddress> mcurrentBlockedClientList = originalConfig.getBlockedClientList();
            // Delete duplicate elements
            Set<MacAddress> uniqueSetcurrentBlockedClientList = new LinkedHashSet<>(mcurrentBlockedClientList);
            List<MacAddress> currentBlockedClientList = new ArrayList<>(uniqueSetcurrentBlockedClientList);
            /*==========================================*
             * New builder                              *
             *==========================================*/
            SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
            builder.setSsid(WifiConnectionConstants.TARGET_SSID);
            String Passphrase = WifiSHA256Generator.generateSHA256Hash(WifiConnectionConstants.TARGET_SSID);
            // String Passphrase = WifiConnectionConstants.TARGET_PASSPHRASE; // Test
            builder.setPassphrase(Passphrase, WifiConnectionConstants.TARGET_SECURITY_WPA2_PSK);
            builder.setBand(WifiConnectionConstants.TARGET_BAND_5G);
            builder.setMaxNumberOfClients(WifiConnectionConstants.TARGET_MAXNUMBEROFCLIENTS);
            builder.setHiddenSsid(WifiConnectionConstants.TARGET_HIDDEN);
            builder.setClientControlByUserEnabled(WifiConnectionConstants.TARGET_CLIENTCONTROLBYUSER);
            if (currentBlockedClientList != null) {
                currentBlockedClientList.clear();
                builder.setBlockedClientList(currentBlockedClientList);
            }
            // clearOrCreateFile(WifiConnectionConstants.ALLOWED_CLIENT_LIST_FILE);
            builder.setAutoShutdownEnabled(WifiConnectionConstants.TARGET_AUTOSHUTDOWN);
            // Fixed BSSID
            builder.setMacRandomizationSetting(WifiConnectionConstants.MAC_RANDOMIZATION_SETTING_NONE);
            builder.setBssid(MacAddress.fromString(WifiConnectionConstants.TARGET_BSSID));
            SoftApConfiguration currentConfig = builder.build();
            mWifiManager.setSoftApConfiguration(currentConfig);
            // Changed: Output currentBlockedClientList
            for (MacAddress macAddress : mWifiManager.getSoftApConfiguration().getBlockedClientList()) {
                Log.d(WifiConnectionConstants.TAG, "Output softApConfigureAndStartHotspot!" + " currentBlockedClientList: " + macAddress.toString());
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "softApConfigureAndStartHotspot!" + " originalConfig is null!");
            /*==========================================*
             * New builder                              *
             *==========================================*/
            SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
            builder.setSsid(WifiConnectionConstants.TARGET_SSID);
            String Passphrase = WifiSHA256Generator.generateSHA256Hash(WifiConnectionConstants.TARGET_SSID);
            // String Passphrase = WifiConnectionConstants.TARGET_PASSPHRASE; // Test
            builder.setPassphrase(Passphrase, WifiConnectionConstants.TARGET_SECURITY_WPA2_PSK);
            builder.setBand(WifiConnectionConstants.TARGET_BAND_5G);
            builder.setMaxNumberOfClients(WifiConnectionConstants.TARGET_MAXNUMBEROFCLIENTS);
            builder.setHiddenSsid(WifiConnectionConstants.TARGET_HIDDEN);
            builder.setClientControlByUserEnabled(WifiConnectionConstants.TARGET_CLIENTCONTROLBYUSER);
            builder.setAutoShutdownEnabled(WifiConnectionConstants.TARGET_AUTOSHUTDOWN);
            // Fixed BSSID
            builder.setMacRandomizationSetting(WifiConnectionConstants.MAC_RANDOMIZATION_SETTING_NONE);
            builder.setBssid(MacAddress.fromString(WifiConnectionConstants.TARGET_BSSID));
            SoftApConfiguration currentConfig = builder.build();
            mWifiManager.setSoftApConfiguration(currentConfig);
        }
        onStartInternal();  // Being register SoftApCallback!
        updateWifiTetheringState(true);  // only activate the hotspot once!
        Log.d(WifiConnectionConstants.TAG, "softApConfigureAndStartHotspot, onStartInternal and updateWifiTetheringState all completed for the first time!");
        Log.d(WifiConnectionConstants.TAG, "softApConfigureAndStartHotspot ending!"); 
    }

    /*==============================================================================*
     * constructor: initialize an object when creating it                           *
     *==============================================================================*/

    public WifiTetheringHandler(Context context, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        this(context, context.getSystemService(WifiManager.class), context.getSystemService(TetheringManager.class), wifiTetherAvailabilityListener);
    }

    public WifiTetheringHandler(Context context, WifiManager wifiManager, TetheringManager tetheringManager, WifiTetheringAvailabilityListener wifiTetherAvailabilityListener) {
        mContext = context;
        mWifiManager = wifiManager;
        mTetheringManager = tetheringManager;
        mWifiTetheringAvailabilityListener = wifiTetherAvailabilityListener;
        // NewAdd
	    registerAllowClientChangedObserver();
    }

    private void registerAllowClientChangedObserver() {
        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("default_allow_client_list"),
                false, new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                String allowList = Settings.System.getString(mContext.getContentResolver(), "default_allow_client_list");
                Log.d(WifiConnectionConstants.TAG, "receive allowClient Change, allowList: " + allowList);
                String allowArray[] = null;
                if (allowList.contains(",")) {
                    allowArray = allowList.split(",");
                }

                SoftApConfiguration softApConfiguration = mWifiManager.getSoftApConfiguration();
                List<MacAddress> currentBlockedClientList = softApConfiguration.getBlockedClientList();
                Log.d(WifiConnectionConstants.TAG, "currentBlockedClientList: " + currentBlockedClientList);
                if (allowArray != null) {
                    for (String allow : allowArray) {
                        MacAddress foundMacAddress = null;
                        for (MacAddress macAddress : currentBlockedClientList) {
                            if (macAddress.toString().equals(allow)) {
                                foundMacAddress = macAddress;
                                break;
                            }
                        }
                        if (foundMacAddress != null) {
                            softApConfiguration.getBlockedClientList().remove(foundMacAddress);
                            Log.d(WifiConnectionConstants.TAG, "1506 remove blocked client: " + allow);
                        }
                    }
                    mWifiManager.setSoftApConfiguration(softApConfiguration);
                } else {
                    MacAddress foundMacAddress = null;
                    for (MacAddress macAddress : currentBlockedClientList) {
                        if (macAddress.toString().equals(allowList)) {
                            foundMacAddress = macAddress;
                            break;
                        }
                    }
                    if (foundMacAddress != null) {
                        softApConfiguration.getBlockedClientList().remove(foundMacAddress);
                        Log.d(WifiConnectionConstants.TAG, "1520 remove blocked client: " + allowList);
                        mWifiManager.setSoftApConfiguration(softApConfiguration);
                    }
                }

            }
        });

        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("default_block_client_list"),
                false, new ContentObserver(mainHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        String blockList = Settings.System.getString(mContext.getContentResolver(), "default_block_client_list");
                        Log.d(WifiConnectionConstants.TAG, "receive blockList Change, allowList: " + blockList);
                        String blockArray[] = null;
                        if (blockList.contains(",")) {
                            blockArray = blockList.split(",");
                        }

                        SoftApConfiguration softApConfiguration = mWifiManager.getSoftApConfiguration();
                        List<MacAddress> currentBlockedClientList = softApConfiguration.getBlockedClientList();
                        Log.d(WifiConnectionConstants.TAG, "currentBlockedClientList: " + currentBlockedClientList);
                        if (blockArray != null) {
                            for (String blockClient : blockArray) {
                                if (!currentBlockedClientList.contains(blockClient)) {
                                    softApConfiguration.getBlockedClientList().add(MacAddress.fromString(blockClient));
                                    Log.d(WifiConnectionConstants.TAG, "1546 add blocked client: " + blockClient);
                                }
                            }
                            mWifiManager.setSoftApConfiguration(softApConfiguration);
                        } else {
                            if (!currentBlockedClientList.contains(blockList)) {
                                softApConfiguration.getBlockedClientList().add(MacAddress.fromString(blockList));
                                Log.d(WifiConnectionConstants.TAG, "1553 add blocked client: " + blockList);
                                mWifiManager.setSoftApConfiguration(softApConfiguration);
                            }
                        }

                    }
                });
    }

    /**
     * Handles operations that should happen in host's onStartInternal().
     */
    public void onStartInternal() {
        mWifiManager.registerSoftApCallback(mContext.getMainExecutor(), mSoftApCallback);
    }

    /**
     * Handles operations that should happen in host's onStopInternal().
     */
    public void onStopInternal() {
        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
    }

    /**
     * Starts WiFi tethering.
     * Callback for use with {@link #startTethering} to find out whether tethering succeeded.
     */
    private void startTethering() {
        mTetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI,
                ConcurrentUtils.DIRECT_EXECUTOR, new TetheringManager.StartTetheringCallback() {
                    /**
                     * Called when starting tethering failed.
                     *
                     * @param error The error that caused the failure.
                     */
                    @Override
                    public void onTetheringFailed(int error) {
                        Log.d(WifiConnectionConstants.TAG, "onTetheringFailed, error: " + error);
                        mWifiTetheringAvailabilityListener.onWifiTetheringUnavailable();
                    }
                    /**
                     * Called when tethering has been successfully started.
                     */
                    @Override
                    public void onTetheringStarted() {
                        Log.d(WifiConnectionConstants.TAG, "onTetheringStarted!");
                        mWifiTetheringAvailabilityListener.onWifiTetheringAvailable();
                    }
                });
    }

    /**
     * Stops WiFi tethering if it's enabled.
     */
    private void stopTethering() {
        if (isWifiTetheringEnabled()) {
            mTetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
        }
    }

    /**
     * Update Tethering State.
     */
    public void updateWifiTetheringState(boolean enable) {
        if (enable) {
            startTethering();
        } else {
            stopTethering();
        }
    }

    /**
     * Returns whether wifi tethering is enabled
     *
     * @return whether wifi tethering is enabled
     */
    public boolean isWifiTetheringEnabled() {
        return mWifiManager.isWifiApEnabled();
    }

    /**
     * Creates a multicast lock to allow the application to receive multicast packets.
     */
    public void createMulticast() {
        Log.d(WifiConnectionConstants.TAG, "start create multicast lock");

        WifiManager.MulticastLock lock = mWifiManager.createMulticastLock("panconnection");
        lock.acquire();
        Log.d(WifiConnectionConstants.TAG, "end create multicast lock");
    }

    /**
     * Adds a WifiDevice to a file if it doesn't already exist.
     *
     * @param wifiDevice The WifiDevice object to add to the file.
     * @param fileName   The name of the file where the WifiDevice will be added.
     */
    public void addDeviceToFile(WifiDevice wifiDevice, String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        boolean exists = file.exists();
        boolean isAdded = false;
        if (exists) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = br.readLine()) != null) {
                    JSONObject obj = new JSONObject(line);
                    if (obj.getString("address").equals(wifiDevice.getAddress())) {
                        isAdded = true;
                        break;
                    }
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
            }
        }
        if (!isAdded) {
            try {
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_APPEND);
                fos.write((wifiDeviceToJson(wifiDevice) + "\n").getBytes());
                fos.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error writing to file", e);
            }
        }
    }

    /**
     * Removes a WifiDevice from a file if it exists.
     *
     * @param wifiDevice The WifiDevice object to remove from the file.
     * @param fileName   The name of the file containing the list of devices.
     */
    public void removeDeviceFromFile(WifiDevice wifiDevice, String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                HashSet<String> deviceSet = new HashSet<>();
                String line;
                while ((line = br.readLine()) != null) {
                    deviceSet.add(line);
                }
                br.close();
                boolean isRemoved = deviceSet.remove(wifiDeviceToJson(wifiDevice).toString());
                if (isRemoved) {
                    FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                    for (String device : deviceSet) {
                        fos.write((device + "\n").getBytes());
                    }
                    fos.close();
                } else {
                    Log.d(WifiConnectionConstants.TAG, "No such device in file: " + wifiDevice.getAddress());
                }
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading or writing file", e);
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "File does not exist: " + fileName);
        }
    }

    /**
     * Checks if a device with a given address is present in a specific file.
     *
     * @param wifiDevice The WifiDevice object to check against the file.
     * @param fileName   The name of the file containing the list of devices.
     * @return true if the device is present in the file, false otherwise.
     */
    public boolean isAllowedDeviceList(WifiDevice wifiDevice, String fileName) {
        boolean isAllowedDevice = false;
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = br.readLine()) != null) {
                    JSONObject obj = new JSONObject(line);
                    if (obj.getString("address").equals(wifiDevice.getAddress())) {
                        isAllowedDevice = true;
                        break;
                    }
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "File does not exist: " + fileName);
        }
        return isAllowedDevice;
    }

    /**
     * Prints all WifiDevices from a file to the log if the file exists.
     *
     * @param fileName The name of the file containing the list of WifiDevices.
     */
    public void printAllDevicesFromFile(String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = br.readLine()) != null) {
                    Log.d(WifiConnectionConstants.TAG, "Device: " + line);
                }
                br.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "File does not exist: " + fileName);
        }
    }

    /**
     * Clears the content of a file if it exists, or creates a new file if it does not exist.
     * Logs the content of the file if it exists and the file is not empty.
     *
     * @param fileName The name of the file to clear or create.
     */
    public void clearDevicesOrCreateFile(String fileName) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                StringBuilder content = new StringBuilder();
                String line;
                boolean isEmpty = true;
                while ((line = br.readLine()) != null) {
                    content.append(line).append("\n");
                    isEmpty = false;
                }
                br.close();
                if (isEmpty) {
                    Log.d(WifiConnectionConstants.TAG, "File exists but is empty: " + fileName);
                } else {
                    Log.d(WifiConnectionConstants.TAG, "File exists with content: " + fileName);
                    Log.d(WifiConnectionConstants.TAG, "Content: " + content.toString());
                }
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                fos.close();
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading or clearing file", e);
            }
        } else {
            try {
                FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                fos.close();
                Log.d(WifiConnectionConstants.TAG, "File did not exist, created successfully: " + fileName);
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error creating file", e);
            }
        }
    }

    /**
     * Converts WifiDevice object to JSON string.
     *
     * @param wifiDevice The WifiDevice object to convert.
     * @return JSON string representation of the WifiDevice object.
     */
    private String wifiDeviceToJson(WifiDevice wifiDevice) {
        JSONObject json = new JSONObject();
        try {
            json.put("address", wifiDevice.getAddress());
            json.put("deviceName", wifiDevice.getDeviceName());
            json.put("deviceType", wifiDevice.getDeviceType());
            json.put("manufacturer", wifiDevice.getManufacturer());
            json.put("batteryLevel", wifiDevice.getBatteryLevel());
            json.put("position", wifiDevice.getPosition());
        } catch (Exception e) {
            Log.e(WifiConnectionConstants.TAG, "Error converting WifiDevice to JSON", e);
        }
        return json.toString();
    }

    /**
     * Finds a WifiDevice by its MAC address in a file.
     *
     * @param macAddress The MAC address to look for.
     * @param fileName   The file where WifiDevices are stored.
     * @return The WifiDevice if found, or null if not found.
     */
    public WifiDevice findWifiDeviceByMac(String macAddress, String fileName) {
        try {
            FileInputStream fis = mContext.openFileInput(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject deviceJson = new JSONObject(line);
                if (deviceJson.getString("address").equals(macAddress)) {
                    reader.close();
                    return new WifiDevice(
                        deviceJson.getString("address"),
                        deviceJson.getString("deviceName"),
                        deviceJson.getString("deviceType"),
                        deviceJson.getString("manufacturer"),
                        deviceJson.getInt("batteryLevel"),
                        deviceJson.getInt("position")
                    );
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
        }
        return null;
    }

    /**
     * Gets the deviceName of a WifiDevice by its MAC address in a file.
     *
     * @param macAddress The MAC address to look for.
     * @param fileName   The file where WifiDevices are stored.
     * @return The deviceName if found, or null if not found.
     */
    public String getDeviceNameByMac(String macAddress, String fileName) {
        WifiDevice device = findWifiDeviceByMac(macAddress, fileName);
        if (device != null) {
            return device.getDeviceName();
        } else {
            return null;
        }
    }

    /**
     * Gets the manufacturer of a WifiDevice by its MAC address in a file.
     *
     * @param macAddress The MAC address to look for.
     * @param fileName   The file where WifiDevices are stored.
     * @return The manufacturer if found, or null if not found.
     */
    public String getManufacturerByMac(String macAddress, String fileName) {
        WifiDevice device = findWifiDeviceByMac(macAddress, fileName);
        if (device != null) {
            return device.getManufacturer();
        } else {
            return null;
        }
    }

    /**
     * Gets the battery level of a WifiDevice by its MAC address in a file.
     *
     * @param macAddress The MAC address to look for.
     * @param fileName   The file where WifiDevices are stored.
     * @return The battery level if found, or null if not found.
     */
    public Integer getBatteryLevelByMac(String macAddress, String fileName) {
        WifiDevice device = findWifiDeviceByMac(macAddress, fileName);
        if (device != null) {
            return device.getBatteryLevel();
        } else {
            return null;
        }
    }

    /**
     * Gets the position of a WifiDevice by its MAC address in a file.
     *
     * @param macAddress The MAC address to look for.
     * @param fileName   The file where WifiDevices are stored.
     * @return The position if found, or null if not found.
     */
    public Integer getPositionByMac(String macAddress, String fileName) {
        WifiDevice device = findWifiDeviceByMac(macAddress, fileName);
        if (device != null) {
            return device.getPosition();
        } else {
            return null;
        }
    }

    /**
     * Checks if a MAC address is present in the address field of WifiDevices in a specific file.
     * If the file does not exist, it creates the file and returns false.
     *
     * @param macAddress The MAC address to check.
     * @param fileName The name of the file containing the list of WifiDevices.
     * @return true if the MAC address is found, false otherwise.
     */
    public boolean isMacAddressInFile(String macAddress, String fileName) {
        File file = new File(mContext.getFilesDir(), fileName);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        JSONObject obj = new JSONObject(line);
                        if (obj.getString("address").equals(macAddress)) {
                            return true;
                        }
                    } catch (JSONException e) {
                        Log.e(WifiConnectionConstants.TAG, "Error parsing JSON", e);
                    }
                }
            } catch (FileNotFoundException e) {
                // This should not happen since we checked if the file exists
                Log.e(WifiConnectionConstants.TAG, "File not found unexpectedly", e);
            } catch (IOException e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading file", e);
            }
        } else {
            try {
                if (!file.createNewFile()) {
                    Log.e(WifiConnectionConstants.TAG, "Failed to create file: " + fileName);
                    return false;
                }
            } catch (IOException e) {
                Log.e(WifiConnectionConstants.TAG, "Error creating file", e);
                return false;
            }
            Log.d(WifiConnectionConstants.TAG, "File did not exist, created: " + fileName);
        }
        return false;
    }

    /**
     * Dynamically updates the batteryLevel and position of a WifiDevice in a file based on its MAC address.
     *
     * @param macdress The MAC address of the WifiDevice to update.
     * @param fileName The name of the file containing the list of WifiDevices.
     * @param newBatteryLevel The new battery level to set.
     * @param newPosition The new position to set.
     * @return true if the update was successful, false otherwise.
     */
    public boolean updateWifiDeviceParameters(String macdress, String fileName, int newBatteryLevel, int newPosition) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                StringBuilder updatedContent = new StringBuilder();
                String line;
                boolean isUpdated = false;
                while ((line = br.readLine()) != null) {
                    JSONObject obj = new JSONObject(line);
                    if (obj.getString("address").equals(macdress)) {
                        // Update the batteryLevel and position
                        obj.put("batteryLevel", newBatteryLevel);
                        obj.put("position", newPosition);
                        // Replace the old line with the updated JSON object
                        updatedContent.append(obj.toString()).append("\n");
                        isUpdated = true;
                    } else {
                        updatedContent.append(line).append("\n");
                    }
                }
                br.close();
                if (isUpdated) {
                    // Write the updated content back to the file
                    FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                    fos.write(updatedContent.toString().getBytes());
                    fos.close();
                    return true;
                } else {
                    Log.d(WifiConnectionConstants.TAG, "No such device in file: " + macdress);
                    return false;
                }
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading or writing file", e);
                return false;
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "File does not exist: " + fileName);
            return false;
        }
    }

    /**
     * Dynamically updates all parameters of a WifiDevice in a file except for its MAC address.
     *
     * @param macdress The MAC address of the WifiDevice to update.
     * @param fileName The name of the file containing the list of WifiDevices.
     * @param newDeviceName The new device name to set.
     * @param newDeviceType The new device type to set.
     * @param newManufacturer The new manufacturer to set.
     * @param newBatteryLevel The new battery level to set.
     * @param newPosition The new position to set.
     * @return true if the update was successful, false otherwise.
     */
    public boolean updateWifiDeviceExceptMac(String macdress, String fileName, 
                                            String newDeviceName, String newDeviceType, 
                                            String newManufacturer, Integer newBatteryLevel, 
                                            Integer newPosition) {
        File file = mContext.getFileStreamPath(fileName);
        if (file.exists()) {
            try {
                FileInputStream fis = mContext.openFileInput(fileName);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                StringBuilder updatedContent = new StringBuilder();
                String line;
                boolean isUpdated = false;
                while ((line = br.readLine()) != null) {
                    JSONObject obj = new JSONObject(line);
                    if (obj.getString("address").equals(macdress)) {
                        // Update all fields except the address
                        obj.put("deviceName", newDeviceName);
                        obj.put("deviceType", newDeviceType);
                        obj.put("manufacturer", newManufacturer);
                        obj.put("batteryLevel", newBatteryLevel);
                        obj.put("position", newPosition);
                        // Replace the old line with the updated JSON object
                        updatedContent.append(obj.toString()).append("\n");
                        isUpdated = true;
                    } else {
                        updatedContent.append(line).append("\n");
                    }
                }
                br.close();
                if (isUpdated) {
                    // Write the updated content back to the file
                    FileOutputStream fos = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
                    fos.write(updatedContent.toString().getBytes());
                    fos.close();
                    return true;
                } else {
                    Log.d(WifiConnectionConstants.TAG, "No such device in file: " + macdress);
                    return false;
                }
            } catch (Exception e) {
                Log.e(WifiConnectionConstants.TAG, "Error reading or writing file", e);
                return false;
            }
        } else {
            Log.d(WifiConnectionConstants.TAG, "File does not exist: " + fileName);
            return false;
        }
    }

    /**
     * Starts the periodic update task.
     *
     * @param macdress The MAC address of the WifiDevice to update.
     * @param fileName The name of the file containing the list of WifiDevices.
     * @param newDeviceName The new device name to set.
     * @param newDeviceType The new device type to set.
     * @param newManufacturer The new manufacturer to set.
     * @param newBatteryLevel The new battery level to set.
     * @param newPosition The new position to set.
     */
    public void startPeriodicUpdate(String macdress, String fileName,
                                    String newDeviceName, String newDeviceType,
                                    String newManufacturer, Integer newBatteryLevel,
                                    Integer newPosition) {
        updateDeviceTimer = new Timer();
        updateDeviceTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Update operation will be performed.
                updateWifiDeviceExceptMac(macdress, fileName, newDeviceName, newDeviceType,
                                          newManufacturer, newBatteryLevel, newPosition);
            }
        }, 0,  updateDevicedelay);
    }

    /**
     * Stops the periodic update task.
     */
    public void stopPeriodicUpdate() {
        if (updateDeviceTimer != null) {
            updateDeviceTimer.cancel();
        }
    }

    /**
     * Retrieves device information for a specified device type and MAC address.
     * Starts a periodic update task.
     *
     * @param deviceType The type of the device to retrieve information for.
     * @param macAddress The MAC address of the device to retrieve information for.
     */
    public void retrieveDeviceInfoAndPeriodicUpdate(String deviceType, String macAddress) {
        UniDeviceController.getInstance(mContext).requestUniDeviceInfo(deviceType, macAddress, new UniDeviceInfoCallback() {
            @Override
            public void onUniDeviceInfoReady(com.rxw.dds.model.UniDevice ddsUniDevice) {
                try {
                    allQueueAddressUpdate.put(ddsUniDevice.id);
                    allQueueDeviceNameUpdate.put(ddsUniDevice.name);
                    allQueueDeviceTypeUpdate.put(ddsUniDevice.type);
                    allQueueManufacturerUpdate.put(ddsUniDevice.manufacturer);
                    allQueueBatteryLevelUpdate.put(String.valueOf(ddsUniDevice.batteryLevel));
                    allQueuePositionUpdate.put(String.valueOf(ddsUniDevice.position));
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String id = allQueueAddressUpdate.take();
                                String name = allQueueDeviceNameUpdate.take();
                                String type = allQueueDeviceTypeUpdate.take();
                                String manufacturer = allQueueManufacturerUpdate.take();
                                String batteryLevel = allQueueBatteryLevelUpdate.take();
                                String position = allQueuePositionUpdate.take();
                                // Log the successful retrieval of device information
                                String logMessage = "retrieveDeviceInfoAndPeriodicUpdate!, macAddress: " + id +
                                ", deviceName: " + name +
                                ", deviceType: " + type +
                                ", manufacturer: " + manufacturer +
                                ", batteryLevel: " + batteryLevel +
                                ", position: " + position;
                                Log.d(WifiConnectionConstants.TAG, logMessage);
                                WifiDevice device = new WifiDevice(id, name, type, manufacturer, Integer.parseInt(batteryLevel), Integer.parseInt(position));
                                // Starts the periodic update task!
                                startPeriodicUpdate(id, WifiConnectionConstants.RECORDED_WIFIDEVICE_FILE, name, type, manufacturer, Integer.parseInt(batteryLevel), Integer.parseInt(position));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
