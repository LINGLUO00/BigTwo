package view;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.bigtwo.game.R;

/**
 * 设置活动，用于调整游戏设置
 */
public class SettingsActivity extends AppCompatActivity {
    
    private SharedPreferences preferences;
    private SeekBar sbCardSpeed;
    private TextView tvCardSpeed;
    private CheckBox cbSoundEffects;
    private CheckBox cbVibration;
    private CheckBox cbAutoSave;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 获取SharedPreferences
        preferences = getSharedPreferences("game_settings", MODE_PRIVATE);
        
        // 初始化视图
        sbCardSpeed = findViewById(R.id.sb_card_speed);
        tvCardSpeed = findViewById(R.id.tv_card_speed);
        cbSoundEffects = findViewById(R.id.cb_sound_effects);
        cbVibration = findViewById(R.id.cb_vibration);
        cbAutoSave = findViewById(R.id.cb_auto_save);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnReset = findViewById(R.id.btn_reset);
        Button btnBack = findViewById(R.id.btn_back);
        
        // 加载保存的设置
        loadSettings();
        
        // 设置动画速度Seekbar
        sbCardSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCardSpeedText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 保存设置按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                finish();
            }
        });
        
        // 重置设置按钮
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetSettings();
            }
        });
        
        // 返回按钮
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    
    /**
     * 加载保存的设置
     */
    private void loadSettings() {
        int cardSpeed = preferences.getInt("card_speed", 50);
        boolean soundEffects = preferences.getBoolean("sound_effects", true);
        boolean vibration = preferences.getBoolean("vibration", true);
        boolean autoSave = preferences.getBoolean("auto_save", true);
        
        sbCardSpeed.setProgress(cardSpeed);
        updateCardSpeedText(cardSpeed);
        cbSoundEffects.setChecked(soundEffects);
        cbVibration.setChecked(vibration);
        cbAutoSave.setChecked(autoSave);
    }
    
    /**
     * 保存设置
     */
    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        
        editor.putInt("card_speed", sbCardSpeed.getProgress());
        editor.putBoolean("sound_effects", cbSoundEffects.isChecked());
        editor.putBoolean("vibration", cbVibration.isChecked());
        editor.putBoolean("auto_save", cbAutoSave.isChecked());
        
        editor.apply();
    }
    
    /**
     * 重置设置为默认值
     */
    private void resetSettings() {
        sbCardSpeed.setProgress(50);
        updateCardSpeedText(50);
        cbSoundEffects.setChecked(true);
        cbVibration.setChecked(true);
        cbAutoSave.setChecked(true);
    }
    
    /**
     * 更新动画速度文本
     */
    private void updateCardSpeedText(int progress) {
        String speedText;
        
        if (progress < 25) {
            speedText = "慢";
        } else if (progress < 50) {
            speedText = "较慢";
        } else if (progress < 75) {
            speedText = "中等";
        } else if (progress < 90) {
            speedText = "较快";
        } else {
            speedText = "快";
        }
        
        tvCardSpeed.setText("动画速度: " + speedText);
    }
} 