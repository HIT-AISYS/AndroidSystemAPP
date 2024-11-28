package com.rxw.panconnection.service.wifi;

import java.util.List;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;

/**
 * Interface for observing changes related to Wi-Fi tethering.
 * This interface provides callbacks for various Wi-Fi tethering events,
 * such as device discovery, connection state changes, and capability changes.
 */
public interface WifiTetheringObserver {

    /*===================================================================*
     * WifiTethering Observer
     *===================================================================*/

    /**
     * Callback for pan device discovery state
     * @param deviceList JSON list of discovered devices
     * @param discoveryState DISCOVERY_SEARCHING (1) or DISCOVERY_END (2)
     */
    default void onDiscoveryStateChanged(String deviceList, int discoveryState) {
    }

    /**
     * Callback for pan device connection state
     * @param device JSON of UniDevice
     * @param connectionState CONNECTING (3), CONNECTED (4), DISCONNECTING (5), DISCONNECTED (6)
     */
    default void onConnectionStateChanged(String device, int connectionState) {
    }

    /**
     * Callback for pan device connection failure
     * @param device JSON of UniDevice
     * @param errorCode Error code for connection failure
     */
    default void onConnectionFailed(String device, int errorCode) {
    }

    /**
     * Callback for pan device unbonding state
     * @param device JSON of UniDevice
     * @param bondState UNBONDING (7), UNBONDED (8)
     */
    default void onUnbondStateChanged(String device,  int bondState) {
    }

    /**
     * Callback for pan device unbonding failure
     * @param device JSON of UniDevice
     * @param errorCode Error code for unbonding failure
     */
    default void onUnbondFailed(String device, int errorCode) {
    }

    /*====================================================================================*
     * Getter Observer
     *====================================================================================*/

    /**
     * Called when tethering state changes.
     * @param state New tethering state.
     * @param failureReason Reason for failure, if applicable.
     */
    default void getOnStateChanged(int state, int failureReason) {
    }

    /**
     * Called when connected clients change.
     * @param info Soft AP info.
     * @param clients List of connected clients.
     */
    default void getOnConnectedClientsChanged(SoftApInfo info, List<WifiClient> clients) {
    }

    /**
     * Called when a blocked client attempts to connect.
     * @param client Attempting client.
     * @param blockedReason Reason for block.
     */
    default void getOnBlockedClientConnecting(WifiClient client, int blockedReason) {
    }

    /**
     * Called when tethering capability changes.
     * @param softApCapability New tethering capability.
     */
    default void getOnCapabilityChanged(SoftApCapability softApCapability) {
    }

    /**
     * Called when tethering info changes.
     * @param softApInfoList List of Soft AP info.
     */
    default void getOnInfoChanged(List<SoftApInfo> softApInfoList) {
    }
}