package controller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Intent;

import models.NetworkManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 蓝牙网络管理器实现类
 */
public class BluetoothNetworkManager implements NetworkManager {
    private static final String TAG = "BluetoothNetworkManager";// 日志标签
    private static final String APP_NAME = "BigTwo";// 应用名称
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");// 应用UUID
    
    private static BluetoothNetworkManager instance;// 单例实例
    private final Context context;// 上下文
    private final BluetoothAdapter bluetoothAdapter;// 蓝牙适配器
    private ConnectionListener connectionListener;// 连接监听器
    private MessageListener messageListener;// 消息监听器
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;// 连接状态
    private final List<DeviceInfo> discoveredDevices = new ArrayList<>();// 发现的设备列表
    private final Handler mainHandler = new Handler(Looper.getMainLooper());// 主线程处理器

    // 线程控制
    private AcceptThread acceptThread;// 接受线程
    private ConnectThread connectThread;// 连接线程
    private ConnectedThread connectedThread;// 已连接线程

    // 构造函数
    private BluetoothNetworkManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // 获取单例实例
    public static synchronized BluetoothNetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothNetworkManager(context.getApplicationContext());
        }
        return instance;
    }

    // 在主线程运行
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    // 显示调试消息
    private void showDebugMessage(String message) {
        Log.d(TAG, message);
    }

    // 更新连接状态
    private void updateConnectionStatus(ConnectionStatus status) {
        if (this.connectionStatus != status) {
            this.connectionStatus = status;
            if (connectionListener != null) {
                runOnMainThread(() -> connectionListener.onConnectionStatusChanged(status));
            }
        }
    }

    // 初始化网络连接
    @Override
    public void initialize(ConnectionType type) {
        if (type != ConnectionType.BLUETOOTH) {
            Log.e(TAG, "不支持的连接类型: " + type);
            return;
        }

        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);// 设备发现  
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);// 发现结束
        context.registerReceiver(bluetoothReceiver, filter);// 注册接收器，接收蓝牙设备发现和发现结束的广播
    }

    // 开始设备搜索
    @Override
    public boolean startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "蓝牙未启用或适配器为空");
            return false;
        }

        try {
            stopDiscovery(); // 确保停止之前的搜索
            discoveredDevices.clear();// 清空发现的设备列表
            boolean success = bluetoothAdapter.startDiscovery();// 开始搜索
            Log.d(TAG, "开始设备搜索: " + (success ? "成功" : "失败"));
            return success;
        } catch (Exception e) {
            Log.e(TAG, "设备搜索启动失败", e);
            return false;
        }
    }

    // 停止设备搜索
    @Override
    public boolean stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "停止搜索设备");
            return true;
        }
        return false;
    }

    // 创建房间
    @Override
    public boolean createRoom(String roomName) {
        stopDiscovery();

        if (acceptThread != null) {
            acceptThread.cancel();
        }

        acceptThread = new AcceptThread();
        acceptThread.start();
        updateConnectionStatus(ConnectionStatus.CONNECTING);
        return true;
    }

    // 加入房间
    @Override
    public boolean joinRoom(String deviceAddress) {
        stopDiscovery();

        if (connectThread != null) {
            connectThread.cancel();
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        connectThread = new ConnectThread(device);
        connectThread.start();
        updateConnectionStatus(ConnectionStatus.CONNECTING);
        return true;
    }

    /* 发送消息
     * if (connectionStatus != ConnectionStatus.CONNECTED || connectedThread == null) {：如果连接状态不是连接，或者连接线程为空，则返回false。
     * type.ordinal()：获取消息类型的序号。
     * data：消息数据。
     * "\n"：换行符。
     * getBytes()：将字符串转换为字节数组。
     * connectedThread.write()：发送消息。
    */
    @Override
    public boolean sendMessage(MessageType type, String data) {
        if (connectionStatus != ConnectionStatus.CONNECTED || connectedThread == null) {
            Log.e(TAG, "未连接或连接线程为空");
            return false;
        }
        return connectedThread.write((type.ordinal() + "|" + data + "\n").getBytes());
    }

    // 断开连接
    @Override
    public void disconnect() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        updateConnectionStatus(ConnectionStatus.DISCONNECTED);
    }

    // 获取连接状态
    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    // 获取发现的设备列表
    @Override
    public List<DeviceInfo> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    // 设置连接监听器
    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    // 设置消息监听器
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    // 蓝牙广播接收器
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    DeviceInfo deviceInfo = new DeviceInfo(device.getName(), device.getAddress());
                    if (!discoveredDevices.contains(deviceInfo)) {
                        discoveredDevices.add(deviceInfo);
                        if (connectionListener != null) {
                            runOnMainThread(() -> connectionListener.onDeviceDiscovered(deviceInfo));
                        }
                    }
                }
            }
        }
    };

    // 接受线程
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;

        public AcceptThread() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "创建服务器套接字失败", e);
            }
        }

        @Override
        public void run() {
            BluetoothSocket socket;
            while (true) {
                try {
                    socket = serverSocket.accept();
                    if (socket != null) {
                        manageConnectedSocket(socket);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "等待客户端连接时出错", e);
                    break;
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭服务器套接字时出错", e);
            }
        }
    }

    // 连接线程
    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        private BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            try {
                socket = device.createRfcommSocketToServiceRecord(APP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "创建客户端套接字失败", e);
            }
        }

        @Override
        public void run() {
            try {
                socket.connect();
                manageConnectedSocket(socket);
            } catch (IOException e) {
                Log.e(TAG, "连接失败", e);
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭连接时出错", e);
            }
        }
    }

    // 已连接线程，负责处理与远程设备的通信
    private class ConnectedThread extends Thread {
        private BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "获取流失败", e);
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        int idx = message.indexOf('|');
                        if (idx > 0) {
                            try {
                                int typeOrdinal = Integer.parseInt(message.substring(0, idx));
                                String dataPart = message.substring(idx + 1);
                                // 移除可能的换行符
                                dataPart = dataPart.replaceAll("\\r?\\n", "");
                                messageListener.onMessageReceived(MessageType.values()[typeOrdinal], dataPart, socket.getRemoteDevice().getAddress());
                            } catch (Exception e) {
                                Log.e(TAG, "解析收到的消息失败", e);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "读取数据时出错", e);
                    break;
                }
            }
        }

        public boolean write(byte[] bytes) {
            try {
                outputStream.write(bytes);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "写入数据时出错", e);
                return false;
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭连接时出错", e);
            }
        }
    }

    // 管理已连接的套接字
    private void manageConnectedSocket(BluetoothSocket socket) {
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        updateConnectionStatus(ConnectionStatus.CONNECTED);
    }
}
