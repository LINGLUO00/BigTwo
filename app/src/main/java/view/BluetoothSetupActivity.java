package view;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.bigtwo.game.R;

import java.util.ArrayList;
import java.util.List;

import controller.BluetoothNetworkManager;
import controller.NetworkController;
import controller.NetworkView;
import models.NetworkManager;
import util.AppExecutors;

/**
 * 蓝牙设置活动，用于配置蓝牙联机
 */
public class BluetoothSetupActivity extends AppCompatActivity implements NetworkView {
    private static final String TAG = "BluetoothSetupActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    private EditText etPlayerName;
    private Button btnCreateGame;
    private Button btnScan;
    private Button btnBack;
    private ListView lvDevices;
    private TextView tvStatus;

    private NetworkController networkController;
    private BluetoothNetworkManager bluetoothManager;
    private List<NetworkManager.DeviceInfo> deviceList;
    private ArrayAdapter<String> deviceAdapter;

    // 客户端延迟发送JOIN_GAME相关字段
    private String pendingJoinPlayerName;
    private boolean joinSent;

    private final AppExecutors executors = AppExecutors.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_setup);

        // 初始化视图
        etPlayerName = findViewById(R.id.et_player_name);
        btnCreateGame = findViewById(R.id.btn_create_game);
        btnScan = findViewById(R.id.btn_scan);
        btnBack = findViewById(R.id.btn_back);
        lvDevices = findViewById(R.id.lv_devices);
        tvStatus = findViewById(R.id.tv_status);

        // 初始化设备列表
        deviceList = new ArrayList<>();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        lvDevices.setAdapter(deviceAdapter);

        // 设置监听器
        btnCreateGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createGame();
            }
        });

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanForDevices();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        lvDevices.setOnItemClickListener((parent, view, position, id) -> connectToDevice(position));

        // 检查蓝牙权限
        checkBluetoothPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothManager != null) {
            bluetoothManager.stopDiscovery();
            bluetoothManager.disconnect();
        }
    }

    /**
     * 检查蓝牙权限
     */
    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上需要BLUETOOTH_SCAN和BLUETOOTH_CONNECT权限
            String[] permissions = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };

            boolean needRequest = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }

            if (needRequest) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // Android 12以下需要ACCESS_FINE_LOCATION权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        // 权限已获取，初始化蓝牙
        initBluetooth();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                initBluetooth();
            } else {
                Toast.makeText(this, "没有蓝牙权限，无法使用蓝牙功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 初始化蓝牙
     */
    private void initBluetooth() {
        // 检查蓝牙是否支持
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 检查蓝牙是否开启
        if (!adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // 确保网络控制器已经初始化
            if (networkController == null) {
                // 尝试初始化蓝牙服务及网络控制器
                initBluetoothService();
            }

            if (networkController != null) {
                networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);
            } else {
                Log.e(TAG, "initBluetooth: 网络控制器依旧为null");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // 蓝牙已启用，确保网络控制器已初始化
                if (networkController != null) {
                    Log.d(TAG, "onActivityResult: 蓝牙已启用，初始化网络控制器");
                    networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);
                    Toast.makeText(this, "3BluetoothSetupActivity - onActivityResult - 网络控制器初始化成功", Toast.LENGTH_LONG)
                            .show();
                } else {
                    Log.e(TAG, "onActivityResult: 网络控制器未正确创建");
                    Toast.makeText(this, "错误: 网络控制器未正确创建", Toast.LENGTH_LONG).show();
                    // 尝试重新初始化蓝牙服务
                    initBluetoothService();
                    // 如果重新初始化成功，继续初始化网络控制器
                    if (networkController != null) {
                        networkController.initialize(NetworkManager.ConnectionType.BLUETOOTH);
                    }
                }
            } else {
                Toast.makeText(this, "蓝牙未启用，无法使用蓝牙功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 扫描蓝牙设备
     */
    private void scanForDevices() {
        String playerName = etPlayerName.getText().toString().trim();
        if (playerName.isEmpty()) {
            Toast.makeText(this, "请输入玩家名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示状态
        tvStatus.setText("正在扫描设备...");

        // 确保按钮状态
        btnScan.setEnabled(false);
        btnScan.setText("正在扫描...");

        // 清空设备列表
        deviceList.clear();
        deviceAdapter.clear();
        deviceAdapter.notifyDataSetChanged();

        Toast.makeText(this, "开始扫描设备...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "scanForDevices: 清空设备列表并开始扫描");

        // 开始扫描设备
        try {
            if (networkController != null) {
                Log.d(TAG, "scanForDevices: 调用networkController.startDiscovery()");
                networkController.startDiscovery();

                // 添加30秒超时，自动重新启用扫描按钮
                new Handler().postDelayed(() -> {
                    if (deviceList.isEmpty()) {
                        Log.d(TAG, "scanForDevices: 扫描超时，未找到设备");
                        tvStatus.setText("未找到设备");
                    } else {
                        Log.d(TAG, "scanForDevices: 扫描完成，找到 " + deviceList.size() + " 个设备");
                        tvStatus.setText("找到 " + deviceList.size() + " 个设备");
                    }

                    // 重新启用扫描按钮
                    btnScan.setEnabled(true);
                    btnScan.setText("重新扫描");

                    // 自动停止搜索
                    if (networkController != null) {
                        networkController.stopDiscovery();
                    }
                }, 30000); // 30秒超时

            } else {
                Log.e(TAG, "scanForDevices: 无法扫描，networkController为null");
                tvStatus.setText("扫描失败");
                btnScan.setEnabled(true);
                btnScan.setText("重新扫描");
            }
        } catch (Exception e) {
            Log.e(TAG, "scanForDevices: 扫描出错", e);
            e.printStackTrace();
            Toast.makeText(this, "扫描出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
            tvStatus.setText("扫描出错");
            btnScan.setEnabled(true);
            btnScan.setText("重新扫描");
        }
    }

    /**
     * 创建蓝牙游戏（作为主机）
     */
    private void createGame() {
        String playerName = etPlayerName.getText().toString().trim();
        if (playerName.isEmpty()) {
            Toast.makeText(this, "请输入玩家名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确保蓝牙已正确初始化
        if (bluetoothManager == null || networkController == null) {
            Log.e(TAG, "createGame: 蓝牙服务未正确初始化");
            return;
        }

        // 显示状态
        tvStatus.setText("正在创建房间...");

        // 调用网络控制器创建房间
        boolean roomCreated = networkController.createRoom(playerName + "_room");

        if (!roomCreated) {
            Log.e(TAG, "createGame: 创建房间失败");
            tvStatus.setText("创建房间失败");
            return;
        }

        tvStatus.setText("等待其他玩家连接...");

        // 添加延迟，确保网络状态更新
        new Handler().postDelayed(() -> {
            // 检查网络状态
            NetworkManager.ConnectionStatus status = networkController.getConnectionStatus();

            // 打开游戏活动
            Intent intent = new Intent(BluetoothSetupActivity.this, GameActivity.class);
            intent.putExtra("game_mode", GameSetupActivity.MODE_NETWORK);
            intent.putExtra("player_name", playerName);
            intent.putExtra("is_bluetooth_host", true);
            intent.putExtra("host_ready", true); // 添加标志，表示主机已准备好
            startActivity(intent);
        }, 1000); // 添加1秒延迟，确保网络初始化完成
    }

    /**
     * 发送加入游戏消息
     */
    private void sendJoinGameMessage(String playerName) {
        if (networkController == null) {
            Log.e(TAG, "sendJoinGameMessage: networkController 为 null，无法发送JOIN_GAME");
            return;
        }

        executors.io().execute(() -> {
            boolean success = networkController.sendMessage(NetworkManager.MessageType.JOIN_GAME, playerName);
            Log.d(TAG, "sendJoinGameMessage: 发送JOIN_GAME消息: " + (success ? "成功" : "失败") + ", 玩家名: " + playerName);
        });
    }

    /**
     * 连接到设备（作为客户端）
     */
    private void connectToDevice(int position) {
        if (position < 0 || position >= deviceList.size()) {
            return;
        }

        String playerName = etPlayerName.getText().toString().trim();
        if (playerName.isEmpty()) {
            Toast.makeText(this, "请输入玩家名称", Toast.LENGTH_SHORT).show();
            return;
        }

        NetworkManager.DeviceInfo deviceInfo = deviceList.get(position);
        tvStatus.setText("正在连接到 " + deviceInfo.getName() + "...");

        // 记录连接开始时间戳，便于调试
        final long startTime = System.currentTimeMillis();

        // 先尝试建立连接
        boolean joinResult = networkController.joinRoom(deviceInfo.getAddress());

        if (!joinResult) {
            Log.e(TAG, "connectToDevice: 连接失败");
            return;
        }

        // 等CONNECTED后再发送 JOIN_GAME
        pendingJoinPlayerName = playerName;
        joinSent = false;

        // 确保连接尝试后添加足够延迟，再打开游戏界面
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 连接后添加诊断信息
                long connTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "connectToDevice: 连接耗时: " + connTime + "ms，准备打开游戏界面");

                // 延迟打开游戏活动
                new Handler().postDelayed(() -> {
                    // 打开游戏活动
                    Intent intent = new Intent(BluetoothSetupActivity.this, GameActivity.class);
                    intent.putExtra("game_mode", GameSetupActivity.MODE_NETWORK);
                    intent.putExtra("player_name", playerName);
                    intent.putExtra("is_bluetooth_client", true);
                    intent.putExtra("device_name", deviceInfo.getName());
                    intent.putExtra("device_address", deviceInfo.getAddress());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }, 1000); // 额外延迟1秒，确保连接过程完成
            }
        }, 2000); // 增加到2秒延迟，给网络控制器更多时间初始化
    }

    // NetworkView接口实现
    @Override
    public void updateConnectionStatus(NetworkManager.ConnectionStatus status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case DISCONNECTED:
                        tvStatus.setText("未连接");
                        break;
                    case CONNECTING:
                        tvStatus.setText("正在连接...");
                        break;
                    case CONNECTED:
                        tvStatus.setText("已连接");

                        if (!joinSent && pendingJoinPlayerName != null) {
                            sendJoinGameMessage(pendingJoinPlayerName);
                            joinSent = true;
                        }
                        break;
                }
            }
        });
    }

    @Override
    public void addDiscoveredDevice(NetworkManager.DeviceInfo device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "addDiscoveredDevice: 尝试添加设备: " + device.getName() + " (" + device.getAddress() + ")");

                // 检查设备是否已存在
                boolean exists = false;
                for (NetworkManager.DeviceInfo existingDevice : deviceList) {
                    if (existingDevice.getAddress().equals(device.getAddress())) {
                        Log.d(TAG, "addDiscoveredDevice: 设备已存在，跳过");
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    Log.d(TAG, "addDiscoveredDevice: 添加新设备");
                    deviceList.add(device);
                    String displayName = device.getName() + " (" + device.getAddress() + ")";
                    deviceAdapter.add(displayName);
                    deviceAdapter.notifyDataSetChanged();

                    // 更新状态，显示找到的设备数量
                    tvStatus.setText("找到 " + deviceList.size() + " 个设备");

                    // 如果是第一个设备，重新启用按钮
                    if (deviceList.size() == 1) {
                        btnScan.setEnabled(true);
                        btnScan.setText("继续扫描");
                    }

                }
            }
        });
    }

    @Override
    public void showMessage(String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BluetoothSetupActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 初始化蓝牙服务
     */
    private void initBluetoothService() {
        // 初始化蓝牙管理器
        try {
            Log.d(TAG, "initBluetoothService: 开始初始化蓝牙服务");

            // 使用单例获取蓝牙管理器
            bluetoothManager = BluetoothNetworkManager.getInstance(this);
            Log.d(TAG, "initBluetoothService: 蓝牙管理器创建成功");

            // 初始化网络控制器 - 使用单例模式
            networkController = NetworkController.getInstance(bluetoothManager, this);
            Log.d(TAG, "initBluetoothService: 网络控制器创建成功");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "initBluetoothService: 初始化蓝牙服务失败: " + e.getMessage());
        }
    }
}