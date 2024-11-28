package com.rxw.panconnection.service.unidevice;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Objects;

public class UniDevice {
    private static final String TAG = UniDevice.class.getSimpleName();

    private String mAddress;
    private int mProtocolType;
    private String mDeviceName;
    private String mDeviceClass;

    /*===================*
     * Utils             *
     *===================*/

    public static final int PROTOCOL_TYPE_UNKNOWN           = 0     ;
    public static final int PROTOCOL_TYPE_BLUETOOTH_CLASSIC = 1     ;
    public static final int PROTOCOL_TYPE_BLUETOOTH_LE      = 1 << 1;
    public static final int PROTOCOL_TYPE_WIFI              = 1 << 2;

    public static boolean isBcDevice(final int type) {
        return (type & (PROTOCOL_TYPE_BLUETOOTH_CLASSIC)) != 0;
    }

    public static boolean isBleDevice(final int type) {
        return (type & (PROTOCOL_TYPE_BLUETOOTH_LE)) != 0;
    }

    public static boolean isWifiDevice(final int type) {
        return (type & (PROTOCOL_TYPE_WIFI)) != 0;
    }

    public static final String DEVICE_CLASS_UNKNOWN = "unknown";
    public static final String DEVICE_CLASS_CAMERA = "camera";
    public static final String DEVICE_CLASS_FRAGRANCE = "aroma";
    public static final String DEVICE_CLASS_ATMOSPHERE_LIGHT = "atmosphere_light";
    public static final String DEVICE_CLASS_MIRROR = "mirror";
    public static final String DEVICE_CLASS_DISINFECTION = "disinfection";
    public static final String DEVICE_CLASS_HUMIDIFIER = "humidifier";
    public static final String DEVICE_CLASS_MICROPHONE = "microphone";

    /*===================*
     * Constructors      *
     *===================*/

     public UniDevice(String address, int protocolType, String deviceName, String deviceClass) {
        Log.d(TAG, "Constructor (4): {address=" + address + ", protocol=" + protocolType + ", name=" + deviceName + ", class=" + deviceClass + "}");
        this.mAddress = address;
        this.mProtocolType = protocolType;
        this.mDeviceName = deviceName;
        this.mDeviceClass = deviceClass;
    }

    public UniDevice(String address, int protocolType, String deviceName) {
        Log.d(TAG, "Constructor (3): {address=" + address + ", protocol=" + protocolType + ", name=" + deviceName + "}");
        String type = "unknown";

        if (deviceName != null && deviceName.contains("ai-thinker")) {
            type = DEVICE_CLASS_FRAGRANCE;
        }

        this.mAddress = address;
        this.mProtocolType = protocolType;
        this.mDeviceName = deviceName;
        this.mDeviceClass = type;
    }

    /*========================*
     * Getters and Setters    *
     *========================*/

    public String getAddress() {
        Log.d(TAG, "getAddress");
        return this.mAddress;
    }

    public int getProtocolType() {
        Log.d(TAG, "getProtocolType");
        return this.mProtocolType;
    }

    public void addProtocolType(int mask) {
        Log.d(TAG, "addProtocolType");
        this.mProtocolType = this.mProtocolType | mask;
    }

    public void deleteProtocolType(int mask) {
        Log.d(TAG, "deleteProtocolType");
        this.mProtocolType = this.mProtocolType & (~mask);
    }

    public String getDeviceName() {
        Log.d(TAG, "getDeviceName");
        return this.mDeviceName;
    }

    public String getDeviceClass() {
        Log.d(TAG, "getDeviceClass");
        return this.mDeviceClass;
    }

    public static JSONObject toJSONObject(UniDevice device) throws JSONException {
        Log.d(TAG, "toJSONObject (static): " + device.toString());
        String name = device.getDeviceName();
        String address = device.getAddress();
        String clazz = device.getDeviceClass();

        JSONObject obj = new JSONObject();
        obj.put("device_type", clazz);
        obj.put("device_name", name);
        obj.put("device_id", address);

        Log.d(TAG, "After toJSONObject (static): " + obj.toString());
        return obj;
    }

    public JSONObject toJSONObject() throws JSONException {
        Log.d(TAG, "toJSONObject: " + this.toString());

        JSONObject obj = new JSONObject();
        obj.put("device_type", this.mDeviceClass);
        obj.put("device_name", this.mDeviceName);
        obj.put("device_id", this.mAddress);

        Log.d(TAG, "After toJSONObject: " + obj.toString());
        return obj;
    }

    public static JSONArray getDeviceInfoJsonArray(Collection<UniDevice> collection) throws JSONException {
        Log.d(TAG, "getDeviceInfoJsonArray");

        JSONArray array = new JSONArray();
        for (Object obj : collection) {

            if (obj instanceof UniDevice) {
                array.put(((UniDevice) obj).toJSONObject());
            }

            if (obj instanceof JSONObject) {
                array.put(obj);
            }

        }

        Log.d(TAG, "After getDeviceInfoJsonArray: " + array.toString());

        return array;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniDevice device = (UniDevice) o;
        return (
                TextUtils.equals(this.mAddress        , device.mAddress       ) &&
                TextUtils.equals(this.mDeviceName     , device.mDeviceName    ) &&
                Objects.equals(this.mProtocolType   , device.mProtocolType  )
        );
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAddress);
    }

    @Override
    public String toString() {
        return String.format("[name=%s, protocol=%d, address=%s, type=%s]", this.mDeviceName, this.mProtocolType, this.mAddress, this.mDeviceClass);
    }

    /*===================*
     * temp              *
     *===================*/

    public static String getDeviceType(UniDevice device) {
        String type = "unknown";
        String name = device.getDeviceName();

        if (name != null && name.contains("ai-thinker")) {
            type = DEVICE_CLASS_FRAGRANCE;
        } else if (UniDevice.isWifiDevice(device.mProtocolType)) {
            type = DEVICE_CLASS_CAMERA;
        }

        return type;
    }

}
