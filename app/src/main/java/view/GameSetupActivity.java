package view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.bigtwo.game.R;

/**
 * 游戏设置活动，用于配置游戏参数
 */
public class GameSetupActivity extends AppCompatActivity {
    public static final int MODE_SINGLE_PLAYER = 1;
    public static final int MODE_NETWORK = 2;
    
    private int gameMode;
    private EditText etPlayerName;
    private RadioGroup rgAiDifficulty;
    private SeekBar sbAiCount;
    private TextView tvAiCount;
    private EditText[] etPlayerNames;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_setup);
        
        // 获取传入的游戏模式
        gameMode = getIntent().getIntExtra("game_mode", MODE_SINGLE_PLAYER);
        
        // 初始化视图
        etPlayerName = findViewById(R.id.et_player_name);
        rgAiDifficulty = findViewById(R.id.rg_ai_difficulty);
        sbAiCount = findViewById(R.id.sb_ai_count);
        tvAiCount = findViewById(R.id.tv_ai_count);
        
        // 根据游戏模式显示不同的设置选项
        View singlePlayerSettings = findViewById(R.id.layout_single_player_settings);
        View multiPlayerSettings = findViewById(R.id.layout_multi_player_settings);
        
        if (gameMode == MODE_SINGLE_PLAYER) {
            singlePlayerSettings.setVisibility(View.VISIBLE);
            multiPlayerSettings.setVisibility(View.GONE);
            
            // 配置AI数量选择器
            sbAiCount.setMax(3);
            sbAiCount.setProgress(2); // 默认2个AI
            tvAiCount.setText("AI对手数量: 2");
            
            sbAiCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    progress = Math.max(1, progress); // 至少1个AI对手
                    tvAiCount.setText("AI对手数量: " + progress);
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        } else {
            singlePlayerSettings.setVisibility(View.GONE);
            multiPlayerSettings.setVisibility(View.VISIBLE);
            
            // 初始化多玩家名称输入框
            etPlayerNames = new EditText[4];
            etPlayerNames[0] = findViewById(R.id.et_player_name_1);
            etPlayerNames[1] = findViewById(R.id.et_player_name_2);
            etPlayerNames[2] = findViewById(R.id.et_player_name_3);
            etPlayerNames[3] = findViewById(R.id.et_player_name_4);
        }
        
        // 开始游戏按钮
        Button btnStartGame = findViewById(R.id.btn_start_game);
        btnStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });
        
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    
    /**
     * 开始游戏
     */
    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        
        if (gameMode == MODE_SINGLE_PLAYER) {
            String playerName = etPlayerName.getText().toString().trim();
            if (playerName.isEmpty()) {
                Toast.makeText(this, "请输入玩家名称", Toast.LENGTH_SHORT).show();
                return;
            }
            
            int aiCount = Math.max(1, sbAiCount.getProgress());
            int aiDifficultyId = rgAiDifficulty.getCheckedRadioButtonId();
            boolean advancedAI = aiDifficultyId == R.id.rb_advanced;
            
            intent.putExtra("game_mode", MODE_SINGLE_PLAYER);
            intent.putExtra("player_name", playerName);
            intent.putExtra("ai_count", aiCount);
            intent.putExtra("advanced_ai", advancedAI);
        } else {
            // 收集玩家名称
            int playerCount = 0;
            String[] playerNames = new String[4];
            
            for (int i = 0; i < 4; i++) {
                String name = etPlayerNames[i].getText().toString().trim();
                if (!name.isEmpty()) {
                    playerNames[playerCount++] = name;
                }
            }
            
            if (playerCount < 2) {
                Toast.makeText(this, "至少需要2名玩家", Toast.LENGTH_SHORT).show();
                return;
            }
            
            intent.putExtra("game_mode", MODE_NETWORK);
            intent.putExtra("player_names", playerNames);
            intent.putExtra("player_count", playerCount);
        }
        
        startActivity(intent);
    }
} 