package view;

import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
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

import com.bigtwo.game.R;

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
import models.AIStrategy;
import models.AdvancedAIStrategy;
import models.SmartAIStrategy;
import util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity implements GameView, NetworkView {

    private static final String TAG = "GameActivity";
    private GameController gameController;
    private NetworkController networkController;
    private BluetoothNetworkManager bluetoothManager;

    // UI Elements
    private TextView tvCurrentPlayer, tvLastPlayedCards, tvGameInfo, tvLastPlayer, tvDebugInfo;
    private FrameLayout layoutTopPlayer, layoutLeftPlayer, layoutRightPlayer;
    private RecyclerView rvPlayerCards, rvLastPlayedCards;
    private Button btnPlayCards, btnPass, btnQuit, btnStartGame;
    private CardAdapter cardAdapter, lastPlayedCardsAdapter;
    private ScrollView svDebugInfo;
    private boolean debugInfoEnabled = true;
    private StringBuilder debugLog = new StringBuilder();

    //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 初始化UI
        initializeUI();

        // 初始化游戏组件
        initializeGameComponents();

        // 设置"按钮点击"监听
        setButtonListeners();

        /*
         * 根据intent初始化游戏模式，默认单人模式
         * getIntent()：这是 Activity 类中的一个方法，用于获取启动当前 Activity 时所传递的 Intent 对象。
         * Intent 是一种消息对象，用于在不同的组件之间传递信息。
         * getIntExtra()：这是 Intent 类中的一个方法，用于获取指定键的值，并提供一个默认值。
         * "game_mode"：表示我们希望从 Intent 中提取的键名，用来查找传递的数据
         * GameSetupActivity.MODE_SINGLE_PLAYER：表示如果找不到"game_mode"键，或者它的值为空，则使用默认值
         * GameSetupActivity.MODE_SINGLE_PLAYER。
         */
        int gameMode = getIntent().getIntExtra("game_mode", GameSetupActivity.MODE_SINGLE_PLAYER);// 获取游戏模式
        if (gameMode == GameSetupActivity.MODE_SINGLE_PLAYER) {
            initSinglePlayerGame();
        } else {
            initBluetoothGame();
            // 如果是蓝牙房主，显示开始游戏按钮
            boolean isHost = getIntent().getBooleanExtra("is_bluetooth_host", false);
            if (isHost) {
                btnStartGame.setVisibility(View.VISIBLE);
                updateConnectionStatus(NetworkManager.ConnectionStatus.CONNECTING);
            }
        }
    }

    // -------------------------中间函数--------------------------------

    /*
     * 初始化UI元素
     * findViewById：这是 Activity 类中的一个方法，用于在当前活动的布局（layout）中查找指定 ID 的视图组件，并返回对应的 View
     * 对象
     * tv：textView
     * layout：layout
     * rv：recyclerView
     * btn：button
     * sv：scrollView
     */
    private void initializeUI() {
        tvCurrentPlayer = findViewById(R.id.tv_current_player);// 当前玩家
        tvLastPlayedCards = findViewById(R.id.tv_last_played_cards);// 最后打出的牌
        tvGameInfo = findViewById(R.id.tv_game_info);// 游戏信息
        tvLastPlayer = findViewById(R.id.tv_last_player);// 最后打牌玩家
        layoutTopPlayer = findViewById(R.id.layout_top_player);// 顶部玩家
        layoutLeftPlayer = findViewById(R.id.layout_left_player);// 左玩家
        layoutRightPlayer = findViewById(R.id.layout_right_player);// 右玩家
        rvPlayerCards = findViewById(R.id.rv_player_cards);// 玩家手牌
        rvLastPlayedCards = findViewById(R.id.rv_last_played_cards);// 最后打出的牌
        btnPlayCards = findViewById(R.id.btn_play_cards);// 出牌
        btnPass = findViewById(R.id.btn_pass);// pass
        btnQuit = findViewById(R.id.btn_quit);// 退出
        tvDebugInfo = findViewById(R.id.tv_debug_info);// 调试信息
        svDebugInfo = findViewById(R.id.sv_debug_info);// 调试信息滚动视图

        // 设置调试信息可见性切换
        svDebugInfo.setOnClickListener(v -> {
            debugInfoEnabled = !debugInfoEnabled;
            svDebugInfo.setVisibility(debugInfoEnabled ? View.VISIBLE : View.GONE);
        });
    }

    private void initializeGameComponents() {
        // 初始化游戏组件和控制器,this表示当前活动
        gameController = new GameController(this);
        // 确保蓝牙管理器在创建网络控制器之前初始化
        bluetoothManager = BluetoothNetworkManager.getInstance(this);
        networkController = NetworkController.getInstance(bluetoothManager, this);
        // 创建卡片适配器,目的是将卡片数据绑定到RecyclerView上，安卓的适配器是将数据与界面视图进行连接的组件，将数据源（如列表、数组、数据库查询结果等）转换成可以在界面上显示的视图元素，充当了数据源与视图之间的中介
        cardAdapter = new CardAdapter(new ArrayList<>(), this::handleCardSelection);
        lastPlayedCardsAdapter = new CardAdapter(new ArrayList<>(), null);

        // 设置RecyclerView与适配器
        setupRecyclerViews();
    }

    private void setupRecyclerViews() {
        LinearLayoutManager playerCardsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
                false);// 水平布局管理器
        rvPlayerCards.setLayoutManager(playerCardsLayoutManager);
        rvPlayerCards.setAdapter(cardAdapter);

        LinearLayoutManager lastPlayedLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
                false);// 水平布局管理器
        rvLastPlayedCards.setLayoutManager(lastPlayedLayoutManager);
        rvLastPlayedCards.setAdapter(lastPlayedCardsAdapter);

        // 添加卡片装饰器，用于重叠卡片显示
        rvPlayerCards.addItemDecoration(createItemDecoration());
        rvLastPlayedCards.addItemDecoration(createItemDecoration());
    }

    /*
     * 创建卡片装饰器
     * RecyclerView.ItemDecoration：这是 RecyclerView 类中的一个抽象类，用于在 RecyclerView 的每个
     * item 之间添加装饰效果，如边框、阴影、间距等。
     * getItemOffsets()：这是 RecyclerView.ItemDecoration 类中的一个方法，用于设置 item 的偏移量。
     * outRect：这是 RecyclerView.ItemDecoration 类中的一个参数，用于存储 item 的偏移量。
     * view：这是 RecyclerView.ItemDecoration 类中的一个参数，用于存储 item 的视图。
     * parent：这是 RecyclerView.ItemDecoration 类中的一个参数，用于存储 RecyclerView 的父视图。
     * state：这是 RecyclerView.ItemDecoration 类中的一个参数，用于存储 RecyclerView 的状态。
     */
    private RecyclerView.ItemDecoration createItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                if (parent.getChildAdapterPosition(view) > 0) {
                    outRect.left = -30; // 负左边距用于重叠
                }
            }
        };
    }

    /*
     * 设置按钮点击监听
     * setOnClickListener：这是 View 类中的一个方法，用于设置点击事件的监听器。
     * v：这是 View 类中的一个参数，用于存储点击事件的视图
     * btnPlayCards：这是 Button 类中的一个参数，用于存储出牌按钮
     * btnPass：这是 Button 类中的一个参数，用于存储pass按钮
     * btnQuit：这是 Button 类中的一个参数，用于存储退出按钮
     * btnStartGame：这是 Button 类中的一个参数，用于存储开始游戏按钮
     */
    private void setButtonListeners() {
        btnPlayCards.setOnClickListener(v -> {
            Player currentPlayer = gameController.getGame().getCurrentPlayer();
            if (currentPlayer.isHuman()) {
                playSelected();
            }
        });

        btnPass.setOnClickListener(v -> pass());
        btnQuit.setOnClickListener(v -> confirmQuit());

        btnStartGame = new Button(this);// 创建开始游戏按钮
        btnStartGame.setText("Start Game");
        btnStartGame.setTextSize(14);
        btnStartGame.setPadding(8, 8, 8, 8);
        btnStartGame.setBackgroundResource(R.drawable.button_background);

        LinearLayout buttonLayout = findViewById(R.id.layout_buttons);
        buttonLayout.addView(btnStartGame, 0);// 将新创建的按钮 btnStartGame 添加到 LinearLayout 中。0 表示按钮将被添加到布局的第一位置（即最前面）
        btnStartGame.setVisibility(View.GONE);// 设置按钮不可见
        btnStartGame.setOnClickListener(v -> startGame());// 设置按钮点击事件
    }

    private void handleCardSelection(int position) {
        Player currentPlayer = gameController.getGame().getCurrentPlayer();
        if (currentPlayer == null || !currentPlayer.isHuman()) {
            Toast.makeText(this, "Not your turn", Toast.LENGTH_SHORT).show();
            return;
        }

        if (gameController.getGame().getGameState() == Game.STATE_PLAYING) {
            selectCard(position);
        }
    }

    private void selectCard(int position) {
        Player currentPlayer = gameController.getGame().getCurrentPlayer();
        if (currentPlayer != null && currentPlayer.isHuman()) {
            currentPlayer.toggleCardSelection(position);
            updatePlayerHand(currentPlayer);
        }
    }

    /*
     * 开始游戏
     * e.printStackTrace()：这是 Exception 类中的一个方法，用于打印异常的堆栈跟踪信息。
     */
    private void startGame() {
        try {
            gameController.startGame();
            Toast.makeText(this, "Game started successfully", Toast.LENGTH_SHORT).show();
            btnStartGame.setVisibility(View.GONE);
        } catch (Exception e) {
            handleGameStartError(e);
        }
    }

    private void handleGameStartError(Exception e) {
        String errorMsg = "Error starting game: " + e.getMessage();
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        Log.e(TAG, errorMsg);
        e.printStackTrace();
    }

    private void initSinglePlayerGame() {
        String playerName = getIntent().getStringExtra("player_name");
        int aiCount = getIntent().getIntExtra("ai_count", 2);
        boolean advancedAI = getIntent().getBooleanExtra("advanced_ai", false);

        AIStrategy aiStrategy = advancedAI ? new AdvancedAIStrategy() : new SmartAIStrategy();
        gameController.createSinglePlayerGame(playerName, aiCount, aiStrategy);
        gameController.startGame();

        Game game = gameController.getGame();
        if (game != null) {
            updatePlayerHand(game.getCurrentPlayer());
        }
    }

    private void initBluetoothGame() {
        // 初始化蓝牙游戏组件
        try {
            bluetoothManager = BluetoothNetworkManager.getInstance(this);
            bluetoothManager.setConnectionListener(new NetworkConnectionListener());
            networkController = NetworkController.getInstance(bluetoothManager, this);
            gameController = new GameController(this, networkController);
            networkController.setGameController(gameController);

            // 初始化蓝牙游戏和网络设置
            networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);

            // 创建网络游戏模型
            String playerName = getIntent().getStringExtra("player_name");// 获取玩家名称
            boolean isHost = getIntent().getBooleanExtra("is_bluetooth_host", false);// 获取是否是蓝牙房主
            gameController.createNetworkGame(playerName, isHost);// 创建网络游戏
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Bluetooth game: " + e.getMessage());
            Toast.makeText(this, "Error initializing Bluetooth game: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmQuit() {
        new AlertDialog.Builder(this)
                .setTitle("Quit Game")
                .setMessage("Are you sure you want to quit the game?")
                .setPositiveButton("Yes", (dialog, which) -> finish())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void updateGameState(Game game) {
        if (game == null)
            return;

        // Update game UI using AppExecutors
        AppExecutors.getInstance().main().execute(() -> updateGameUI(game));
    }

    private void updateGameUI(Game game) {
        tvGameInfo.setText("Game State: " + game.getGameState());
        Player current = game.getCurrentPlayer();
        if (current != null) {
            tvCurrentPlayer.setText("Current Player: " + current.getName());
        } else {
            tvCurrentPlayer.setText("Current Player: -");
        }

        // 根据是否轮到本地玩家启用/禁用操作按钮
        boolean myTurn = current != null && current.isHuman();
        btnPlayCards.setEnabled(myTurn);
        btnPass.setEnabled(myTurn);

        Player last = game.getLastPlayer();
        if (last != null) {
            tvLastPlayer.setText("Last Player: " + last.getName());
        } else {
            tvLastPlayer.setText("Last Player: -");
        }

        // 刷新除本地玩家外的其他玩家（AI 或远程玩家）图标
        updateOtherPlayerIcons(game);

        // 如果是房主，根据玩家数量控制开始游戏按钮状态
        if (btnStartGame.getVisibility() == View.VISIBLE) {
            boolean enoughPlayers = game.getPlayers().size() >= 2 &&
                    !networkController.hasPendingJoins();
            btnStartGame.setEnabled(enoughPlayers);
            btnStartGame.setText(enoughPlayers ? "开始游戏" : "等待玩家加入...");
        }

        updatePlayerHand(current);
    }

    /**
     * 根据 AI 数量在三角形顶点放置玩家信息。
     * 规则：
     * 1 个 AI -> 顶部
     * 2 个 AI -> 左 + 右
     * 3 个 AI -> 顶部 + 左 + 右
     */
    private void updateOtherPlayerIcons(Game game) {
        if (game == null)
            return;

        // 清空旧视图
        layoutTopPlayer.removeAllViews();
        layoutLeftPlayer.removeAllViews();
        layoutRightPlayer.removeAllViews();

        // 本地玩家
        Player localHuman = null;
        for (Player p : game.getPlayers())
            if (p.isHuman()) {
                localHuman = p;
                break;
            }

        List<Player> others = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            if (p != localHuman)
                others.add(p);
        }

        int count = others.size();

        if (count == 0)
            return;

        // Helper lambda to create icon
        java.util.function.BiConsumer<Player, FrameLayout> addIcon = (player, container) -> {
            View icon = getLayoutInflater().inflate(R.layout.item_player_icon, container, false);
            TextView tvName = icon.findViewById(R.id.tv_player_name);
            TextView tvCnt = icon.findViewById(R.id.tv_card_count);
            tvName.setText(player.getName());
            tvCnt.setText(player.getHand().size() + "张");
            container.addView(icon);
        };

        switch (count) {
            case 1:
                addIcon.accept(others.get(0), layoutTopPlayer);
                break;
            case 2:
                addIcon.accept(others.get(0), layoutLeftPlayer);
                addIcon.accept(others.get(1), layoutRightPlayer);
                break;
            default: // 3 或更多，仅取前三
                addIcon.accept(others.get(0), layoutTopPlayer);
                addIcon.accept(others.get(1), layoutLeftPlayer);
                addIcon.accept(others.get(2), layoutRightPlayer);
                break;
        }
    }

    @Override
    public void updatePlayerHand(Player player) {
        if (player == null)
            return;
        if (!player.isHuman())
            return;

        AppExecutors.getInstance().main().execute(() -> cardAdapter.updateCards(player.getHand()));
    }

    @Override
    public void showMessage(String message) {
        AppExecutors.getInstance().main().execute(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showGameResult(Player winner) {
        AppExecutors.getInstance().main().execute(() -> {
            new AlertDialog.Builder(GameActivity.this)
                    .setTitle("Game Over")
                    .setMessage(winner.getName() + " Wins!")
                    .setPositiveButton("Play Again", (dialog, which) -> restartGame())
                    .setNegativeButton("Exit", (dialog, which) -> finish())
                    .show();
        });
    }

    /*
     * 重新开始游戏
     * gameController：这是 GameController 类中的一个参数，用于存储游戏控制器对象。
     * gameController.getGame()：这是 GameController 类中的一个方法，用于获取游戏对象。
     * gameController.getGame().resetGameState()：这是 Game 类中的一个方法，用于重置游戏状态。
     * gameController.startGame()：这是 GameController 类中的一个方法，用于重新开始游戏。
     */
    private void restartGame() {
        if (gameController != null) {
            if (gameController.getGame() != null) {
                gameController.getGame().resetGameState();
                gameController.startGame();
            } else {
                int gameMode = getIntent().getIntExtra("game_mode", GameSetupActivity.MODE_SINGLE_PLAYER);
                if (gameMode == GameSetupActivity.MODE_SINGLE_PLAYER) {
                    initSinglePlayerGame();
                } else {
                    initBluetoothGame();
                }
            }
        }
    }

    @Override
    public void updateConnectionStatus(NetworkManager.ConnectionStatus status) {
        AppExecutors.getInstance().main().execute(() -> {
            tvGameInfo.setText("Connection Status: " + status);
        });
    }

    @Override
    public void addDiscoveredDevice(NetworkManager.DeviceInfo device) {
        // Implementation for adding discovered devices
        AppExecutors.getInstance().main().execute(() -> {
            addDebugMessage("Device discovered: " + device.getName());
            // Additional implementation can be added based on the app requirements
        });
    }

    private class NetworkConnectionListener implements NetworkManager.ConnectionListener {
        @Override
        public void onConnectionStatusChanged(NetworkManager.ConnectionStatus status) {
            updateConnectionStatus(status);
        }

        @Override
        public void onDeviceDiscovered(NetworkManager.DeviceInfo device) {
            // Handle discovered devices
        }
    }

    private void addDebugMessage(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String formattedMessage = "[" + timestamp + "] " + message;

        if (debugLog.length() > 10000) {
            debugLog.delete(0, debugLog.length() - 5000);
            debugLog.append("...[Log truncated]...\n");
        }

        debugLog.append(formattedMessage).append("\n");

        if (debugInfoEnabled) {
            AppExecutors.getInstance().main().execute(() -> {
                tvDebugInfo.setText(debugLog.toString());
                svDebugInfo.post(() -> svDebugInfo.fullScroll(View.FOCUS_DOWN));
            });
        }
    }

    private void playSelected() {
        if (gameController != null && gameController.getGame() != null) {
            boolean success = gameController.getGame().playSelected();
            if (!success) {
                Toast.makeText(this, "Invalid play", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pass() {
        if (gameController == null || gameController.getGame() == null)
            return;
        Player cur = gameController.getGame().getCurrentPlayer();
        if (cur == null || !cur.isHuman()) {
            Toast.makeText(this, "Not your turn", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean success = gameController.getGame().pass();
        if (!success) {
            Toast.makeText(this, "Cannot pass now", Toast.LENGTH_SHORT).show();
        }
    }

    // --- 显示上一手牌 ---
    @Override
    public void updateLastPlayedCards(List<Card> cards, Player player) {
        if (cards == null)
            return;
        AppExecutors.getInstance().main().execute(() -> {
            lastPlayedCardsAdapter.updateCards(cards);
            if (player != null)
                tvLastPlayer.setText(player.getName());
        });
    }
}
