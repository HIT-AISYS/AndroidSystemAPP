package com.rxw.panconnection.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class DataStoreUtils {

    private static final String TAG = DataStoreUtils.class.getSimpleName();

    private RxDataStore<Preferences> mDataStore;

    /**
     *
     * @param context the context from which we retrieve files directory.
     * @param name the filename relative to Context.filesDir that DataStore acts on.
     *             The File is obtained by calling File(this.filesDir, "datastore/$name.preferences_pb").
     *             No two instances of DataStore should act on the same file at the same time.
     */
    public void build(Context context, String name) {
        this.mDataStore = new RxPreferenceDataStoreBuilder(context, name).build();
    }

    public void writeValue(String key, Boolean value) {
        Preferences.Key<Boolean> pk = PreferencesKeys.booleanKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, (Boolean) value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, Integer value) {
        Preferences.Key<Integer> pk = PreferencesKeys.intKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, Long value) {
        Preferences.Key<Long> pk = PreferencesKeys.longKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, Float value) {
        Preferences.Key<Float> pk = PreferencesKeys.floatKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, Double value) {
        Preferences.Key<Double> pk = PreferencesKeys.doubleKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, String value) {
        Preferences.Key<String> pk = PreferencesKeys.stringKey(key);
        this.mDataStore.updateDataAsync(
                preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, (String) value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public void writeValue(String key, Set<String> value) {
        Preferences.Key<Set<String>> pk = PreferencesKeys.stringSetKey(key);
        this.mDataStore.updateDataAsync(preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.set(pk, value);
                    return Single.just(mutablePreferences);
                }
        );
    }

    public Boolean readBooleanValue(String key) {
        Preferences.Key<Boolean> pk = PreferencesKeys.booleanKey(key);
        Flowable<Boolean> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Boolean readBooleanValue(String key, @NonNull Boolean defaultValue) {
        Preferences.Key<Boolean> pk = PreferencesKeys.booleanKey(key);
        Flowable<Boolean> flowable = this.mDataStore.data().map(
                preferences -> {
                    Boolean result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public Integer readIntegerValue(String key) {
        Preferences.Key<Integer> pk = PreferencesKeys.intKey(key);
        Flowable<Integer> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Integer readIntegerValue(String key, @NonNull Integer defaultValue) {
        Preferences.Key<Integer> pk = PreferencesKeys.intKey(key);
        Flowable<Integer> flowable = this.mDataStore.data().map(
                preferences -> {
                    Integer result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public Long readLongValue(String key) {
        Preferences.Key<Long> pk = PreferencesKeys.longKey(key);
        Flowable<Long> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Long readLongValue(String key, @NonNull Long defaultValue) {
        Preferences.Key<Long> pk = PreferencesKeys.longKey(key);
        Flowable<Long> flowable = this.mDataStore.data().map(
                preferences -> {
                    Long result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public Float readFloatValue(String key) {
        Preferences.Key<Float> pk = PreferencesKeys.floatKey(key);
        Flowable<Float> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Float readFloatValue(String key, @NonNull Float defaultValue) {
        Preferences.Key<Float> pk = PreferencesKeys.floatKey(key);
        Flowable<Float> flowable = this.mDataStore.data().map(
                preferences -> {
                    Float result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public Double readDoubleValue(String key) {
        Preferences.Key<Double> pk = PreferencesKeys.doubleKey(key);
        Flowable<Double> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Double readDoubleValue(String key, @NonNull Double defaultValue) {
        Preferences.Key<Double> pk = PreferencesKeys.doubleKey(key);
        Flowable<Double> flowable = this.mDataStore.data().map(
                preferences -> {
                    Double result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public String readStringValue(String key) {
        Preferences.Key<String> pk = PreferencesKeys.stringKey(key);
        Flowable<String> flowable = this.mDataStore.data().map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public String readStringValue(String key, @NonNull String defaultValue) {
        Preferences.Key<String> pk = PreferencesKeys.stringKey(key);
        Flowable<String> flowable = this.mDataStore.data().map(
                preferences -> {
                    String result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }

    public Set<String> readStringSetValue(String key) {
        Preferences.Key<Set<String>> pk = PreferencesKeys.stringSetKey(key);
        Flowable<Preferences> data = this.mDataStore.data();
        Flowable<Set<String>> flowable = data.map(
                preferences -> preferences.get(pk)
        );
        return flowable.blockingFirst();
    }
    public Set<String> readStringSetValue(String key, @NonNull Set<String> defaultValue) {
        Preferences.Key<Set<String>> pk = PreferencesKeys.stringSetKey(key);
        Flowable<Preferences> data = this.mDataStore.data();
        Flowable<Set<String>> flowable = data.map(
                preferences -> {
                    Set<String> result = preferences.get(pk);
                    return result == null ? defaultValue : result;
                }
        );
        return flowable.blockingFirst();
    }
}
