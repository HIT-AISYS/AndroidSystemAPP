package com.rxw.panconnection.service.wifi;

import java.util.List;

public interface WifiTetheringAvailabilityListener {

    /*===================================================================*
     * WifiTethering Listener
     *===================================================================*/

    /**
     * Callback for when Wifi tethering is available
     */
    default void onWifiTetheringAvailable() {
    }

    /**
     * Callback for when Wifi tethering is unavailable
     */
    default void onWifiTetheringUnavailable() {
    }

    /**
     * Callback for when the number of tethered devices has changed
     *
     * @param clientCount number of connected clients
     */
    default void onConnectedClientsChanged(int clientCount) {
    }

    /**
     * Callback for when a Wifi device is discovered
     *
     * @param device the discovered Wifi device
     */
    default void onDiscoveryWifiDevice(WifiDevice device) {
    }

    /**
     * Callback for when connected Wifi devices are retrieved
     *
     * @param connectedWifiDevices a list of connected Wifi devices
     */
    default void connectedWifiDevices(List<WifiDevice> connectedWifiDevices) {
    }
}