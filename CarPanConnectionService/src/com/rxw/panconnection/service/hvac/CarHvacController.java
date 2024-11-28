package com.rxw.panconnection.service.hvac;

import android.content.Context;

import com.rxw.panconnection.control.command.Command;
import com.rxw.panconnection.service.command.CommandManager;
import com.rxw.panconnection.service.xcore.CarServiceManager;


/**
 * receive control command from the device(such as pad) to control the hvac of the car
 */
public class CarHvacController {

    private final static String TOPIC_HVAC_CHANGED_SWITCH = "hvac_changed_switch";
    private final static String TOPIC_HVAC_CHANGED_TEMPERATURE = "hvac_changed_temperature";
    private final static String TOPIC_HVAC_CHANGED_WIND_SPEED = "hvac_changed_wind_speed";

    private static volatile CarHvacController sInstance;
    private CarServiceManager mCarServiceManager;
    private CommandManager mCommandManager;

    private Command mHvacSwitchChangedCmd;
    private Command mTemperatureChangedCmd;
    private Command mWindSpeedChangedCmd;


    public static CarHvacController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (CarHvacController.class) {
                if (sInstance == null) {
                    sInstance = new CarHvacController(context);
                }
            }
        }
        return sInstance;
    }

    private IHvacCallback mIHvacCallback = new IHvacCallback() {
        @Override
        public void onTemperatureChanged(float temperature) {
            mTemperatureChangedCmd.content = temperature;
            mCommandManager.sendCommand(mTemperatureChangedCmd);
        }

        @Override
        public void onWindSpeedChanged(int windSpeed) {
            mWindSpeedChangedCmd.content = windSpeed;
            mCommandManager.sendCommand(mWindSpeedChangedCmd);
        }

        @Override
        public void onHvacSwitchChanged(boolean opened) {
            mHvacSwitchChangedCmd.content = opened;
            mCommandManager.sendCommand(mHvacSwitchChangedCmd);
        }
    };

    public void init() {
        mCarServiceManager.addAirConditionListener(mIHvacCallback);
    }

    private CarHvacController(Context context) {
        mCarServiceManager = CarServiceManager.getInstance();
        mCarServiceManager.create(context);
        mCommandManager = CommandManager.getInstance(context);

        mHvacSwitchChangedCmd = Command.create(TOPIC_HVAC_CHANGED_SWITCH);
        mTemperatureChangedCmd = Command.create(TOPIC_HVAC_CHANGED_TEMPERATURE);
        mWindSpeedChangedCmd = Command.create(TOPIC_HVAC_CHANGED_WIND_SPEED);
     }

}
