package com.rxw.panconnection.service.xcore;

import android.car.Car;
import android.car.xcore.audio.XcoreCarAudioManager;
import android.car.xcore.key.XcoreCarInputManager;
import android.car.xcore.key.XcoreKeyEvent;
import android.car.xcore.vehicle.AirCondition;
import android.car.xcore.vehicle.AirConditionState;
import android.car.xcore.vehicle.VehicleState;
import android.car.xcore.vehicle.XCoreVehicleListener;
import android.car.xcore.vehicle.XCoreVehicleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.rxw.panconnection.service.hvac.IHvacCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CarServiceManager {

    private static final String TAG = "CarServiceManager";
    private final List<CarConnectionListener> mCarConnectionListenerList = new CopyOnWriteArrayList<>();
    private Car mCar;
    private boolean mIsConnected = false;
    private ComponentName mName;
    private IBinder mService;
    private XcoreCarAudioManager mXcoreCarAudioManager;
    private XCoreVehicleManager mXCoreVehicleManager;
    private XcoreCarInputManager mXcoreInputManager;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<VehicleState, Set<XCoreVehicleListenerInner>> mVehicleListeners = new ConcurrentHashMap<>();
    private final Set<AudioVolumeListenerInner> mAudioListeners = Collections.synchronizedSet(new HashSet<>());
    private final Set<CarKeyListenerInner> mCarKeyListeners = Collections.synchronizedSet(new HashSet<>());
    private List<IHvacCallback> mAirConditionListeners = new ArrayList<>();


    public interface XCoreVehicleListenerInner {
        void onVehicleSignalStatusChanged(VehicleState vehicle, int state, int signal_status);
    }

    public interface AudioVolumeListenerInner {
        void onStreamVolumeChange(int streamType, int volume, int flags);

        void onMuteStateChange(boolean mute, int streamType);
    }

    public interface CarKeyListenerInner {
        void onKeyEvent(XcoreKeyEvent event);

        default void onEvent(XcoreKeyEvent event, String var2, int var3) {
        }
    }


    private static final class CarServiceManagerHolder {
        private static final CarServiceManager instance = new CarServiceManager();
    }

    public static CarServiceManager getInstance() {
        return CarServiceManagerHolder.instance;
    }

    public void create(Context context) {
        if (mCar == null) {
            mCar = Car.createCar(context, mServiceConnection);
            Log.d(TAG, "create: car = " + mCar);
            mCar.connect();
        }
    }

    private final XCoreVehicleListener mXcoreListenerOuter = new XCoreVehicleListener() {
        @Override
        public void onVehicleSignalStatusChanged(VehicleState vehicle, int state, int signal_status) {
            Set<XCoreVehicleListenerInner> listenerInners = mVehicleListeners.get(vehicle);
            if (listenerInners != null && listenerInners.size() > 0) {
                mHandler.post(() -> {
                    for (XCoreVehicleListenerInner l : listenerInners) {
                        l.onVehicleSignalStatusChanged(vehicle, state, signal_status);
                    }
                });
            }
        }

        @Override
        public void onAirConditionChanged(AirCondition airConditionData) throws RemoteException {
            if (airConditionData == null) {
                return;
            }

            int temperature = airConditionData.getAirDriverTemp();
            int fanSpeed = airConditionData.getAirFBlowerSpeed();
            boolean isOn = airConditionData.getAirPowerSwitch() == AirConditionState.SWITCH_ON;

            // 通知所有的回调监听器
            mHandler.post(() -> {
                for (IHvacCallback listener : mAirConditionListeners) {
                    listener.onTemperatureChanged(temperature);
                    listener.onWindSpeedChanged(fanSpeed);
                    listener.onHvacSwitchChanged(isOn);
                }
            });

        }
    };


    private final XcoreCarAudioManager.AudioVolumeListener mAudioVolumeListenerOuter = new XcoreCarAudioManager.AudioVolumeListener() {

        @Override
        public void onStreamVolumeChange(int streamType, int volume, int flags) {
            if (mAudioListeners.size() > 0) {
                mHandler.post(() -> {
                    for (AudioVolumeListenerInner l : mAudioListeners) {
                        l.onStreamVolumeChange(streamType, volume, flags);
                    }
                });
            }
        }

        @Override
        public void onMuteStateChange(boolean mute, int streamType) {
            if (mAudioListeners.size() > 0) {
                mHandler.post(() -> {
                    for (AudioVolumeListenerInner l : mAudioListeners) {
                        l.onMuteStateChange(mute, streamType);
                    }
                });
            }
        }
    };

    private final XcoreCarInputManager.CarKeyManagerCallback mCarKeyManagerCallbackOuter = new XcoreCarInputManager.CarKeyManagerCallback() {
        @Override
        public int onKeyEvent(XcoreKeyEvent xcoreKeyEvent) {
            if (mCarKeyListeners.size() > 0) {
                mHandler.post(() -> {
                    for (CarKeyListenerInner l : mCarKeyListeners) {
                        l.onKeyEvent(xcoreKeyEvent);
                    }
                });
            }
            return 0;
        }

        @Override
        public int onEvent(XcoreKeyEvent xcoreKeyEvent, String s, int i) {
            if (mCarKeyListeners.size() > 0) {
                mHandler.post(() -> {
                    for (CarKeyListenerInner l : mCarKeyListeners) {
                        l.onEvent(xcoreKeyEvent, s, i);
                    }
                });
            }
            return 0;
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: service = " + service);
            mName = name;
            mService = service;
            try {
                mXcoreCarAudioManager = (XcoreCarAudioManager) mCar.getCarManager(Car.XCORE_AUDIO_SERVICE);
                Log.d(TAG, "onServiceConnected: mXcoreCarAudioManager = " + mXcoreCarAudioManager);
                mXCoreVehicleManager = (XCoreVehicleManager) mCar.getCarManager(Car.XCORE_VEHICLE_SERVICE);

                Log.d(TAG, "onServiceConnected: mXCoreVehicleManager = " + mXCoreVehicleManager);
                mXcoreInputManager = (XcoreCarInputManager) mCar.getCarManager(Car.XCORE_INPUT_SERVICE);
                Log.d(TAG, "onServiceConnected: mXcoreInputManager = " + mXcoreInputManager);
                mXcoreCarAudioManager.registerAudioVolumeListener(mAudioVolumeListenerOuter);
                mXCoreVehicleManager.registerXCoreVehicleListener(mXcoreListenerOuter);
                for (Map.Entry<VehicleState, Set<XCoreVehicleListenerInner>> entry : mVehicleListeners.entrySet()) {
                    mXCoreVehicleManager.registerVehicleState(entry.getKey());
                }
                mXcoreInputManager.registerKeyManagerCallback(mCarKeyManagerCallbackOuter);
            } catch (Exception exception) {
                Log.e(TAG, "initCar: " + exception.getMessage());
                exception.printStackTrace();
            }
            mHandler.post(() -> {
                for (CarConnectionListener connectListener : mCarConnectionListenerList) {
                    connectListener.onServiceConnected(name, service);
                }
            });
            mIsConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: ");
            mHandler.post(() -> {
                for (CarConnectionListener connectListener : mCarConnectionListenerList) {
                    connectListener.onServiceDisconnected(name);
                }
            });
            mXcoreCarAudioManager = null;
            mXCoreVehicleManager = null;
            mXcoreInputManager = null;
            mIsConnected = false;
            mName = null;
            mService = null;
        }
    };

    // 添加回调
    public void addAirConditionListener(IHvacCallback listener) {
        if (listener != null && !mAirConditionListeners.contains(listener)) {
            mAirConditionListeners.add(listener);
        }
    }

    // 移除回调
    public void removeAirConditionListener(IHvacCallback listener) {
        mAirConditionListeners.remove(listener);
    }


    public void destroy() {
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }


    /**
     * 设置空调风速
     * value: 温度数值
     */
    public void setWindLevel(int value) {
        if (mXCoreVehicleManager != null) {
            mXCoreVehicleManager.setByXCoreProperID(VehicleState.XCORESET_FICM_AC_FRONT_BLOWER_LVL_REQ, 0, value);
            Log.d(TAG, " setWindLevel = " + value);
        } else {
            Log.d(TAG, "setWindLevel: error");
        }
    }


    /**
     * 获取空调风速
     */
    public int getWindSpeed() {
        if (mXCoreVehicleManager != null) {
            Log.d(TAG, " getWindSpeed = " + mXCoreVehicleManager.getAirCondition().getAirFBlowerSpeed());
            return mXCoreVehicleManager.getAirCondition().getAirFBlowerSpeed();
        } else {
            Log.d(TAG, "getWindSpeed: error");
            return -1;
        }
    }


    /**
     * 设置空调温度
     * value: 温度数值
     */
    public void setTemperature(int value) {
        if (mXCoreVehicleManager != null) {
            mXCoreVehicleManager.setByXCoreProperID(VehicleState.XCORESET_FICM_HVAC_LEFT_TEMPERATURE_REQUEST, 0, value);
            Log.d(TAG, " setTemperature = " + value);
        } else {
            Log.d(TAG, "setTemperature: error");
        }
    }


    /**
     * 获取空调温度值
     *
     * @return 温度值
     */
    public int getTemperature() {
        if (mXCoreVehicleManager != null && mXCoreVehicleManager.getAirCondition() != null) {
            return mXCoreVehicleManager.getAirCondition().getAirDriverTemp();
        }
        return -1;
    }


    /**
     * 获取空调电源是否打开
     * true表示打开，false表示关闭
     */
    public boolean isAirPowerSwitchOn() {
        if (mXCoreVehicleManager == null || mXCoreVehicleManager.getAirCondition() == null) {
            Log.d(TAG, " isAirPowerSwitchOn = " + false);
            return false;
        } else {
            Log.d(TAG, " isAirPowerSwitchOn = " + mXCoreVehicleManager.getAirCondition().getAirPowerSwitch());
            return mXCoreVehicleManager.getAirCondition().getAirPowerSwitch() == AirConditionState.SWITCH_ON;
        }
    }

    /**
     * 判断空调电源是否打开
     *
     * @return true表示打开，false表示关闭 ;
     */
    public void setAirPowerSwitchOn(int value) {
        if (mXCoreVehicleManager == null || mXCoreVehicleManager.getAirCondition() == null) {
            Log.d(TAG, " setAirPowerSwitchOn = " + false);
            return;
        }
        Log.d(TAG, " setAirPowerSwitchOn = " + value);
        mXCoreVehicleManager.getAirCondition().setAirPowerSwitch(value);
    }
}
