package com.rxw.panconnection.service.util;

import android.util.Log;

public class Logger {

    private final static String TAG = "PanConnectionService";

    public static void e(String tag, String msg) {
        Log.e(TAG, tag + ": " + msg);
    }

    public static void d(String tag, String msg) {
        Log.d(TAG, tag + ": " + msg);
    }

    public static void i(String tag, String msg) {
        Log.i(TAG, tag + ": " + msg);
    }
}
