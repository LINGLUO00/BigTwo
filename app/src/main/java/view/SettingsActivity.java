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
import util.AppExecutors;
import models.SettingsManager;

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
    private SettingsManager settingsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 初始化SettingsManager并加载设置
        settingsManager = new SettingsManager(getSharedPreferences("game_settings", MODE_PRIVATE));
        
        // 初始化视图
        initViews();
        
        // 加载保存的设置
        loadSettings();
        
        // 设置动画速度Seekbar
        sbCardSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCardSpeedText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });
        
        // 保存设置按钮
        findViewById(R.id.btn_save).setOnClickListener(v -> saveSettingsAndFinish());
        
        // 重置设置按钮
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetSettings());
        
        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
    
    /**
     * 初始化视图组件
     */
    private void initViews() {
        sbCardSpeed = findViewById(R.id.sb_card_speed);
        tvCardSpeed = findViewById(R.id.tv_card_speed);
        cbSoundEffects = findViewById(R.id.cb_sound_effects);
        cbVibration = findViewById(R.id.cb_vibration);
        cbAutoSave = findViewById(R.id.cb_auto_save);
    }

    /**
     * 加载保存的设置
     */
    private void loadSettings() {
        int cardSpeed = settingsManager.getCardSpeed();
        boolean soundEffects = settingsManager.isSoundEffectsEnabled();
        boolean vibration = settingsManager.isVibrationEnabled();
        boolean autoSave = settingsManager.isAutoSaveEnabled();
        
        sbCardSpeed.setProgress(cardSpeed);
        updateCardSpeedText(cardSpeed);
        cbSoundEffects.setChecked(soundEffects);
        cbVibration.setChecked(vibration);
        cbAutoSave.setChecked(autoSave);
    }
    
    /**
     * 保存设置并退出
     */
    private void saveSettingsAndFinish() {
        settingsManager.saveSettings(
            sbCardSpeed.getProgress(),
            cbSoundEffects.isChecked(),
            cbVibration.isChecked(),
            cbAutoSave.isChecked()
        );
        finish();
    }

    /**
     * 重置设置为默认值
     */
    private void resetSettings() {
        settingsManager.resetSettings();
        loadSettings();  // 重新加载默认设置
    }
    
    /**
     * 更新动画速度文本
     */
    private void updateCardSpeedText(int progress) {
        String speedText = getSpeedText(progress);
        tvCardSpeed.setText("动画速度: " + speedText);
    }

    /**
     * 根据进度条值获取动画速度文本
     */
    private String getSpeedText(int progress) {
        if (progress < 25) return "慢";
        if (progress < 50) return "较慢";
        if (progress < 75) return "中等";
        if (progress < 90) return "较快";
        return "快";
    }
}
