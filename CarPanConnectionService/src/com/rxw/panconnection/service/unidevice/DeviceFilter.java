package com.rxw.panconnection.service.unidevice;

import android.content.Context;
import android.util.Log;

import com.rxw.panconnection.service.utils.WhitelistUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class DeviceFilter {
    private static final String TAG = "PanConnectionService.DeviceFilter";

    private Set<String> mWhitelist;

    public DeviceFilter(Context context) {
        WhitelistUtils wu = new WhitelistUtils(context);
        this.mWhitelist = new HashSet<>(wu.loadAllowedDeviceNames());

        Log.d(TAG, "WhiteList: " + this.mWhitelist);
    }

    public Predicate<UniDevice> mFilter = device -> {

        Log.d(TAG, "WhiteList: " + this.mWhitelist);

        int type = device.getProtocolType();
        String name = device.getDeviceName();
        if (name == null) {
            return false;
        }


        // Classic Bluetooth
        if (UniDevice.isBcDevice(type)){
            for(String item : this.mWhitelist) {
                Log.d(TAG, "Whitelist: " + item);
                if (name.contains(item)) {
                    return true;
                }
            }
        }

        // Bluetooth Low Energy
        if (UniDevice.isBleDevice(type)) {
            for(String item : this.mWhitelist) {
                Log.d(TAG, "Whitelist: " + item);
                if (name.contains(item)) {
                    return true;
                }
            }
        }

        // Wi-Fi
        // TODO: Wi-Fi

        return false;
    };

}
