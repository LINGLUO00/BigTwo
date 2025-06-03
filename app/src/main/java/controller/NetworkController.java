package controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import models.NetworkManager;
import models.Player;
import models.Game;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import util.AppExecutors;

/**
 * 网络控制器，负责协调网络通信与游戏逻辑之间的交互
 */
public class NetworkController implements NetworkManager.ConnectionListener, NetworkManager.MessageListener {
    private static final String TAG = "NetworkController";

    private final NetworkManager networkManager; // 网络管理器
    private NetworkView currentView; // 当前活跃视图
    private GameController gameController; // 游戏控制器
    private final Handler mainHandler = new Handler(Looper.getMainLooper());// 主线程处理器

    private final AtomicLong lastJoinMessageTime = new AtomicLong(0);// 最后处理加入消息的时间
    private final AtomicReference<String> lastJoinedPlayer = new AtomicReference<>("");// 最后加入的玩家
    private final AppExecutors executors;// 执行器

    private boolean isHostFlag = false; // 标志当前设备是否为主机

    // 单例实例
    private static NetworkController instance;

    // 防止重复发送JOIN_GAME的限制
    private static final long JOIN_MESSAGE_THROTTLE = 3000;

    private final java.util.List<String> pendingJoins = new java.util.ArrayList<>();

    // 构造器
    public NetworkController(NetworkManager networkManager, NetworkView networkView, AppExecutors executors) {
        this.networkManager = networkManager;// 网络管理器
        this.currentView = networkView;// 当前活跃视图
        this.executors = executors;// 执行器

        // 设置网络管理器的监听器
        networkManager.setConnectionListener(this);
        networkManager.setMessageListener(this);
    }

    // 获取单例实例
    public static synchronized NetworkController getInstance(NetworkManager networkManager, NetworkView networkView) {
        if (instance == null) {
            instance = new NetworkController(networkManager, networkView, AppExecutors.getInstance());
        } else {
            instance.updateNetworkView(networkView);
        }
        return instance;
    }

    // 更新网络视图
    private void updateNetworkView(NetworkView newNetworkView) {
        if (newNetworkView != null && newNetworkView != this.currentView) {
            this.currentView = newNetworkView;
            mainHandler.post(() -> {
                currentView.updateConnectionStatus(networkManager.getConnectionStatus());
                for (NetworkManager.DeviceInfo device : networkManager.getDiscoveredDevices()) {
                    currentView.addDiscoveredDevice(device);
                }
            });
        }
    }

    // 设置游戏控制器
    public void setGameController(GameController gameController) {
        this.gameController = gameController;
        // flush pending joins
        if (isHostFlag && gameController != null && !pendingJoins.isEmpty()) {
            for (String n : pendingJoins) {
                gameController.addRemotePlayer(n);
            }
            pendingJoins.clear();
        }
    }

    // 获取网络视图
    public NetworkView getNetworkView() {
        return currentView;
    }

    // 初始化网络连接
    public void initialize(NetworkManager.ConnectionType type) {
        executors.io().execute(() -> {
            try {
                networkManager.initialize(type);
            } catch (Exception e) {
                Log.e(TAG, "初始化网络失败", e);
            }
        });
    }

    // 开始设备搜索
    public void startDiscovery() {
        executors.io().execute(() -> {
            try {
                networkManager.startDiscovery();
            } catch (Exception e) {
                Log.e(TAG, "搜索设备失败", e);
            }
        });
    }

    // 停止设备搜索
    public void stopDiscovery() {
        executors.io().execute(() -> {
            try {
                networkManager.stopDiscovery();
            } catch (Exception e) {
                Log.e(TAG, "停止搜索时出错", e);
            }
        });
    }

    // 创建房间
    public boolean createRoom(String roomName) {
        try {
            boolean success = networkManager.createRoom(roomName);
            if (success)
                isHostFlag = true;
            mainHandler.post(() -> currentView.showMessage(success ? "创建房间成功" : "创建房间失败"));
            return success;
        } catch (Exception e) {
            Log.e(TAG, "创建房间出错", e);
            return false;
        }
    }

    // 加入房间
    public boolean joinRoom(String deviceAddress) {
        try {
            boolean success = networkManager.joinRoom(deviceAddress);
            if (success)
                isHostFlag = false;
            mainHandler.post(() -> currentView.showMessage(success ? "正在连接到房间..." : "加入房间失败"));
            return success;
        } catch (Exception e) {
            mainHandler.post(() -> currentView.showMessage("加入房间出错: " + e.getMessage()));
            Log.e(TAG, "加入房间出错", e);
            return false;
        }
    }

    // 断开连接
    public void disconnect() {
        executors.io().execute(() -> {
            try {
                networkManager.disconnect();
                mainHandler.post(() -> currentView.showMessage("已断开连接"));
            } catch (Exception e) {
                Log.e(TAG, "断开连接时出错", e);
            }
        });
    }

    // 获取设备列表
    public List<NetworkManager.DeviceInfo> getDiscoveredDevices() {
        return networkManager.getDiscoveredDevices();
    }

    // 获取当前连接状态
    public NetworkManager.ConnectionStatus getConnectionStatus() {
        return networkManager.getConnectionStatus();
    }

    // 发送消息
    public boolean sendMessage(NetworkManager.MessageType type, String data) {
        currentView.showMessage("发送消息: " + type);
        executors.io().execute(() -> {
            boolean success = networkManager.sendMessage(type, data);
            mainHandler.post(() -> {
                if (success) {
                    currentView.showMessage(type + " 消息发送成功");
                } else {
                    currentView.showMessage(type + " 消息发送失败");
                }
            });
        });
        return true;
    }

    // 连接状态改变事件处理
    @Override
    public void onConnectionStatusChanged(NetworkManager.ConnectionStatus status) {
        mainHandler.post(() -> currentView.updateConnectionStatus(status));
    }

    // 消息接收事件处理
    @Override
    public void onMessageReceived(NetworkManager.MessageType type, String data, String senderId) {
        executors.main().execute(() -> {
            switch (type) {
                case JOIN_GAME:
                    currentView.showMessage("玩家加入: " + data);
                    if (gameController != null) {
                        gameController.addRemotePlayer(data);
                    } else {
                        pendingJoins.add(data);
                    }
                    break;
                case GAME_START:
                    currentView.showMessage("游戏开始");
                    break;
                case DEAL_CARDS:
                    if (gameController != null) {
                        gameController.buildGameFromDealMessage(data);
                    }
                    break;
                case PLAY_REQUEST: // 只有房主会处理
                    if (isHostFlag && gameController != null) {
                        handlePlayRequestAsHost(data);
                    }
                    break;
                case PLAY_BROADCAST: // 所有端都应用
                    if (gameController != null) {
                        applyPlayCards(data);
                    }
                    break;
                case PASS:
                    currentView.showMessage("玩家选择不出");
                    if (gameController != null) {
                        handlePassBroadcast(data);
                    }
                    break;
                case CHAT_MESSAGE:
                    currentView.showMessage("聊天: " + data);
                    break;
                case PLAYER_LEFT:
                    currentView.showMessage("玩家离开: " + data);
                    break;
                default:
                    Log.d(TAG, "未处理的消息类型: " + type);
            }
        });
    }

    // 发现设备事件处理
    @Override
    public void onDeviceDiscovered(NetworkManager.DeviceInfo device) {
        mainHandler.post(() -> currentView.addDiscoveredDevice(device));
    }

    /** 供外部判断当前是否为房主 */
    public boolean isHost() {
        return isHostFlag;
    }

    /** 快捷发送 GAME_START 消息 */
    public boolean sendGameStartMessage() {
        return sendMessage(NetworkManager.MessageType.GAME_START, "start");
    }

    /** Host 用于判断是否还有尚未处理的 JOIN_GAME */
    public boolean hasPendingJoins() {
        return !pendingJoins.isEmpty();
    }

    private void applyPlayCards(String data) {
        try {
            String[] parts = data.split(":", 2);
            if (parts.length != 2)
                return;
            String name = parts[0].trim();
            String body = parts[1];
            String nextName = null;
            int metaIdx = body.indexOf("|next=");
            if (metaIdx >= 0) {
                nextName = body.substring(metaIdx + 6).trim();
                body = body.substring(0, metaIdx);
            }
            String[] cardTokens = body.split(";");
            Game g = gameController.getGame();
            if (g == null)
                return;
            java.util.List<models.Card> removed = new java.util.ArrayList<>();
            java.util.List<models.Card> parsed = new java.util.ArrayList<>();
            int playedIdx = -1;
            for (int i = 0; i < g.getPlayers().size(); i++) {
                models.Player p = g.getPlayers().get(i);
                if (p.getName().equalsIgnoreCase(name)) {
                    java.util.List<models.Card> remove = new java.util.ArrayList<>();
                    for (String tk : cardTokens) {
                        if (tk.trim().isEmpty())
                            continue;
                        String[] sr = tk.split("\\.");
                        int suit = Integer.parseInt(sr[0]);
                        int rank = Integer.parseInt(sr[1]);
                        models.Card newCard = new models.Card(suit, rank);
                        parsed.add(newCard);
                        for (models.Card c : p.getHand())
                            if (c.getSuit() == suit && c.getRank() == rank) {
                                remove.add(c);
                                break;
                            }
                    }
                    java.util.List<models.Card> newHand = new java.util.ArrayList<>(p.getHand());
                    newHand.removeAll(remove);
                    p.setHand(newHand);
                    removed.addAll(remove);
                    playedIdx = i;
                    break;
                }
            }
            // 推进 currentIdx 到下一位玩家（仅客户端侧估算）
            if (nextName != null) {
                try {
                    for (int i = 0; i < g.getPlayers().size(); i++)
                        if (g.getPlayers().get(i).getName().equalsIgnoreCase(nextName)) {
                            java.lang.reflect.Field idxF = g.getClass().getDeclaredField("currentIdx");
                            idxF.setAccessible(true);
                            idxF.setInt(g, i);
                            break;
                        }
                    if (playedIdx >= 0) {
                        java.lang.reflect.Field lastF = g.getClass().getDeclaredField("lastIdx");
                        lastF.setAccessible(true);
                        lastF.setInt(g, playedIdx);
                    }
                } catch (Exception e) {
                }
            } else if (playedIdx >= 0) {
                try {
                    int nextIdx = (playedIdx + 1) % g.getPlayers().size();
                    java.lang.reflect.Field idxF = g.getClass().getDeclaredField("currentIdx");
                    idxF.setAccessible(true);
                    idxF.setInt(g, nextIdx);

                    java.lang.reflect.Field lastF = g.getClass().getDeclaredField("lastIdx");
                    lastF.setAccessible(true);
                    lastF.setInt(g, playedIdx);
                } catch (Exception e) {
                }
            }

            gameController.updateGameState(g);

            // 更新上一手牌显示
            java.util.List<models.Card> toShow = removed.isEmpty() ? parsed : removed;
            if (!toShow.isEmpty()) {
                gameController.externalCardsPlayed(name, toShow);
            }

            // 同步 lastPattern 与 passCount，防止客户端后续校验失配
            try {
                models.PatternValidator validator = new models.PatternValidatorImpl();
                models.CardPattern pat = validator.validate(parsed);
                java.lang.reflect.Field lpF = g.getClass().getDeclaredField("lastPattern");
                lpF.setAccessible(true);
                lpF.set(g, pat);

                java.lang.reflect.Field pcF = g.getClass().getDeclaredField("passCount");
                pcF.setAccessible(true);
                pcF.setInt(g, 0);
            } catch (Exception e) {
            }
        } catch (Exception ignored) {
        }
    }

    // 房主收到客户端 PLAY_REQUEST 时调用
    private void handlePlayRequestAsHost(String data) {
        if (gameController == null)
            return;
        Game g = gameController.getGame();
        if (g == null)
            return;

        String[] parts = data.split(":", 2);
        if (parts.length != 2)
            return;
        String reqPlayer = parts[0];
        String cardPart = parts[1];

        // 解析牌列表，供后续 UI 显示
        java.util.List<models.Card> parsed = new java.util.ArrayList<>();
        for (String tk : cardPart.split(";")) {
            if (tk.trim().isEmpty())
                continue;
            String[] sr = tk.split("\\.");
            if (sr.length < 2)
                continue;
            int suit = Integer.parseInt(sr[0]);
            int rank = Integer.parseInt(sr[1]);
            parsed.add(new models.Card(suit, rank));
        }

        // 1. 找到玩家
        models.Player target = null;
        for (models.Player p : g.getPlayers())
            if (p.getName().equalsIgnoreCase(reqPlayer)) {
                target = p;
                break;
            }
        if (target == null)
            return;

        // 2. 取消所有选中并重新选中请求中的牌
        target.clearSelections();

        String[] tokens = cardPart.split(";");
        for (String tk : tokens) {
            if (tk.trim().isEmpty())
                continue;
            String[] sr = tk.split("\\.");
            if (sr.length < 2)
                continue;
            int suit = Integer.parseInt(sr[0]);
            int rank = Integer.parseInt(sr[1]);
            // toggle selection for card in hand
            java.util.List<models.Card> hand = target.getHand();
            for (int idx = 0; idx < hand.size(); idx++) {
                models.Card c = hand.get(idx);
                if (c.getSuit() == suit && c.getRank() == rank) {
                    target.toggleCardSelection(idx);
                    break;
                }
            }
        }

        boolean ok = g.playSelected();
        if (!ok) {
            return; // 非法牌，忽略
        }

        // 3. playSelected 已推进 currentPlayer；GameController.onCardsPlayed() 将负责广播。

        // 刷新房主 UI 与上一手牌
        gameController.updateGameState(g);
        gameController.externalCardsPlayed(reqPlayer, parsed);
    }

    private void handlePassBroadcast(String data) {
        if (gameController == null)
            return;
        String name;
        String next = null;
        int sep = data.indexOf("|next=");
        if (sep >= 0) {
            name = data.substring(0, sep).trim();
            next = data.substring(sep + 6).trim();
        } else {
            name = data.trim();
        }
        gameController.handlePassBroadcast(name);
        if (next != null) {
            Game g = gameController.getGame();
            if (g != null) {
                try {
                    for (int i = 0; i < g.getPlayers().size(); i++)
                        if (g.getPlayers().get(i).getName().equalsIgnoreCase(next)) {
                            java.lang.reflect.Field idxF = g.getClass().getDeclaredField("currentIdx");
                            idxF.setAccessible(true);
                            idxF.setInt(g, i);
                            break;
                        }
                    gameController.updateGameState(g);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
