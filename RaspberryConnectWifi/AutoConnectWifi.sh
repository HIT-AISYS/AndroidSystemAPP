#!/bin/bash

# Function: get current timestamp
log_with_timestamp() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> /home/pi/RaspberryConnectWifi/AutoConnectWifi.log
}

# Function: generate SHA-256 hash
generateSHA256Hash() {
    PASSWORD=$1
    echo -n "$PASSWORD" | sha256sum | awk '{print substr($1, 1, 20)}'
}

# Function: check if connected to the specified network
isConnectedToSSID() {
    # Get the active WiFi connection status
    ACTIVE_CONNECTIONS=$(nmcli -t -f ACTIVE,SSID dev wifi)

    # Check if there is an active connection to the specified SSID
    local connected
    for line in $ACTIVE_CONNECTIONS; do
        if [[ $line == "是:$SSID" ]]; then
            connected=1
            CURRENT_SSID=$SSID
            log_with_timestamp "CURRENT_SSID: $CURRENT_SSID"
            break
        elif [[ $line == "是:"* ]]; then
            CURRENT_SSID=$(echo $line | cut -d':' -f2)
            log_with_timestamp "CURRENT_SSID: $CURRENT_SSID"
        fi
    done

    if [[ -n $connected ]]; then
        # If connected to the target SSID, return 0
        return 0
    elif [[ -n $CURRENT_SSID && "$CURRENT_SSID" != "$SSID" ]]; then
        # If connected to a different SSID, disconnect and forget the network
        log_with_timestamp "Disconnecting from $CURRENT_SSID and forgetting the network..."
        sudo nmcli connection down "$CURRENT_SSID"
        sudo nmcli connection delete "$CURRENT_SSID"

        log_with_timestamp "Network $CURRENT_SSID forgotten."

        return 1
    else
        log_with_timestamp "No active WiFi connection or not connected to the target SSID."
        return 1
    fi
}

# Function: attempt connection and set autoconnect
attemptConnectionAndAutoConnect() {
    while true; do
        # Scan for available networks and check if the specified SSID is present
        # nmcli -t -f SSID dev wifi list | grep -i "PanConnection"
        AVAILABLE_SSIDS=$(nmcli -t -f SSID dev wifi list | grep -i "$SSID")
        if [ -n "$AVAILABLE_SSIDS" ]; then
            log_with_timestamp "SSID $SSID found. Attempting to connect..."
            sleep 1
            sudo nmcli dev wifi connect "$SSID" password "$ENCRYPTED_PASSWORD"
            sleep 1
            sudo nmcli connection modify "$SSID" connection.autoconnect no
            log_with_timestamp "Autoconnect set for $SSID."
            return 0
        else
            log_with_timestamp "SSID $SSID not found. Scanning again in 15 seconds..."
            sleep 2
        fi
    done
}

#########################################################################
# WiFi SSID and password                                                
#########################################################################
SSID="PanConnection"
PASSWORD="PanConnection"
ENCRYPTED_PASSWORD=$(generateSHA256Hash "$PASSWORD")
log_with_timestamp "System is opening! Script start!"
log_with_timestamp "SSID: $SSID, ENCRYPTED_PASSWORD: $ENCRYPTED_PASSWORD"

#########################################################################
# Attempt connection and set autoconnect                                 
#########################################################################
attemptConnectionAndAutoConnect

#########################################################################
# Monitor WiFi status                                                
#########################################################################
while true; do
    if isConnectedToSSID; then
        log_with_timestamp "Successfully connected to $SSID."
        sleep 20 
        sudo nmcli connection up $SSID
        break
    else
        log_with_timestamp "Continuing waiting for $SSID to connect."
    fi
    sleep 5
done

# Execute DeleteConnectWifi.sh and MonitorConnectWifi.sh in the background
bash /home/pi/RaspberryConnectWifi/MonitorConnectWifi.sh &

log_with_timestamp "Script finished!"
log_with_timestamp "------------------------------------------------------------------------"