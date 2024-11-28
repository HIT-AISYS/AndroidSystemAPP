package com.rxw.panconnection.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SharedPreferencesUtils {

    private static final String TAG = SharedPreferencesUtils.class.getSimpleName();

    private static final boolean DEBUG_ALL = false;

    private static final Boolean        DEFAULT_BOOLEAN     = false;
    private static final Float          DEFAULT_FLOAT       = 0.0f;
    private static final Integer        DEFAULT_INTEGER     = 0;
    private static final Long           DEFAULT_LONG        = 0L;
    private static final String         DEFAULT_STRING      = "";
    private static final Set<String>    DEFAULT_STRING_SET  = new HashSet<>(0);

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    public SharedPreferencesUtils(Context context, String name, int mode) {
        if (DEBUG_ALL) {
            Log.d(TAG, "Construct SharedPreferencesUtils: {name=" + name + ", mode=" + mode + "}");
        }

        this.mSharedPreferences = context.getSharedPreferences(name, mode);
        this.mEditor = this.mSharedPreferences.edit();
    }



    /*==============*
     * Singleton    *
     *==============*/

    private static SharedPreferencesUtils sInstance;
    private static String sName = null;
    private static int sMode = 0;   // TODO: Invalid value.

    public static synchronized SharedPreferencesUtils getInstance(Context context, String name, int mode) {
        if (SharedPreferencesUtils.sInstance == null) {
            SharedPreferencesUtils.sInstance = new SharedPreferencesUtils(context, name, mode);
        } else if (!TextUtils.equals(SharedPreferencesUtils.sName, name) || SharedPreferencesUtils.sMode != mode) {
            SharedPreferencesUtils.sInstance = new SharedPreferencesUtils(context, name, mode);
        }

        return SharedPreferencesUtils.sInstance;
    }



    /*==============*
     * Contain      *
     *==============*/

    public boolean containsKey(String key) {
        return this.mSharedPreferences.contains(key);
    }



    /*==============*
     * Read         *
     *==============*/

    // Boolean
    public boolean readBooleanValue(String key, boolean defValue) {
        return this.mSharedPreferences.getBoolean(key, defValue);
    }

    public boolean readBooleanValue(String key) {
        return this.readBooleanValue(key, DEFAULT_BOOLEAN);
    }

    // Float
    public float readFloatValue(String key, float defValue) {
        return this.mSharedPreferences.getFloat(key, defValue);
    }

    public float readFloatValue(String key) {
        return this.readFloatValue(key, DEFAULT_FLOAT);
    }

    // Integer
    public int readIntegerValue(String key, int defValue) {
        return this.mSharedPreferences.getInt(key, defValue);
    }

    public int readIntegerValue(String key) {
        return this.readIntegerValue(key, DEFAULT_INTEGER);
    }

    // Long
    public long readLongValue(String key, long defValue) {
        return this.mSharedPreferences.getLong(key, defValue);
    }

    public long readLongValue(String key) {
        return this.readLongValue(key, DEFAULT_LONG);
    }

    // String
    public String readStringValue(String key, String defValue) {
        return this.mSharedPreferences.getString(key, defValue);
    }

    public String readStringValue(String key) {
        return this.readStringValue(key, DEFAULT_STRING);
    }

    // String Set
    public Set<String> readStringSetValue(String key, Set<String> defValue) {
        return this.mSharedPreferences.getStringSet(key, defValue);
    }

    public Set<String> readStringSetValue(String key) {
        return this.readStringSetValue(key, DEFAULT_STRING_SET);
    }

    // All
    public Map<String, ?> readAllValues() {
        return this.mSharedPreferences.getAll();
    }



    /*==============*
     * Write        *
     *==============*/

    // Boolean
    public SharedPreferencesUtils writeValue(String key, boolean value) {
        this.mEditor.putBoolean(key, value);
        this.mEditor.apply();
        return this;
    }

    // Float
    public SharedPreferencesUtils writeValue(String key, float value) {
        this.mEditor.putFloat(key, value);
        this.mEditor.apply();
        return this;
    }

    // Integer
    public SharedPreferencesUtils writeValue(String key, int value) {
        this.mEditor.putInt(key, value);
        this.mEditor.apply();
        return this;
    }

    // Long
    public SharedPreferencesUtils writeValue(String key, long value) {
        this.mEditor.putLong(key, value);
        this.mEditor.apply();
        return this;
    }

    // String
    public SharedPreferencesUtils writeValue(String key, String value) {
        this.mEditor.putString(key, value);
        this.mEditor.apply();
        return this;
    }

    // String Set
    public SharedPreferencesUtils writeValue(String key, Set<String> value) {
        this.mEditor.putStringSet(key, value);
        this.mEditor.apply();
        return this;
    }



    /*==============*
     * Remove       *
     *==============*/

    public SharedPreferencesUtils removeValue(String key) {
        this.mEditor.remove(key);
        this.mEditor.apply();
        return this;
    }



    /*==============*
     * Clear        *
     *==============*/

    public SharedPreferencesUtils removeAll() {
        this.mEditor.clear();
        this.mEditor.apply();
        return this;
    }


}
