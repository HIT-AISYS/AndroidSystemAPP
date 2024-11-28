package com.rxw.panconnection.service.hvac;

public interface IHvacCallback {

    void onTemperatureChanged(float temperature);
    void onWindSpeedChanged(int windSpeed);
    void onHvacSwitchChanged(boolean opened);
}
