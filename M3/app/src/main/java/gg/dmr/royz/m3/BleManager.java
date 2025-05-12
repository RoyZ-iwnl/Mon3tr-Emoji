package gg.dmr.royz.m3;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import gg.dmr.royz.m3.utils.BleConstants;

import java.util.ArrayList;
import java.util.List;

public class BleManager {
    private static final String TAG = "BleManager";

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private final BleCallback bleCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isScanning = false;

    public interface BleCallback {
        void onDeviceFound(BluetoothDevice device);

        void onConnected();

        void onDisconnected();

        void onDataReceived(byte[] data);

        void onError(String message);
    }

    public BleManager(Context context, BleCallback callback) {
        this.context = context;
        this.bleCallback = callback;
        bluetoothManager = context.getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        } else {
            throw new IllegalStateException("蓝牙不可用");
        }
    }

    // 合并的scanCallback实现
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            // 添加日志输出所有扫描到的设备
            Log.d(TAG, "扫描到设备: " +
                    (deviceName != null ? deviceName : "未知") +
                    " MAC: " + device.getAddress());

            // 如果设备名称为null，尝试从广播数据获取
            if (deviceName == null && result.getScanRecord() != null) {
                byte[] scanRecord = result.getScanRecord().getBytes();
                deviceName = parseScanRecord(scanRecord);
            }

            if (deviceName != null) {
                Log.d(TAG, "设备名称: " + deviceName + " RSSI: " + result.getRssi());
                if (deviceName.equals(BleConstants.DEVICE_NAME)) {
                    stopScan();
                    bleCallback.onDeviceFound(device);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "扫描失败，错误代码: " + errorCode);
            stopScan();
            bleCallback.onError("扫描失败: " + getErrorMessage(errorCode));
        }
    };

    public void startScan() {
        if (!isScanning && ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "开始扫描蓝牙设备");

            // 设置扫描参数
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            isScanning = true;
            bluetoothLeScanner.startScan(null, settings, scanCallback);  // 暂时不使用过滤器
        }
    }

    public void stopScan() {
        if (isScanning && ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            isScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(TAG, "停止扫描");
        }
    }

    public void connect(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
            // 连接后请求更大的MTU
            bluetoothGatt.requestMtu(517); // 请求最大MTU
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
    }

    public void sendCommand(byte command, byte[] data) {
        if (bluetoothGatt != null && ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

            BluetoothGattService service = bluetoothGatt.getService(BleConstants.SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(BleConstants.CHARACTERISTIC_COMMAND_UUID);
                if (characteristic != null) {
                    byte[] commandData = new byte[data.length + 1];
                    commandData[0] = command;
                    System.arraycopy(data, 0, commandData, 1, data.length);
                    characteristic.setValue(commandData);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            }
        }
    }

    private static final int MAX_MTU = 20; // 默认MTU大小为23，保守使用20
    private byte[] pendingData; // 存储待发送的数据
    private int currentOffset; // 当前发送位置

    public void sendData(byte[] data) {
        if (bluetoothGatt != null && ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

            BluetoothGattService service = bluetoothGatt.getService(BleConstants.SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(BleConstants.CHARACTERISTIC_DATA_UUID);
                if (characteristic != null) {
                    // 存储完整数据和起始位置
                    pendingData = data;
                    currentOffset = 0;
                    // 发送第一块数据
                    sendNextChunk(characteristic);
                }
            }
        }
    }

    private void sendNextChunk(BluetoothGattCharacteristic characteristic) {
        if (pendingData == null || currentOffset >= pendingData.length) {
            // 传输完成
            pendingData = null;
            currentOffset = 0;
            return;
        }

        // 计算当前块的大小
        int chunkSize = Math.min(MAX_MTU, pendingData.length - currentOffset);
        byte[] chunk = new byte[chunkSize];
        System.arraycopy(pendingData, currentOffset, chunk, 0, chunkSize);

        // 设置数据并写入
        characteristic.setValue(chunk);
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    private String parseScanRecord(byte[] scanRecord) {
        int index = 0;
        while (index < scanRecord.length) {
            int length = scanRecord[index++] & 0xFF;
            if (length == 0) break;

            int type = scanRecord[index] & 0xFF;
            if (type == 0x09) { // Complete Local Name
                byte[] nameBytes = new byte[length - 1];
                System.arraycopy(scanRecord, index + 1, nameBytes, 0, length - 1);
                return new String(nameBytes);
            }
            index += length;
        }
        return null;
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "扫描已经在进行中";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "应用注册失败";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "设备不支持BLE扫描";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "内部错误";
            default:
                return "未知错误: " + errorCode;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "已连接到GATT服务器");
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                }
                handler.post(() -> bleCallback.onConnected());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "与GATT服务器断开连接");
                handler.post(() -> bleCallback.onDisconnected());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic =
                            service.getCharacteristic(BleConstants.CHARACTERISTIC_COMMAND_UUID);
                    if (characteristic != null &&
                            ActivityCompat.checkSelfPermission(context,
                                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.setCharacteristicNotification(characteristic, true);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            handler.post(() -> bleCallback.onDataReceived(value));
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status
        ) {
            byte[] value = characteristic.getValue(); // 关键修改点
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentOffset += MAX_MTU;
                sendNextChunk(characteristic);
            } else {
                handleWriteError(status);
            }
        }

        private void handleWriteError(int status) {
            Log.e(TAG, "写入特征值失败: " + status);
            handler.post(() -> {
                String errorMsg;
                switch (status) {
                    case 17:
                        errorMsg = "数据长度超出限制";
                        break;
                    case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                        errorMsg = "写入未授权";
                        break;
                    case BluetoothGatt.GATT_INVALID_OFFSET:
                        errorMsg = "无效的偏移量";
                        break;
                    default:
                        errorMsg = "写入失败: " + status;
                }
                bleCallback.onError(errorMsg);
            });
            // 清理待发送数据
            pendingData = null;
            currentOffset = 0;
        }


        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU 已更新为: " + mtu);
            }
        }
    };

}

