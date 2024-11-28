package com.rxw.panconnection.service.wifi;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import android.net.wifi.WifiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WifiConnectionUtils {

    /**
     * Finds the list of disconnected clients by comparing the previous list of clients with the current list.
     *
     * @param previousClients The list of clients from the previous state.
     * @param currentClients The current list of clients.
     * @return A list of disconnected clients.
     */
    public static List<WifiClient> findDisconnectedClients(List<WifiClient> previousClients, List<WifiClient> currentClients) {
        List<WifiClient> disconnectedClients = new ArrayList<>();
        for (WifiClient previousClient : previousClients) {
            if (!currentClients.contains(previousClient)) {
                disconnectedClients.add(previousClient);
            }
        }
        return disconnectedClients;
    }

    /**
     * Converts device information to a JSON string.
     *
     * @param deviceType The type of the device.
     * @param deviceName The name of the device.
     * @param macAddress The MAC address of the device.
     * @return A JSON string representing the device information, or an empty string if an error occurs.
     */
    public static String getDeviceToJSON(String deviceType, String deviceName, String macAddress) {
        try {
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("device_type", deviceType);
            deviceObject.put("device_name", deviceName);
            deviceObject.put("device_id", macAddress);
            String device = deviceObject.toString();
            return device;
        } catch (Exception e) {
            Log.e(WifiConnectionConstants.TAG, "Error creating broadcast JSON: ", e);
            return  "";
        }
    }
}