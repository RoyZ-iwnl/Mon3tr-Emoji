package gg.dmr.royz.m3;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gg.dmr.royz.m3.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Mon3trClient";

    // BLE相关UUID
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    private static final String CHAR_COMMAND_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    private static final String CHAR_DATA_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9";
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    // 命令类型
    private static final byte CMD_START_TRANSFER = 1;
    private static final byte CMD_IMAGE_DATA = 2;
    private static final byte CMD_END_TRANSFER = 3;
    private static final byte CMD_DELETE_IMAGE = 4;
    private static final byte CMD_REORDER_IMAGES = 5;
    private static final byte CMD_GET_IMAGE_LIST = 6;
    private static final byte CMD_SET_DISPLAY = 7;
    private static final byte CMD_GET_STATUS = 8;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 权限相关
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private ActivityMainBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic dataCharacteristic;

    private ImageAdapter imageAdapter;
    private List<ImageInfo> imageList = new ArrayList<>();
    private boolean isConnected = false;
    private boolean isScanning = false;
    private boolean isUploading = false;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 图片选择器
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadImage(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initViews();
        initBluetooth();
        checkPermissions();

        // 初始隐藏上传进度
        binding.uploadProgress.setVisibility(View.GONE);
    }

    private void initViews() {
        // 设置RecyclerView
        imageAdapter = new ImageAdapter(imageList, this::onImageClick, this::onImageLongClick);
        binding.recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerView.setAdapter(imageAdapter);

        // 设置日志文本
        binding.logText.setMovementMethod(new ScrollingMovementMethod());

        // 设置点击事件
        binding.scanButton.setOnClickListener(v -> toggleScan());
        binding.uploadButton.setOnClickListener(v -> {
            if (!isUploading) {
                selectImage();
            } else {
                log("上传正在进行中，请等待完成");
            }
        });
        binding.refreshButton.setOnClickListener(v -> refreshImageList());
        binding.statusButton.setOnClickListener(v -> getDeviceStatus());

        // 初始状态
        updateConnectionStatus(false);
        binding.uploadButton.setEnabled(false);
        binding.refreshButton.setEnabled(false);
        binding.statusButton.setEnabled(false);

        // 设置"没有图片"提示，默认显示
        binding.noImagesText.setVisibility(View.VISIBLE);
    }

    private void initBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            showError("设备不支持蓝牙");
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    private void checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showError("需要所有权限才能正常工作");
                    return;
                }
            }
            log("权限已授予");
        }
    }

    private void toggleScan() {
        if (isScanning) {
            stopScan();
        } else {
            startScan();
        }
    }

    private void startScan() {
        if (bluetoothLeScanner == null) {
            showError("蓝牙扫描器不可用");
            return;
        }

        log("开始扫描BLE设备...");
        isScanning = true;
        binding.scanButton.setText("停止扫描");
        binding.progressBar.setVisibility(View.VISIBLE);

        try {
            bluetoothLeScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            showError("没有蓝牙权限");
            stopScan();
        }

        // 10秒后自动停止扫描
        new Handler(Looper.getMainLooper()).postDelayed(this::stopScan, 10000);
    }

    private void stopScan() {
        if (bluetoothLeScanner == null || !isScanning) return;

        log("停止扫描");
        isScanning = false;
        binding.scanButton.setText("扫描设备");
        binding.progressBar.setVisibility(View.GONE);

        try {
            bluetoothLeScanner.stopScan(scanCallback);
        } catch (SecurityException e) {
            showError("没有蓝牙权限");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String deviceName = device.getName();
                if (deviceName != null && deviceName.equals("RoyZ-Mon3tr")) {
                    log("发现目标设备: " + deviceName);
                    stopScan();
                    connectToDevice(device);
                }
            } catch (SecurityException e) {
                showError("没有蓝牙权限");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            log("扫描失败，错误码: " + errorCode);
            stopScan();
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        log("正在连接到设备...");
        binding.progressBar.setVisibility(View.VISIBLE);

        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            showError("没有蓝牙连接权限");
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            runOnUiThread(() -> {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    log("已连接到设备");
                    isConnected = true;
                    updateConnectionStatus(true);
                    try {
                        gatt.discoverServices();
                    } catch (SecurityException e) {
                        showError("没有蓝牙权限");
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    log("设备断开连接");
                    isConnected = false;
                    updateConnectionStatus(false);
                    cleanup();
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> {
                    log("发现服务");
                    setupCharacteristics();
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString();
            runOnUiThread(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("写入成功: " + uuid.substring(0, 8));
                } else {
                    log("写入失败: " + uuid.substring(0, 8) + ", 状态: " + status);
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (characteristic.getUuid().equals(UUID.fromString(CHAR_COMMAND_UUID))) {
                handleNotification(data);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> {
                    log("通知已启用");
                });
            }
        }
    };

    private void setupCharacteristics() {
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(SERVICE_UUID));
        if (service == null) {
            log("未找到服务");
            return;
        }

        commandCharacteristic = service.getCharacteristic(UUID.fromString(CHAR_COMMAND_UUID));
        dataCharacteristic = service.getCharacteristic(UUID.fromString(CHAR_DATA_UUID));

        if (commandCharacteristic == null || dataCharacteristic == null) {
            log("未找到特征");
            return;
        }

        // 设置无响应写入类型
        commandCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        dataCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        // 启用通知
        try {
            bluetoothGatt.setCharacteristicNotification(commandCharacteristic, true);

            // 启用CCCD
            BluetoothGattDescriptor descriptor = commandCharacteristic.getDescriptor(
                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
            }

            log("特征设置完成");

            // 延迟后获取设备状态
            mainHandler.postDelayed(() -> {
                getDeviceStatus();
                refreshImageList();
            }, 500);
        } catch (SecurityException e) {
            showError("没有蓝牙权限");
        }
    }

    private void updateConnectionStatus(boolean connected) {
        binding.connectionStatus.setText(connected ? "已连接" : "未连接");
        binding.connectionStatus.setTextColor(getColor(connected ?
                android.R.color.holo_green_dark : android.R.color.holo_red_dark));

        binding.uploadButton.setEnabled(connected);
        binding.refreshButton.setEnabled(connected);
        binding.statusButton.setEnabled(connected);
        binding.progressBar.setVisibility(View.GONE);

        if (connected) {
            // 当连接成功时获取最新状态
            mainHandler.postDelayed(() -> {
                getDeviceStatus();
                refreshImageList();
            }, 1000);
        }
    }

    private void selectImage() {
        if (isUploading) {
            showError("正在上传中，请等待完成");
            return;
        }

        isUploading = true;
        binding.uploadButton.setEnabled(false);
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void finishUpload(boolean success) {
        isUploading = false;
        binding.uploadButton.setEnabled(isConnected);
        binding.uploadProgress.setVisibility(View.GONE);

        if (success) {
            // 上传成功后刷新列表和状态
            mainHandler.postDelayed(() -> {
                refreshImageList();
                getDeviceStatus();
            }, 1500);
        }
    }

    private void uploadImage(Uri imageUri) {
        runOnUiThread(() -> {
            binding.uploadProgress.setProgress(0);
            binding.uploadProgress.setVisibility(View.VISIBLE);
            log("开始处理图片...");
        });

        executorService.execute(() -> {
            Bitmap originalBitmap = null;
            Bitmap tempBitmap = null;
            Bitmap scaledBitmap = null;

            try {
                // 读取原始图片
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                originalBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // 缩放计算
                float scale = Math.min(240f / originalBitmap.getWidth(), 240f / originalBitmap.getHeight());
                int newWidth = Math.round(originalBitmap.getWidth() * scale);
                int newHeight = Math.round(originalBitmap.getHeight() * scale);
                int left = (240 - newWidth) / 2;
                int top = (240 - newHeight) / 2;

                // 创建临时缩放位图
                tempBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
                originalBitmap.recycle();
                originalBitmap = null;

                // 创建最终位图并绘制
                scaledBitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(scaledBitmap);
                canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(tempBitmap, left, top, null);
                tempBitmap.recycle();
                tempBitmap = null;

                // 转换为RGB565
                ByteArrayOutputStream baos = new ByteArrayOutputStream(115200);
                for (int y = 0; y < 240; y++) {
                    for (int x = 0; x < 240; x++) {
                        int pixel = scaledBitmap.getPixel(x, y);
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        int rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
                        baos.write(rgb565 & 0xFF);
                        baos.write((rgb565 >> 8) & 0xFF);
                    }
                }
                byte[] imageData = baos.toByteArray();
                baos.close();

                // 确保处理完成后再回收
                scaledBitmap.recycle();
                scaledBitmap = null;

                runOnUiThread(() -> {
                    log("图片处理完成，准备上传");
                    uploadImageData(imageData);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showError("处理失败: " + e.getMessage());
                    finishUpload(false);
                });
            } finally {
                // 安全回收资源
                if (originalBitmap != null && !originalBitmap.isRecycled()) {
                    originalBitmap.recycle();
                }
                if (tempBitmap != null && !tempBitmap.isRecycled()) {
                    tempBitmap.recycle();
                }
                if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                    scaledBitmap.recycle();
                }
            }
        });
    }

    private void uploadImageData(byte[] imageData) {
        // 生成文件索引
        byte fileIndex = (byte) (System.currentTimeMillis() % 256);
        log("开始上传图片，索引: " + (fileIndex & 0xFF) + ", 大小: " + imageData.length + " 字节");

        // 重置进度条
        binding.uploadProgress.setProgress(0);
        binding.uploadProgress.setVisibility(View.VISIBLE);

        // 发送开始传输命令
        sendCommand(CMD_START_TRANSFER, new byte[]{fileIndex});

        // 分块发送数据
        executorService.execute(() -> {
            try {
                // 等待设备准备就绪
                Thread.sleep(1000);

                // 更小的块大小和更长的延迟以防止缓冲区溢出
                int chunkSize = 128; // 更小的块大小，避免BLE缓冲区溢出
                int totalChunks = (int) Math.ceil(imageData.length / (double)chunkSize);

                // 声明为final，以便在lambda表达式中使用
                final int totalChunksCount = totalChunks;

                int chunksSent = 0;
                int offset = 0;

                runOnUiThread(() -> log("开始传输数据，总块数: " + totalChunksCount));

                // 监控传输的变量
                long startTime = System.currentTimeMillis();
                long lastProgressUpdate = 0;

                while (offset < imageData.length) {
                    int remaining = imageData.length - offset;
                    int currentChunkSize = Math.min(chunkSize, remaining);

                    byte[] chunk = new byte[currentChunkSize];
                    System.arraycopy(imageData, offset, chunk, 0, currentChunkSize);

                    // 写入数据块
                    writeDataChunk(chunk);

                    // 更新进度
                    offset += currentChunkSize;
                    chunksSent++;

                    final int progress = (int)((offset * 100L) / imageData.length);
                    final int currentSent = chunksSent; // 创建一个有效final变量

                    // 限制UI更新频率，避免UI线程过载
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastProgressUpdate > 100) {  // 每100ms更新一次UI
                        lastProgressUpdate = currentTime;
                        runOnUiThread(() -> {
                            binding.uploadProgress.setProgress(progress);
                            if (progress % 10 == 0) { // 每10%记录一次日志
                                log("上传进度: " + progress + "% (块 " + currentSent + "/" + totalChunksCount + ")");
                            }
                        });
                    }

                    // 控制发送速度，防止设备接收缓冲区溢出
                    Thread.sleep(50 + (currentChunkSize / 16)); // 动态调整延迟
                }

                // 所有数据发送完毕，等待一段时间确保设备处理完成
                Thread.sleep(500);

                // 为最终的log创建一个有效final变量
                final int finalChunksSent = chunksSent;

                // 最终进度更新
                runOnUiThread(() -> {
                    log("所有数据发送完成 (" + finalChunksSent + " 块)，等待设备处理...");
                    binding.uploadProgress.setProgress(100);
                });

                // 等待设备处理
                Thread.sleep(1000);

                // 发送结束传输命令
                runOnUiThread(() -> {
                    log("发送结束传输命令");
                    sendCommand(CMD_END_TRANSFER, new byte[0]);
                });

                // 计算耗时
                final long totalTime = System.currentTimeMillis() - startTime;

                runOnUiThread(() -> {
                    log(String.format("图片上传完成，总耗时: %.1f 秒", totalTime/1000f));

                    // 延迟刷新图片列表，给设备处理时间
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        binding.uploadProgress.setVisibility(View.GONE);
                        refreshImageList();
                        getDeviceStatus();
                    }, 2000);
                });

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                runOnUiThread(() -> {
                    showError("上传失败: " + errorMsg);
                    binding.uploadProgress.setVisibility(View.GONE);
                    e.printStackTrace();
                });
            }
        });
    }

    private void writeDataChunk(byte[] data) {
        if (dataCharacteristic == null || bluetoothGatt == null) {
            runOnUiThread(() -> log("无法发送数据，未连接"));
            return;
        }

        try {
            // 同步等待特性写入完成
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            mainHandler.post(() -> {
                try {
                    dataCharacteristic.setValue(data);
                    success[0] = bluetoothGatt.writeCharacteristic(dataCharacteristic);
                    latch.countDown();
                } catch (SecurityException e) {
                    runOnUiThread(() -> showError("没有蓝牙权限"));
                    latch.countDown();
                }
            });

            // 等待写入完成，但不超过100ms
            latch.await(100, TimeUnit.MILLISECONDS);

            if (!success[0]) {
                // 写入失败，重试一次
                Thread.sleep(50);
                mainHandler.post(() -> {
                    try {
                        dataCharacteristic.setValue(data);
                        bluetoothGatt.writeCharacteristic(dataCharacteristic);
                    } catch (SecurityException e) {
                        // 已经在上面处理过了
                    }
                });
            }
        } catch (Exception e) {
            runOnUiThread(() -> log("数据块写入失败: " + e.getMessage()));
        }
    }

    private void sendCommand(byte command, byte[] data) {
        if (commandCharacteristic == null || bluetoothGatt == null) {
            log("无法发送命令，未连接");
            return;
        }

        byte[] commandData = new byte[1 + data.length];
        commandData[0] = command;
        System.arraycopy(data, 0, commandData, 1, data.length);

        mainHandler.post(() -> {
            try {
                commandCharacteristic.setValue(commandData);
                boolean success = bluetoothGatt.writeCharacteristic(commandCharacteristic);

                if (!success) {
                    // If sending fails, try to retry after delay
                    log("命令发送失败，准备重试: " + getCommandName(command));
                    mainHandler.postDelayed(() -> {
                        try {
                            commandCharacteristic.setValue(commandData);
                            boolean retrySuccess = bluetoothGatt.writeCharacteristic(commandCharacteristic);
                            log("重试发送命令: " + getCommandName(command) + (retrySuccess ? " 成功" : " 再次失败"));
                        } catch (SecurityException e) {
                            showError("没有蓝牙权限");
                        }
                    }, 200);
                } else {
                    log("发送命令: " + getCommandName(command) + " 成功");
                }
            } catch (SecurityException e) {
                showError("没有蓝牙权限");
            }
        });
    }

    private String getCommandName(byte command) {
        switch (command) {
            case CMD_START_TRANSFER: return "开始传输";
            case CMD_IMAGE_DATA: return "图片数据";
            case CMD_END_TRANSFER: return "结束传输";
            case CMD_DELETE_IMAGE: return "删除图片";
            case CMD_REORDER_IMAGES: return "重排图片";
            case CMD_GET_IMAGE_LIST: return "获取列表";
            case CMD_SET_DISPLAY: return "设置显示";
            case CMD_GET_STATUS: return "获取状态";
            default: return "未知命令";
        }
    }

    private void refreshImageList() {
        log("正在刷新图片列表...");
        binding.progressBar.setVisibility(View.VISIBLE);
        sendCommand(CMD_GET_IMAGE_LIST, new byte[0]);

        // 添加超时处理，以防设备没有响应
        mainHandler.postDelayed(() -> {
            if (binding.progressBar.getVisibility() == View.VISIBLE) {
                binding.progressBar.setVisibility(View.GONE);
                log("获取图片列表超时");
            }
        }, 5000);
    }

    private void getDeviceStatus() {
        log("正在获取设备状态...");
        binding.progressBar.setVisibility(View.VISIBLE);
        sendCommand(CMD_GET_STATUS, new byte[0]);

        // 添加超时处理
        mainHandler.postDelayed(() -> {
            if (binding.progressBar.getVisibility() == View.VISIBLE) {
                binding.progressBar.setVisibility(View.GONE);
                log("获取设备状态超时");
            }
        }, 5000);
    }

    private void handleNotification(byte[] data) {
        try {
            String jsonStr = new String(data);
            Log.d(TAG, "收到通知数据: " + jsonStr);

            // Check for empty data
            if (jsonStr.isEmpty()) {
                runOnUiThread(() -> log("收到空响应"));
                return;
            }

            // Log the original JSON
            final String originalJsonStr = jsonStr;
            runOnUiThread(() -> log("收到响应: " + originalJsonStr));

            // Try to fix incomplete JSON
            if (!jsonStr.trim().endsWith("}") && !jsonStr.trim().endsWith("]}") && jsonStr.contains("{")) {
                runOnUiThread(() -> log("JSON不完整，尝试修复"));

                // Find the last position of a complete JSON object's closing bracket
                int lastBrace = jsonStr.lastIndexOf("}");
                if (lastBrace > 0) {
                    jsonStr = jsonStr.substring(0, lastBrace + 1);
                    final String fixedJson = jsonStr;
                    runOnUiThread(() -> log("修复后: " + fixedJson));
                } else {
                    runOnUiThread(() -> log("无法修复JSON"));
                    return;
                }
            }

            // Parse JSON
            final String finalJsonStr = jsonStr; // Create final copy for lambda
            JSONObject json = new JSONObject(jsonStr);

            // Image list response - handle multiple possible formats
            if (json.has("images") || json.has("img") || json.has("count")) {
                handleImageListResponse(json);
            }
            // Status response - handle multiple possible formats
            else if (json.has("status") || json.has("cur") || json.has("c") ||
                    json.has("currentImage") || json.has("tot") || json.has("t")) {
                handleStatusResponse(json);
            }
            // Unknown response
            else {
                runOnUiThread(() -> log("收到未识别的响应格式: " + finalJsonStr));
            }
        } catch (Exception e) {
            final String errorMsg = e.getMessage();
            final String dataStr = new String(data);

            runOnUiThread(() -> {
                log("解析响应失败: " + errorMsg);
                log("原始数据: " + dataStr);
                log("十六进制: " + bytesToHex(data));
                binding.progressBar.setVisibility(View.GONE);
            });

            e.printStackTrace();
        }
    }

    private void handleImageListResponse(JSONObject json) throws Exception {
        // Clear current list
        imageList.clear();

        // Three possible formats:
        // 1. {"images": [...]} - Full format
        // 2. {"img": [...]} - Simplified format
        // 3. {"count": n, "cur": n} - Minimal format with only count information

        if (json.has("count")) {
            // Only received image count info, need to request full list again
            int count = json.getInt("count");
            int current = json.optInt("cur", 0);

            runOnUiThread(() -> {
                log("收到图片计数信息: 总数=" + count + ", 当前=" + current);
                log("等待1秒后重新请求完整列表...");
                binding.progressBar.setVisibility(View.GONE);

                // Request full list after delay
                mainHandler.postDelayed(() -> {
                    sendCommand(CMD_GET_IMAGE_LIST, new byte[0]);
                }, 1000);
            });
            return;
        }

        // Process image list data
        JSONArray images = null;
        if (json.has("images")) {
            images = json.getJSONArray("images");
        } else if (json.has("img")) {
            images = json.getJSONArray("img");
        }

        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.getJSONObject(i);
                ImageInfo info = new ImageInfo();

                // Support fields for both full and simplified formats
                if (img.has("index")) {
                    info.index = img.getInt("index");
                } else if (img.has("i")) {
                    info.index = img.getInt("i");
                } else {
                    info.index = i; // Default to array index
                }

                // Handle name field
                if (img.has("name")) {
                    info.name = img.getString("name");
                } else if (img.has("n")) {
                    info.name = img.getString("n");
                } else {
                    info.name = String.valueOf(info.index); // Default to index as name
                }

                // Ensure correct name format
                if (!info.name.startsWith("/img_") && !info.name.startsWith("img_")) {
                    info.name = "/img_" + info.name;
                }
                if (!info.name.endsWith(".bin")) {
                    info.name = info.name + ".bin";
                }

                // Handle active status
                if (img.has("active")) {
                    info.active = img.getBoolean("active");
                } else if (img.has("a")) {
                    info.active = img.getInt("a") == 1;
                } else {
                    info.active = true; // Default to active state
                }

                imageList.add(info);
            }

            runOnUiThread(() -> {
                imageAdapter.notifyDataSetChanged();
                log("获取到 " + imageList.size() + " 张图片");
                binding.progressBar.setVisibility(View.GONE);

                // Show or hide "No images" message
                binding.noImagesText.setVisibility(imageList.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void handleStatusResponse(JSONObject json) throws Exception {
        // 创建final副本用于lambda表达式
        final int currentImage = json.optInt("c", json.optInt("cur", -1));
        final int totalImages = json.optInt("t", json.optInt("tot", -1));
        final long freeSpace = json.optLong("free", -1) * 1024; // 直接转换为字节

        runOnUiThread(() -> {
            String statusText;
            if (freeSpace >= 0) {
                statusText = String.format(Locale.getDefault(),
                        "当前: %d/%d, 剩余: %.1f MB",
                        currentImage, totalImages,
                        freeSpace / (1024.0 * 1024.0));
            } else {
                statusText = String.format("当前: %d/%d", currentImage, totalImages);
            }

            binding.connectionStatus.setText("已连接 - " + statusText);
            binding.progressBar.setVisibility(View.GONE);
        });
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    private void onImageClick(ImageInfo image) {
        sendCommand(CMD_SET_DISPLAY, new byte[]{(byte) image.index});
        Snackbar.make(binding.getRoot(), "设置显示: " + image.name, Snackbar.LENGTH_SHORT).show();
    }

    private void onImageLongClick(ImageInfo image) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除图片")
                .setMessage("确定要删除 " + image.name + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    String indexStr = image.name.replace("/img_", "").replace(".bin", "");
                    byte index = Byte.parseByte(indexStr);
                    sendCommand(CMD_DELETE_IMAGE, new byte[]{index});
                    refreshImageList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timestamp + " - " + message + "\n";
        binding.logText.append(logMessage);

        // 自动滚动到底部
        binding.logScrollView.post(() -> {
            binding.logScrollView.fullScroll(View.FOCUS_DOWN);
        });

        Log.d(TAG, message);
    }

    private void showError(String message) {
        log("错误: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void cleanup() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭GATT失败", e);
            }
            bluetoothGatt = null;
        }
        commandCharacteristic = null;
        dataCharacteristic = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isScanning) {
            stopScan();
        }
        cleanup();
        executorService.shutdown();
    }
}