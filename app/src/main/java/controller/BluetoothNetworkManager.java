package controller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import models.NetworkManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//NetworkController：是高层控制器，负责协调网络通信与游戏逻辑之间的交互，不直接实现网络通信功能。
//BluetoothNetworkManager：是具体的网络管理器实现，专门负责蓝牙连接的建立、维护和数据传输细节。


/**
 * 蓝牙网络管理器实现类
 * @param connectionListener 监听蓝牙连接状态的变化
 * @param messageListener 处理通过已建立的连接接收到的游戏消息，如玩家加入消息,游戏开始消息,出牌消息,不出消息,聊天消息等
 * @param mainHandler 主线程(UI线程)的Handler,Handler是一个用于在线程之间传递和处理消息的机制. 蓝牙连接、数据发送和接收都是在后台线程中进行的，但处理这些事件的结果（如连接状态变化、接收到消息）通常需要更新UI或与UI相关的组件进行交互。
 *                     Android规定所有UI操作必须在主线程上执行（防止多线程同时访问UI组件导致的崩溃）。当蓝牙操作在后台线程完成时，它们不能直接修改UI，必须通过主线程来执行UI更新。
 *                     Handler通过Looper.getMainLooper()与主线程关联，允许将代码从后台线程发送到主线程执行。
 * 
 */
public class BluetoothNetworkManager implements NetworkManager {
    private static final String TAG = "BluetoothNetworkManager";
    private static final String APP_NAME = "BigTwo";
    // 使用标准的串行端口服务UUID
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int MAX_BUFFER_SIZE = 1024;
    
    // 单例实例
    private static BluetoothNetworkManager instance;
    
    // 防止JOIN消息循环的变量
    private long lastJoinMessageTime = 0;
    private static final long JOIN_MESSAGE_THROTTLE = 3000; // 3秒内不重复处理/发送JOIN消息
    private String lastJoinPlayerData = "";
    
    // 获取单例实例
    public static synchronized BluetoothNetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothNetworkManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private ConnectionListener connectionListener;
    private MessageListener messageListener;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private final List<DeviceInfo> discoveredDevices = new ArrayList<>();
    
    private AcceptThread acceptThread;//服务器线程，等待客户端连接
    private ConnectThread connectThread;//客户端线程，连接到服务器
    private ConnectedThread connectedThread;//连接线程，处理数据的发送和接收
    //Looper是Android系统中用于管理线程的消息队列。每个线程都有一个Looper。
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * 创建蓝牙网络管理器
     * @param context Android上下文
     */
    private BluetoothNetworkManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    
    /**
     * 用于在主线程执行任务的帮助方法，通过post方法将任务添加到主线程的消息队列中，等待执行
     * @param runnable 要在主线程执行的任务，Runnable是一个接口，表示一个可以执行的任务，它只有一个抽象方法run()
     */
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
    
    /**
     * 显示调试消息
     */
    private void showDebugMessage(String message) {
        Log.d(TAG, message);
    }
    
    /**
     * 更新连接状态并通知监听器（已注册的ConnectionListener）
     * runOnMainThread()方法将更新连接状态的任务添加到主线程的消息队列中，等待执行
     */
    private void updateConnectionStatus(ConnectionStatus status) {
        if (this.connectionStatus != status) {
            this.connectionStatus = status;
            if (connectionListener != null) {
                runOnMainThread(() -> connectionListener.onConnectionStatusChanged(status));
            }
        }
    }
    
    @Override
    public void initialize(ConnectionType type) {
        // 仅支持蓝牙类型
        if (type != ConnectionType.BLUETOOTH) {
            Log.e(TAG, "不支持的连接类型: " + type);
            return;
        }
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return;
        }
        
        // 注册蓝牙广播接收器，用于接收蓝牙设备发现和搜索完成的事件
        //IntentFilter是一个用于过滤Intent的类，用来声明我们要监听哪些广播事件
        IntentFilter filter = new IntentFilter();
        // 添加蓝牙设备发现事件
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        // 添加蓝牙搜索完成事件（搜索完成不等于找到了设备，可能是超时、取消或全部设备都已经发现）
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // 注册广播接收器，当有蓝牙设备发现或搜索完成时，会调用bluetoothReceiver的onReceive方法
        context.registerReceiver(bluetoothReceiver, filter);
    }
    
    /**
     * 开始搜索蓝牙设备
     * @return 是否成功启动搜索
     */
    @Override
    public boolean startDiscovery() {
        Log.d(TAG, "startDiscovery: 开始执行蓝牙设备搜索");
        
        try {
            // 确保蓝牙已启用
            if (bluetoothAdapter == null) {
                Log.e(TAG, "startDiscovery: 蓝牙适配器为空");
                return false;
            }
            
            if (!bluetoothAdapter.isEnabled()) {
                Log.e(TAG, "startDiscovery: 蓝牙未启用");
                return false;
            }
            
            // 先尝试停止之前的搜索
            stopDiscovery();
            
            // 确保我们有必要的权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "startDiscovery: 缺少BLUETOOTH_SCAN权限");
                    return false;
                }
                
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "startDiscovery: 缺少BLUETOOTH_CONNECT权限");
                    return false;
                }
            } else if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "startDiscovery: 缺少ACCESS_FINE_LOCATION权限");
                return false;
            }
            
            // 清空发现的设备列表
            discoveredDevices.clear();
            
            // 注册广播接收器
            try {
                // 确保使用正确的广播接收器
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                
                // 确保使用正确的bluetoothReceiver
                context.registerReceiver(bluetoothReceiver, filter);
                Log.d(TAG, "startDiscovery: 成功注册蓝牙设备搜索广播接收器");
            } catch (Exception e) {
                Log.e(TAG, "startDiscovery: 注册广播接收器失败", e);
            }
            
            // 开始搜索设备
            boolean success = bluetoothAdapter.startDiscovery();
            Log.d(TAG, "startDiscovery: 蓝牙搜索设备启动" + (success ? "成功" : "失败"));
            
            return success;
        } catch (Exception e) {
            Log.e(TAG, "startDiscovery: 开始搜索设备时出错", e);
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 停止搜索蓝牙设备
     * @return 是否成功停止搜索
     */
    @Override
    public boolean stopDiscovery() {
        Log.d(TAG, "stopDiscovery: 停止搜索");
        try {
            if (bluetoothAdapter == null) {
                Log.e(TAG, "stopDiscovery: 蓝牙适配器为空");
                return false;
            }
            
            // 检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "stopDiscovery: 缺少BLUETOOTH_SCAN权限");
                    return false;
                }
            }
            
            // 检查是否正在搜索
            if (bluetoothAdapter.isDiscovering()) {
                boolean success = bluetoothAdapter.cancelDiscovery();
                Log.d(TAG, "stopDiscovery: 取消搜索" + (success ? "成功" : "失败"));
                return success;
            } else {
                Log.d(TAG, "stopDiscovery: 蓝牙不在搜索状态，无需取消");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "stopDiscovery: 停止搜索时出错", e);
            return false;
        }
    }
    /**
     * 创建房间
     * @param roomName 房间名称
     * @return 是否成功创建房间
     */
    @Override
    public boolean createRoom(String roomName) {
        // 停止搜索蓝牙设备
        stopDiscovery();
        
        // 停止之前的接受客户端连接线程
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        
        try {
            // 创建服务器线程
            acceptThread = new AcceptThread();
            acceptThread.start();
            updateConnectionStatus(ConnectionStatus.CONNECTING);
            showDebugMessage("成功创建房间: " + roomName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "创建房间时出错: " + e.getMessage(), e);
            updateConnectionStatus(ConnectionStatus.DISCONNECTED);
            return false;
        }
    }
    
    @Override
    public boolean joinRoom(String deviceAddress) {
        // 停止搜索模式
        stopDiscovery();
        
        // 停止之前向主机设备发起连接的线程
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        
        try {
            // 获取远程设备
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device == null) {
                Log.e(TAG, "找不到设备: " + deviceAddress);
                return false;
            }
            
            // 创建客户端线程
            connectThread = new ConnectThread(device);
            connectThread.start();
            updateConnectionStatus(ConnectionStatus.CONNECTING);
            showDebugMessage("成功加入房间, 设备地址: " + deviceAddress);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "加入房间时出错: " + e.getMessage(), e);
            updateConnectionStatus(ConnectionStatus.DISCONNECTED);
            return false;
        }
    }
    
    @Override
    public boolean sendMessage(MessageType type, String data) {
        showDebugMessage("BluetoothNetworkManager.sendMessage()开始执行");
        // 检查是否已连接
        if (connectionStatus != ConnectionStatus.CONNECTED || connectedThread == null) {
            Log.e(TAG, "发送消息失败：未连接或连接线程为空");
            showDebugMessage("发送消息失败：未连接或连接线程为空");
            return false;
        }
        
        try {
            // 对于JOIN_GAME消息，防止频繁发送
            if (type == MessageType.JOIN_GAME) {
                if (data.equals(lastJoinPlayerData) && 
                    System.currentTimeMillis() - lastJoinMessageTime < JOIN_MESSAGE_THROTTLE) {
                    Log.d(TAG, "跳过频繁发送JOIN_GAME消息: " + data);
                    return true; // 返回成功，避免上层重试
                }
                
                // 更新时间戳和上次发送的玩家
                lastJoinMessageTime = System.currentTimeMillis();
                lastJoinPlayerData = data;
            }
            
            // 创建消息格式：消息类型|数据
            String message = type.ordinal() + "|" + data;
            // 确保消息结尾有分隔符
            if (!message.endsWith(ConnectedThread.MESSAGE_DELIMITER)) {
                message += ConnectedThread.MESSAGE_DELIMITER;
            }
            //将消息转换为字节数组，便于进行底层通信或存储操作
            byte[] messageBytes = message.getBytes();
            
            // 记录发送的消息
            Log.d(TAG, "发送消息: 类型=" + type + ", 数据='" + data + "'");
            showDebugMessage("尝试发送消息: 类型=" + type);
            
            // 确保输出流有效
            if (connectedThread.isOutputStreamValid()) {
                // 发送消息
                boolean success = connectedThread.write(messageBytes);
                if (success) {
                    showDebugMessage("消息发送成功: 类型=" + type);
                    
                    // 针对重要消息做额外确认
                    if (type == MessageType.GAME_START || 
                        (type == MessageType.JOIN_GAME && !data.equals(lastJoinPlayerData))) {
                        try {
                            // 增加延迟，确保先前消息已被处理
                            Thread.sleep(500);
                            success = connectedThread.write(messageBytes);
                            showDebugMessage("重要消息已发送第二次: " + type);
                            
                            // 对于GAME_START，发送第三次
                            if (type == MessageType.GAME_START) {
                                Thread.sleep(500);
                                success = connectedThread.write(messageBytes);
                                showDebugMessage("重要消息已发送第三次: " + type);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "重发消息出错: " + e.getMessage());
                        }
                    }
                } else {
                    showDebugMessage("消息发送失败: 类型=" + type);
                }
                
                return success;
            } else {
                Log.e(TAG, "输出流无效或已关闭");
                showDebugMessage("输出流无效或已关闭，发送失败");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "发送消息时出错: " + e.getMessage(), e);
            showDebugMessage("发送消息时出错: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void disconnect() {
        // 停止所有线程
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
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
    
    @Override
    public List<DeviceInfo> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
    
    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }
    
    /**
     * 处理设备发现的广播接收器
     */
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "bluetoothReceiver: 收到广播: " + action);
            
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 发现设备
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    
                    if (device == null) {
                        Log.e(TAG, "bluetoothReceiver: 发现的设备为null");
                        return;
                    }
                    
                    String deviceName = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            deviceName = device.getName();
                        } else {
                            Log.e(TAG, "bluetoothReceiver: 缺少BLUETOOTH_CONNECT权限");
                        }
                    } else {
                        deviceName = device.getName();
                    }
                    
                    // 设备名可能为null，使用地址作为备用名称
                    if (deviceName == null || deviceName.isEmpty()) {
                        deviceName = "未知设备";
                    }
                    
                    String deviceAddress = device.getAddress();
                    Log.d(TAG, "bluetoothReceiver: 发现设备: " + deviceName + " (" + deviceAddress + ")");
                    
                    // 创建设备信息对象
                    DeviceInfo deviceInfo = new DeviceInfo(deviceName, deviceAddress);
                    
                    // 避免重复添加
                    boolean exists = false;
                    for (DeviceInfo existing : discoveredDevices) {
                        if (existing.getAddress().equals(deviceInfo.getAddress())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        discoveredDevices.add(deviceInfo);
                        Log.d(TAG, "bluetoothReceiver: 添加设备到列表: " + deviceName);
                        
                        if (connectionListener != null) {
                            runOnMainThread(() -> {
                                connectionListener.onDeviceDiscovered(deviceInfo);
                            });
                        } else {
                            Log.e(TAG, "bluetoothReceiver: connectionListener为null");
                        }
                    } else {
                        Log.d(TAG, "bluetoothReceiver: 设备已存在: " + deviceName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "bluetoothReceiver: 处理ACTION_FOUND时出错", e);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 搜索完成
                Log.d(TAG, "bluetoothReceiver: 蓝牙设备搜索完成，发现了 " + discoveredDevices.size() + " 个设备");
                
                // 如果没有发现设备，可能尝试重新开始搜索
                if (discoveredDevices.isEmpty()) {
                    Log.d(TAG, "bluetoothReceiver: 未发现设备，考虑重试搜索");
                }
                
                try {
                    // 尝试解注册广播接收器，防止重复注册
                    context.unregisterReceiver(this);
                    Log.d(TAG, "bluetoothReceiver: 广播接收器已解注册");
                } catch (IllegalArgumentException e) {
                    // 接收器可能未注册
                    Log.e(TAG, "bluetoothReceiver: 解注册广播接收器失败，可能未注册", e);
                }
            }
        }
    };
    
    /**
     * 服务器线程，等待客户端连接
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;
        private boolean isRunning = true;
        private int connectionCount = 0;
        private static final int MAX_CONNECTIONS = 3; // 最多允许3个客户端连接（加上主机共4个玩家）
        
        public AcceptThread() {
            // 使用临时变量，在异常时赋值为null
            BluetoothServerSocket tmp = null;
            try {
                // 创建一个监听连接的服务器端套接字
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID);
                showDebugMessage("创建服务器套接字成功");
            } catch (IOException e) {
                Log.e(TAG, "创建服务器套接字失败", e);
            }
            serverSocket = tmp;
        }
        
        public void run() {
            BluetoothSocket socket = null;
            
            // 持续监听，直到线程被取消或达到最大连接数
            while (isRunning && connectionCount < MAX_CONNECTIONS) {
                try {
                    if (serverSocket == null) {
                        Log.e(TAG, "服务器套接字为null，无法接受连接");
                        isRunning = false;
                        break;
                    }
                    
                    showDebugMessage("等待客户端连接... (已连接 " + connectionCount + "/" + MAX_CONNECTIONS + ")");
                    socket = serverSocket.accept();
                    
                    // 如果有客户端连接
                    if (socket != null) {
                        // 增加连接计数
                        connectionCount++;
                        
                        // 建立连接后的处理
                        manageConnectedSocket(socket);
                        
                        // 检查是否达到最大连接数
                        if (connectionCount >= MAX_CONNECTIONS) {
                            showDebugMessage("已达到最大连接数 (" + MAX_CONNECTIONS + ")，停止接受新连接");
                            break;
                        }
                        
                        // 不要关闭服务器socket，继续接受新连接
                        socket = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "接受连接时出错: " + e.getMessage(), e);
                    
                    // 添加重试机制
                    if (isRunning && connectionCount < MAX_CONNECTIONS) {
                        showDebugMessage("5秒后重试接受连接...");
                        try {
                            Thread.sleep(5000);  // 等待5秒后重试
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            isRunning = false;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理连接时出现意外错误: " + e.getMessage(), e);
                    // 对于未预期的错误，也尝试重试
                    try {
                        Thread.sleep(3000);  // 等待3秒后重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        isRunning = false;
                    }
                }
            }
            
            // 确保在退出循环时关闭资源
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                    showDebugMessage("服务器套接字已关闭，总共接受了 " + connectionCount + " 个连接");
                }
            } catch (IOException e) {
                Log.e(TAG, "关闭服务器套接字时出错", e);
            }
        }
        
        public void cancel() {
            isRunning = false;
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                    showDebugMessage("服务器套接字已关闭");
                }
            } catch (IOException e) {
                Log.e(TAG, "关闭服务器套接字时出错", e);
            }
        }
    }
    
    /**
     * 客户端线程，连接到服务器
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private boolean isRunning = true;
        private static final int MAX_RETRY_COUNT = 3;
        private static final int RETRY_DELAY_MS = 2000; // 2秒重试间隔
        
        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            
            try {
                // 创建一个用于连接的套接字
                tmp = device.createRfcommSocketToServiceRecord(APP_UUID);
                showDebugMessage("创建客户端套接字成功");
            } catch (IOException e) {
                Log.e(TAG, "创建客户端套接字失败", e);
                // 即使创建失败也不设为null，让外部逻辑处理
            }
            socket = tmp;
        }
        
        public void run() {
            // 取消搜索，因为会降低连接速度
            bluetoothAdapter.cancelDiscovery();
            
            // 检查套接字是否有效
            if (socket == null) {
                Log.e(TAG, "套接字为null，无法连接");
                updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                runOnMainThread(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                    }
                });
                return;
            }
            
            // 添加重试机制
            boolean connected = false;
            int retryCount = 0;
            
            while (isRunning && !connected && retryCount < MAX_RETRY_COUNT) {
                try {
                    showDebugMessage("尝试连接到服务器... (尝试 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");
                    
                    // 连接到服务器
                    socket.connect();
                    showDebugMessage("连接到服务器成功");
                    
                    // 连接成功
                    connected = true;
                    manageConnectedSocket(socket);
                    
                } catch (IOException e) {
                    // 连接失败
                    retryCount++;
                    Log.e(TAG, "连接失败 (尝试 " + retryCount + "/" + MAX_RETRY_COUNT + "): " + e.getMessage(), e);
                    
                    if (retryCount >= MAX_RETRY_COUNT) {
                        showDebugMessage("连接失败，达到最大重试次数");
                        // 通知主线程连接失败
                        runOnMainThread(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                            }
                        });
                        break;
                    } else {
                        // 等待一段时间后重试
                        showDebugMessage((MAX_RETRY_COUNT - retryCount) + "秒后重试连接...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            isRunning = false;
                        }
                    }
                }
            }
            
            // 连接失败且达到最大重试次数
            if (!connected) {
                updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                // 确保关闭套接字
                cancel();
            }
        }
        
        public void cancel() {
            isRunning = false;
            // 使用finally块确保资源释放
            if (socket != null) {
                try {
                    socket.close();
                    showDebugMessage("客户端套接字已关闭");
                } catch (IOException e) {
                    Log.e(TAG, "关闭客户端套接字时出错", e);
                }
            }
        }
    }
    
    /**
     * 处理已连接的套接字
     */
    private synchronized void manageConnectedSocket(BluetoothSocket socket) {
        if (socket == null) {
            Log.e(TAG, "无法管理空套接字");
            return;
        }
        
        showDebugMessage("开始管理已连接的套接字");
        
        // 停止所有收发消息线程，确保当前线程结束
        cleanupAllThreads();
        
        // 创建新的连接线程
        try {
            showDebugMessage("创建新的连接线程...");
            ConnectedThread newThread = new ConnectedThread(socket);
            
            // 先启动线程，再更新引用，减少线程安全问题
            newThread.start();
            
            // 更新全局引用
            synchronized (this) {
                connectedThread = newThread;
            }
            
            showDebugMessage("新连接线程已创建并启动");
        } catch (Exception e) {
            Log.e(TAG, "创建新连接线程时出错: " + e.getMessage(), e);
            updateConnectionStatus(ConnectionStatus.DISCONNECTED);
            
            // 尝试关闭套接字，避免资源泄漏
            try {
                socket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "关闭套接字时出错", closeException);
            }
            return;
        }
        
        // 更新连接状态
        updateConnectionStatus(ConnectionStatus.CONNECTED);
        showDebugMessage("连接状态已更新为已连接");
    }
    
    /**
     * 清理所有线程，确保资源释放
     */
    private void cleanupAllThreads() {
        if (connectedThread != null) {
            try {
                // 先检查线程是否仍在运行
                if (connectedThread.isAlive()) {
                    showDebugMessage("正在停止已有的连接线程...");
                    
                    // 安全取消现有线程
                    try {
                        connectedThread.cancel();
                        
                        // 给线程一些时间来完成关闭操作
                        long startTime = System.currentTimeMillis();
                        long timeout = 1000; // 1秒超时
                        
                        while (connectedThread.isAlive() && System.currentTimeMillis() - startTime < timeout) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        
                        // 如果线程仍在运行，记录警告但继续处理
                        if (connectedThread.isAlive()) {
                            Log.w(TAG, "现有连接线程未能在预期时间内停止，继续创建新线程");
                        } else {
                            showDebugMessage("成功停止已有的连接线程");
                        }
                    } catch (Exception e) {
                        // 取消操作中的异常不应阻止我们创建新的线程
                        Log.e(TAG, "停止现有连接线程时出错: " + e.getMessage(), e);
                    }
                } else {
                    showDebugMessage("现有连接线程已经停止，无需取消");
                }
            } catch (Exception e) {
                Log.e(TAG, "检查现有连接线程状态时出错: " + e.getMessage(), e);
            }
            
            // 清除引用
            connectedThread = null;
        }
    }
    
    /**
     * 连接线程，处理数据的发送和接收
     */
    private class ConnectedThread extends Thread {
        final BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private boolean isRunning = true;
        // 消息缓冲区和分隔符
        private static final String MESSAGE_DELIMITER = "\n"; // 消息分隔符
        private StringBuilder messageBuffer = new StringBuilder();
        // 重试相关常量
        private static final int MAX_WRITE_RETRY = 3;
        private static final int WRITE_RETRY_DELAY = 1000; // 毫秒
        
        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            boolean initSuccess = false;
            
            try {
                // 尝试获取输入输出流
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                
                if (inputStream != null && outputStream != null) {
                    initSuccess = true;
                    showDebugMessage("获取输入/输出流成功");
                } else {
                    throw new IOException("无法获取有效的输入/输出流");
                }
            } catch (IOException e) {
                Log.e(TAG, "获取输入/输出流时出错: " + e.getMessage(), e);
                // 处理流初始化失败的情况
                updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                
                // 尝试安全地关闭已创建的流
                closeStreams();
            }
            
            // 如果初始化失败，确保连接状态更新
            if (!initSuccess) {
                runOnMainThread(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                    }
                });
            }
        }
        
        private void closeStreams() {
            // 安全关闭输入流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭输入流时出错", e);
                }
                inputStream = null;
            }
            
            // 安全关闭输出流
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭输出流时出错", e);
                }
                outputStream = null;
            }
        }
        
        public void run() {
            // 再次检查流是否有效
            if (inputStream == null || outputStream == null) {
                Log.e(TAG, "输入/输出流无效，连接线程退出");
                updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                return;
            }
            
            byte[] buffer = new byte[MAX_BUFFER_SIZE];
            int bytes;
            
            // 发送一个起始标记，表示连接已建立
            write((MessageType.CONNECTION_ESTABLISHED.ordinal() + "|" + "connected" + MESSAGE_DELIMITER).getBytes());
            
            while (isRunning) {
                try {
                    // 读取数据，确保可以处理流被关闭的情况
                    bytes = inputStream.read(buffer);
                    
                    if (bytes > 0) {
                        // 将读取的数据添加到缓冲区
                        String receivedData = new String(buffer, 0, bytes);
                        messageBuffer.append(receivedData);
                        
                        // 处理可能包含多条消息的缓冲区
                        processMessageBuffer();
                    } else if (bytes == -1) {
                        // 流已关闭
                        Log.w(TAG, "输入流已关闭，连接可能已断开");
                        updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                        break;
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "读取数据时出错: " + e.getMessage(), e);
                        updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                        
                        // 尝试重新连接或通知UI
                        runOnMainThread(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                            }
                        });
                        break;
                    }
                }
            }
            
            // 线程结束时确保清理资源
            closeStreams();
        }
        
        /**
         * 处理消息缓冲区，提取完整消息并处理
         */
        private void processMessageBuffer() {
            String bufferStr = messageBuffer.toString();
            int delimiterIndex;
            
            // 处理缓冲区中的所有完整消息
            while ((delimiterIndex = bufferStr.indexOf(MESSAGE_DELIMITER)) != -1) {
                // 提取一条完整消息
                String completeMessage = bufferStr.substring(0, delimiterIndex);
                
                // 处理这条消息
                if (!completeMessage.isEmpty()) {
                    processReceivedMessage(completeMessage);
                }
                
                // 从缓冲区中移除已处理的消息
                bufferStr = bufferStr.substring(delimiterIndex + MESSAGE_DELIMITER.length());
            }
            
            // 更新缓冲区为剩余未处理的部分
            messageBuffer = new StringBuilder(bufferStr);
        }
        
        /**
         * 写入数据到输出流
         */
        public boolean write(byte[] bytes) {
            if (outputStream == null) {
                Log.e(TAG, "输出流为空，无法写入数据");
                return false;
            }
            
            // 确保消息以分隔符结尾
            if (bytes.length > 0 && bytes[bytes.length - 1] != '\n') {
                byte[] newBytes = new byte[bytes.length + 1];
                System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                newBytes[bytes.length] = '\n';
                bytes = newBytes;
            }
            
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < MAX_WRITE_RETRY && !success && isRunning) {
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                    success = true;
                } catch (IOException e) {
                    retryCount++;
                    Log.e(TAG, "写入数据时出错 (尝试 " + retryCount + "/" + MAX_WRITE_RETRY + "): " + e.getMessage(), e);
                    
                    if (retryCount >= MAX_WRITE_RETRY) {
                        // 达到最大重试次数，更新连接状态
                        updateConnectionStatus(ConnectionStatus.DISCONNECTED);
                        
                        // 通知UI线程连接断开
                        runOnMainThread(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED);
                            }
                        });
                        
                        return false;
                    } else {
                        // 等待一段时间后重试
                        try {
                            Thread.sleep(WRITE_RETRY_DELAY);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            return success;
        }
        
        /**
         * 检查输出流是否有效
         */
        public boolean isOutputStreamValid() {
            return outputStream != null;
        }
        
        /**
         * 取消连接
         */
        public void cancel() {
            isRunning = false;
            
            // 首先关闭流，然后关闭套接字
            closeStreams();
            
            // 关闭套接字
            if (socket != null) {
                try {
                    socket.close();
                    showDebugMessage("连接线程已关闭");
                } catch (IOException e) {
                    Log.e(TAG, "关闭连接线程时出错", e);
                }
            }
        }
    }
    
    /**
     * 处理接收到的消息，并通知消息监听器
     */
    private void processReceivedMessage(String message) {
        if (message == null || message.isEmpty()) {
            Log.e(TAG, "收到空消息");
            return;
        }
        
        try {
            // 解析消息格式（类型|数据）
            String[] parts = message.split("\\|", 2);
            if (parts.length < 2) {
                Log.e(TAG, "无效消息格式: " + message);
                return;
            }
            
            // 解析消息类型
            int typeOrdinal = Integer.parseInt(parts[0]);
            MessageType type = MessageType.values()[typeOrdinal];
            
            // 获取消息数据
            String data = parts[1];
            String senderId = connectedThread.socket.getRemoteDevice().getAddress();
            
            // 记录收到的消息
            Log.d(TAG, "收到消息: 类型=" + type + ", 数据='" + data + "'");
            
            // 特殊处理GAME_START消息，确保状态正确且能够成功传递
            if (type == MessageType.GAME_START) {
                Log.d(TAG, "收到游戏开始消息，确保消息传递");
                
                // 先存储原始消息
                final MessageType finalType = type;
                final String finalData = data;
                final String finalSenderId = senderId;
                
                // 确保在主线程处理消息
                mainHandler.post(() -> {
                    if (messageListener != null) {
                        // 发送确认
                        Log.d(TAG, "正在处理游戏开始消息");
                        messageListener.onMessageReceived(finalType, finalData, finalSenderId);
                        
                        // 游戏开始消息特别重要，延迟再处理一次以确保被接收
                        mainHandler.postDelayed(() -> {
                            Log.d(TAG, "再次处理游戏开始消息以确保接收");
                            messageListener.onMessageReceived(finalType, finalData, finalSenderId);
                        }, 500);
                    }
                });
                
                return; // 已处理GAME_START消息
            }
            
            // 处理加入游戏消息时，确保回应，但避免无限循环
            if (type == MessageType.JOIN_GAME) {
                // 防止频繁处理重复的JOIN_GAME消息
                if (data.equals(lastJoinPlayerData) && 
                    System.currentTimeMillis() - lastJoinMessageTime < JOIN_MESSAGE_THROTTLE) {
                    Log.d(TAG, "忽略重复的JOIN_GAME消息: " + data);
                    return;
                }
                
                // 更新时间戳和上次处理的玩家
                lastJoinMessageTime = System.currentTimeMillis();
                lastJoinPlayerData = data;
                
                // 确保在主线程处理消息
                mainHandler.post(() -> {
                    if (messageListener != null) {
                        messageListener.onMessageReceived(type, data, senderId);
                        
                        // 不再自动发送状态更新，避免循环
                        // 移除服务器端收到客户端加入消息后再发送自己加入消息的逻辑
                    }
                });
                
                return; // 已处理JOIN_GAME消息
            }
            
            // 其他类型消息的常规处理
            if (messageListener != null) {
                mainHandler.post(() -> messageListener.onMessageReceived(type, data, senderId));
            }
        } catch (Exception e) {
            Log.e(TAG, "处理消息时出错: " + e.getMessage(), e);
        }
    }
} 