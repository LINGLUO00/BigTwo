package controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import models.NetworkManager;
import models.Player;
import models.Game;
import java.util.List;
import java.util.UUID;

//NetworkController：是高层控制器，负责协调网络通信与游戏逻辑之间的交互，不直接实现网络通信功能。
//BluetoothNetworkManager：是具体的网络管理器实现，专门负责蓝牙连接的建立、维护和数据传输细节。

/**
 * 网络控制器，负责协调网络通信与游戏逻辑之间的交互
 */
public class NetworkController implements NetworkManager.ConnectionListener, NetworkManager.MessageListener {
    private static final String TAG = "NetworkController";
    
    private final NetworkManager networkManager;      // 网络管理器
    private final NetworkView defaultNetworkView;     // 首次创建的视图
    private volatile NetworkView currentView;         // 当前活跃视图
    private GameController gameController;      // 游戏控制器
    private final GameViewAdapter gameViewAdapter;    // GameView适配器
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 单例实例
    private static NetworkController instance;
    
    // 添加防止循环JOIN_GAME的变量
    private long lastJoinMessageTime = 0;
    private static final long JOIN_MESSAGE_THROTTLE = 3000; // 3秒内不重复发送JOIN消息
    private String lastJoinedPlayer = "";
    
    // 存储主机状态
    private boolean networkHostStatus = false;
    
    /**
     * 获取单例实例
     * @param networkManager 网络管理器实现
     * @param networkView 网络视图接口
     * @return NetworkController 单例实例
     */
    public static synchronized NetworkController getInstance(NetworkManager networkManager, NetworkView networkView) {
        if (instance == null) {
            instance = new NetworkController(networkManager, networkView);
        } else {
            // 更新视图以确保回调到最新的视图上
            instance.updateNetworkView(networkView);
            
            // 重新设置监听器，确保所有回调指向当前实例
            networkManager.setConnectionListener(instance);
            networkManager.setMessageListener(instance);
        }
        return instance;
    }
    
    /**
     * 更新网络视图，用于切换Activity时保持UI刷新
     * @param newNetworkView 新的网络视图接口
     */
    private void updateNetworkView(NetworkView newNetworkView) {
        if (newNetworkView != null && newNetworkView != this.currentView) {
            this.currentView = newNetworkView;
            // 这里我们不能直接替换networkView和gameViewAdapter，因为它们是final的
            // 只能在此更新通知方法，确保消息能传递到新视图
            mainHandler.post(() -> {
                // 通知新视图当前连接状态
                newNetworkView.updateConnectionStatus(networkManager.getConnectionStatus());
                // 更新发现的设备列表
                for (NetworkManager.DeviceInfo device : networkManager.getDiscoveredDevices()) {
                    newNetworkView.addDiscoveredDevice(device);
                }
            });
        }
    }
    
    /**
     * 创建网络控制器
     * @param networkManager 网络管理器实现，用来管理网络连接和消息传递
     * @param networkView 网络视图接口，用来显示网络状态和消息
     * @param gameViewAdapter GameView适配器，目的是将NetworkView的接口转换为GameView的接口，以便在GameActivity中使用
     */
    private NetworkController(NetworkManager networkManager, NetworkView networkView) {
        this.networkManager = networkManager;
        this.defaultNetworkView = networkView;
        this.currentView = networkView;
        this.gameViewAdapter = new GameViewAdapter(networkView);
        
        // 设置网络管理器的监听器
        networkManager.setConnectionListener(this);
        networkManager.setMessageListener(this);
    }
    
    /**
     * 设置游戏控制器
     * @param gameController 游戏控制器
     */
    public void setGameController(GameController gameController) {
        this.gameController = gameController;
    }
    
    /**
     * 获取NetworkView接口
     * @return NetworkView接口
     */
    public NetworkView getNetworkView() {
        return currentView;
    }
    
    /**
     * 获取GameView适配器
     * @return GameView适配器
     */
    public GameView getGameViewAdapter() {
        return gameViewAdapter;
    }
    
    /**
     * 初始化网络连接，委托给网络管理器实现具体操作
     * @param type 连接类型
     */
    public void initialize(NetworkManager.ConnectionType type) {
        try {
            // 获取当前视图的类名，用于判断不同的初始化逻辑
            boolean isInSetupActivity = false;
            if (currentView != null) {
                String viewClassName = currentView.getClass().getSimpleName();
                isInSetupActivity = viewClassName.contains("SetupActivity");
            }
            
            // 设备扫描/配置界面的初始化逻辑，不需要完整的游戏对象
            if (isInSetupActivity) {
                networkManager.initialize(type);
                currentView.showMessage("2网络连接已初始化");
                return;
            }
            
            // 游戏活动中需要检查更多前置条件
            
            // 检查gameController是否已设置
            if (gameController == null) {
                currentView.showMessage("3错误: 游戏控制器未设置");
                return;
            }
            
            // 检查Game对象是否已创建
            if (gameController.getGame() == null) {
                currentView.showMessage("4错误: 游戏对象未创建");
                return;
            }
            
            // 一切就绪，初始化网络连接
            networkManager.initialize(type);
            currentView.showMessage("5网络连接已初始化");
            
        } catch (Exception e) {
            currentView.showMessage("初始化网络失败: " + e.getMessage());
            Log.e(TAG, "初始化网络失败", e);
            e.printStackTrace();
        }
    }
    
    /**
     * 开始搜索设备
     * @return 搜索是否成功启动
     */
    public boolean startDiscovery() {
        try {
            Log.d(TAG, "startDiscovery: 当前视图类型: " + currentView.getClass().getSimpleName());
            boolean result = networkManager.startDiscovery();
            currentView.showMessage("开始扫描设备...");
            return result;
        } catch (Exception e) {
            currentView.showMessage("搜索设备失败: " + e.getMessage());
            Log.e(TAG, "搜索设备失败", e);
            return false;
        }
    }
    
    /**
     * 停止搜索设备
     */
    public void stopDiscovery() {
        try {
            networkManager.stopDiscovery();
            currentView.showMessage("停止搜索设备");
        } catch (Exception e) {
            Log.e(TAG, "停止搜索时出错", e);
        }
    }
    
    /**
     * 创建游戏房间(作为主机)
     * @param roomName 房间名称
     * @return 是否成功创建
     */
    public boolean createRoom(String roomName) {
        try {
            if (networkManager.createRoom(roomName)) {
                currentView.showMessage("创建房间成功: " + roomName);
                return true;
            } else {
                currentView.showMessage("创建房间失败");
                return false;
            }
        } catch (Exception e) {
            currentView.showMessage("创建房间出错: " + e.getMessage());
            Log.e(TAG, "创建房间出错", e);
            return false;
        }
    }
    
    /**
     * 加入游戏房间(作为客户端)
     * @param deviceAddress 设备地址
     * @return 是否成功加入
     */
    public boolean joinRoom(String deviceAddress) {
        try {
            if (networkManager.joinRoom(deviceAddress)) {
                currentView.showMessage("正在连接到房间...");
                return true;
            } else {
                currentView.showMessage("加入房间失败");
                return false;
            }
        } catch (Exception e) {
            currentView.showMessage("加入房间出错: " + e.getMessage());
            Log.e(TAG, "加入房间出错", e);
            return false;
        }
    }
    
    /**
     * 断开网络连接
     */
    public void disconnect() {
        try {
            networkManager.disconnect(); 
            currentView.showMessage("已断开连接");
        } catch (Exception e) {
            Log.e(TAG, "断开连接时出错", e);
        }
    }
    
    /**
     * 获取已发现的设备列表
     * @return 设备列表
     */
    public List<NetworkManager.DeviceInfo> getDiscoveredDevices() {
        return networkManager.getDiscoveredDevices();
    }
    
    /**
     * 获取当前连接状态
     * @return 连接状态
     */
    public NetworkManager.ConnectionStatus getConnectionStatus() {
        return networkManager.getConnectionStatus();
    }
    
    /**
     * 发送消息
     * @param type 消息类型
     * @param data 消息数据
     * @return 是否成功发送
     */
    public boolean sendMessage(NetworkManager.MessageType type, String data) {
        currentView.showMessage("开始执行: " + type);
        // 添加调试信息
        if (type == NetworkManager.MessageType.GAME_START) {
            currentView.showMessage("发送: 游戏开始");
            
            // 检查GameController和Game是否已正确初始化
            if (gameController == null) {
                currentView.showMessage("错误: GAME_START失败-控制器为空");
                return false;
            } else if (gameController.getGame() == null) {
                currentView.showMessage("警告: GAME_START失败-游戏为空");
                return false;
            }
            
        } else if (type == NetworkManager.MessageType.JOIN_GAME) {
            currentView.showMessage("加入: " + data);
            
            // 检查连接状态
            NetworkManager.ConnectionStatus status = networkManager.getConnectionStatus();
            if (status != NetworkManager.ConnectionStatus.CONNECTED) {
                currentView.showMessage("未连接: JOIN_GAME失败-状态" + status);
                return false;
            }
        }
        
        // 尝试发送消息
        boolean success = networkManager.sendMessage(type, data);
        
        // 显示发送结果
        if (success) {
            Log.d(TAG, "消息发送成功: 类型=" + type + ", 数据=" + data);
            
            if (type == NetworkManager.MessageType.GAME_START) {
                currentView.showMessage("成功: 游戏开始消息");
            } else if (type == NetworkManager.MessageType.JOIN_GAME) {
                currentView.showMessage("完成: 加入消息-" + data);
            }
        } else {
            Log.e(TAG, "发送消息失败: 类型=" + type + ", 数据=" + data);
            
            if (type == NetworkManager.MessageType.GAME_START) {
                currentView.showMessage("失败: 游戏开始-检查蓝牙");
                
                // 重试发送GAME_START消息
                try {
                    Thread.sleep(500); // 短暂延迟
                    currentView.showMessage("重试: 游戏开始消息");
                    boolean retrySuccess = networkManager.sendMessage(type, data);
                    if (retrySuccess) {
                        currentView.showMessage("已重发: 游戏开始成功");
                    } else {
                        currentView.showMessage("依然失败: 游戏开始-状态" + networkManager.getConnectionStatus());
                    }
                    return retrySuccess;
                } catch (Exception e) {
                    currentView.showMessage("异常: 重试时-" + e.getMessage());
                }
            } else if (type == NetworkManager.MessageType.JOIN_GAME) {
                currentView.showMessage("拒绝: 加入消息-检查蓝牙");
                
                // 重试发送JOIN_GAME消息
                try {
                    Thread.sleep(500); // 短暂延迟
                    currentView.showMessage("尝试: 重发加入-" + data);
                    boolean retrySuccess = networkManager.sendMessage(type, data);
                    if (retrySuccess) {
                        currentView.showMessage("好了: 加入重发成功");
                    } else {
                        currentView.showMessage("仍失败: 加入-状态" + networkManager.getConnectionStatus());
                    }
                    return retrySuccess;
                } catch (Exception e) {
                    currentView.showMessage("问题: 重试加入-" + e.getMessage());
                }
            } else {
                currentView.showMessage("消息失败: " + type);
            }
        }
        
        return success;
    }
    
    /**
     * 发送游戏开始消息
     * @return 是否成功发送
     */
    public boolean sendGameStartMessage() {
        currentView.showMessage("sendGameStartMessage()准备开始游戏...");
        return sendMessage(NetworkManager.MessageType.GAME_START, "");
    }
    
    /**
     * 发送加入游戏消息
     * @param playerName 玩家名称
     * @return 是否成功发送
     */
    public boolean sendJoinGameMessage(String playerName) {
        currentView.showMessage("玩家 " + playerName + " 加入游戏");
        return sendMessage(NetworkManager.MessageType.JOIN_GAME, playerName);
    }
    
    /**
     * 发送出牌消息
     * @param cardsData 卡牌数据
     * @return 是否成功发送
     */
    public boolean sendPlayCardsMessage(String cardsData) {
        return sendMessage(NetworkManager.MessageType.PLAY_CARDS, cardsData);
    }
    
    /**
     * 发送不出消息
     * @return 是否成功发送
     */
    public boolean sendPassMessage() {
        return sendMessage(NetworkManager.MessageType.PASS, "");
    }
    
    /**
     * 发送聊天消息
     * @param message 聊天内容
     * @return 是否成功发送
     */
    public boolean sendChatMessage(String message) {
        return sendMessage(NetworkManager.MessageType.CHAT_MESSAGE, message);
    }
    
    /**
     * 判断当前设备是否为主机
     * @return 是否为主机
     */
    public boolean isHost() {
        // 假设NetworkManager提供了获取主机状态的方法，实际需要根据具体实现来获取
        // 或者存储在NetworkController中
        return networkHostStatus;
    }
    
    /**
     * 标记当前设备为主机
     */
    public void setAsHost() {
        this.networkHostStatus = true;
    }
    
    /**
     * 发送发牌结果消息
     * @param dealCardsData 发牌数据
     * @return 是否发送成功
     */
    public boolean sendDealCardsMessage(String dealCardsData) {
        return sendMessage(NetworkManager.MessageType.DEAL_CARDS, dealCardsData);
    }
    
    // ConnectionListener接口实现
    
    private NetworkManager.ConnectionStatus lastShownStatus = null;
    private static final int MAX_JOIN_RETRY = 3;
    private static final int JOIN_RETRY_DELAY = 500; // 毫秒
    
    @Override
    public void onConnectionStatusChanged(NetworkManager.ConnectionStatus status) {
        // 在主线程中处理状态变化
        mainHandler.post(() -> {
            // 只有当状态变化时才更新UI
            if (lastShownStatus != status) {
                lastShownStatus = status;
                
                // 先通知视图状态更新
                currentView.updateConnectionStatus(status);
                
                // 继续按原有逻辑处理
                switch (status) {
                    case CONNECTED:
                        currentView.showMessage("已连接");
                        handleConnected();
                        break;
                        
                    case CONNECTING:
                        currentView.showMessage("正在连接...");
                        break;
                        
                    case DISCONNECTED:
                        currentView.showMessage("已断开连接");
                        break;
                }
            }
        });
    }
    
    /**
     * 处理连接成功后的逻辑
     */
    private void handleConnected() {
        // 在连接成功后，查找本地玩家
        String localPlayerName = null;
        
        // 检查游戏控制器和游戏是否已初始化
        if (gameController != null && gameController.getGame() != null) {
            // 查找本地玩家
            for (Player player : gameController.getGame().getPlayers()) {
                if (player.isHuman()) {
                    localPlayerName = player.getName();
                    break;
                }
            }
            
            // 如果找到本地玩家，发送JOIN_GAME消息
            if (localPlayerName != null && !localPlayerName.isEmpty()) {
                currentView.showMessage("准备: 发送玩家-" + localPlayerName);
                // 使用重试机制发送
                sendJoinGameWithRetry(localPlayerName, 0);
                
                // 延迟一段时间后确认再发一次
                final String playerNameCopy = localPlayerName;
                mainHandler.postDelayed(() -> {
                    currentView.showMessage("重新发送JOIN_GAME消息以确保同步");
                    sendJoinGameWithRetry(playerNameCopy, 0);
                }, 2000);
            } else {
                // 游戏已初始化但没有本地玩家，这是一种异常情况
                // 这条消息通常意味着Game对象已创建，但未加入玩家或出现了其他问题
                currentView.showMessage("无玩家: 检查控制器");
                
                // 详细诊断信息
                String detailedInfo = "游戏: 有效，玩家数" + 
                                     gameController.getGame().getPlayers().size() +
                                     "，状态" + gameController.getGame().getGameState();
                currentView.showMessage(detailedInfo);
                
                // 不立即断开连接，尝试恢复
                mainHandler.postDelayed(() -> {
                    // 检查是否现在有了有效的玩家
                    Player humanPlayer = findHumanPlayer();
                    if (humanPlayer != null) {
                        currentView.showMessage("已恢复玩家: " + humanPlayer.getName());
                        sendJoinGameWithRetry(humanPlayer.getName(), 0);
                    } else {
                        currentView.showMessage("无法恢复玩家，将断开连接");
                        disconnect();
                    }
                }, 2000); // 给其他初始化过程2秒时间完成
            }
        } else {
            // 游戏尚未初始化，应该先断开连接
            String errorDetail = "";
            
            if (gameController == null) {
                errorDetail = "控制器: 为空-先初始化再连接";
                
                // 尝试在2秒后重新检查，给其他初始化过程时间完成
                mainHandler.postDelayed(() -> {
                    if (gameController != null && gameController.getGame() != null) {
                        // 重新执行连接逻辑
                        currentView.showMessage("控制器已恢复，重新尝试连接");
                        handleConnected();
                    } else {
                        currentView.showMessage("控制器仍为空，无法恢复连接");
                    }
                }, 2000);
            } else if (gameController.getGame() == null) {
                errorDetail = "游戏: 为空-先创建再连接";
                
                // 尝试在2秒后重新检查
                mainHandler.postDelayed(() -> {
                    if (gameController.getGame() != null) {
                        // 重新执行连接逻辑
                        currentView.showMessage("游戏对象已恢复，重新尝试连接");
                        handleConnected();
                    } else {
                        currentView.showMessage("游戏对象仍为空，无法恢复连接");
                    }
                }, 2000);
            }
            
            currentView.showMessage(errorDetail);
            
            // 记录调用栈以帮助定位问题
            Exception e = new Exception("调试堆栈跟踪");
            Log.e(TAG, "调用堆栈:", e);
            
            // 不立即断开连接，在延迟检查后决定是否断开
        }
    }
    
    /**
     * 查找人类玩家
     */
    private Player findHumanPlayer() {
        if (gameController != null && gameController.getGame() != null) {
            for (Player player : gameController.getGame().getPlayers()) {
                if (player.isHuman()) {
                    return player;
                }
            }
        }
        return null;
    }
    
    /**
     * 带重试机制的JOIN_GAME消息发送
     * @param playerName 玩家名称
     * @param retryCount 当前重试次数
     */
    private void sendJoinGameWithRetry(String playerName, int retryCount) {
        if (retryCount >= MAX_JOIN_RETRY) {
            currentView.showMessage("加入失败，已达最大重试次数");
            return;
        }
        
        // 检查是否可以发送JOIN_GAME消息(防止频繁发送)
        if (System.currentTimeMillis() - lastJoinMessageTime < JOIN_MESSAGE_THROTTLE && 
            playerName.equals(lastJoinedPlayer) && retryCount > 0) {
            Log.d(TAG, "跳过重复发送JOIN_GAME: " + playerName);
            return;
        }
        
        // 添加调试信息
        currentView.showMessage("发送JOIN_GAME: " + playerName + " (重试: " + retryCount + ")");
        
        // 更新时间戳和玩家
        lastJoinMessageTime = System.currentTimeMillis();
        lastJoinedPlayer = playerName;
        
        // 异步发送，避免阻塞UI线程
        new Thread(() -> {
            // 发送JOIN_GAME消息
            boolean success = networkManager.sendMessage(NetworkManager.MessageType.JOIN_GAME, playerName);
            
            // 如果失败，尝试延迟后重试
            if (!success) {
                try {
                    Thread.sleep(JOIN_RETRY_DELAY);
                    mainHandler.post(() -> sendJoinGameWithRetry(playerName, retryCount + 1));
                } catch (Exception e) {
                    // 忽略线程中断异常
                }
            } else {
                // 即使成功，也不要再发送一次确认，以避免循环
                // 成功后不再发送
            }
        }).start();
    }
    
    @Override
    public void onDeviceDiscovered(NetworkManager.DeviceInfo device) {
        // 在UI线程中更新设备列表
        mainHandler.post(() -> {
            currentView.addDiscoveredDevice(device);
            currentView.showMessage("发现设备: " + device.getName());
        });
    }
    
    // MessageListener接口实现
    
    @Override
    public void onMessageReceived(NetworkManager.MessageType type, String data, String senderId) {
        Log.d(TAG, "收到消息: " + type + ", 数据: " + data + ", 发送者: " + senderId);
        
        try {
            // 1. 首先确认GameController是否可用
            if (gameController == null) {
                currentView.showMessage("错误: 收到消息但GameController未初始化");
                return;
            }
            
            // 2. 处理消息
            mainHandler.post(() -> {
                try {
                    switch (type) {
                        case JOIN_GAME:
                            currentView.showMessage("玩家 " + data + " 已加入");
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        case GAME_START:
                            currentView.showMessage("主机已开始游戏");
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        case DEAL_CARDS:
                            currentView.showMessage("已收到发牌数据");
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        case PLAY_CARDS:
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        case PASS:
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        case CHAT_MESSAGE:
                            currentView.showMessage("聊天: " + data);
                            break;
                            
                        case PLAYER_LEFT:
                            currentView.showMessage("玩家 " + data + " 已离开");
                            gameController.handleNetworkMessage(type, data, senderId);
                            break;
                            
                        default:
                            Log.d(TAG, "未知消息类型: " + type);
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理消息时出错: " + type, e);
                    currentView.showMessage("处理 " + type + " 消息时出错: " + e.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "onMessageReceived异常", e);
            currentView.showMessage("处理消息时出错: " + e.getMessage());
        }
    }
    
    /**
     * GameView的适配器类，将NetworkView转换为GameView
     */
    private class GameViewAdapter implements GameView {
        private final NetworkView networkView;
        
        public GameViewAdapter(NetworkView networkView) {
            this.networkView = networkView;
        }
        
        @Override
        public void updateGameState(Game game) {
            if (networkView instanceof GameView) {
                ((GameView) networkView).updateGameState(game);
            }
        }
        
        @Override
        public void updatePlayerHand(Player player) {
            if (networkView instanceof GameView) {
                ((GameView) networkView).updatePlayerHand(player);
            }
        }
        
        @Override
        public void showMessage(String message) {
            networkView.showMessage(message);
        }
        
        @Override
        public void showGameResult(Player winner) {
            if (networkView instanceof GameView) {
                ((GameView) networkView).showGameResult(winner);
            } else {
                networkView.showMessage("游戏结束，胜利者: " + winner.getName());
            }
        }
    }
} 