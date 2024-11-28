package com.rxw.panconnection.service.xcore;

import android.content.ComponentName;
import android.os.IBinder;

public interface CarConnectionListener {
    void onServiceConnected(ComponentName name, IBinder service);
    void onServiceDisconnected(ComponentName name);
}
