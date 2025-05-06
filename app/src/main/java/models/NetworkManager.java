package models;

import java.util.List;

/**
 * 网络管理接口，定义网络通信的基本功能
 */
public interface NetworkManager {

    // 连接类型
    enum ConnectionType {
        BLUETOOTH,
        WIFI
    }

    // 连接状态
    enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    // 消息类型
    enum MessageType {
        CONNECTION_ESTABLISHED, // 连接建立
        JOIN_GAME,              // 玩家加入游戏
        GAME_START,             // 游戏开始
        DEAL_CARDS,             // 发牌结果
        PLAY_REQUEST,           // 客户端向主机请求出牌
        PLAY_BROADCAST,         // 主机确认后广播的正式出牌
        PLAY_CARDS,             // 出牌
        PASS,                   // 不出
        PLAYER_LEFT,            // 玩家离开
        GAME_OVER,              // 游戏结束
        CHAT_MESSAGE            // 聊天消息
    }

    // 初始化网络连接
    void initialize(ConnectionType type);

    // 开始发现设备
    boolean startDiscovery();

    // 停止发现设备
    boolean stopDiscovery();

    // 创建房间
    boolean createRoom(String roomName);

    // 加入房间 
    boolean joinRoom(String deviceAddress);

    // 发送消息
    boolean sendMessage(MessageType type, String data);

    // 断开连接
    void disconnect();

    // 获取连接状态
    ConnectionStatus getConnectionStatus();

    // 获取发现的设备列表
    List<DeviceInfo> getDiscoveredDevices();

    // 设置连接监听器
    void setConnectionListener(ConnectionListener listener);

    // 设置消息监听器
    void setMessageListener(MessageListener listener);

    // 连接监听器接口
    interface ConnectionListener {
        void onConnectionStatusChanged(ConnectionStatus status);

        void onDeviceDiscovered(DeviceInfo device);
    }

    // 消息监听器接口
    interface MessageListener {
        void onMessageReceived(MessageType type, String data, String senderId);
    }

    // 设备信息类
    class DeviceInfo {
        private final String name;
        private final String address;

        public DeviceInfo(String name, String address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
