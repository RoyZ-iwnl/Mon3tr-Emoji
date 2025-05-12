package gg.dmr.royz.m3;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


import gg.dmr.royz.m3.utils.BleConstants;
import gg.dmr.royz.m3.utils.ImageConverter;
import gg.dmr.royz.m3.utils.JsonParser;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BleManager.BleCallback {
    private static final int REQUEST_PERMISSIONS = 1;
    private BleManager bleManager;
    private ImageAdapter imageAdapter;
    private List<ImageItem> imageList = new ArrayList<>();
    private boolean isConnected = false;

    // UI组件
    private TextView statusText;
    private MaterialButton connectButton;
    private FloatingActionButton fab;
    private ProgressDialog progressDialog;

    // 图片选择器
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleImageSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        initializeViews();

        // 初始化BLE管理器
        bleManager = new BleManager(this, this);

        // 设置RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imageAdapter = new ImageAdapter(this, imageList);
        recyclerView.setAdapter(imageAdapter);

        // 检查权限
        checkPermissions();
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
        } else {
            // 所有权限都已授予，可以开始扫描
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "需要蓝牙和位置权限才能使用此应用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeViews() {
        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化其他视图
        statusText = findViewById(R.id.statusText);
        connectButton = findViewById(R.id.connectButton);
        fab = findViewById(R.id.fab);

        // 设置连接按钮点击事件
        connectButton.setOnClickListener(v -> {
            if (!isConnected) {
                startScan();
            } else {
                disconnectDevice();
            }
        });

        // 设置FAB点击事件
        fab.setOnClickListener(v -> {
            if (isConnected) {
                imagePickerLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleImageSelection(Uri uri) {
        if (uri != null) {
            // 显示进度对话框
            ProgressDialog processDialog = new ProgressDialog(this);
            processDialog.setTitle("处理图片");
            processDialog.setMessage("请稍候...");
            processDialog.setCancelable(false);
            processDialog.show();

            // 在后台线程处理图片
            new Thread(() -> {
                try {
                    byte[] imageData = ImageConverter.convertImage(this, uri);
                    runOnUiThread(() -> {
                        processDialog.dismiss();
                        uploadImage(imageData);
                    });
                } catch (Exception e) {
                    Log.e("MainActivity", "图片处理失败", e);
                    runOnUiThread(() -> {
                        processDialog.dismiss();
                        Toast.makeText(this,
                                "图片处理失败: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        }
    }

    private void uploadImage(byte[] imageData) {
        ProgressDialog uploadDialog = new ProgressDialog(this);
        uploadDialog.setTitle("正在上传");
        uploadDialog.setMessage("请稍候...");
        uploadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        uploadDialog.setMax(100);
        uploadDialog.setCancelable(false);
        uploadDialog.show();

        new Thread(() -> {
            try {
                // 开始传输
                bleManager.sendCommand(BleConstants.CMD_START_TRANSFER,
                        new byte[]{(byte) imageList.size()});
                Thread.sleep(100); // 等待命令处理

                // 分块传输数据
                int totalSize = imageData.length;
                int chunkSize = 240; // 每次发送240字节
                int sentBytes = 0;

                while (sentBytes < totalSize) {
                    int length = Math.min(chunkSize, totalSize - sentBytes);
                    byte[] chunk = new byte[length];
                    System.arraycopy(imageData, sentBytes, chunk, 0, length);
                    bleManager.sendData(chunk);

                    // 更新进度
                    sentBytes += length;
                    final int progress = (sentBytes * 100) / totalSize;
                    runOnUiThread(() -> uploadDialog.setProgress(progress));

                    // 添加延迟
                    Thread.sleep(50);
                }

                // 结束传输
                Thread.sleep(100); // 确保最后的数据发送完成
                bleManager.sendCommand(BleConstants.CMD_END_TRANSFER, new byte[0]);

                runOnUiThread(() -> {
                    uploadDialog.dismiss();
                    Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("MainActivity", "上传失败", e);
                runOnUiThread(() -> {
                    uploadDialog.dismiss();
                    Toast.makeText(this,
                            "上传失败: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void startScan() {
        // 显示扫描对话框，但允许取消
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("扫描设备中");
        progressDialog.setMessage("正在搜索RoyZ-Mon3tr...\n点击返回键取消");
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(dialog -> {
            bleManager.stopScan();
            connectButton.setEnabled(true);
            statusText.setText("扫描已取消");
        });
        progressDialog.show();

        // 开始扫描
        bleManager.startScan();

        // 更新UI
        connectButton.setEnabled(false);
        statusText.setText("正在扫描...");

        // 添加日志记录
        Log.d("MainActivity", "开始扫描设备");
    }

    private void disconnectDevice() {
        bleManager.disconnect();
        updateConnectionState(false);
    }

    private void updateConnectionState(boolean connected) {
        isConnected = connected;
        runOnUiThread(() -> {
            statusText.setText(connected ? "已连接" : "未连接");
            connectButton.setText(connected ? "断开连接" : "扫描设备");
            connectButton.setEnabled(true);
            fab.setVisibility(connected ? View.VISIBLE : View.GONE);

            if (!connected) {
                imageList.clear();
                imageAdapter.notifyDataSetChanged();
            }
        });
    }

    // BLE回调实现
    @Override
    public void onDeviceFound(BluetoothDevice device) {
        runOnUiThread(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            Toast.makeText(this, "找到设备，正在连接...", Toast.LENGTH_SHORT).show();
            bleManager.connect(device);
        });
    }

    @Override
    public void onConnected() {
        updateConnectionState(true);
        // 获取图片列表
        bleManager.sendCommand(BleConstants.CMD_GET_IMAGE_LIST, new byte[0]);
    }

    @Override
    public void onDisconnected() {
        updateConnectionState(false);
    }

    @Override
    public void onDataReceived(byte[] data) {
        // 处理接收到的数据（JSON格式）
        String jsonStr = new String(data);
        try {
            // 解析图片列表数据并更新UI
            List<ImageItem> newList = JsonParser.parseImageList(jsonStr);
            runOnUiThread(() -> {
                imageList.clear();
                imageList.addAll(newList);
                imageAdapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(this, "数据解析错误: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.disconnect();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}