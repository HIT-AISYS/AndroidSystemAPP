package com.rxw.panconnection.service.wifi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import android.util.Log;

public class WifiDevice {

    private String address;
    private String deviceName;
    private String deviceType;
    private String manufacturer;
    private int batteryLevel;
    private int position;

    // Constructor to initialize the WifiDevice object
    public WifiDevice(String address, String deviceName, String deviceType, String manufacturer, int batteryLevel, int position) {
        this.address = address;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.manufacturer = manufacturer;
        this.batteryLevel = batteryLevel;
        this.position = position;
    }

    /*===================================================================*
     * Utils
     *===================================================================*/
    /**
     * Converts the WifiDevice object to a string representation.
     * @return A string representation of the WifiDevice object.
     */
    @Override
    public String toString() {
        return "WifiDevice{" +
                "address='" + address + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", position=" + position +
                '}';
    }

    /**
     * Compares this device with another for equality.
     * @param other The other device to compare with.
     * @return true if the devices are the same, false otherwise.
     */
    public boolean equals(WifiDevice other) {
        if (other == null) return false;
        return this.address.equals(other.address) &&
               this.deviceName.equals(other.deviceName) &&
               this.deviceType.equals(other.deviceType) &&
               this.manufacturer.equals(other.manufacturer) &&
               this.batteryLevel == other.batteryLevel &&
               this.position == other.position;
    }

    /**
     * Returns a JSON representation of the WifiDevice object.
     * @return A JSON string representing the WifiDevice object.
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("device_type", deviceType);
            json.put("deviceName", deviceName);
            json.put("device_name", address);
            json.put("manufacturer", manufacturer);
            json.put("battery", batteryLevel);
            json.put("position", position);
        } catch (JSONException e) {
            Log.e(WifiConnectionConstants.TAG, "Error converting WifiDevice to JSON", e);
        }
        return json.toString();
    }

    /*===================================================================*
     * Getter
     *===================================================================*/

    public String getAddress() {
        return address;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public int getPosition() {
        return position;
    }

    /*===================================================================*
     * Setter
     *===================================================================*/

    public void setAddress(String address) {
        this.address = address;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}