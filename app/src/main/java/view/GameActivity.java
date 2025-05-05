package view;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import com.bigtwo.game.R;

import java.util.ArrayList;
import java.util.List;

import controller.BluetoothNetworkManager;
import controller.GameController;
import controller.GameView;
import controller.NetworkController;
import controller.NetworkView;
import models.Card;
import models.CardPattern;
import models.Game;
import models.NetworkManager;
import models.Player;

/**
 * 游戏活动，实现游戏界面
 */
public class GameActivity extends AppCompatActivity implements GameView, NetworkView {
    private static final String TAG = "GameActivity";
    
    private GameController gameController;
    private NetworkController networkController;
    private BluetoothNetworkManager bluetoothManager;

    private TextView tvCurrentPlayer;
    private TextView tvLastPlayedCards;
    private TextView tvGameInfo;
    private TextView tvLastPlayer;
    private FrameLayout layoutTopPlayer;
    private FrameLayout layoutLeftPlayer;
    private FrameLayout layoutRightPlayer;
    private RecyclerView rvPlayerCards;
    private RecyclerView rvLastPlayedCards;
    private Button btnPlayCards;
    private Button btnPass;
    private Button btnQuit;
    private Button btnStartGame;

    private CardAdapter cardAdapter;
    private CardAdapter lastPlayedCardsAdapter;
    
    private TextView tvDebugInfo;
    private ScrollView svDebugInfo;
    private StringBuilder debugLog = new StringBuilder();
    private boolean debugInfoEnabled = true;  // 控制是否显示调试区域
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        // 初始化视图
        tvCurrentPlayer = findViewById(R.id.tv_current_player);
        tvLastPlayedCards = findViewById(R.id.tv_last_played_cards);
        tvGameInfo = findViewById(R.id.tv_game_info);
        tvLastPlayer = findViewById(R.id.tv_last_player);
        layoutTopPlayer = findViewById(R.id.layout_top_player);
        layoutLeftPlayer = findViewById(R.id.layout_left_player);
        layoutRightPlayer = findViewById(R.id.layout_right_player);
        rvPlayerCards = findViewById(R.id.rv_player_cards);
        rvLastPlayedCards = findViewById(R.id.rv_last_played_cards);
        btnPlayCards = findViewById(R.id.btn_play_cards);
        btnPass = findViewById(R.id.btn_pass);
        btnQuit = findViewById(R.id.btn_quit);
        
        // 初始化调试信息区域
        tvDebugInfo = findViewById(R.id.tv_debug_info);
        svDebugInfo = findViewById(R.id.sv_debug_info);
        
        // 设置调试区域的点击事件，点击可切换显示/隐藏
        svDebugInfo.setOnClickListener(v -> {
            debugInfoEnabled = !debugInfoEnabled;
            svDebugInfo.setVisibility(debugInfoEnabled ? View.VISIBLE : View.GONE);
        });
        
        // 初始化调试信息
        addDebugMessage("游戏界面初始化完成");
        
        // 创建并添加开始游戏按钮
        btnStartGame = new Button(this);
        btnStartGame.setText("开始游戏");
        btnStartGame.setTextSize(14);
        btnStartGame.setPadding(8, 8, 8, 8);
        btnStartGame.setBackgroundResource(R.drawable.button_background);
        
        // 将开始游戏按钮添加到控制按钮区域
        LinearLayout buttonLayout = findViewById(R.id.layout_buttons);
        buttonLayout.addView(btnStartGame, 0); // 添加到最左侧
        
        // 默认隐藏开始游戏按钮
        btnStartGame.setVisibility(View.GONE);
        
        // 设置开始游戏按钮点击事件
        btnStartGame.setOnClickListener(v -> {
            Log.d(TAG, "点击开始游戏按钮");
            Toast.makeText(this, "尝试开始游戏...", Toast.LENGTH_SHORT).show();
            
            try {
                if (gameController != null) {
                    Log.d(TAG, "调用gameController.startGame()");
                    Toast.makeText(this, "调用gameController.startGame()", Toast.LENGTH_SHORT).show();
                    gameController.startGame();
                    Toast.makeText(this, "游戏成功启动", Toast.LENGTH_SHORT).show();
                    btnStartGame.setVisibility(View.GONE); // 游戏开始后隐藏按钮
                } else {
                    Toast.makeText(this, "错误: 游戏控制器未初始化", Toast.LENGTH_LONG).show();
                    showMessage("游戏控制器未初始化");
                }
            } catch (Exception e) {
                // 捕获并显示详细错误信息
                String errorMsg = "启动游戏时出错: " + e.getMessage();
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                showMessage(errorMsg);
                
                // 记录异常堆栈
                Log.e(TAG, "游戏启动异常: " + e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    Log.e(TAG, "\t at " + element.toString());
                }
                e.printStackTrace();
                
                // 显示堆栈第一行
                if (e.getStackTrace().length > 0) {
                    Toast.makeText(this, 
                        "错误位置: " + e.getStackTrace()[0].toString(), 
                        Toast.LENGTH_LONG).show();
                }
            }
        });
        
        // 配置RecyclerView和适配器 - 玩家手牌
        cardAdapter = new CardAdapter(new ArrayList<>(), position -> {
            Log.d(TAG, "玩家点击卡牌，位置: " + position);
            
            // 如果不是人类玩家的回合，不允许选牌
            Player currentPlayer = gameController.getGame().getCurrentPlayer();
            if (currentPlayer == null || !currentPlayer.isHuman()) {
                Toast.makeText(this, "不是你的回合", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 如果是游戏中且是人类玩家回合，才允许选牌
            if (gameController.getGame().getGameState() == Game.STATE_PLAYING && currentPlayer.isHuman()) {
                Log.d(TAG, "选择卡牌: " + position);
                gameController.selectCard(position);
            } else {
                Log.d(TAG, "当前状态不允许选牌: " + gameController.getGame().getGameState());
            }
        });
        
        // 配置RecyclerView和适配器 - 最后出的牌
        lastPlayedCardsAdapter = new CardAdapter(new ArrayList<>(), null); // 不需要点击事件
        
        // 设置布局管理器 - 水平布局且可以从右向左查看所有卡牌
        LinearLayoutManager playerCardsLayoutManager = new LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false);
        rvPlayerCards.setLayoutManager(playerCardsLayoutManager);
        rvPlayerCards.setAdapter(cardAdapter);
        
        // 设置布局管理器 - 最后出的牌
        LinearLayoutManager lastPlayedLayoutManager = new LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false);
        rvLastPlayedCards.setLayoutManager(lastPlayedLayoutManager);
        rvLastPlayedCards.setAdapter(lastPlayedCardsAdapter);
        
        // 添加间隔装饰器使玩家手牌重叠显示
        rvPlayerCards.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, 
                                      RecyclerView parent, RecyclerView.State state) {
                // 设置负的左边距让卡牌重叠显示
                if (parent.getChildAdapterPosition(view) > 0) {
                    outRect.left = -30; // 负值使卡牌重叠
                }
            }
        });
        
        // 添加间隔装饰器使最后出的牌重叠显示
        rvLastPlayedCards.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, 
                                      RecyclerView parent, RecyclerView.State state) {
                // 设置负的左边距让卡牌重叠显示
                if (parent.getChildAdapterPosition(view) > 0) {
                    outRect.left = -30; // 负值使卡牌重叠
                }
            }
        });
        
        // 设置按钮监听器
        btnPlayCards.setOnClickListener(v -> {
            Log.d(TAG, "点击出牌按钮");
            
            Player currentPlayer = gameController.getGame().getCurrentPlayer();
            if (currentPlayer.isHuman()) {
                List<Card> selectedCards = currentPlayer.getSelectedCards();
                Log.d(TAG, "当前选中 " + selectedCards.size() + " 张牌");
                for (Card card : selectedCards) {
                    Log.d(TAG, " - " + card.getDisplayName());
                }
            }
            
            gameController.playCards();
        });

        btnPass.setOnClickListener(v -> {
            Log.d(TAG, "点击不出按钮");
            gameController.pass();
        });
        
        btnQuit.setOnClickListener(v -> {
            Log.d(TAG, "点击退出按钮");
            confirmQuit();
        });
        
        // 根据游戏模式初始化
        int gameMode = getIntent().getIntExtra("game_mode", GameSetupActivity.MODE_SINGLE_PLAYER);
        
        if (gameMode == GameSetupActivity.MODE_SINGLE_PLAYER) {
            initSinglePlayerGame();
        } else if (getIntent().getBooleanExtra("is_bluetooth_host", false) || 
                   getIntent().getBooleanExtra("is_bluetooth_client", false)) {
            initBluetoothGame();
        } else {
            // 默认为单人游戏
            initSinglePlayerGame();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.disconnect();
        }
    }
    
    /**
     * 初始化单人游戏
     */
    private void initSinglePlayerGame() {
        String playerName = getIntent().getStringExtra("player_name");
        int aiCount = getIntent().getIntExtra("ai_count", 2);
        boolean advancedAI = getIntent().getBooleanExtra("advanced_ai", false);
        
        // 输出调试信息
        Log.d(TAG, "初始化单人游戏 - 玩家名: " + playerName + ", AI数量: " + aiCount + ", 高级AI: " + advancedAI);
        
        gameController = new GameController(this);
        gameController.createSinglePlayerGame(playerName, aiCount);
        
        // 先启动游戏，再确保手牌显示
        gameController.startGame();
        
        // 显示所有玩家信息
        Game game = gameController.getGame();
        if (game != null) {
            List<Player> players = game.getPlayers();
            Log.d(TAG, "游戏玩家数量: " + players.size());
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                Log.d(TAG, "玩家" + i + ": " + p.getName() + ", 是否AI: " + !p.isHuman() + ", 手牌数: " + p.getHand().size());
            }
        }
        
        // 确保初始化后手牌已正确显示
        for (Player player : gameController.getGame().getPlayers()) {
            if (player.isHuman()) {
                updatePlayerHand(player);
                break;
            }
        }
        
        // 显示谁持有方块3的提示
        int diamondThreePlayerIndex = gameController.getGame().findStartingPlayerIndex();
        Player playerWithDiamondThree = gameController.getGame().getPlayers().get(diamondThreePlayerIndex);
        if (playerWithDiamondThree != null) {
            Log.d(TAG, "持有方块3的玩家: " + playerWithDiamondThree.getName());
            if (playerWithDiamondThree.isHuman()) {
                Toast.makeText(this, "你持有方块3，请先出牌", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, playerWithDiamondThree.getName() + " 持有方块3，将先出牌", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "警告：没有找到持有方块3的玩家");
        }
    }
    
    /**
     * 初始化蓝牙游戏
     */
    private void initBluetoothGame() {
        String playerName = getIntent().getStringExtra("player_name");
        boolean isHost = getIntent().getBooleanExtra("is_bluetooth_host", false);
        boolean isClient = getIntent().getBooleanExtra("is_bluetooth_client", false);
        boolean hostReady = getIntent().getBooleanExtra("host_ready", false);
        
        // 显示诊断信息
        String deviceInfo = "";
        if (isClient) {
            String hostName = getIntent().getStringExtra("device_name");
            String hostAddr = getIntent().getStringExtra("device_address");
            deviceInfo = " 连接至: " + (hostName != null ? hostName : "未知") + 
                        " (" + (hostAddr != null ? hostAddr : "无地址") + ")";
        }
        
        Toast.makeText(this, "蓝牙: " + (isHost ? "主机模式" : "客户端模式") + deviceInfo, 
                     Toast.LENGTH_LONG).show();
        
        try {
            // 先完全初始化所有控制器和游戏对象，然后再初始化蓝牙网络
            
            Log.d(TAG, "初始化蓝牙游戏 - 开始初始化组件");
            
            // 1. 使用单例模式初始化蓝牙管理器
            bluetoothManager = BluetoothNetworkManager.getInstance(this);
            bluetoothManager.setConnectionListener(new NetworkConnectionListener());
            Log.d(TAG, "蓝牙管理器初始化成功");
            Toast.makeText(this, "蓝牙管理器: OK", Toast.LENGTH_SHORT).show();
            
            // 2. 使用单例模式初始化网络控制器
            networkController = NetworkController.getInstance(bluetoothManager, this);
            Log.d(TAG, "网络控制器初始化成功");
            Toast.makeText(this, "网控: OK", Toast.LENGTH_SHORT).show();
            
            // 3. 初始化游戏控制器
            gameController = new GameController(this, networkController);
            Log.d(TAG, "游戏控制器初始化成功");
            
            // 建立双向引用
            networkController.setGameController(gameController);
            gameController.setNetworkController(networkController);
            Log.d(TAG, "控制器关联设置完成");
            Toast.makeText(this, "游控: OK", Toast.LENGTH_SHORT).show();
            
            // 4. 创建游戏对象
            boolean gameCreated = gameController.createNetworkGame(playerName, isHost);
            if (!gameCreated) {
                Log.e(TAG, "游戏对象创建失败");
                Toast.makeText(this, "错误: 游戏创建失败", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Log.d(TAG, "游戏对象创建成功");
            Toast.makeText(this, "游戏对象: " + (gameCreated ? "OK" : "失败"), Toast.LENGTH_SHORT).show();
            
            // 确认所有对象已正确初始化
            if (networkController == null) {
                Log.e(TAG, "网络控制器为空");
                Toast.makeText(this, "错误: 网络控制器为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (gameController == null) {
                Log.e(TAG, "游戏控制器为空");
                Toast.makeText(this, "错误: 游戏控制器为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            if (gameController.getGame() == null) {
                Log.e(TAG, "游戏对象为空");
                Toast.makeText(this, "错误: 游戏对象为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 5. 最后初始化蓝牙网络连接 - 此时所有对象已准备好
            new Handler().postDelayed(() -> {
                Log.d(TAG, "延迟1秒后开始初始化蓝牙网络");
                // 再次确认所有组件正确初始化
                if (networkController != null && 
                    gameController != null && 
                    gameController.getGame() != null) {
                    
                    Log.d(TAG, "开始初始化网络连接...");
                    Toast.makeText(this, "蓝牙网络: 开始初始化", Toast.LENGTH_SHORT).show();
                    networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);
                    Log.d(TAG, "蓝牙网络初始化完成");
                    Toast.makeText(this, "蓝牙网络: 初始化完成", Toast.LENGTH_SHORT).show();
                    
                    // 确认网络控制器状态
                    Toast.makeText(this, "当前连接状态：" + networkController.getConnectionStatus(), 
                                 Toast.LENGTH_SHORT).show();
                    
                    // 继续游戏初始化流程
                    continueBluetoothGameInitialization(playerName, isHost, isClient);
                } else {
                    Log.e(TAG, "组件未准备好，无法初始化网络");
                    Toast.makeText(this, "错误: 组件未准备好", Toast.LENGTH_LONG).show();
                }
            }, 1000); // 确保有足够延迟
        
        } catch (Exception e) {
            Log.e(TAG, "初始化蓝牙游戏时发生异常: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "初始化错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 继续蓝牙游戏初始化流程
     */
    private void continueBluetoothGameInitialization(String playerName, boolean isHost, boolean isClient) {
        try {
            Log.d(TAG, "继续蓝牙游戏初始化流程 - 玩家: " + playerName);
            Toast.makeText(this, "UI初始化...", Toast.LENGTH_SHORT).show();
            
            // 检查网络控制器是否存在
            if (networkController == null) {
                Log.e(TAG, "继续初始化失败 - 网络控制器为空");
                Toast.makeText(this, "错误: 网络控制器为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 检查游戏控制器是否存在
            if (gameController == null) {
                Log.e(TAG, "继续初始化失败 - 游戏控制器为空");
                Toast.makeText(this, "错误: 游戏控制器为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 检查游戏对象是否存在
            Game game = gameController.getGame();
            if (game == null) {
                Log.e(TAG, "继续初始化失败 - 游戏对象为空");
                Toast.makeText(this, "错误: 游戏对象为空", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 对于主机，强制显示和启用"开始游戏"按钮
            if (isHost) {
                // 主机需要等待客户端连接后才能开始游戏
                btnPlayCards.setEnabled(false);
                btnPass.setEnabled(false);
                // 显示开始游戏按钮
                btnStartGame.setVisibility(View.VISIBLE);
                btnStartGame.setEnabled(true); // 确保按钮可点击
                showMessage("等待其他玩家加入...");
                Toast.makeText(this, "请点击开始游戏按钮", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "主机模式: 已启用开始游戏按钮");
                
                // 检查当前连接状态
                NetworkManager.ConnectionStatus status = networkController.getConnectionStatus();
                Log.d(TAG, "主机当前连接状态: " + status);
                Toast.makeText(this, "主机当前连接状态: " + status, Toast.LENGTH_SHORT).show();
            } else if (isClient) {
                // 客户端额外确认初始化
                // 确保客户端能够接收来自主机的消息
                Log.d(TAG, "客户端: 等待接收主机消息");
                showMessage("等待主机开始游戏...");
                
                // 检查当前连接状态
                NetworkManager.ConnectionStatus status = networkController.getConnectionStatus();
                Log.d(TAG, "当前连接状态: " + status);
                Toast.makeText(this, "当前连接状态: " + status, Toast.LENGTH_SHORT).show();
                
                // 如果未连接，尝试重新初始化
                if (status != NetworkManager.ConnectionStatus.CONNECTED) {
                    Log.d(TAG, "连接状态不是CONNECTED，尝试重新初始化网络");
                    networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);
                    
                    // 延迟后再次检查
                    new Handler().postDelayed(() -> {
                        NetworkManager.ConnectionStatus newStatus = networkController.getConnectionStatus();
                        Log.d(TAG, "重新初始化后连接状态: " + newStatus);
                        Toast.makeText(this, "重新初始化后连接状态: " + newStatus, Toast.LENGTH_SHORT).show();
                    }, 1000);
                }
                
                // 尝试发送JOIN_GAME消息以确保加入成功，但减少发送频率
                new Handler().postDelayed(() -> {
                    boolean messageSent = networkController.sendJoinGameMessage(playerName);
                    Log.d(TAG, "延迟JOIN_GAME: " + (messageSent ? "成功" : "失败"));
                }, 1000);
                
                // 客户端需要等待主机开始游戏
                btnPlayCards.setEnabled(false);
                btnPass.setEnabled(false);
                showMessage("等待主机开始游戏...");
            }
        } catch (Exception e) {
            Log.e(TAG, "继续初始化过程中发生异常: " + e.getMessage());
            Toast.makeText(this, "错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
            for (StackTraceElement element : e.getStackTrace()) {
                Log.e(TAG, "\t at " + element.toString());
            }
            e.printStackTrace();
        }
    }
    
    /**
     * 确认退出游戏
     */
    private void confirmQuit() {
        new AlertDialog.Builder(this)
            .setTitle("退出游戏")
            .setMessage("确定要退出游戏吗？")
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 更新按钮状态
     */
    private void updateButtonStates(Game game) {
        Player currentPlayer = game.getCurrentPlayer();
        Player lastPlayer = game.getLastPlayer();
        CardPattern lastPattern = game.getLastPlayedPattern();
        int lastPlayerIndex = -1;
        
        // 获取上一个出牌玩家的索引
        if (lastPlayer != null) {
            for (int i = 0; i < game.getPlayers().size(); i++) {
                if (game.getPlayers().get(i) == lastPlayer) {
                    lastPlayerIndex = i;
                    break;
                }
            }
        }
        
        // 获取当前玩家索引
        int currentPlayerIndex = game.getCurrentPlayerIndex();
        
        Log.d(TAG, "\n==== 更新按钮状态 ====");
        Log.d(TAG, "当前玩家: " + (currentPlayer != null ? currentPlayer.getName() : "无") + 
                         " (索引: " + currentPlayerIndex + ")");
        Log.d(TAG, "上一玩家: " + (lastPlayer != null ? lastPlayer.getName() : "无") + 
                         " (索引: " + lastPlayerIndex + ")");
        Log.d(TAG, "上一牌型: " + (lastPattern != null ? patternToString(lastPattern) : "无"));
        
        // 启用/禁用按钮
        boolean isHumanTurn = currentPlayer != null && currentPlayer.isHuman() && game.getGameState() == Game.STATE_PLAYING;
        boolean playEnabled = isHumanTurn;
        
        // 判断是否可以不出：
        // 1. 必须是人类玩家回合
        // 2. 不是第一轮出牌（有上一手牌）
        // 修正: 不需要检查上一个出牌的玩家是不是当前玩家
        boolean isFirstRound = (lastPattern == null);
        
        // 只有非首轮才能不出
        boolean canPass = isHumanTurn && !isFirstRound;
        
        Log.d(TAG, "- 是否人类回合: " + isHumanTurn);
        Log.d(TAG, "- 是否首轮: " + isFirstRound);
        Log.d(TAG, "- 是否可以不出: " + canPass);
        
        btnPass.setEnabled(canPass);
        Log.d(TAG, "按钮状态: 出牌=" + btnPlayCards.isEnabled() + ", 不出=" + btnPass.isEnabled());
        
        // 显示当前状态提示
        if (isHumanTurn) {
            if (isFirstRound) {
                // 首轮出牌
                boolean hasDiamondThree = false;
                for (Card card : currentPlayer.getHand()) {
                    if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                        hasDiamondThree = true;
                        break;
                    }
                }
                
                if (hasDiamondThree) {
                    showMessage("你持有方块3，必须出含方块3的牌");
                } else {
                    // 首轮且没有方块3，不允许点击"出牌"
                    playEnabled = false;
                }
            } else {
                if (canPass) {
                    showMessage("轮到你出牌，你可以出牌或选择不出");
                } else {
                    showMessage("轮到你出牌，你必须出牌");
                }
            }
        }
        
        btnPlayCards.setEnabled(playEnabled);
    }
    
    /**
     * 更新其他玩家信息
     */
    private void updateOtherPlayersInfo(List<Player> players) {
        // 首先检查玩家数量，决定布局方式
        int playerCount = players.size();
        Log.d(TAG, "更新玩家信息 - 玩家数量: " + playerCount);
        
        // 清空所有玩家图标容器
        layoutTopPlayer.removeAllViews();
        layoutLeftPlayer.removeAllViews();
        layoutRightPlayer.removeAllViews();
        
        // 获取当前玩家的索引
        int currentPlayerIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).isHuman()) {
                currentPlayerIndex = i;
                break;
            }
        }
        
        if (currentPlayerIndex == -1) {
            Log.d(TAG, "警告：找不到人类玩家");
            return;
        }
        
        Log.d(TAG, "当前玩家索引: " + currentPlayerIndex);
        
        // 根据玩家数量确定布局
        if (playerCount == 3) {
            // 三角形布局: 上、左、右(当前玩家)
            // 上方玩家 (对面)
            addPlayerIcon(layoutTopPlayer, players.get((currentPlayerIndex + 1) % 3));
            // 左侧玩家
            addPlayerIcon(layoutLeftPlayer, players.get((currentPlayerIndex + 2) % 3));
        } else if (playerCount == 4) {
            // 四角形布局: 上、左、右(当前玩家)、下
            // 上方玩家 (对面)
            addPlayerIcon(layoutTopPlayer, players.get((currentPlayerIndex + 2) % 4));
            // 左侧玩家
            addPlayerIcon(layoutLeftPlayer, players.get((currentPlayerIndex + 1) % 4));
            // 右侧玩家
            addPlayerIcon(layoutRightPlayer, players.get((currentPlayerIndex + 3) % 4));
        } else if (playerCount == 2) {
            // 对战布局: 一个人类玩家 + 一个对手 (显示在上方)
            addPlayerIcon(layoutTopPlayer, players.get((currentPlayerIndex + 1) % 2));
        }
        
        Log.d(TAG, "玩家图标布局更新完成");
    }
    
    /**
     * 添加玩家图标到布局
     */
    private void addPlayerIcon(ViewGroup container, Player player) {
        View playerIcon = getLayoutInflater().inflate(R.layout.item_player_icon, container, false);
        TextView tvPlayerName = playerIcon.findViewById(R.id.tv_player_name);
        TextView tvCardCount = playerIcon.findViewById(R.id.tv_card_count);
        
        // 设置玩家信息
        tvPlayerName.setText(player.getName());
        int cardCount = player.getCardCount();
        tvCardCount.setText(cardCount + " 张牌");
        
        // 检查玩家是否持有方块3
        boolean hasDiamondThree = false;
        for (Card card : player.getHand()) {
            if (card.getSuit() == Card.DIAMOND && card.getRank() == Card.THREE) {
                hasDiamondThree = true;
                break;
            }
        }
        
        // 如果玩家持有方块3，改变图标背景
        if (hasDiamondThree) {
            playerIcon.findViewById(R.id.player_icon_card).setBackgroundResource(R.drawable.diamond_three_holder_background);
        }
        
        // 添加到容器
        container.addView(playerIcon);
        
        Log.d(TAG, "添加玩家图标: " + player.getName() + ", 手牌: " + player.getCardCount() + 
                          ", 持有方块3: " + hasDiamondThree);
    }
    
    // GameView接口实现
    @Override
    public void updateGameState(Game game) {
        if (game == null) {
            Log.e(TAG, "updateGameState: game对象为空");
            return;
        }
        
        runOnUiThread(() -> {
            try {
                Log.d(TAG, "开始更新游戏状态UI (游戏状态: " + game.getGameState() + ")");
                
                // 确认游戏状态显示
                String gameStateText = "等待中";
                switch (game.getGameState()) {
                    case Game.STATE_WAITING:
                        gameStateText = "等待开始";
                        break;
                    case Game.STATE_DEALING:
                        gameStateText = "发牌中";
                        break;
                    case Game.STATE_PLAYING:
                        gameStateText = "游戏中";
                        break;
                    case Game.STATE_GAME_OVER:
                        gameStateText = "游戏结束";
                        break;
                }
                tvGameInfo.setText("游戏状态: " + gameStateText);
                
                // 记录当前玩家名称
                Player currentPlayer = game.getCurrentPlayer();
                String currentPlayerName = currentPlayer != null ? currentPlayer.getName() : "无";
                tvCurrentPlayer.setText("当前玩家: " + currentPlayerName);
                
                // 记录上一个出牌玩家
                Player lastPlayer = game.getLastPlayer();
                String lastPlayerName = lastPlayer != null ? lastPlayer.getName() : "无";
                tvLastPlayer.setText("上一出牌: " + lastPlayerName);
                
                // 更新所有玩家信息
                updateOtherPlayersInfo(game.getPlayers());
                
                // 更新最后出的牌
                updateLastPlayedCards(game);
                
                // 更新按钮状态
                updateButtonStates(game);
                
                // 游戏状态改变为PLAYING，处理开始游戏后的显示
                if (game.getGameState() == Game.STATE_PLAYING) {
                    // 游戏开始了，隐藏开始游戏按钮
                    btnStartGame.setVisibility(View.GONE);
                    
                    // 尝试立即显示当前玩家的牌
                    if (currentPlayer != null && currentPlayer.isHuman()) {
                        // 强制更新手牌 - 修复卡死问题
                        Log.d(TAG, "游戏已开始，强制更新人类玩家手牌");
                        updatePlayerHand(currentPlayer);
                        
                        // 输出调试信息
                        Log.d(TAG, "当前人类玩家手牌: " + currentPlayer.getHand().size() + "张");
                        for (Card card : currentPlayer.getHand()) {
                            Log.d(TAG, "- " + card.getDisplayName());
                        }
                    }
                    
                    // 遍历所有玩家，确保手牌正确显示
                    for (Player player : game.getPlayers()) {
                        if (player.isHuman() && !player.getHand().isEmpty()) {
                            // 如果适配器没有内容但手牌有内容，强制更新
                            if (cardAdapter.getItemCount() == 0) {
                                Log.d(TAG, "检测到手牌未显示，强制更新玩家 " + player.getName() + " 的手牌");
                                cardAdapter.updateCards(new ArrayList<>(player.getHand()));
                                cardAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 记录异常详情
                Log.e(TAG, "更新游戏状态时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 更新上一次出的牌
     */
    private void updateLastPlayedCards(Game game) {
        Player lastPlayer = game.getLastPlayer();
        CardPattern lastPattern = game.getLastPlayedPattern();
        
        if (lastPattern != null && lastPlayer != null) {
            // 显示上一个玩家和牌型信息
            String patternDesc = patternToString(lastPattern);
            Log.d(TAG, "显示上次出牌: " + lastPlayer.getName() + " - " + patternDesc);
            
            // 为单牌添加更详细的描述
            if (lastPattern.getPatternType() == CardPattern.SINGLE && lastPattern.getCards() != null && !lastPattern.getCards().isEmpty()) {
                Card singleCard = lastPattern.getCards().get(0);
                String cardName = singleCard.getDisplayName();
                tvLastPlayedCards.setText(lastPlayer.getName() + " 出牌: 单牌 " + cardName);
            } else {
                tvLastPlayedCards.setText(lastPlayer.getName() + " 出牌: " + patternDesc);
            }
            
            tvLastPlayer.setText(lastPlayer.getName() + " 出牌");
            
            // 更新显示的牌
            List<Card> displayCards = lastPattern.getCards();
            if (displayCards != null && !displayCards.isEmpty()) {
                rvLastPlayedCards.setVisibility(View.VISIBLE);
                lastPlayedCardsAdapter.updateCards(new ArrayList<>(displayCards));
                lastPlayedCardsAdapter.notifyDataSetChanged();
            } else {
                rvLastPlayedCards.setVisibility(View.GONE);
            }
        } else {
            // 没有上一次出牌记录，或者上一个玩家选择不出
            if (lastPlayer != null && lastPattern == null) {
                // 上一个玩家选择不出
                tvLastPlayedCards.setText(lastPlayer.getName() + " 选择不出");
                tvLastPlayer.setText(lastPlayer.getName() + " 不出");
            } else {
                // 没有出牌记录
                tvLastPlayedCards.setText("没有出牌记录");
                tvLastPlayer.setText("");
            }
            rvLastPlayedCards.setVisibility(View.GONE);
        }
    }
    
    @Override
    public void updatePlayerHand(Player player) {
        if (player == null) {
            // 特殊情况，更新所有人类玩家手牌
            Player humanPlayer = null;
            
            if (gameController.getGame() != null) {
                for (Player p : gameController.getGame().getPlayers()) {
                    if (p.isHuman()) {
                        humanPlayer = p;
                        break;
                    }
                }
            }
            
            if (humanPlayer != null) {
                updatePlayerHand(humanPlayer);
            } else {
                Log.e(TAG, "updatePlayerHand: 未找到人类玩家");
            }
            
            return;
        }
        
        // 只处理人类玩家
        if (!player.isHuman()) {
            return;
        }
        
        // 在UI线程中更新
        runOnUiThread(() -> {
            try {
                List<Card> cards = player.getHand();
                
                // 记录当前的手牌状态
                Log.d(TAG, "更新玩家" + player.getName() + "的手牌: " + cards.size() + "张");
                
                // 更新适配器
                cardAdapter.updateCards(cards);
                
                // 检查更新后的适配器内容
                int displayedCount = cardAdapter.getItemCount();
                if (displayedCount != cards.size()) {
                    Log.e(TAG, "警告: 显示的卡牌数量(" + displayedCount + 
                          ")与实际手牌数量(" + cards.size() + ")不匹配");
                    
                    // 尝试强制再更新一次
                    new Handler().postDelayed(() -> {
                        cardAdapter.updateCards(new ArrayList<>(cards));
                        Log.d(TAG, "重试更新后手牌显示: " + cardAdapter.getItemCount() + "张");
                    }, 500);
                }
            } catch (Exception e) {
                Log.e(TAG, "更新玩家手牌时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    @Override
    public void showMessage(String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(GameActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void showGameResult(Player winner) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(GameActivity.this)
                    .setTitle("游戏结束")
                    .setMessage(winner.getName() + " 获胜！")
                    .setPositiveButton("再来一局", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            gameController.restartGame();
                        }
                    })
                    .setNegativeButton("返回菜单", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            }
        });
    }
    
    /**
     * 将牌型转换为字符串描述
     */
    private String patternToString(CardPattern pattern) {
        if (pattern == null) {
            return "无";
        }

        switch (pattern.getPatternType()) {
            case CardPattern.SINGLE:
                return "单张";
            case CardPattern.PAIR:
                return "对子";
            case CardPattern.THREE_OF_A_KIND:
                return "三条";
            case CardPattern.THREE_WITH_ONE:
                return "三带一";
            case CardPattern.THREE_WITH_PAIR:
                return "三带二";
            case CardPattern.STRAIGHT:
                return "顺子";
            case CardPattern.FLUSH_STRAIGHT:
                return "同花顺";
            case CardPattern.BOMB:
                return "炸弹";
            default:
                return "无效牌型";
        }
    }
    
    // NetworkView接口实现
    @Override
    public void updateConnectionStatus(NetworkManager.ConnectionStatus status) {
        runOnUiThread(() -> {
            String statusText = "连接状态: ";
            switch (status) {
                case DISCONNECTED:
                    statusText += "未连接";
                    break;
                case CONNECTING:
                    statusText += "连接中...";
                    break;
                case CONNECTED:
                    statusText += "已连接";
                    break;
            }
            tvGameInfo.setText(statusText);
            showMessage(statusText);
        });
    }
    
    /**
     * 蓝牙设备发现回调（该方法不会在GameActivity中使用）
     */
    @Override
    public void addDiscoveredDevice(NetworkManager.DeviceInfo device) {
        // 游戏界面不处理设备发现
    }
    
    /**
     * 网络连接监听器
     */
    private class NetworkConnectionListener implements NetworkManager.ConnectionListener {
        @Override
        public void onConnectionStatusChanged(NetworkManager.ConnectionStatus status) {
            updateConnectionStatus(status);
            
            if (status == NetworkManager.ConnectionStatus.CONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(GameActivity.this, "蓝牙连接已建立", Toast.LENGTH_SHORT).show();
                    showMessage("蓝牙连接已建立，等待游戏开始...");
                    
                    // 主机需要显示开始游戏按钮
                    if (getIntent().getBooleanExtra("is_bluetooth_host", false)) {
                        btnStartGame.setVisibility(View.VISIBLE);
                        showMessage("请点击\"开始游戏\"按钮启动游戏");
                    } else {
                        // 客户端显示等待消息
                        showMessage("等待主机开始游戏...");
                    }
                });
            } else if (status == NetworkManager.ConnectionStatus.DISCONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(GameActivity.this, "蓝牙连接已断开", Toast.LENGTH_SHORT).show();
                    showMessage("蓝牙连接已断开，游戏将终止");
                    
                    // 考虑返回上一界面
                    // 首先显示确认对话框
                    new AlertDialog.Builder(GameActivity.this)
                        .setTitle("连接已断开")
                        .setMessage("与其他玩家的连接已断开，是否返回主菜单?")
                        .setPositiveButton("返回", (dialog, which) -> {
                            finish();
                        })
                        .setNegativeButton("留在此页面", null)
                        .show();
                });
            }
        }
        
        @Override
        public void onDeviceDiscovered(NetworkManager.DeviceInfo device) {
            // 游戏界面不处理设备发现
        }
    }
    
    /**
     * 添加调试信息到调试区域
     * @param message 调试信息内容
     */
    private void addDebugMessage(String message) {
        // 添加时间戳
        String timeStamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String formattedMessage = "[" + timeStamp + "] " + message;
        
        // 限制日志长度，防止过长导致内存问题
        if (debugLog.length() > 10000) {
            debugLog.delete(0, debugLog.length() - 5000);
            debugLog.append("...[日志已截断]...\n");
        }
        
        debugLog.append(formattedMessage).append("\n");
        
        if (debugInfoEnabled) {
            runOnUiThread(() -> {
                tvDebugInfo.setText(debugLog.toString());
                // 滚动到底部
                svDebugInfo.post(() -> {
                    svDebugInfo.fullScroll(View.FOCUS_DOWN);
                });
            });
        }
    }
    
    @Override
    public void showDebugInfo(String tag, String message) {
        addDebugMessage("[" + tag + "] " + message);
        // 关键调试信息也通过Log输出，方便通过adb查看
        Log.d(tag, message);
    }
} 