package models;

import java.util.List;
//这里只定义了网络管理器的基本功能，具体的实现由BluetoothNetworkManager来实现

/**
 * 网络管理接口，定义网络通信的基本功能
 */
public interface NetworkManager {
    /**
     * 连接类型
     */
    enum ConnectionType {
        BLUETOOTH,
        WIFI
    }
    
    /**
     * 连接状态
     */
    enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
    
    /**
     * 游戏消息类型
     */
    enum MessageType {
        JOIN_GAME,         // 加入游戏
        GAME_START,        // 游戏开始
        DEAL_CARDS,        // 发牌结果
        PLAY_CARDS,        // 出牌
        PASS,              // 不出
        GAME_OVER,         // 游戏结束
        PLAYER_LEFT,       // 玩家离开
        CHAT_MESSAGE,      // 聊天消息
        CONNECTION_ESTABLISHED  // 连接已建立
    }
    
    /**
     * 初始化网络管理器
     * @param type 连接类型
     */
    void initialize(ConnectionType type);
    
    /**
     * 开始搜索可用设备
     * @return 是否成功开始搜索
     */
    boolean startDiscovery();
    
    /**
     * 停止搜索
     * @return 是否成功停止搜索
     */
    boolean stopDiscovery();
    
    /**
     * 创建游戏房间（作为主机）
     * @param roomName 房间名
     * @return 是否成功创建
     */
    boolean createRoom(String roomName);
    
    /**
     * 加入游戏房间（作为客户端）
     * @param deviceAddress 设备地址
     * @return 是否成功加入
     */
    boolean joinRoom(String deviceAddress);
    
    /**
     * 发送游戏消息
     * @param type 消息类型
     * @param data 消息数据
     * @return 是否成功发送
     */
    boolean sendMessage(MessageType type, String data);
    
    /**
     * 关闭连接
     */
    void disconnect();
    
    /**
     * 获取当前连接状态
     * @return 当前连接状态
     */
    ConnectionStatus getConnectionStatus();
    
    /**
     * 获取已发现的设备列表
     * @return 设备列表
     */
    List<DeviceInfo> getDiscoveredDevices();
    
    /**
     * 设置连接监听器
     * @param listener 连接监听器
     */
    void setConnectionListener(ConnectionListener listener);
    
    /**
     * 设置消息监听器
     * @param listener 消息监听器
     */
    void setMessageListener(MessageListener listener);
    
    /**
     * 连接监听器接口
     */
    interface ConnectionListener {
        /**
         * 连接状态变化
         * @param status 新的连接状态
         */
        void onConnectionStatusChanged(ConnectionStatus status);
        
        /**
         * 发现新设备
         * @param device 设备信息
         */
        void onDeviceDiscovered(DeviceInfo device);
    }
    
    /**
     * 消息监听器接口
     */
    interface MessageListener {
        /**
         * 接收到消息
         * @param type 消息类型
         * @param data 消息数据
         * @param senderId 发送者ID
         */
        void onMessageReceived(MessageType type, String data, String senderId);
    }
    
    /**
     * 设备信息类
     */
    class DeviceInfo {
        private final String name;       // 设备名称
        private final String address;    // 设备地址
        
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