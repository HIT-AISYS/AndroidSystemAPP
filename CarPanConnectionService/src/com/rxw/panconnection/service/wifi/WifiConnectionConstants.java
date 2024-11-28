package com.rxw.panconnection.service.wifi;

import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.net.wifi.SoftApCapability;

public class WifiConnectionConstants {

    public static String TAG = "WifiTetheringHandler";
    public static String ALLOWED_CLIENT_LIST_FILE = "AllowedClientList.txt";
    public static String RECORDED_WIFIDEVICE_FILE = "RecordedWifiDevice.txt";
    public static String TO_CONNECTEDCLIENTLIST_FILE = "ToConnectedClientList.txt";

    // WifiTethering setting
    public static String TARGET_SSID = "PanConnection";
    public static String TARGET_PASSPHRASE = "12345678";
    public static int SHA256HashLength = 20;
    public static String TARGET_BSSID = "66:2B:31:C3:05:FE";
    public static int TARGET_MAXNUMBEROFCLIENTS = 10;
    public static boolean TARGET_HIDDEN = false;  // Donot broadcast SSID
    public static boolean TARGET_CLIENTCONTROLBYUSER = false;
    public static boolean TARGET_AUTOSHUTDOWN = false;

    /**
     * CHANNEL_WIDTH_AUTO = -1;
     * CHANNEL_WIDTH_INVALID = 0;
     * CHANNEL_WIDTH_20MHZ_NOHT = 1;
     * CHANNEL_WIDTH_20MHZ = 2;
     * CHANNEL_WIDTH_40MHZ = 3;
     * CHANNEL_WIDTH_80MHZ = 4;
     * CHANNEL_WIDTH_80MHZ_PLUS_MHZ = 5;
     * CHANNEL_WIDTH_160MHZ = 6;
     * CHANNEL_WIDTH_2160MHZ = 7;
     * CHANNEL_WIDTH_4320MHZ = 8;
     * CHANNEL_WIDTH_6480MHZ = 9;
     * CHANNEL_WIDTH_8640MHZ = 10;
     * CHANNEL_WIDTH_320MHZ = 11;
     */
    public static final int TARGET_CHANNEL_WIDTH_AUTO = SoftApInfo.CHANNEL_WIDTH_AUTO;
    public static final int TARGET_CHANNEL_WIDTH_INVALID = SoftApInfo.CHANNEL_WIDTH_INVALID;
    public static final int TARGET_CHANNEL_WIDTH_20MHZ_NOHT = SoftApInfo.CHANNEL_WIDTH_20MHZ_NOHT;
    public static final int TARGET_CHANNEL_WIDTH_20MHZ = SoftApInfo.CHANNEL_WIDTH_20MHZ;
    public static final int TARGET_CHANNEL_WIDTH_40MHZ = SoftApInfo.CHANNEL_WIDTH_40MHZ;
    public static final int TARGET_CHANNEL_WIDTH_80MHZ = SoftApInfo.CHANNEL_WIDTH_80MHZ;
    public static final int TARGET_CHANNEL_WIDTH_80MHZ_PLUS_MHZ = SoftApInfo.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
    public static final int TARGET_CHANNEL_WIDTH_160MHZ = SoftApInfo.CHANNEL_WIDTH_160MHZ;
    public static final int TARGET_CHANNEL_WIDTH_2160MHZ = SoftApInfo.CHANNEL_WIDTH_2160MHZ;
    public static final int TARGET_CHANNEL_WIDTH_4320MHZ = SoftApInfo.CHANNEL_WIDTH_4320MHZ;
    public static final int TARGET_CHANNEL_WIDTH_6480MHZ = SoftApInfo.CHANNEL_WIDTH_6480MHZ;
    public static final int TARGET_CHANNEL_WIDTH_8640MHZ = SoftApInfo.CHANNEL_WIDTH_8640MHZ;
    public static final int TARGET_CHANNEL_WIDTH_320MHZ = SoftApInfo.CHANNEL_WIDTH_320MHZ;

    /**
     * SECURITY_TYPE_OPEN = 0;
     * SECURITY_TYPE_WPA2_PSK = 1;
     * SECURITY_TYPE_WPA3_SAE_TRANSITION = 2;
     * SECURITY_TYPE_WPA3_SAE = 3;
     * SECURITY_TYPE_WPA3_OWE_TRANSITION = 4;
     * SECURITY_TYPE_WPA3_OWE = 5;
     */
    public static int TARGET_SECURITY_OPEN = SoftApConfiguration.SECURITY_TYPE_OPEN;
    public static int TARGET_SECURITY_WPA2_PSK = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK;
    public static int TARGET_SECURITY_WPA3_SAE_TRANSITIO = SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION;
    public static int TARGET_SECURITY_WPA3_SAE = SoftApConfiguration.SECURITY_TYPE_WPA3_SAE;
    public static int TARGET_SECURITY_WPA3_OWE_TRANSITION = SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION;
    public static int TARGET_SECURITY_TYPE_WPA3_OWE = SoftApConfiguration.SECURITY_TYPE_WPA3_OWE;

    /**
     * RANDOMIZATION_NONE = 0;
     * RANDOMIZATION_PERSISTENT = 1;
     * RANDOMIZATION_NON_PERSISTENT = 2;
     */
    public static int MAC_RANDOMIZATION_SETTING_NONE = SoftApConfiguration.RANDOMIZATION_NONE; 
    public static int MAC_RANDOMIZATION_PERSISTENT = SoftApConfiguration.RANDOMIZATION_PERSISTENT;
    public static int MAC_RANDOMIZATION_SETTING_NONE_PERSISTENT = SoftApConfiguration.RANDOMIZATION_NON_PERSISTENT;

    /**
     * BAND_2GHZ = 1 << 0;
     * BAND_5GHZ = 1 << 1;
     * BAND_6GHZ = 1 << 2;
     * BAND_60GHZ = 1 << 3;
     * BAND_ANY = BAND_2GHZ | BAND_5GHZ | BAND_6GHZ
     */
    public static int TARGET_BAND_2G = SoftApConfiguration.BAND_2GHZ;
    public static int TARGET_BAND_5G = SoftApConfiguration.BAND_5GHZ;
    public static int TARGET_BAND_6G = SoftApConfiguration.BAND_6GHZ;
    public static int TARGET_BAND_60G = SoftApConfiguration.BAND_60GHZ;
    public static int TARGET_BAND_ANY = SoftApConfiguration.BAND_ANY;

    /**
     * SOFTAP_FEATURE_ACS_OFFLOAD = 1 << 0;
     * SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT = 1 << 1;
     * SOFTAP_FEATURE_WPA3_SAE = 1 << 2;
     * SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION = 1 << 3;
     * SOFTAP_FEATURE_IEEE80211_AX = 1 << 4;
     * SOFTAP_FEATURE_BAND_24G_SUPPORTED = 1 << 5;
     * SOFTAP_FEATURE_BAND_5G_SUPPORTED = 1 << 6;
     * SOFTAP_FEATURE_BAND_6G_SUPPORTED = 1 << 7;
     * SOFTAP_FEATURE_BAND_60G_SUPPORTED = 1 << 8;
     * SOFTAP_FEATURE_IEEE80211_BE = 1 << 9;
     * SOFTAP_FEATURE_WPA3_OWE_TRANSITION = 1 << 10;
     * SOFTAP_FEATURE_WPA3_OWE = 1 << 11;
     */
    public static final long TARGET_SOFTAP_FEATURE_ACS_OFFLOAD = SoftApCapability.SOFTAP_FEATURE_ACS_OFFLOAD;
    public static final long TARGET_SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT = SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT;
    public static final long TARGET_SOFTAP_FEATURE_WPA3_SAE = SoftApCapability.SOFTAP_FEATURE_WPA3_SAE;
    public static final long TARGET_SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION = SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION;
    public static final long TARGET_SOFTAP_FEATURE_IEEE80211_AX = SoftApCapability.SOFTAP_FEATURE_IEEE80211_AX;
    public static final long TARGET_SOFTAP_FEATURE_BAND_24G_SUPPORTED = SoftApCapability.SOFTAP_FEATURE_BAND_24G_SUPPORTED;
    public static final long TARGET_SOFTAP_FEATURE_BAND_5G_SUPPORTED = SoftApCapability.SOFTAP_FEATURE_BAND_5G_SUPPORTED;
    public static final long TARGET_SOFTAP_FEATURE_BAND_6G_SUPPORTED = SoftApCapability.SOFTAP_FEATURE_BAND_6G_SUPPORTED;
    public static final long TARGET_SOFTAP_FEATURE_BAND_60G_SUPPORTED = SoftApCapability.SOFTAP_FEATURE_BAND_60G_SUPPORTED;
    public static final long TARGET_SOFTAP_FEATURE_IEEE80211_BE = SoftApCapability.SOFTAP_FEATURE_IEEE80211_BE;
    public static final long TARGET_SOFTAP_FEATURE_WPA3_OWE_TRANSITION = SoftApCapability.SOFTAP_FEATURE_WPA3_OWE_TRANSITION;
    public static final long TARGET_SOFTAP_FEATURE_WPA3_OWE = SoftApCapability.SOFTAP_FEATURE_WPA3_OWE;

    // deviceType
    public static String DEVICE_TYPE_UNKNOWN = "unknown";
    public static String DEVICE_TYPE_CAMERA = "camera";
    public static String DEVICE_TYPE_FRAGRANCE = "fragrance";
    public static String DEVICE_TYPE_ATMOSPHERE_LIGHT = "atmosphere_light";
    public static String DEVICE_TYPE_MIRROR = "mirror";
    public static String DEVICE_TYPE_DISINFECTION = "disinfection";
    public static String DEVICE_TYPE_HUMIDIFIER = "humidifier";
    public static String DEVICE_TYPE_MICROPHONE = "microphone";

    // camera
    public static List<String> DEVICE_TYPE_LIST_CAMERA = Arrays.asList(
    "d8:3a:dd:c0:cd:ba",
        "d8:3a:dd:c0:cd:50"
    );

    // fragrance
    public static List<String> DEVICE_TYPE_LIST_FRAGRANCE = new ArrayList<>();

    // atmosphere_light
    public static List<String> DEVICE_TYPE_LIST_ATMOSPHERE_LIGHT = new ArrayList<>();

    // mirror
    public static List<String> DEVICE_TYPE_LIST_MIRROR = new ArrayList<>();

    // disinfection
    public static List<String> DEVICE_TYPE_LIST_DISINFECTION = new ArrayList<>();

    // humidifier
    public static List<String> DEVICE_TYPE_LIST_HUMIDIFIER = new ArrayList<>();

    // microphone
    public static List<String> DEVICE_TYPE_LIST_MICROPHONE = new ArrayList<>();

    // intent
    public static String ACTION_DISCOVERED_DEVICES = "com.rxw.ACTION_DISCOVERED_DEVICES";
    public static String PACKAGE_DISCOVERED_DEVICES = "com.rxw.car.panconnection";

    // discoveryState
    public static int DISCOVERY_SEARCHING = 1;
    public static int DISCOVERY_END = 2;

    // connectionState
    public static int CONNECTING = 3;
    public static int CONNECTED = 4;
    public static int DISCONNECTING = 5;
    public static int DISCONNECTED = 6;

    // bondState
    public static int UNBONDING = 7;
    public static int UNBONDED = 8;

    // errorCode
    public static int ERRORCODE_CONNECTION_NOTEXIST = 0;
    public static int ERRORCODE_CONNECTION_NOCLIENTSNUM = 1;
    public static int ERRORCODE_CONNECTION_SOFTAPSHUTDOWN = 2;
    public static int ERRORCODE_CONNECTION_OTHER = 4;
    public static int ERRORCODE_UNBOND = 5;
}
