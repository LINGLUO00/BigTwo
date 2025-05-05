package view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import com.bigtwo.game.R;

/**
 * 主活动，作为应用的入口点，显示主菜单
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 单人游戏按钮
        Button btnSinglePlayer = findViewById(R.id.btn_single_player);
        btnSinglePlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, GameSetupActivity.class);
                intent.putExtra("game_mode", GameSetupActivity.MODE_SINGLE_PLAYER);
                startActivity(intent);
            }
        });

        // 蓝牙联机按钮
        Button btnBluetoothMultiPlayer = findViewById(R.id.btn_bluetooth_multi_player);
        btnBluetoothMultiPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, BluetoothSetupActivity.class);
                startActivity(intent);
            }
        });

        // 游戏规则按钮
        Button btnRules = findViewById(R.id.btn_rules);
        btnRules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RulesActivity.class);
                startActivity(intent);
            }
        });

        // 设置按钮
        Button btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }
} 