package com.rxw.panconnection.service.aidl;


interface IUniDeviceConnectionCallback {

    /**
     * 泛设备的发现状态回调
     *
     * @param deviceList 发现的泛设备列表, JSON格式，跟发广播通知泛连接App的内容一样即可
     * @param discoveryState 发现状态：DISCOVERY_SEARCHING：值为1, DISCOVERY_END：值为2
     */
    void onDiscoveryStateChanged(String deviceList, int discoveryState);

    /**
     * 泛设备的连接状态回调
     *
     * @param device 泛设备，JOSN格式
     * @param connectionState 连接状态：CONNECTING：值为3， CONNECTED：值为4
     *                        DISCONNECTING：值为5， DISCONNECTED：值为6
     */
    void onConnectionStateChanged(String device, int connectionState);

    /**
     * 泛设备连接失败回调
     *
     * @param device 泛设备，JOSN格式
     * @param errorCode 连接失败的错误码
     */
    void onConnectionFailed(String device, int errorCode);

    /**
     * 泛设备解绑状态回调
     *
     * @param device 泛设备，JOSN格式
     * @param bondState 解绑状态：UNBONDING: 值为7, UNBONDED：值为8
     */
    void onUnbondStateChanged(String device,  int bondState);

    /**
     * 泛设备解绑失败回调
     *
     * @param device 泛设备，JOSN格式
     * @param errorCode 解绑失败的错误码
     */
    void onUnbondFailed(String device, int errorCode);
}
