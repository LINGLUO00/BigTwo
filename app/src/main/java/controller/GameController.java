package controller;

import models.*;
import util.AppExecutors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 游戏控制器类，负责协调游戏逻辑和UI交互
 */
public class GameController implements Game.GameStateListener {

    private Game game; // 游戏模型
    private GameView gameView; // 游戏视图
    private NetworkController networkController; // 网络控制器
    private boolean isNetworkGame; // 是否是网络游戏
    private String localPlayerName; // 本机玩家名
    private final AtomicLong lastJoinMessageTime = new AtomicLong(0); // 最后处理加入消息的时间
    private final AtomicReference<String> lastProcessedJoinPlayer = new AtomicReference<>(""); // 最后处理的加入玩家
    private final AppExecutors executors = AppExecutors.getInstance(); // 执行器

    // 构造函数
    public GameController(GameView gameView) {
        this.gameView = gameView;
        this.isNetworkGame = false;
    }

    // 构造函数，用于网络游戏
    public GameController(GameView gameView, NetworkController networkController) {
        this.gameView = gameView;
        this.networkController = networkController;
        this.isNetworkGame = true;
    }

    // 获取游戏实例
    public Game getGame() {
        return game;
    }

    // 创建单机游戏
    public void createSinglePlayerGame(String playerName, int aiCount, AIStrategy aiStrategy) {
        this.localPlayerName = playerName;
        game = safeExecute(() -> GameFactory.createSinglePlayerGame(playerName, aiCount, aiStrategy), "创建单机游戏失败", null);
        if (game != null) {
            game.addGameStateListener(this);
            updateGameUI();
            updatePlayerHand(findHumanPlayer());
        }
    }

    // 创建网络游戏
    public boolean createNetworkGame(String playerName, boolean isHost) {
        this.localPlayerName = playerName;
        try {
            game = new Game();
            game.addGameStateListener(this);
            Player humanPlayer = new Player(playerName, true);
            game.addPlayer(humanPlayer);
            game.setAutoFillBots(false);
            isNetworkGame = true;

            if (game == null || game.getPlayers().isEmpty()) {
                return false;
            }

            if (isHost) {
                sendJoinMessageIfNeeded();
            } else {
                sendJoinMessageIfNeeded();
            }

            updateGameUI();
            logPlayerInfo();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
     * 开始游戏
     * if (!checkGameState(null)) return;：如果游戏状态不正确，则返回。
     * if (isNetworkGame && networkController != null)：如果游戏是网络游戏，并且网络控制器不为空，则执行以下代码。
     * executors.io().execute(() -> {：在后台线程中执行以下代码。
     * boolean success = sendGameStartMessage();：发送游戏开始消息。
     * if (success) {：如果发送游戏开始消息成功，则执行以下代码。
     * executors.main().execute(this::completeGameStart);：在主线程中执行以下代码。
     */
    public void startGame() {
        if (!checkGameState(null))
            return;
        if (isNetworkGame && networkController != null) {
            executors.io().execute(() -> {
                boolean success = sendGameStartMessage();
                if (success) {
                    executors.main().execute(this::completeGameStart);
                } else {
                    showDebugToast("发送游戏开始消息失败，请重试");
                }
            });
        } else {
            completeGameStart();
        }
    }

    // 发送游戏开始消息
    private boolean sendGameStartMessage() {
        for (int i = 0; i < 3; i++) {
            if (networkController.sendGameStartMessage()) {
                return true;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    // 完成"游戏开始"
    private void completeGameStart() {
        if (!checkGameState(null))
            return;
        if (game.getPlayers().size() < 2) {
            return;
        }
        if (isNetworkGame && networkController != null && isHost()) {
            game.startGame(); // 开始游戏
            sendDealCardsMessage(); // 发送发牌消息
            handleInitialPlayer(); // 处理初始玩家
            updateGameUI(); // 更新游戏UI
            // 主机端：立即刷新自己的手牌（避免首家不是自己时界面没有手牌）
            updatePlayerHand(findHumanPlayer());
        } else {
            game.startGame(); // 开始游戏
            handleInitialPlayer(); // 处理初始玩家
            updateGameUI(); // 更新游戏UI
            updatePlayerHand(findHumanPlayer());
        }
    }

    // 发送发牌消息
    private void sendDealCardsMessage() {
        if (game == null || networkController == null)
            return;
        try {
            String message = generateDealCardsMessage();
            executors.io().execute(() -> {
                boolean success = networkController.sendMessage(NetworkManager.MessageType.DEAL_CARDS, message);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 生成发牌消息
    private String generateDealCardsMessage() {
        List<List<Card>> allHands = game.getPlayers().stream()
                .map(Player::getHand)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(allHands.size()).append(":");
        for (int i = 0; i < allHands.size(); i++) {
            List<Card> hand = allHands.get(i);
            sb.append(game.getPlayers().get(i).getName()).append(",")
                    .append(hand.size()).append(",");
            hand.forEach(card -> sb.append(card.getSuit()).append(".").append(card.getRank()).append(";"));
            if (i < allHands.size() - 1)
                sb.append("|");
        }
        sb.append(";self=").append(localPlayerName);
        return sb.toString();
    }

    // 更新游戏UI
    private void updateGameUI() {
        safeExecute(() -> gameView.updateGameState(game), "更新游戏状态失败");
    }

    // 更新玩家手牌
    private void updatePlayerHand(Player player) {
        if (player != null && player.isHuman()) {
            safeExecute(() -> gameView.updatePlayerHand(player), "更新玩家手牌失败");
        } else {
            game.getPlayers().stream().filter(Player::isHuman)
                    .forEach(p -> safeExecute(() -> gameView.updatePlayerHand(p), "更新玩家手牌失败"));
        }
    }

    // 记录玩家信息
    private void logPlayerInfo() {
        StringBuilder playerInfo = new StringBuilder("当前玩家:\n");
        for (Player player : game.getPlayers()) {
            playerInfo.append(player.getName()).append(player.isHuman() ? " (人类)" : " (AI)").append("\n");
        }
        showDebugToast(playerInfo.toString());
    }

    // 显示调试消息
    private void showDebugToast(String message) {
        if (gameView instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) gameView;
            activity.runOnUiThread(() -> android.widget.Toast
                    .makeText(activity, "调试: " + message, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    // 安全执行操作
    private <T> T safeExecute(Supplier<T> operation, String errorMessage, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            showDebugToast(errorMessage + ": " + e.getMessage());
            e.printStackTrace();
            return defaultValue;
        }
    }

    // 检查游戏状态
    private boolean checkGameState(Integer expectedState) {
        if (game == null) {
            showDebugToast("严重错误: 游戏未创建");
            return false;
        }
        if (expectedState != null && game.getState().ordinal() != expectedState) {
            return false;
        }
        return true;
    }

    // 查找人类玩家
    private Player findHumanPlayer() {
        return game.getPlayers().stream().filter(Player::isHuman).findFirst().orElse(null);
    }

    // 检查是否是主机
    private boolean isHost() {
        return networkController != null && networkController.isHost();
    }

    // 游戏开始事件处理
    @Override
    public void onGameStarted(Game game) {
        updateGameUI();
        maybeAutoPlayNext();
    }

    // 卡片被打出事件处理
    @Override
    public void onCardsPlayed(Game game, Player player, List<Card> cards, CardPattern pattern) {
        // 更新上一手牌显示
        safeExecute(() -> gameView.updateLastPlayedCards(cards, player), "更新上一手牌失败");

        if (isNetworkGame && networkController != null) {
            String msg = buildPlayCardsMessage(player, cards);

            NetworkManager.MessageType t;
            if (isHost()) {
                // 手动计算下一位，而不是使用仍指向自己的 currentPlayer
                int idx = game.getPlayers().indexOf(player);
                int nextIdx = (idx + 1) % game.getPlayers().size();
                Player nxt = game.getPlayers().get(nextIdx);
                msg += "|next=" + nxt.getName();
                t = NetworkManager.MessageType.PLAY_BROADCAST;
            } else {
                t = NetworkManager.MessageType.PLAY_REQUEST; // 客户端请求不带 next
            }

            final String sendStr = msg;
            showDebugToast((isHost() ? "BROADCAST " : "REQ ") + sendStr);
            executors.io().execute(() -> networkController.sendMessage(t, sendStr));
        }

        updateGameUI();
        if (player.isHuman())
            updatePlayerHand(player);
        maybeAutoPlayNext();
    }

    // 游戏结束事件处理
    @Override
    public void onGameOver(Game game, Player winner) {
        updateGameUI();
        gameView.showGameResult(winner);
    }

    // 玩家pass事件处理
    @Override
    public void onPlayerPassed(Game game, Player player) {
        try {
            updateGameUI();
        } catch (Exception e) {
            showDebugToast("DBG updateUI err=" + e.getMessage());
        }

        // 网络同步 pass
        if (isNetworkGame && networkController != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(player.getName());
            if (isHost()) {
                Player next = game.getCurrentPlayer();
                if (next != null)
                    sb.append("|next=").append(next.getName());
            }
            executors.io().execute(() -> networkController.sendMessage(NetworkManager.MessageType.PASS, sb.toString()));
        }

        // pass 不更新牌面
        maybeAutoPlayNext();
    }

    // 发送加入消息
    private void sendJoinMessageIfNeeded() {
        long now = System.currentTimeMillis();
        String playerName = findHumanPlayer().getName();

        if (now - lastJoinMessageTime.get() > 5000 || !lastProcessedJoinPlayer.get().equals(playerName)) {
            lastJoinMessageTime.set(now);
            lastProcessedJoinPlayer.set(playerName);

            if (networkController != null) {
                networkController.sendMessage(NetworkManager.MessageType.JOIN_GAME, playerName);
            }
        }
    }

    // 处理初始玩家
    private void handleInitialPlayer() {
        // 如果是第一位玩家，而且是AI，自动出牌
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != null && !currentPlayer.isHuman()) {
            // 延迟500ms，让界面有时间更新
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (currentPlayer instanceof AIPlayer) {
                ((AIPlayer) currentPlayer).autoPlay(game);
            }
        }
    }

    // 安全执行操作
    private void safeExecute(Runnable runnable, String errorMessage) {
        try {
            runnable.run();
        } catch (Exception e) {
            showDebugToast(errorMessage + ": " + e.getMessage());
        }
    }

    /**
     * 如果下一个轮到 AI，自动执行它的回合。
     */
    private void maybeAutoPlayNext() {
        executors.io().execute(() -> {
            try {
                Thread.sleep(1500); // 稍作延迟让 UI 更新并让模型切换到下一位
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Player cur = game.getCurrentPlayer();
            if (cur instanceof AIPlayer) {
                AIPlayer ai = (AIPlayer) cur;
                boolean played = ai.autoPlay(game);
                if (!played) {
                    game.pass();
                }
            }
        });
    }

    /**
     * 从主机发送的发牌消息重建客户端游戏状态。
     * 格式: playersCount:player,handSize,suit.rank;...|player2,...
     */
    public void buildGameFromDealMessage(String message) {
        try {
            String[] parts = message.split(":", 2);// 分割消息
            if (parts.length != 2)
                return;// 如果分割后的长度不等于2，则返回
            String body = parts[1];// 获取消息体
            String selfName = null;
            int selfIdx = body.indexOf(";self=");// 获取self=的位置
            if (selfIdx >= 0) {
                selfName = body.substring(selfIdx + 6);// 获取self=后面的字符串
                body = body.substring(0, selfIdx);// 获取self=前面的字符串
            }
            String[] playerBlocks = body.split("\\|");// 分割玩家块
            game = new Game();// 创建新游戏
            game.addGameStateListener(this);// 添加游戏状态监听器
            for (String block : playerBlocks) {
                String[] seg = block.split(",", 3);// 分割玩家块
                if (seg.length < 3)
                    continue;// 如果分割后的长度小于3，则跳过
                String name = seg[0].trim();// 获取玩家名并去掉可能的空白
                Player p = new Player(name, false);// 创建新玩家
                game.addPlayer(p);// 添加玩家
                String cardsStr = seg[2];// 获取玩家手牌
                String[] cardTokens = cardsStr.split(";");// 分割手牌
                List<Card> hand = new ArrayList<>();// 创建手牌列表
                for (String tk : cardTokens) {// 遍历手牌
                    if (tk == null || tk.trim().isEmpty())
                        continue;// 如果手牌为空，则跳过
                    tk = tk.trim();// 去除手牌中的空格
                    String[] sr = tk.split("\\.");// 分割手牌
                    if (sr.length < 2)
                        continue;// 如果分割后的长度小于2，则跳过
                    int suit = Integer.parseInt(sr[0]);// 获取花色
                    int rank = Integer.parseInt(sr[1]);// 获取牌值
                    hand.add(new Card(suit, rank));// 添加手牌
                }
                p.setHand(hand);// 设置玩家手牌
            }
            if (localPlayerName != null) {// 如果本地玩家名不为空
                for (Player p : game.getPlayers())
                    if (p.getName().equalsIgnoreCase(localPlayerName)) {
                        p.setHuman(true);
                        break;
                    } // 设置本地玩家为人类
            } else if (selfName != null) {// 如果selfName不为空
                for (Player p : game.getPlayers())
                    if (p.getName().equals(selfName)) {
                        p.setHuman(true);
                        break;
                    } // 设置本地玩家为人类
            }

            // 设定游戏已开始的基本状态 (PLAYING)
            try {
                java.lang.reflect.Field stateF = game.getClass().getDeclaredField("state");// 获取游戏状态字段
                stateF.setAccessible(true);// 设置字段可访问
                stateF.set(game, Game.State.PLAYING);// 设置游戏状态为PLAYING

                int idx = 0;// 当前玩家索引
                for (int i = 0; i < game.getPlayers().size(); i++) {
                    Player pp = game.getPlayers().get(i);// 获取当前玩家
                    boolean hasD3 = pp.getHand().stream()// 获取当前玩家手牌
                            .anyMatch(c -> c.getSuit() == Card.DIAMOND && c.getRank() == Card.THREE);// 判断当前玩家手牌中是否有3
                    if (hasD3) {
                        idx = i;
                        break;
                    } // 如果有3，则设置当前玩家索引
                }

                java.lang.reflect.Field idxF = game.getClass().getDeclaredField("currentIdx");// 获取当前玩家索引字段
                idxF.setAccessible(true);// 设置字段可访问
                idxF.setInt(game, idx);// 设置当前玩家索引
            } catch (Exception ignore) {
            } // 捕获异常

            // 更新界面
            updateGameUI();// 更新游戏UI
            updatePlayerHand(findHumanPlayer());// 更新玩家手牌
            showDebugToast("Client parsed players=" + game.getPlayers().size() + " self="
                    + (findHumanPlayer() != null ? findHumanPlayer().getName() : "null"));// 显示调试消息
        } catch (Exception e) {
            showDebugToast("解析发牌消息失败" + e.getMessage());
        }
    }

    // 主机端在等待阶段添加远程玩家
    public void addRemotePlayer(String name) {
        if (game == null)
            return;
        if (game.getState() != Game.State.WAITING)
            return;
        boolean exists = game.getPlayers().stream().anyMatch(p -> p.getName().equals(name));
        if (exists)
            return;
        Player p = new Player(name, false);
        game.addPlayer(p);
        updateGameUI();
    }

    /** 构造 PLAY_CARDS 消息 player:cards */
    private String buildPlayCardsMessage(Player player, List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        sb.append(player.getName()).append(":");
        for (Card c : cards)
            sb.append(c.getSuit()).append('.').append(c.getRank()).append(';');
        return sb.toString();
    }

    /**
     * 供 NetworkController 在收到同步消息后刷新 UI。
     */
    public void updateGameState(Game updated) {
        if (updated != null)
            this.game = updated;
        updateGameUI();
        updatePlayerHand(findHumanPlayer());
    }

    /** 来自网络的出牌结果，用于除该玩家外的玩家刷新上一手牌显示，比如更新这个玩家还有多少牌 */
    public void externalCardsPlayed(String playerName, java.util.List<Card> cards) {
        if (game == null || cards == null)
            return;
        Player temp = null;
        for (Player pl : game.getPlayers()) {
            if (pl.getName().equals(playerName)) {
                temp = pl;
                break;
            }
        }
        if (temp == null)
            return;
        final Player targetPlayer = temp; // effectively final for lambda
        // 更新上一手牌显示
        safeExecute(() -> gameView.updateLastPlayedCards(cards, targetPlayer), "更新上一手牌失败");
        updateGameUI();
    }

    /** 处理 PASS 广播：data 为玩家名 */
    public void handlePassBroadcast(String playerName) {
        if (game == null)
            return;

        // 若轮到的人选择 pass，则游戏模型已有状态更新在房主；客户端仅推进到下一位
        Player cur = game.getCurrentPlayer();
        if (cur != null && cur.getName().equalsIgnoreCase(playerName)) {
            // 模拟 pass：
            game.pass();
            updateGameUI();
        } else {
            // 跳过
        }
    }
}
