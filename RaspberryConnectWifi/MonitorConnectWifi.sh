#!/bin/bash

# Function: get current timestamp
log_with_timestamp() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> /home/pi/RaspberryConnectWifi/MonitorConnectWifi.log
}

# Function: save iw dev wlan0 link command output to a file and check for specific MAC address
save_and_check_connection() {
    sudo iw dev wlan0 link > /home/pi/RaspberryConnectWifi/temp.txt
    if grep -q "66:2b:31:c3:05:fe" /home/pi/RaspberryConnectWifi/temp.txt; then
        return 0
    else
        return 1
    fi
}

########################################################################
# Monitor WiFi status using iw command                                                
########################################################################
log_with_timestamp "MonitorConnectWifi starting!!!!"
# Check if MonitorConnectWifi.log exists and clear its content if it does
if [ -f "/home/pi/RaspberryConnectWifi/MonitorConnectWifi.log" ]; then
    > /home/pi/RaspberryConnectWifi/MonitorConnectWifi.log
fi

SSID="PanConnection"

while true; do
    if save_and_check_connection; then
        sleep 1
        log_with_timestamp "Successfully connected to $SSID using iw command."
    else
        log_with_timestamp "Not connected to WiFi. Attempting to reconnect..."
        sudo nmcli connection up "$SSID" &
        CONNECT_PID=$!
        sleep 10
        log_with_timestamp "10 seconds have passed, proceeding with next attempt."
        kill $CONNECT_PID 2>/dev/null
    fi
done

log_with_timestamp "MonitorConnectWifi ending!!!!"