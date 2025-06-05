package view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.bigtwo.game.R;
import util.AppExecutors;
import models.AIStrategy;
import models.AdvancedAIStrategy;
import models.SmartAIStrategy;

/**
 * 游戏设置活动，用于配置游戏参数
 */
public class GameSetupActivity extends AppCompatActivity {
    public static final int MODE_SINGLE_PLAYER = 1;// 单人模式
    public static final int MODE_NETWORK = 2;// 网络模式

    private int gameMode;// 游戏模式
    private EditText etPlayerName;// 玩家名称
    private RadioGroup rgAiDifficulty;// AI难度
    private SeekBar sbAiCount;// AI数量,sb:seekBar
    private TextView tvAiCount;// AI数量文本,tv:textView
    private EditText[] etPlayerNames;// 玩家名称数组,et:editText

    // 使用AppExecutors来处理背景线程
    private AppExecutors appExecutors = AppExecutors.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_setup);

        // 获取传入的游戏模式
        gameMode = getIntent().getIntExtra("game_mode", MODE_SINGLE_PLAYER);

        // 初始化视图组件
        initViews();

        // 根据游戏模式显示不同的设置选项
        configureGameModeSettings();

        // 设置按钮点击事件
        setUpButtons();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        etPlayerName = findViewById(R.id.et_player_name);
        rgAiDifficulty = findViewById(R.id.rg_ai_difficulty);
        sbAiCount = findViewById(R.id.sb_ai_count);
        tvAiCount = findViewById(R.id.tv_ai_count);
    }

    /**
     * 根据游戏模式配置设置项
     */
    private void configureGameModeSettings() {
        View singlePlayerSettings = findViewById(R.id.layout_single_player_settings);
        View multiPlayerSettings = findViewById(R.id.layout_multi_player_settings);

        if (gameMode == MODE_SINGLE_PLAYER) {
            configureSinglePlayerSettings(singlePlayerSettings, multiPlayerSettings);
        } else {
            configureMultiPlayerSettings(singlePlayerSettings, multiPlayerSettings);
        }
    }

    /**
     * 配置单人模式设置
     *
     * @param singlePlayerSettings 单人模式设置项
     * @param multiPlayerSettings  多人模式设置项
     */
    private void configureSinglePlayerSettings(View singlePlayerSettings, View multiPlayerSettings) {
        singlePlayerSettings.setVisibility(View.VISIBLE);
        multiPlayerSettings.setVisibility(View.GONE);

        // 配置AI数量选择器
        sbAiCount.setMax(3);
        sbAiCount.setMin(2);
        sbAiCount.setProgress(2); // 默认2个AI
        tvAiCount.setText("AI对手数量: 2");

        sbAiCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = Math.max(2, progress); // 至少2个AI对手
                tvAiCount.setText("AI对手数量: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * 配置多人模式设置
     *
     * @param singlePlayerSettings 单人模式设置项
     * @param multiPlayerSettings  多人模式设置项
     */
    private void configureMultiPlayerSettings(View singlePlayerSettings, View multiPlayerSettings) {
        singlePlayerSettings.setVisibility(View.GONE);
        multiPlayerSettings.setVisibility(View.VISIBLE);

        // 初始化多玩家名称输入框
        etPlayerNames = new EditText[4];
        etPlayerNames[0] = findViewById(R.id.et_player_name_1);
        etPlayerNames[1] = findViewById(R.id.et_player_name_2);
        etPlayerNames[2] = findViewById(R.id.et_player_name_3);
        etPlayerNames[3] = findViewById(R.id.et_player_name_4);
    }

    /**
     * 设置按钮点击事件
     */
    private void setUpButtons() {
        Button btnStartGame = findViewById(R.id.btn_start_game);
        btnStartGame.setOnClickListener(v -> startGame());

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * 开始游戏
     */
    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);

        if (gameMode == MODE_SINGLE_PLAYER) {
            handleSinglePlayerGame(intent);
        } else {
            handleMultiPlayerGame(intent);
        }
    }

    /**
     * 处理单人游戏逻辑
     *
     * @param intent 游戏活动的Intent
     */
    private void handleSinglePlayerGame(Intent intent) {
        String playerName = etPlayerName.getText().toString().trim();
        if (playerName.isEmpty()) {
            showToast("请输入玩家名称");
            return;
        }

        int aiCount = Math.max(1, sbAiCount.getProgress());
        int aiDifficultyId = rgAiDifficulty.getCheckedRadioButtonId();
        boolean advancedAI = aiDifficultyId == R.id.rb_advanced;

        intent.putExtra("game_mode", MODE_SINGLE_PLAYER);
        intent.putExtra("player_name", playerName);
        intent.putExtra("ai_count", aiCount);
        intent.putExtra("advanced_ai", advancedAI);
        startActivity(intent);
    }

    /**
     * 处理多人游戏逻辑
     *
     * @param intent 游戏活动的Intent
     */
    private void handleMultiPlayerGame(Intent intent) {
        int playerCount = 0;
        String[] playerNames = new String[4];

        for (int i = 0; i < 4; i++) {
            String name = etPlayerNames[i].getText().toString().trim();
            if (!name.isEmpty()) {
                playerNames[playerCount++] = name;
            }
        }

        if (playerCount < 2) {
            showToast("至少需要2名玩家");
            return;
        }

        intent.putExtra("game_mode", MODE_NETWORK);
        intent.putExtra("player_names", playerNames);
        intent.putExtra("player_count", playerCount);
        startActivity(intent);
    }

    /**
     * 显示Toast信息
     *
     * @param message 要显示的消息
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // 在GameSetupActivity中添加
    private AIStrategy getSelectedAIStrategy() {
        RadioGroup rgAiDifficulty = findViewById(R.id.rg_ai_difficulty);
        int checkedId = rgAiDifficulty.getCheckedRadioButtonId();

        if (checkedId == R.id.rb_advanced) {
            return new AdvancedAIStrategy();
        } else {
            return new SmartAIStrategy();
        }
    }
}
