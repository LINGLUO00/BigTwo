package models;

import android.content.SharedPreferences;



/**
 * 管理设置的存储和加载
 */
public class SettingsManager {
    private SharedPreferences preferences;

    public SettingsManager(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public int getCardSpeed() {
        return preferences.getInt("card_speed", 50);
    }

    public boolean isSoundEffectsEnabled() {
        return preferences.getBoolean("sound_effects", true);
    }

    public boolean isVibrationEnabled() {
        return preferences.getBoolean("vibration", true);
    }

    public boolean isAutoSaveEnabled() {
        return preferences.getBoolean("auto_save", true);
    }

    public void saveSettings(int cardSpeed, boolean soundEffects, boolean vibration, boolean autoSave) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("card_speed", cardSpeed);
        editor.putBoolean("sound_effects", soundEffects);
        editor.putBoolean("vibration", vibration);
        editor.putBoolean("auto_save", autoSave);
        editor.apply();
    }

    public void resetSettings() {
        saveSettings(50, true, true, true);  // 重置为默认值
    }
}

