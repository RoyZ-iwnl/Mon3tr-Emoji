package gg.dmr.royz.m3.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import gg.dmr.royz.m3.model.DeviceImage;
import gg.dmr.royz.m3.model.DeviceStatus;
import gg.dmr.royz.m3.utils.LogUtil;

/**
 * 蓝牙管理器
 * 负责BLE设备的扫描、连接和数据收发
 */
@SuppressLint("MissingPermission")
public class BleManager {
    private static final String TAG = "BleManager";
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // 超时和重试参数
    private static final int SCAN_TIMEOUT = 10000; // 扫描超时时间(ms)
    private static final int CONNECTION_TIMEOUT = 8000; // 连接超时时间(ms)
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数
    private static final int WRITE_TIMEOUT = 250; // 写操作超时时间(ms)
    private static final int WRITE_DELAY = 30; // 写操作间隔延迟(ms)

    // 状态定义
    public enum State {
        DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, CONNECTED, TRANSMITTING
    }

    // 蓝牙状态回调接口
    public interface BleCallback {
        void onStateChanged(State state);
        void onDeviceFound(BluetoothDevice device);
        void onConnected();
        void onDisconnected();
        void onCommandResponse(byte[] response);
        void onError(String message);
    }

    // 单例实例
    private static BleManager instance;

    // 蓝牙相关成员
    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;

    // 服务与特征
    private BluetoothGattService targetService;
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic dataCharacteristic;

    // 状态与回调
    private State currentState = State.DISCONNECTED;
    private BleCallback callback;

    // 重试计数
    private int retryCount = 0;

    // 命令队列与处理
    private final Queue<byte[]> commandQueue = new ConcurrentLinkedQueue<>();
    private boolean isProcessingCommand = false;
    private boolean isWriteInProgress = false;

    // 正在传输的图片数据
    private byte[] imageData;
    private int transferOffset = 0;

    // 数据传输回调
    public interface TransferCallback {
        void onProgress(int current, int total);
        void onComplete();
        void onError(String message);
    }
    private TransferCallback transferCallback;

    // Handler用于处理延迟和超时
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 写操作超时检查任务
    private final Runnable writeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isWriteInProgress) {
                Log.w(TAG, "写操作超时，重置状态继续处理队列");
                isWriteInProgress = false;

                // 继续处理队列
                if (currentState == State.TRANSMITTING) {
                    continueImageTransfer();
                } else {
                    processCommandQueue();
                }
            }
        }
    };

    // 单例获取方法
    public static synchronized BleManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleManager(context.getApplicationContext());
        }
        return instance;
    }

    // 私有构造函数(单例模式)
    private BleManager(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void setCallback(BleCallback callback) {
        this.callback = callback;
    }

    public State getState() {
        return currentState;
    }

    private void setState(State state) {
        Log.d(TAG, "状态变更: " + currentState + " -> " + state);
        currentState = state;
        if (callback != null) {
            handler.post(() -> callback.onStateChanged(state));
        }
    }

    // 错误通知
    private void notifyError(String message) {
        Log.e(TAG, message);
        if (callback != null) {
            handler.post(() -> callback.onError(message));
        }
    }

    // ==== 扫描相关方法 ====

    public void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            notifyError("蓝牙未启用");
            return;
        }

        if (currentState == State.SCANNING) {
            return;
        }

        stopScan();
        setState(State.SCANNING);

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter nameFilter = new ScanFilter.Builder().build();
        filters.add(nameFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            scanner.startScan(filters, settings, scanCallback);
            handler.postDelayed(this::stopScan, SCAN_TIMEOUT);
        } else {
            setState(State.DISCONNECTED);
            notifyError("无法启动蓝牙扫描");
        }
    }

    public void stopScan() {
        if (scanner != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled() &&
                currentState == State.SCANNING) {
            scanner.stopScan(scanCallback);
        }

        if (currentState == State.SCANNING) {
            setState(State.DISCONNECTED);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();

            if (deviceName != null && deviceName.contains(Constants.DEVICE_NAME_PREFIX)) {
                Log.d(TAG, "找到目标设备: " + deviceName);

                if (callback != null) {
                    callback.onDeviceFound(device);
                }

                stopScan();
                connect(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "扫描失败: " + errorCode);
            setState(State.DISCONNECTED);
            notifyError("设备扫描失败，错误码: " + errorCode);
        }
    };

    // ==== 连接相关方法 ====

    public void connect(BluetoothDevice device) {
        if (device == null) {
            notifyError("设备为空，无法连接");
            return;
        }

        disconnect();
        setState(State.CONNECTING);
        Log.d(TAG, "正在连接到设备: " + device.getName() + " [" + device.getAddress() + "]");

        // Android 10及以上使用优化的连接方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 使用TRANSPORT_LE参数指定低功耗连接
            bluetoothGatt = device.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK,
                    handler);
            Log.d(TAG, "使用优化的BLE连接方式(Android 10+)");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0-9.0
            bluetoothGatt = device.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
            Log.d(TAG, "使用LE传输模式连接(Android 6.0+)");
        } else {
            // 老版本Android
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            Log.d(TAG, "使用标准GATT连接");
        }

        if (bluetoothGatt == null) {
            Log.e(TAG, "连接失败，无法创建GATT连接");
            setState(State.DISCONNECTED);
            notifyError("无法创建GATT连接");
            return;
        }

        // 设置连接超时
        handler.postDelayed(() -> {
            if (currentState == State.CONNECTING) {
                Log.e(TAG, "连接超时");
                disconnect();

                // 尝试重新连接
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    Log.d(TAG, "重试连接，第" + retryCount + "次");
                    // 稍微延迟后再重试
                    handler.postDelayed(() -> connect(device), 500);
                } else {
                    retryCount = 0;
                    notifyError("连接超时，请重试");
                }
            }
        }, CONNECTION_TIMEOUT);
    }

    public void disconnect() {
        // 清除任何可能的超时任务
        handler.removeCallbacks(writeTimeoutRunnable);

        commandQueue.clear();
        isProcessingCommand = false;
        isWriteInProgress = false;

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        targetService = null;
        commandCharacteristic = null;
        dataCharacteristic = null;

        if (currentState != State.DISCONNECTED) {
            setState(State.DISCONNECTED);
            if (callback != null) {
                handler.post(() -> callback.onDisconnected());
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    setState(State.DISCOVERING);
                    retryCount = 0;

                    handler.post(() -> {
                        if (bluetoothGatt != null) {
                            bluetoothGatt.discoverServices();
                        }
                    });
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnect();
                }
            } else {
                disconnect();

                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    BluetoothDevice device = gatt.getDevice();
                    handler.postDelayed(() -> connect(device), 1000);
                } else {
                    retryCount = 0;
                    notifyError("连接失败");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "服务发现完成");

                // 列出所有发现的服务，用于调试
                List<BluetoothGattService> services = gatt.getServices();
                Log.d(TAG, "发现" + services.size() + "个服务:");
                for (BluetoothGattService service : services) {
                    Log.d(TAG, "服务: " + service.getUuid().toString());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "  特征: " + characteristic.getUuid().toString() +
                                " 属性: " + characteristic.getProperties());
                    }
                }

                // 查找目标服务
                targetService = gatt.getService(UUID.fromString(Constants.SERVICE_UUID));
                if (targetService != null) {
                    Log.d(TAG, "找到目标服务: " + Constants.SERVICE_UUID);

                    // 获取命令特征
                    commandCharacteristic = targetService.getCharacteristic(
                            UUID.fromString(Constants.CHAR_COMMAND_UUID));

                    // 获取数据特征
                    dataCharacteristic = targetService.getCharacteristic(
                            UUID.fromString(Constants.CHAR_DATA_UUID));

                    if (commandCharacteristic != null && dataCharacteristic != null) {
                        Log.d(TAG, "找到所需特征，启用通知");

                        // 设置MTU大小，提高传输效率
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            bluetoothGatt.requestMtu(512);
                            Log.d(TAG, "请求MTU大小: 512");
                        }

                        // 启用命令特征的通知
                        enableNotification(commandCharacteristic);
                    } else {
                        Log.e(TAG, "未找到所需特征，commandChar=" + (commandCharacteristic != null) +
                                ", dataChar=" + (dataCharacteristic != null));
                        disconnect();
                        notifyError("设备不兼容，未找到所需特征");
                    }
                } else {
                    Log.e(TAG, "未找到目标服务: " + Constants.SERVICE_UUID);

                    // 尝试使用BLe.py中的服务UUID
                    String altServiceUuid = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
                    Log.d(TAG, "尝试替代服务UUID: " + altServiceUuid);
                    targetService = gatt.getService(UUID.fromString(altServiceUuid));

                    if (targetService != null) {
                        Log.d(TAG, "使用替代UUID找到服务");
                        // 尝试查找特征
                        commandCharacteristic = targetService.getCharacteristic(
                                UUID.fromString(Constants.CHAR_COMMAND_UUID));
                        dataCharacteristic = targetService.getCharacteristic(
                                UUID.fromString(Constants.CHAR_DATA_UUID));

                        if (commandCharacteristic != null && dataCharacteristic != null) {
                            enableNotification(commandCharacteristic);
                        } else {
                            disconnect();
                            notifyError("设备不兼容，未找到所需特征");
                        }
                    } else {
                        disconnect();
                        notifyError("设备不兼容，未找到目标服务");
                    }
                }
            } else {
                Log.e(TAG, "服务发现失败: " + status);
                disconnect();
                notifyError("服务发现失败");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // 处理命令特征的通知回调
            if (characteristic.getUuid().equals(UUID.fromString(Constants.CHAR_COMMAND_UUID))) {
                byte[] response = characteristic.getValue();
                if (response != null && response.length > 0) {
                    LogUtil.logHex("收到特征通知", response);
                    if (callback != null) {
                        handler.post(() -> callback.onCommandResponse(response));
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // 移除写超时处理
            handler.removeCallbacks(writeTimeoutRunnable);
            isWriteInProgress = false;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "特征写入成功: " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "特征写入失败: " + characteristic.getUuid().toString() + " 状态: " + status);
            }

            if (characteristic.getUuid().equals(UUID.fromString(Constants.CHAR_DATA_UUID))) {
                // 数据特征写入完成，稍微延迟后继续发送下一包数据
                handler.postDelayed(() -> continueImageTransfer(), WRITE_DELAY);
            } else if (characteristic.getUuid().equals(UUID.fromString(Constants.CHAR_COMMAND_UUID))) {
                // 命令特征写入完成，处理命令队列
                isProcessingCommand = false;
                handler.postDelayed(() -> processCommandQueue(), WRITE_DELAY);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            // 描述符写入完成，表示通知已启用
            Log.d(TAG, "描述符写入完成，状态: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "通知启用成功");
                setState(State.CONNECTED);
                if (callback != null) {
                    handler.post(() -> callback.onConnected());
                }
            } else {
                Log.e(TAG, "启用通知失败: " + status);

                // 尝试直接进入已连接状态，某些设备可能不需要描述符写入
                Log.d(TAG, "尽管描述符写入失败，但尝试继续操作");
                setState(State.CONNECTED);
                if (callback != null) {
                    handler.post(() -> callback.onConnected());
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG, "MTU变更: " + mtu + " 状态: " + status);
        }
    };

    // 启用特征通知
    private void enableNotification(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            Log.e(TAG, "enableNotification: bluetoothGatt或characteristic为空");
            return;
        }

        // 添加详细日志，帮助调试
        Log.d(TAG, "启用特征通知: " + characteristic.getUuid().toString());

        // 首先设置本地通知
        boolean success = bluetoothGatt.setCharacteristicNotification(characteristic, true);
        if (!success) {
            Log.e(TAG, "启用特征通知失败");
            notifyError("启用特征通知失败");
            disconnect();
            return;
        }

        // 获取配置描述符
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            Log.d(TAG, "找到配置描述符，设置通知值");
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            // 延迟100ms再写入描述符，解决某些设备的兼容性问题
            handler.postDelayed(() -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt.writeDescriptor(descriptor);
                    Log.d(TAG, "写入通知描述符");
                }
            }, 100);
        } else {
            Log.e(TAG, "未找到配置描述符");

            // 对于某些特殊设备，可能没有描述符但能正常工作，尝试直接设置连接状态
            Log.d(TAG, "尝试直接设置为已连接状态");
            setState(State.CONNECTED);
            if (callback != null) {
                handler.post(() -> callback.onConnected());
            }
        }
    }

    // ==== 命令发送与数据传输 ====

    // 发送命令
    public boolean sendCommand(byte[] command) {
        if (currentState != State.CONNECTED && currentState != State.TRANSMITTING) {
            Log.e(TAG, "发送命令失败: 设备未连接");
            return false;
        }

        commandQueue.add(command);
        processCommandQueue();
        return true;
    }

    // 处理命令队列
    private void processCommandQueue() {
        if (isProcessingCommand || commandQueue.isEmpty() || bluetoothGatt == null ||
                commandCharacteristic == null || isWriteInProgress) {
            return;
        }

        isProcessingCommand = true;
        byte[] command = commandQueue.poll();

        if (command != null) {
            // 标记写入进行中
            isWriteInProgress = true;

            // 设置写入超时处理
            handler.postDelayed(writeTimeoutRunnable, WRITE_TIMEOUT);

            commandCharacteristic.setValue(command);
            boolean writeSuccess = bluetoothGatt.writeCharacteristic(commandCharacteristic);

            if (!writeSuccess) {
                Log.e(TAG, "写入命令特征失败，稍后重试");
                isWriteInProgress = false;
                isProcessingCommand = false;
                handler.postDelayed(this::processCommandQueue, 100);
            }
        } else {
            isProcessingCommand = false;
        }
    }

    // 发送图片数据
    public void sendImageData(byte[] data, TransferCallback callback) {
        if (currentState != State.CONNECTED) {
            if (callback != null) {
                try {
                    callback.onError("设备未连接");
                } catch (Exception e) {
                    Log.e(TAG, "回调执行异常: " + e.getMessage(), e);
                }
            }
            return;
        }

        // 重置状态
        setState(State.TRANSMITTING);
        imageData = data;
        transferOffset = 0;

        // 安全地设置回调
        TransferCallback oldCallback = transferCallback;
        transferCallback = callback;

        // 如果有旧的回调，需要清理
        if (oldCallback != null) {
            try {
                oldCallback.onError("传输被新的请求中断");
            } catch (Exception e) {
                Log.e(TAG, "清理旧回调时发生异常: " + e.getMessage(), e);
            }
        }

        // 确保没有写入操作在进行中
        isWriteInProgress = false;

        // 移除可能存在的超时任务
        handler.removeCallbacks(writeTimeoutRunnable);

        // 开始传输
        continueImageTransfer();
    }

    // 继续发送图片数据
    private void continueImageTransfer() {
        if (imageData == null || dataCharacteristic == null || bluetoothGatt == null) {
            return;
        }

        // 检查是否传输完成
        if (transferOffset >= imageData.length) {
            Log.d(TAG, "图片数据传输完成");

            // 保存回调引用，避免并发问题
            TransferCallback callback = transferCallback;

            // 先清空成员变量，然后再调用回调，避免回调中可能导致的递归调用
            imageData = null;
            transferCallback = null;
            setState(State.CONNECTED);

            // 使用保存的局部变量引用安全调用回调
            if (callback != null) {
                handler.post(() -> {
                    try {
                        callback.onComplete();
                    } catch (Exception e) {
                        Log.e(TAG, "回调执行异常: " + e.getMessage(), e);
                    }
                });
            }
            return;
        }

        // 如果有写入操作正在进行，不要启动新的写入
        if (isWriteInProgress) {
            Log.d(TAG, "有写入操作正在进行，稍后继续传输");
            return;
        }

        // 计算当前块大小
        int chunkSize = Math.min(Constants.CHUNK_SIZE, imageData.length - transferOffset);
        byte[] chunk = new byte[chunkSize];
        System.arraycopy(imageData, transferOffset, chunk, 0, chunkSize);

        // 标记写入进行中
        isWriteInProgress = true;

        // 设置写入超时处理
        handler.postDelayed(writeTimeoutRunnable, WRITE_TIMEOUT);

        // 发送数据块
        dataCharacteristic.setValue(chunk);
        boolean writeSuccess = bluetoothGatt.writeCharacteristic(dataCharacteristic);

        if (!writeSuccess) {
            Log.e(TAG, "写入数据特征失败，重试");
            isWriteInProgress = false;
            handler.postDelayed(this::continueImageTransfer, 100);
            return;
        }

        // 记录当前的偏移量
        final int currentOffset = transferOffset;
        transferOffset += chunkSize;

        // 更新进度
        if (transferCallback != null) {
            final int totalSize = imageData.length;
            handler.post(() -> {
                try {
                    // 通过局部变量引用调用回调，避免可能的空指针异常
                    TransferCallback callback = transferCallback;
                    if (callback != null) {
                        callback.onProgress(currentOffset, totalSize);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "进度回调执行异常: " + e.getMessage(), e);
                }
            });
        }
    }

    // ==== 高级命令接口 ====

    // 获取图片列表
    public void getImageList(CommandHandler.ImageListCallback callback) {
        sendCommand(CommandHandler.cmdGetImageList());
    }

    // 获取设备状态
    public void getDeviceStatus(CommandHandler.StatusCallback callback) {
        sendCommand(CommandHandler.cmdGetStatus());
    }

    /**
     * 开始图片传输，支持格式标识
     *
     * @param fileIndex 文件索引
     * @param format 图片格式 (0x00=原始格式, 0x10=JPG, 0x20=PNG, 0x30=GIF)
     * @return 是否成功发送命令
     */
    public boolean startImageTransfer(byte fileIndex, byte format) {
        // 组合索引和格式
        byte combinedIndex = (byte)((format & 0xF0) | (fileIndex & 0x0F));
        return sendCommand(CommandHandler.cmdStartTransfer(combinedIndex));
    }

    /**
     * 开始图片传输 (兼容老方法)
     *
     * @param fileIndex 文件索引
     * @return 是否成功发送命令
     */
    public boolean startImageTransfer(byte fileIndex) {
        // 默认使用原始格式
        return startImageTransfer(fileIndex, (byte)0);
    }

    // 结束图片传输
    public boolean endImageTransfer() {
        return sendCommand(CommandHandler.cmdEndTransfer());
    }

    // 删除图片
    public boolean deleteImage(byte fileIndex) {
        return sendCommand(CommandHandler.cmdDeleteImage(fileIndex));
    }

    // 重排图片
    public boolean reorderImages(byte[] order) {
        return sendCommand(CommandHandler.cmdReorderImages(order));
    }

    // 设置显示图片
    public boolean setDisplayImage(byte fileIndex) {
        return sendCommand(CommandHandler.cmdSetDisplay(fileIndex));
    }
}