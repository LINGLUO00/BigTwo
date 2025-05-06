package controller;

import models.NetworkManager;

/**
 * 网络视图接口，定义网络控制器与视图之间的交互
 * 具体的实现在GameActivity中
 */
public interface NetworkView {

    /**
     * 更新连接状态
     * @param status 连接状态
     */
    void updateConnectionStatus(NetworkManager.ConnectionStatus status);

    /**
     * 添加发现的设备到列表
     * @param device 设备信息
     */
    void addDiscoveredDevice(NetworkManager.DeviceInfo device);

    /**
     * 显示网络消息
     * @param message 消息内容
     */
    void showMessage(String message);
}
