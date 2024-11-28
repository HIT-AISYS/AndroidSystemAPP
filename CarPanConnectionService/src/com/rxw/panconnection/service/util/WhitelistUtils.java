package com.rxw.panconnection.service.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.rxw.panconnection.service.unidevice.UniDevice;
// import com.rxw.panconnection.utils.DataStoreUtils;
import com.rxw.panconnection.utils.SharedPreferencesUtils;

public class WhitelistUtils {

    private static final String TAG = WhitelistUtils.class.getSimpleName();

    private static final Boolean        INVALID_BOOLEAN     = false;
    private static final Integer        INVALID_INTEGER     = -1;
    private static final String         INVALID_STRING      = "";
    private static final Set<String>    INVALID_STRING_SET  = new HashSet<>();

    private static final String NAMESPACE_WHITELIST = "rivotek_whitelist";

    // private final DataStoreUtils mDataStoreUtils;
    private final SharedPreferencesUtils mSharedPreferencesUtils;

    public WhitelistUtils(Context context) {
        // this.mDataStoreUtils = new DataStoreUtils();
        // this.mDataStoreUtils.build(context, NAMESPACE_WHITELIST);
        this.mSharedPreferencesUtils = new SharedPreferencesUtils(context, NAMESPACE_WHITELIST, Context.MODE_PRIVATE);
    }

    /*===================*
     * Allowed Devices   *
     *===================*/
    private static final String KEY_ALLOWED_DEVICE_NAME = "allowed_device_name";

    public void saveAllowedDeviceNames(Set<String> names) {
        // this.mDataStoreUtils.writeValue(KEY_ALLOWED_DEVICE_NAME, names);
        this.mSharedPreferencesUtils.writeValue(KEY_ALLOWED_DEVICE_NAME, names);
    }

    public void saveAllowedDeviceNames(String[] names) {
        // this.mDataStoreUtils.writeValue(KEY_ALLOWED_DEVICE_NAME, new HashSet<>(Arrays.asList(names)));
        this.mSharedPreferencesUtils.writeValue(KEY_ALLOWED_DEVICE_NAME, new HashSet<>(Arrays.asList(names)));
    }

    public Set<String> loadAllowedDeviceNames() {
        // return this.mDataStoreUtils.readStringSetValue(KEY_ALLOWED_DEVICE_NAME, INVALID_STRING_SET);
        return this.mSharedPreferencesUtils.readStringSetValue(KEY_ALLOWED_DEVICE_NAME, INVALID_STRING_SET);
    }


    /*========================*
     * Auto Connect Devices   *
     *========================*/
    private static final String KEY_AUTO_CONNECT_UNIDEVICE = "auto_connect_unidevice";

    public Set<UniDevice> loadAutoConnectDevices() throws JSONException {
        Set<UniDevice> devices = new HashSet<>();

        // Set<String> infoSet = this.mDataStoreUtils.readStringSetValue(KEY_AUTO_CONNECT_UNIDEVICE, INVALID_STRING_SET);
        Set<String> infoSet = this.mSharedPreferencesUtils.readStringSetValue(KEY_AUTO_CONNECT_UNIDEVICE, INVALID_STRING_SET);
        if (infoSet != null) {

            Log.d(TAG, "Load auto connected devices: " + infoSet);

            // Deserialize.
            for (String info : infoSet) {
                JSONObject obj = new JSONObject(info);
                UniDevice device = new UniDevice(
                    obj.optString("address", null),
                    obj.optInt("protocol_type", 0),
                    obj.optString("device_name", null),
                    obj.optString("device_class", null)
                );

                devices.add(device);
                Log.d(TAG, "Added Uni-Device: " + device);
            }
        }

        return devices;
    }

    public void saveAutoConnectDevices(Set<UniDevice> devices) throws JSONException {
        Set<String> infoSet = new HashSet<>();

        // Serialize.
        for(UniDevice device : devices) {
            JSONObject obj = new JSONObject();

            obj.put("address", device.getAddress());
            obj.put("device_name", device.getDeviceName());
            obj.put("protocol_type", device.getProtocolType());
            obj.put("device_class", device.getDeviceClass());

            infoSet.add(obj.toString());
        }

        this.mSharedPreferencesUtils.writeValue(KEY_AUTO_CONNECT_UNIDEVICE, infoSet);
    }

    public void addAutoConnectDevice(UniDevice device) throws JSONException {
        Set<UniDevice> devices = this.loadAutoConnectDevices();

        if (devices.contains(device)) {
            return;
        }

        devices.add(device);

        Log.d(TAG, "Device Set (After added):" + device);
        this.saveAutoConnectDevices(devices);
    }

    public void deleteAutoConnectDevice(UniDevice device) throws JSONException {
        Set<UniDevice> devices = this.loadAutoConnectDevices();

        if (!devices.contains(device)) {
            return;
        }

        devices.remove(device);

        Log.d(TAG, "Device Set (After removed):" + device);
        this.saveAutoConnectDevices(devices);
    }


}
