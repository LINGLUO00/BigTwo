package controller;

import models.*;
import java.util.List;
import java.util.function.Supplier;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.lang.StringBuilder;

/**
 * 游戏控制器类，负责协调游戏逻辑和UI交互
 * 
 * 注意：目前Game类只支持以下状态:
 * - STATE_WAITING(0)：等待开始
 * - STATE_DEALING(1)：发牌中
 * - STATE_PLAYING(2)：游戏中
 * - STATE_GAME_OVER(3)：游戏结束
 * 
 * 对于网络游戏，我们使用STATE_WAITING来表示"等待玩家加入"和"等待游戏开始"。
 * 这种实现需要依靠NetworkController来维护更详细的连接和准备状态。
 */
public class GameController implements Game.GameStateListener {
    private Game game;                  // 游戏模型
    private GameView gameView;          // 游戏视图接口
    private NetworkController networkController; // 网络控制器
    private boolean isNetworkGame;      // 是否为联网游戏
    
    // 添加变量防止JOIN消息循环
    private long lastJoinMessageTime = 0;
    private static final long JOIN_MESSAGE_THROTTLE = 3000; // 3秒内不重复发送JOIN消息
    private String lastProcessedJoinPlayer = "";
    
    /**
     * 获取当前游戏对象
     * @return 当前游戏对象
     */
    public Game getGame() {
        return game;
    }
    
    /**
     * 创建一个单机游戏控制器
     * @param gameView 游戏视图接口
     */
    public GameController(GameView gameView) {
        this.gameView = gameView;
        this.isNetworkGame = false;
        this.networkController = null;  // 非网络模式不需要networkController
    }
    
    /**
     * 创建一个联网游戏控制器
     * @param gameView 游戏视图接口
     * @param networkController 网络控制器
     */
    public GameController(GameView gameView, NetworkController networkController) {
        this.gameView = gameView;
        this.networkController = networkController;
        this.isNetworkGame = true;
    }
    
    /**
     * 创建单机游戏
     * @param playerName 玩家名称
     * @param aiCount AI数量
     */
    public void createSinglePlayerGame(String playerName, int aiCount) {
        game = safeExecute(
            () -> GameFactory.createSinglePlayerGame(playerName, aiCount),
            "创建单机游戏失败",
            null
        );
        
        if (game != null) {
            game.addGameStateListener(this);
            updateGameUI();
            updatePlayerHand(findHumanPlayer());
        }
    }
    
    /**
     * 创建网络游戏
     * @param playerName 玩家名称
     * @param isHost 是否是主机
     * @return 是否成功创建游戏
     */
    public boolean createNetworkGame(String playerName, boolean isHost) {
        try {
            showDebugToast("开始: 创建网游-" + playerName);
            
            // 创建只有一个人类玩家的游戏
            game = new Game();
            game.addGameStateListener(this);
            
            // 添加人类玩家
            Player humanPlayer = new Player(playerName, true);
            game.addPlayer(humanPlayer);
            
            // 对于网络游戏，禁止自动补充AI
            game.setAutoFillBots(false);
            
            // 设置游戏为网络游戏
            isNetworkGame = true;
            
            // 记录游戏创建状态
            if (game == null) {
                showDebugToast("错误: 游戏创建失败");
                return false;
            }
            
            // 检查玩家数量
            if (game.getPlayers().isEmpty()) {
                showDebugToast("问题: 玩家列表空");
                return false;
            }
            
            // 如果是主机，等待其他玩家加入
            if (isHost) {
                showDebugToast("主机: 等待玩家加入");
                game.resetGameState(); // 等待状态
                // 主机主动广播自己的加入信息，确保客户端能获取到主机玩家
                sendJoinMessageIfNeeded();
            } else {
                // 如果是客户端，则处于等待游戏开始状态
                showDebugToast("客户: 等待开始");
                // 客户端也处于等待状态
                game.resetGameState(); // 确保游戏处于STATE_WAITING状态
                
                // 客户端需要确保加入房间后发送JOIN_GAME消息
                sendJoinMessageIfNeeded();
            }
            
            // 更新界面
            updateGameUI();
            
            // 打印玩家信息以便调试
            logPlayerInfo();
            
            showDebugToast("完成: 创建网游-状态" + game.getGameState());
            return true;
        } catch (Exception e) {
            showDebugToast("失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 开始游戏
     */
    public void startGame() {
        // 检查游戏是否已创建
        if (!checkGameState(null)) {
            return;
        }
        
        // 如果是网络游戏，且是主机，需要先广播自己的JOIN信息，确保客户端已拥有完整玩家列表
        if (isNetworkGame && networkController != null) {
            showDebugToast("主机: 准备开始网络游戏");
            
            // 1. 先再次广播JOIN_GAME，避免客户端缺少主机玩家导致52张牌
            sendJoinMessageIfNeeded();
            
            // 2. 等待一段时间确保JOIN消息已被接收并处理
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 3. 记录当前玩家情况
            logPlayerInfo();
        }
        
        // 检查玩家数量是否足够
        if (game.getPlayers().size() < 2) {
            gameView.showMessage("等待其他玩家加入，至少需要2名玩家才能开始");
            showDebugToast("开始游戏失败: 玩家数量不足(" + game.getPlayers().size() + ")");
            return;
        }
        
        // 如果是网络游戏，发送开始游戏消息（放在JOIN之后）
        if (isNetworkGame && networkController != null) {
            showDebugToast("发送游戏开始消息");
            
            // 放到后台线程，避免I/O阻塞UI
            new Thread(() -> {
                // 发送游戏开始消息，尝试多次以确保可靠传递
                boolean success = false;
                for (int i = 0; i < 3 && !success; i++) {
                    success = networkController.sendGameStartMessage();
                    if (!success) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                
                // 如果发送成功，给网络一些时间传播消息
                if (success) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // 在主线程上继续游戏流程
                    new Handler(Looper.getMainLooper()).post(() -> {
                        completeGameStart();
                    });
                } else {
                    showDebugToast("发送游戏开始消息失败，请重试");
                }
            }).start();
        } else {
            // 单机游戏，直接开始
            completeGameStart();
        }
    }
    
    /**
     * 完成游戏开始流程，实际执行游戏的启动操作
     */
    private void completeGameStart() {
        // 检查游戏是否已创建
        if (!checkGameState(null)) {
            return;
        }
        
        if (isNetworkGame && networkController != null) {
            if (isHost()) {
                // 主机先执行发牌，然后向客户端广播发牌结果
                showDebugToast("主机: 开始洗牌发牌");
                game.resetGameState();
                game.startGame();
                
                // 发送发牌结果给所有客户端
                sendDealCardsMessage();
                
                // 继续游戏流程
                handleInitialPlayer();
                updateGameUI();
                updatePlayerHand(null);
            } else {
                // 客户端不自行发牌，而是等待接收主机发来的牌
                showDebugToast("客户端: 等待主机发牌...");
                gameView.showMessage("等待主机发牌...");
                
                // 只进行游戏状态初始化，不实际发牌
                game.resetGameState();
                
                // 客户端在接收到DEAL_CARDS消息后再更新UI
            }
        } else {
            // 单机游戏，直接开始
            boolean success = safeExecute(
                () -> {
                    game.startGame();
                    return true;
                },
                "游戏启动失败",
                false
            );
            
            if (!success) {
                return;
            }
            
            // 处理第一个出牌的玩家(持有方块3的玩家)
            handleInitialPlayer();
            
            // 更新UI
            updateGameUI();
            updatePlayerHand(null); // 更新所有人类玩家手牌
        }
        
        // 开始游戏流程
        safeExecute(
            this::handleNextPlayer,
            "处理下一玩家失败"
        );
    }
    
    /**
     * 发送发牌结果给所有客户端
     */
    private void sendDealCardsMessage() {
        if (game == null || networkController == null) {
            return;
        }
        
        try {
            // 收集所有玩家的手牌
            List<List<Card>> allHands = new ArrayList<>();
            for (Player player : game.getPlayers()) {
                allHands.add(player.getHand());
            }
            
            // [调试] 记录主机上每个玩家的牌
            gameView.showDebugInfo("卡牌", "=== 主机发牌结果 ===");
            for (int i = 0; i < game.getPlayers().size(); i++) {
                Player p = game.getPlayers().get(i);
                gameView.showDebugInfo("卡牌", p.getName() + "的牌(" + p.getHand().size() + "张): " + cardListToString(p.getHand()));
            }
            
            // 序列化所有玩家的手牌
            StringBuilder sb = new StringBuilder();
            // 添加玩家数量
            sb.append(allHands.size()).append(":");
            
            // 为每个玩家添加手牌
            for (int i = 0; i < allHands.size(); i++) {
                List<Card> playerHand = allHands.get(i);
                // 添加玩家名称
                sb.append(game.getPlayers().get(i).getName()).append(",");
                // 添加这个玩家的牌数量
                sb.append(playerHand.size()).append(",");
                
                // 添加每张牌的信息
                for (Card card : playerHand) {
                    sb.append(card.getSuit()).append(".").append(card.getRank()).append(";");
                }
                
                // 不同玩家之间用"|"分隔
                if (i < allHands.size() - 1) {
                    sb.append("|");
                }
            }
            
            // [调试] 记录序列化后的字符串前50个字符
            String dataPreview = sb.toString().substring(0, Math.min(50, sb.toString().length())) + "...";
            gameView.showDebugInfo("卡牌", "发牌数据(前50): " + dataPreview);
            
            // 发送消息
            showDebugToast("发送发牌结果...");
            
            // 放到后台线程，避免I/O阻塞UI
            new Thread(() -> {
                boolean success = networkController.sendMessage(NetworkManager.MessageType.DEAL_CARDS, sb.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (success) {
                        showDebugToast("发牌消息发送成功");
                        gameView.showDebugInfo("卡牌", "✓ 发牌消息发送成功");
                    } else {
                        showDebugToast("发牌消息发送失败");
                        gameView.showDebugInfo("卡牌", "✗ 发牌消息发送失败");
                    }
                });
            }).start();
            
        } catch (Exception e) {
            showDebugToast("发送发牌消息时出错: " + e.getMessage());
            gameView.showDebugInfo("卡牌", "发送发牌出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 将卡牌列表转换为易读的字符串形式
     * @param cards 卡牌列表
     * @return 格式化的字符串
     */
    private String cardListToString(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        // 只显示前10张牌，太多会影响界面显示
        int maxDisplayCards = Math.min(8, cards.size());
        for (int i = 0; i < maxDisplayCards; i++) {
            Card card = cards.get(i);
            sb.append(card.getDisplayName());
            if (i < maxDisplayCards - 1) {
                sb.append(", ");
            }
        }
        
        if (cards.size() > maxDisplayCards) {
            sb.append(", ... (还有").append(cards.size() - maxDisplayCards).append("张)");
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 判断当前设备是否为主机
     */
    private boolean isHost() {
        // 通过Intent传递的参数判断，这里假设可以通过NetworkController获取
        // 实际实现可能需要根据应用的具体结构来获取这个信息
        return networkController != null && networkController.isHost();
    }
    
    /**
     * 处理第一个出牌的玩家(持有方块3的玩家)
     */
    private void handleInitialPlayer() {
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer == null) {
            showDebugToast("错误: 当前玩家为null");
            return;
        }
        
        showDebugToast("当前玩家: " + currentPlayer.getName());
        
        // 检查当前玩家是否持有方块3
        boolean hasDiamondThree = safeExecute(
            () -> checkPlayerHasDiamondThree(currentPlayer),
            "检查方块3持有者失败",
            false
        );
        
        // 显示提示信息
        if (hasDiamondThree) {
            String message = currentPlayer.isHuman() ? 
                "你持有方块3，请先出牌" : 
                "玩家 " + currentPlayer.getName() + " 持有方块3，先出牌";
            gameView.showMessage(message);
        } else {
            // 查找哪个玩家持有方块3
            int diamondThreeIndex = game.findStartingPlayerIndex();
            if (diamondThreeIndex >= 0 && diamondThreeIndex < game.getPlayers().size()) {
                Player diamondThreeHolder = game.getPlayers().get(diamondThreeIndex);
                if (diamondThreeHolder != null) {
                    Log.d("卡牌调试", "方块3实际持有者: " + diamondThreeHolder.getName());
                    String holderMsg = "持有方块3的玩家: " + diamondThreeHolder.getName();
                    gameView.showMessage(holderMsg);
                }
            }
            
            showDebugToast("当前玩家没有方块3，可能是随机分牌");
        }
    }
    
    /**
     * 检查玩家是否持有方块3
     */
    private boolean checkPlayerHasDiamondThree(Player player) {
        if (player == null || player.getHand() == null || player.getHand().isEmpty()) {
            return false;
        }
        
        for (Card card : player.getHand()) {
            if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 当前玩家选择一张牌
     * @param cardIndex 牌的索引
     */
    public void selectCard(int cardIndex) {
        // 检查游戏状态是否为游戏中
        if (!checkGameState(Game.STATE_PLAYING)) {
            return;
        }
        
        Player currentPlayer = game.getCurrentPlayer();
        
        // 只有人类玩家才能选牌
        if (!currentPlayer.isHuman()) {
            return;
        }
        
        // 检查索引有效性
        if (cardIndex < 0 || cardIndex >= currentPlayer.getHand().size()) {
            return;
        }
        
        // 选择卡牌
        safeExecute(
            () -> {
                currentPlayer.toggleCardSelection(cardIndex);
                updatePlayerHand(currentPlayer);
            },
            "选择卡牌失败"
        );
    }
    
    /**
     * 当前玩家出牌
     */
    public void playCards() {
        // 检查游戏状态是否为游戏中
        if (!checkGameState(Game.STATE_PLAYING)) {
            return;
        }
        
        // 获取当前玩家
        Player currentPlayer = game.getCurrentPlayer();
        
        // 只有当前玩家是人类玩家时才执行
        if (!currentPlayer.isHuman()) {
            return;
        }
        
        // 检查是否有选中的牌
        List<Card> selectedCards = currentPlayer.getSelectedCards();
        if (selectedCards.isEmpty()) {
            gameView.showMessage("请先选择要出的牌");
            return;
        }
        
        // 尝试出牌
        boolean success = safeExecute(
            () -> game.playCards(),
            "出牌操作失败",
            false
        );
        
        if (success) {
            // 如果是网络游戏，发送出牌消息
            if (isNetworkGame && networkController != null) {
                String cardData = serializeCards(selectedCards);
                new Thread(() -> networkController.sendPlayCardsMessage(cardData)).start();
            }
            
            // 检查是否游戏结束
            if (game.getGameState() == Game.STATE_GAME_OVER) {
                gameView.showGameResult(game.getLastPlayer());
                return;
            }
            
            // 处理下一个玩家的回合
            handleNextPlayer();
        } else {
            gameView.showMessage("出牌失败，请检查牌型是否合法");
        }
    }
    
    /**
     * 当前玩家不出
     */
    public void pass() {
        // 检查游戏状态是否为游戏中
        if (!checkGameState(Game.STATE_PLAYING)) {
            return;
        }
        
        Player currentPlayer = game.getCurrentPlayer();
        
        // 只有人类玩家才能主动选择不出
        if (!currentPlayer.isHuman()) {
            return;
        }
        
        boolean success = safeExecute(
            () -> game.pass(),
            "执行不出操作失败",
            false
        );
        
        if (success) {
            if (isNetworkGame && networkController != null) {
                // 如果是网络游戏，发送不出消息
                new Thread(() -> networkController.sendPassMessage()).start();
            }
            
            handleNextPlayer();
        } else {
            gameView.showMessage("当前不能选择不出");
        }
    }
    
    /**
     * 处理下一个玩家的回合
     */
    private void handleNextPlayer() {
        // 检查游戏状态
        if (!checkGameState(Game.STATE_PLAYING)) {
            return;
        }
        
        // 更新游戏状态视图
        updateGameUI();
        
        // 获取当前玩家
        Player nextPlayer = game.getCurrentPlayer();
        if (nextPlayer == null) {
            showDebugToast("错误：当前玩家为null");
            return;
        }
        
        // 如果游戏已经结束，显示结果
        if (game.getGameState() == Game.STATE_GAME_OVER) {
            gameView.showGameResult(game.getLastPlayer());
            return;
        }
        
        // 如果下一个玩家是AI
        if (!nextPlayer.isHuman()) {
            handleAITurn();
        } else {
            // 人类玩家回合，更新UI
            updatePlayerHand(nextPlayer);
        }
    }
    
    /**
     * 处理AI玩家回合
     */
    private void handleAITurn() {
        // 延迟一段时间，模拟AI思考
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 处理AI回合
        safeExecute(
            () -> {
                game.handleAITurn();
                
                // 如果游戏结束，显示结果
                if (game.getGameState() == Game.STATE_GAME_OVER) {
                    gameView.showGameResult(game.getLastPlayer());
                    return;
                }
                
                // 确保界面更新到最新状态
                updateGameUI();
                
                // 继续处理下一个玩家
                handleNextPlayer();
            },
            "处理AI回合失败"
        );
    }
    
    /**
     * 处理网络消息
     * @param type 消息类型
     * @param data 消息数据
     * @param senderId 发送者ID
     */
    public void handleNetworkMessage(NetworkManager.MessageType type, String data, String senderId) {
        if (game == null) {
            showDebugToast("错误: 游戏对象不存在");
            return;
        }
        
        try {
            switch (type) {
                case JOIN_GAME:
                    handleJoinGameMessage(data);
                    break;
                case GAME_START:
                    handleGameStartMessage();
                    break;
                case DEAL_CARDS:
                    handleDealCardsMessage(data);
                    break;
                case PLAY_CARDS:
                    handlePlayCardsMessage(data);
                    break;
                case PASS:
                    handlePassMessage();
                    break;
                case PLAYER_LEFT:
                    handlePlayerLeftMessage(data);
                    break;
                default:
                    Log.d("GameController", "未处理的消息类型: " + type);
            }
        } catch (Exception e) {
            showDebugToast("处理网络消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理发牌结果消息
     * @param data 发牌数据
     */
    private void handleDealCardsMessage(String data) {
        if (data == null || data.isEmpty()) {
            showDebugToast("收到空的发牌数据");
            return;
        }
        
        try {
            // [调试] 记录收到的发牌数据
            gameView.showDebugInfo("卡牌", "=== 客户端收到发牌数据 ===");
            gameView.showDebugInfo("卡牌", "数据前50字符: " + data.substring(0, Math.min(50, data.length())) + "...");
            
            // 解析数据格式: "玩家数量:玩家1名称,牌数量,牌1;牌2;...|玩家2名称,牌数量,牌1;牌2;...|..."
            String[] parts = data.split(":", 2);
            if (parts.length < 2) {
                showDebugToast("发牌数据格式错误");
                return;
            }
            
            int playerCount = Integer.parseInt(parts[0]);
            String[] playerData = parts[1].split("\\|");
            
            if (playerData.length != playerCount) {
                showDebugToast("玩家数量与数据不匹配");
                return;
            }
            
            // 创建所有玩家的手牌
            List<List<Card>> allPlayerCards = new ArrayList<>();
            gameView.showDebugInfo("卡牌", "解析玩家数量: " + playerCount);
            
            for (int i = 0; i < playerCount; i++) {
                String[] playerInfo = playerData[i].split(",", 3);
                if (playerInfo.length < 3) {
                    showDebugToast("玩家" + i + "的数据格式错误");
                    continue;
                }
                
                String playerName = playerInfo[0];
                int cardCount = Integer.parseInt(playerInfo[1]);
                String cardsData = playerInfo[2];
                
                // 解析这个玩家的牌
                List<Card> playerHand = new ArrayList<>();
                String[] cardStrings = cardsData.split(";");
                for (String cardStr : cardStrings) {
                    if (cardStr == null || cardStr.isEmpty()) continue;
                    
                    String[] cardParts = cardStr.split("\\.");
                    if (cardParts.length == 2) {
                        int suit = Integer.parseInt(cardParts[0]);
                        int rank = Integer.parseInt(cardParts[1]);
                        playerHand.add(new Card(suit, rank));
                    }
                }
                
                // [调试] 记录解析到的牌
                gameView.showDebugInfo("卡牌", "解析玩家'" + playerName + "'的牌: " + 
                      playerHand.size() + "张: " + cardListToString(playerHand));
                
                // 确保牌数量正确
                if (playerHand.size() != cardCount) {
                    showDebugToast("玩家" + playerName + "的牌数量不匹配: " + playerHand.size() + " vs " + cardCount);
                }
                
                allPlayerCards.add(playerHand);
            }
            
            // 设置所有玩家的牌
            game.setAllPlayersCards(allPlayerCards);
            
            // 初始化游戏状态，找出方块3的持有者等
            try {
                // 使用公共方法初始化游戏状态
                game.setupGameStateAfterNetworkDeal();
            } catch (Exception e) {
                gameView.showDebugInfo("卡牌", "初始化游戏状态失败: " + e.getMessage());
                game.resetGameState(); // 回退到重置状态
            }
            
            // [调试] 记录设置后各玩家的牌情况
            gameView.showDebugInfo("卡牌", "=== 客户端设置牌后 ===");
            for (int i = 0; i < game.getPlayers().size(); i++) {
                Player p = game.getPlayers().get(i);
                gameView.showDebugInfo("卡牌", p.getName() + "的牌(" + p.getHand().size() + "张): " + 
                      cardListToString(p.getHand()));
                
                // 检查方块3
                boolean hasDiamond3 = false;
                for(Card c : p.getHand()) {
                    if(c.getSuit() == Card.DIAMOND && c.getRank() == Card.THREE) {
                        hasDiamond3 = true;
                        break;
                    }
                }
                if(hasDiamond3) {
                    gameView.showDebugInfo("卡牌", "*** " + p.getName() + " 拥有方块3 ***");
                }
            }
            
            // 更新游戏UI
            gameView.showMessage("已收到并设置所有玩家的牌");
            updateGameUI();
            updatePlayerHand(null);
            
            // 处理初始玩家
            handleInitialPlayer();
            
            // 进入游戏流程
            handleNextPlayer();
            
        } catch (Exception e) {
            showDebugToast("解析发牌数据时出错: " + e.getMessage());
            gameView.showDebugInfo("卡牌", "解析发牌出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理玩家加入消息
     */
    private void handleJoinGameMessage(String data) {
        if (data == null || data.trim().isEmpty()) {
            gameView.showMessage("错误：无效的玩家信息 (名称为空)");
            return;
        }
        
        // 去除可能存在的空格
        String playerName = data.trim();
        
        // 检查是否已存在相同名称的玩家
        boolean playerExists = game.getPlayers().stream()
            .anyMatch(p -> p.getName().equals(playerName));
        
        if (playerExists) {
            showDebugToast("玩家已存在: " + playerName + "，忽略消息");
            return;
        }
        
        // 添加新玩家
        safeExecute(
            () -> {
                Player newPlayer = new Player(playerName, false);
                game.addPlayer(newPlayer);
                
                updateGameUI();
                gameView.showMessage("玩家 " + playerName + " 加入了游戏");
                logPlayerInfo();
                
                // 如果游戏视图是Activity，在主线程上更新UI状态
                if (gameView instanceof android.app.Activity) {
                    ((android.app.Activity) gameView).runOnUiThread(() -> {
                        // 如果玩家数量足够且是主机，启用开始游戏按钮
                        if (isNetworkGame && game.getPlayers().size() >= 2) {
                            // 查找并启用开始游戏按钮
                            android.view.View btnStartGame = ((android.app.Activity) gameView).findViewById(
                                    ((android.app.Activity) gameView).getResources().getIdentifier(
                                            "btn_start_game", "id", ((android.app.Activity) gameView).getPackageName()));
                            
                            if (btnStartGame != null) {
                                btnStartGame.setEnabled(true);
                                btnStartGame.setVisibility(android.view.View.VISIBLE);
                            }
                        }
                    });
                }
            },
            "添加玩家失败"
        );
    }
    
    /**
     * 如果是客户端，发送JOIN_GAME消息
     */
    private void sendJoinMessageIfNeeded() {
        if (isNetworkGame && networkController != null) {
            Player localPlayer = findHumanPlayer();
            if (localPlayer != null) {
                // 检查是否要发送JOIN消息（避免频繁发送）
                if (System.currentTimeMillis() - lastJoinMessageTime < JOIN_MESSAGE_THROTTLE && 
                    localPlayer.getName().equals(lastProcessedJoinPlayer)) {
                    showDebugToast("跳过重复JOIN消息发送: " + localPlayer.getName());
                    return;
                }
                
                // 记录时间和玩家
                lastJoinMessageTime = System.currentTimeMillis();
                lastProcessedJoinPlayer = localPlayer.getName();
                
                boolean sent = safeExecute(
                    () -> { 
                        new Thread(() -> networkController.sendJoinGameMessage(localPlayer.getName())).start(); 
                        return true; 
                    },
                    "发送加入消息失败",
                    false
                );
                showDebugToast("客户端响应JOIN_GAME: " + (sent ? "成功" : "失败"));
            }
        }
    }
    
    /**
     * 记录当前玩家信息到日志
     */
    private void logPlayerInfo() {
        StringBuilder playerInfo = new StringBuilder("当前玩家:\n");
        for (int i = 0; i < game.getPlayers().size(); i++) {
            Player player = game.getPlayers().get(i);
            playerInfo.append("玩家").append(i + 1).append(": ")
                     .append(player.getName())
                     .append(player.isHuman() ? " (人类)" : " (AI)")
                     .append("\n");
        }
        showDebugToast(playerInfo.toString());
    }
    
    /**
     * 处理游戏开始消息
     */
    private void handleGameStartMessage() {
        // 确保在主线程处理UI更新
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(this::handleGameStartMessage);
            return;
        }
        
        // 收到开始游戏消息
        showDebugToast("收到开始游戏消息");
        
        // 确保玩家信息已同步
        logPlayerInfo();
        
        // 如果游戏已经开始，不再处理
        if (game.getGameState() != Game.STATE_WAITING) {
            showDebugToast("游戏已开始，忽略重复的开始消息");
            return;
        }
        
        // 检查玩家数量
        if (game.getPlayers().size() < 2) {
            showDebugToast("玩家数量不足，无法开始游戏");
            return;
        }
        
        // 开始游戏
        boolean success = safeExecute(
            () -> {
                game.startGame();
                return true;
            },
            "收到开始游戏消息但启动失败",
            false
        );
        
        if (success) {
            // 检查持有方块3的玩家
            checkDiamondThreeHolder();
            
            // 显示第一个出牌玩家信息
            showFirstPlayerInfo();
            
            // 更新界面
            updateUIOnActivity();
        } else {
            showDebugToast("启动游戏失败");
        }
    }
    
    /**
     * 检查方块3的持有者
     */
    private void checkDiamondThreeHolder() {
        for (Player player : game.getPlayers()) {
            if (player.isHuman() && checkPlayerHasDiamondThree(player)) {
                gameView.showMessage("你持有方块3，游戏将由你先出牌");
            }
        }
    }
    
    /**
     * 显示第一个出牌玩家的信息
     */
    private void showFirstPlayerInfo() {
        Player startingPlayer = game.getCurrentPlayer();
        if (startingPlayer != null) {
            String message = "首轮出牌: " + startingPlayer.getName();
            gameView.showMessage(message);
        }
    }
    
    /**
     * 在Activity中额外更新UI
     */
    private void updateUIOnActivity() {
        if (gameView instanceof android.app.Activity) {
            safeExecute(
                () -> {
                    android.app.Activity activity = (android.app.Activity) gameView;
                    activity.runOnUiThread(() -> {
                        // 更新一次本地玩家手牌
                        updatePlayerHand(findHumanPlayer());
                        
                        // 额外更新UI状态提示
                        android.widget.Toast.makeText(
                            (android.content.Context) gameView,
                            "游戏已开始，玩家数量: " + game.getPlayers().size(),
                            android.widget.Toast.LENGTH_LONG
                        ).show();
                    });
                },
                "在Activity中更新UI失败"
            );
        }
    }
    
    /**
     * 处理出牌消息
     */
    private void handlePlayCardsMessage(String data) {
        showDebugToast("收到PLAY_CARDS消息");
        
        safeExecute(
            () -> {
                List<Card> cards = deserializeCards(data);
                Player currentPlayer = game.getCurrentPlayer();
                
                if (currentPlayer != null) {
                    // 清除当前选择状态
                    currentPlayer.clearSelection();
                    
                    // 匹配网络数据中的牌到玩家手中的牌并标记为选中
                    selectNetworkCards(currentPlayer, cards);
                    
                    // 出牌
                    showDebugToast("处理出牌: " + currentPlayer.getName() + " 出 " + cards.size() + "张牌");
                    game.playCards();
                    
                    // 如果游戏结束，显示结果
                    if (game.getGameState() == Game.STATE_GAME_OVER) {
                        gameView.showGameResult(game.getLastPlayer());
                        showDebugToast("游戏结束，胜利者: " + game.getLastPlayer().getName());
                        return;
                    }
                } else {
                    showDebugToast("处理出牌失败: 当前玩家为null");
                }
                
                handleNextPlayer();
            },
            "处理出牌消息失败"
        );
    }
    
    /**
     * 根据网络消息中的牌选择玩家手中对应的牌
     */
    private void selectNetworkCards(Player player, List<Card> networkCards) {
        for (Card networkCard : networkCards) {
            boolean found = false;
            for (int i = 0; i < player.getHand().size(); i++) {
                Card handCard = player.getHand().get(i);
                if (handCard.getSuit() == networkCard.getSuit() && 
                    handCard.getRank() == networkCard.getRank()) {
                    player.toggleCardSelection(i);
                    found = true;
                    break;
                }
            }
            // 如果手牌中未找到对应的牌(可能是因为各端洗牌顺序不同)，则临时添加该牌
            if (!found) {
                // 将网络牌对象克隆到玩家手牌并选中
                Card cloned = new Card(networkCard.getSuit(), networkCard.getRank());
                cloned.setSelected(true);
                player.getHand().add(cloned);
                java.util.Collections.sort(player.getHand());
            }
        }
    }
    
    /**
     * 处理不出消息
     */
    private void handlePassMessage() {
        showDebugToast("收到PASS消息，玩家选择不出");
        
        safeExecute(
            () -> {
                game.pass();
                handleNextPlayer();
            },
            "处理不出消息失败"
        );
    }
    
    /**
     * 处理玩家离开消息
     */
    private void handlePlayerLeftMessage(String data) {
        gameView.showMessage("玩家 " + data + " 离开了游戏");
        showDebugToast("玩家离开: " + data);
        
        safeExecute(
            () -> {
                // 寻找离开的玩家
                Player leftPlayer = game.getPlayers().stream()
                    .filter(p -> p.getName().equals(data))
                    .findFirst()
                    .orElse(null);
                
                if (leftPlayer != null) {
                    // 这里应添加处理玩家离开的逻辑
                    // game.removePlayer(leftPlayer); // 假设存在此方法
                    updateGameUI();
                }
            },
            "处理玩家离开消息失败"
        );
    }
    
    /**
     * 显示调试Toast
     */
    private void showDebugToast(String message) {
        try {
            if (gameView instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) gameView;
                activity.runOnUiThread(() -> {
                    try {
                        android.widget.Toast.makeText(
                            activity, 
                            "调试: " + message, 
                            android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        System.err.println("显示调试Toast失败: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            // 忽略Toast显示错误
        }
    }
    
    /**
     * 发送聊天消息
     * @param message 消息内容
     */
    public void sendChatMessage(String message) {
        if (isNetworkGame && networkController != null) {
            new Thread(() -> networkController.sendChatMessage(message)).start();
        }
    }
    
    /**
     * 重新开始游戏
     */
    public void restartGame() {
        if (game != null) {
            // 获取当前游戏的玩家配置
            Player humanPlayer = findHumanPlayer();
            boolean isSinglePlayer = game.getPlayers().stream()
                .filter(Player::isHuman)
                .count() <= 1;
            
            if (isSinglePlayer && humanPlayer != null) {
                // 如果是单人游戏，使用相同的配置重新创建
                String playerName = humanPlayer.getName();
                int aiCount = game.getPlayers().size() - 1;
                createSinglePlayerGame(playerName, aiCount);
            } else if (isNetworkGame) {
                // 如果是网络游戏，需要重新建立连接
                gameView.showMessage("网络游戏需要重新建立连接");
            }
        }
    }
    
    // GameStateListener接口实现
    
    @Override
    public void onGameStarted(Game game) {
        // 游戏开始时更新当前玩家的手牌
        updateGameUI();
        
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != null && currentPlayer.isHuman()) {
            updatePlayerHand(currentPlayer);
        }
    }
    
    @Override
    public void onCardsPlayed(Game game, Player player, List<Card> cards, CardPattern pattern) {
        // 当前玩家出牌后更新手牌视图
        if (player.isHuman()) {
            updatePlayerHand(player);
        }
        
        // 更新游戏状态视图，显示上一个玩家出的牌
        updateGameUI();
        
        // 如果是AI玩家，提示AI出牌信息
        if (!player.isHuman() && !cards.isEmpty()) {
            String cardInfo = generateCardPlayInfo(player, cards, pattern);
            gameView.showMessage(cardInfo);
            
            // 输出日志信息
            logCardPlayDetails(player, cards, pattern);
        }
    }
    
    /**
     * 生成出牌信息文本
     */
    private String generateCardPlayInfo(Player player, List<Card> cards, CardPattern pattern) {
        StringBuilder cardInfo = new StringBuilder();
        cardInfo.append(player.getName()).append(" 出牌: ");
        
        // 根据牌型，构建详细的出牌信息
        switch (pattern.getPatternType()) {
            case CardPattern.SINGLE:
                cardInfo.append("单牌 ").append(cards.get(0).getDisplayName());
                break;
            case CardPattern.PAIR:
                cardInfo.append("对子 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.THREE_OF_A_KIND:
                cardInfo.append("三条 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.THREE_WITH_ONE:
                cardInfo.append("三带一 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.THREE_WITH_PAIR:
                cardInfo.append("三带二 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.STRAIGHT:
                cardInfo.append("顺子 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.FLUSH_STRAIGHT:
                cardInfo.append("同花顺 ");
                appendCardNames(cardInfo, cards);
                break;
            case CardPattern.BOMB:
                cardInfo.append("炸弹 ");
                appendCardNames(cardInfo, cards);
                break;
            default:
                cardInfo.append(patternToString(pattern));
        }
        
        return cardInfo.toString();
    }
    
    /**
     * 将卡牌名称添加到StringBuilder中
     */
    private void appendCardNames(StringBuilder sb, List<Card> cards) {
        for (int i = 0; i < cards.size(); i++) {
            sb.append(cards.get(i).getDisplayName());
            if (i < cards.size() - 1) {
                sb.append(" ");
            }
        }
    }
    
    /**
     * 记录出牌详情到日志
     */
    private void logCardPlayDetails(Player player, List<Card> cards, CardPattern pattern) {
        System.out.println("AI玩家出牌信息: " + player.getName() + " 打出 " + 
                           pattern.getPatternType() + " 类型牌");
        System.out.println("AI出牌详情:");
        for (Card card : cards) {
            System.out.println(" - " + card.getDisplayName() + 
                              " (点数:" + card.getRank() + ", 花色:" + card.getSuit() + ")");
        }
    }
    
    @Override
    public void onPlayerPassed(Game game, Player player) {
        updateGameUI();
        
        // 显示玩家选择不出牌的提示
        String message = player.getName() + " 选择不出";
        gameView.showMessage(message);
        
        // 输出日志信息
        System.out.println("玩家不出牌: " + player.getName());
    }
    
    @Override
    public void onGameOver(Game game, Player winner) {
        updateGameUI();
        gameView.showGameResult(winner);
    }
    
    // 辅助方法
    
    /**
     * 将卡牌列表序列化为字符串
     */
    private String serializeCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            sb.append(card.getSuit()).append(",").append(card.getRank()).append(";");
        }
        return sb.toString();
    }
    
    /**
     * 从字符串反序列化卡牌列表
     */
    private List<Card> deserializeCards(String data) {
        List<Card> cards = new java.util.ArrayList<>();
        
        if (data == null || data.isEmpty()) {
            return cards;
        }
        
        String[] cardStrings = data.split(";");
        for (String cardString : cardStrings) {
            String[] parts = cardString.split(",");
            if (parts.length == 2) {
                try {
                    int suit = Integer.parseInt(parts[0]);
                    int rank = Integer.parseInt(parts[1]);
                    cards.add(new Card(suit, rank));
                } catch (NumberFormatException e) {
                    // 忽略格式错误的卡牌
                }
            }
        }
        
        return cards;
    }
    
    /**
     * 将牌型转换为字符串描述
     */
    private String patternToString(CardPattern pattern) {
        if (pattern == null) {
            return "无";
        }
        
        StringBuilder sb = new StringBuilder();
        
        switch (pattern.getPatternType()) {
            case CardPattern.SINGLE:
                sb.append("单牌: ");
                break;
            case CardPattern.PAIR:
                sb.append("对子: ");
                break;
            case CardPattern.THREE_OF_A_KIND:
                sb.append("三条: ");
                break;
            case CardPattern.THREE_WITH_PAIR:
                sb.append("三带二: ");
                break;
            case CardPattern.STRAIGHT:
                sb.append("顺子: ");
                break;
            case CardPattern.BOMB:
                sb.append("炸弹: ");
                break;
            case CardPattern.FLUSH_STRAIGHT:
                sb.append("同花顺: ");
                break;
            case CardPattern.INVALID:
                sb.append("无效: ");
                break;
            case CardPattern.THREE_WITH_ONE:
                sb.append("三带一: ");
                break;
        }
        
        // 添加牌的描述
        if (pattern.getCards() != null && !pattern.getCards().isEmpty()) {
            appendCardNames(sb, pattern.getCards());
        } else {
            sb.append("(无牌)");
        }
        
        return sb.toString();
    }

    /**
     * 设置网络控制器
     */
    public void setNetworkController(NetworkController networkController) {
        this.networkController = networkController;
        this.isNetworkGame = (networkController != null);
    }

    /**
     * 安全执行操作，捕获并处理异常
     * @param operation 要执行的操作
     * @param errorMessage 错误消息前缀
     * @param defaultValue 发生异常时的默认返回值
     * @return 操作结果或默认值
     */
    private <T> T safeExecute(Supplier<T> operation, String errorMessage, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            showDebugToast(errorMessage + ": " + e.getMessage());
            Log.e("GameController", errorMessage, e);
            return defaultValue;
        }
    }

    /**
     * 安全执行无返回值的操作
     * @param runnable 要执行的操作
     * @param errorMessage 错误消息前缀
     * @return 是否执行成功
     */
    private boolean safeExecute(Runnable runnable, String errorMessage) {
        try {
            runnable.run();
            return true;
        } catch (Exception e) {
            showDebugToast(errorMessage + ": " + e.getMessage());
            Log.e("GameController", errorMessage, e);
            return false;
        }
    }

    /**
     * 更新游戏状态和UI
     */
    private void updateGameUI() {
        safeExecute(() -> gameView.updateGameState(game), "更新游戏状态失败");
    }

    /**
     * 更新人类玩家的手牌显示
     * @param player 要更新的玩家，如果为null则更新所有人类玩家
     */
    private void updatePlayerHand(Player player) {
        if (player != null && player.isHuman()) {
            safeExecute(() -> gameView.updatePlayerHand(player), "更新玩家手牌失败");
        } else if (player == null) {
            // 更新所有人类玩家的手牌
            for (Player p : game.getPlayers()) {
                if (p.isHuman()) {
                    safeExecute(() -> gameView.updatePlayerHand(p), "更新玩家手牌失败");
                }
            }
        }
    }

    /**
     * 检查游戏状态是否正常
     * @param expectedState 预期的游戏状态，如果为null则不检查
     * @return 游戏状态是否正常
     */
    private boolean checkGameState(Integer expectedState) {
        if (game == null) {
            showDebugToast("严重错误: 游戏未创建");
            return false;
        }
        
        if (expectedState != null && game.getGameState() != expectedState) {
            showDebugToast("游戏状态错误: 当前=" + game.getGameState() + ", 预期=" + expectedState);
            return false;
        }
        
        return true;
    }

    /**
     * 寻找当前人类玩家
     * @return 人类玩家，如果没有则返回null
     */
    private Player findHumanPlayer() {
        if (game == null || game.getPlayers() == null) {
            return null;
        }
        
        for (Player player : game.getPlayers()) {
            if (player.isHuman()) {
                return player;
            }
        }
        
        return null;
    }
} 