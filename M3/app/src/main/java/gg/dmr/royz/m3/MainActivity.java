/*
 * Mon3tr Emoji - ESP32-C3 BLE Project and Android APP for custom display
 * Copyright (C) 2025  RoyZ-iwnl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * 本程序是自由软件，在自由软件联盟发布的GNU通用公共许可证条款下，
 * 你可以对其进行再发布及修改。协议版本为第三版或（随你）更新的版本。
 * 
 * 本程序的发布是希望它能够有用，但不负任何担保责任；
 * 具体详情请参见GNU通用公共许可证。
 * 
 * 你理当已收到一份GNU通用公共许可证的副本。
 * 如果没有，请查阅<https://www.gnu.org/licenses/>
 * 
 * Contact/联系方式: Roy@DMR.gg
 */
package gg.dmr.royz.m3;

import static gg.dmr.royz.m3.utils.ImageConverter.FORMAT_GIF;
import static gg.dmr.royz.m3.utils.ImageConverter.getFormatType;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import gg.dmr.royz.m3.adapter.ImageListAdapter;
import gg.dmr.royz.m3.bluetooth.BleManager;
import gg.dmr.royz.m3.model.DeviceImage;
import gg.dmr.royz.m3.model.DeviceStatus;
import gg.dmr.royz.m3.utils.ImageConverter;
import gg.dmr.royz.m3.utils.LogUtil;
import gg.dmr.royz.m3.view.LogDisplayView;

public class MainActivity extends AppCompatActivity implements ImageListAdapter.OnItemClickListener {

    // 请求码
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final String[] BLUETOOTH_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // ViewModel
    private MainViewModel viewModel;

    // UI组件
    private Button connectButton;
    private Button uploadButton;
    private Button displayButton;
    private TextView batteryLevelView;
    private TextView storageUsageView;
    private TextView connectionStatusView;
    private RecyclerView imageRecyclerView;
    private LogDisplayView logDisplayView;
    private FrameLayout progressOverlay;
    private ProgressBar progressBar;
    private TextView progressText;
    private Toolbar toolbar;

    // 适配器
    private ImageListAdapter imageAdapter;

    // 图片选择结果处理
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleImageSelection(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 设置工具栏
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化视图
        initViews();

        // 设置观察者
        setupObservers();

        // 检查权限
        checkBluetoothPermissions();
    }

    private void initViews() {
        // 获取视图引用
        connectButton = findViewById(R.id.connect_button);
        uploadButton = findViewById(R.id.upload_button);
        displayButton = findViewById(R.id.display_button);
        batteryLevelView = findViewById(R.id.battery_level);
        storageUsageView = findViewById(R.id.storage_usage);
        connectionStatusView = findViewById(R.id.connection_status);
        imageRecyclerView = findViewById(R.id.image_recycler_view);
        logDisplayView = findViewById(R.id.log_display);
        progressOverlay = findViewById(R.id.progress_overlay);
        progressBar = findViewById(R.id.progress_bar);
        progressText = findViewById(R.id.progress_text);

        // 设置按钮点击事件
        connectButton.setOnClickListener(v -> onConnectButtonClick());
        uploadButton.setOnClickListener(v -> onUploadButtonClick());
        displayButton.setOnClickListener(v -> onDisplayButtonClick());
        findViewById(R.id.refresh_button).setOnClickListener(v -> onRefreshButtonClick());

        // 设置图片列表
        imageAdapter = new ImageListAdapter();
        imageAdapter.setOnItemClickListener(this);
        imageRecyclerView.setAdapter(imageAdapter);
        imageRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // 设置拖拽排序
        setupDragAndDrop();
    }

    private void setupObservers() {
        // 观察连接状态
        viewModel.getConnectionState().observe(this, state -> {
            updateConnectionStatus(state);
            updateButtonState(state);
        });

        // 观察图片列表
        viewModel.getImageList().observe(this, images -> {
            imageAdapter.setImageList(images);
            // 更新当前显示的图片
            DeviceStatus status = viewModel.getDeviceStatus().getValue();
            if (status != null) {
                imageAdapter.setCurrentDisplayIndex(status.getCurrentImage());
            }
        });

        // 观察设备状态
        viewModel.getDeviceStatus().observe(this, status -> {
            if (status != null) {
                // 将电池电量显示改为开机时长
                batteryLevelView.setText(status.getFormattedUptime());
                String currentStorage = status.getFormattedStorage(); // 可能是 "--" 或 "500KB"
                storageUsageView.setText(getString(R.string.storage_usage_format, currentStorage));
                imageAdapter.setCurrentDisplayIndex(status.getCurrentImage());
            }
        });

        // 观察传输进度
        viewModel.getTransferProgress().observe(this, progress -> {
            progressBar.setProgress(progress);
            progressText.setText(progress + "%");
        });

        // 观察是否正在传输
        viewModel.getIsTransferring().observe(this, isTransferring -> {
            progressOverlay.setVisibility(isTransferring ? View.VISIBLE : View.GONE);
        });
    }

    private void updateConnectionStatus(BleManager.State state) {
        String statusText;
        switch (state) {
            case CONNECTED:
                statusText = "已连接";
                break;
            case CONNECTING:
                statusText = "正在连接";
                break;
            case SCANNING:
                statusText = "正在扫描";
                break;
            case DISCOVERING:
                statusText = "正在发现服务";
                break;
            case TRANSMITTING:
                statusText = "正在传输";
                break;
            default:
                statusText = "未连接";
                break;
        }
        connectionStatusView.setText(statusText);
    }

    private void updateButtonState(BleManager.State state) {
        boolean isConnected = state == BleManager.State.CONNECTED;
        boolean isDisconnected = state == BleManager.State.DISCONNECTED;
        boolean isTransmitting = state == BleManager.State.TRANSMITTING;

        // 更新按钮状态
        connectButton.setText(isConnected ? "断开连接" : "扫描连接");
        connectButton.setEnabled(!isTransmitting);
        uploadButton.setEnabled(isConnected && !isTransmitting);
        displayButton.setEnabled(isConnected && !isTransmitting);
    }

    private void onConnectButtonClick() {
        BleManager.State state = viewModel.getConnectionState().getValue();
        if (state == BleManager.State.CONNECTED) {
            // 已连接，断开连接
            viewModel.disconnect();
        } else if (state == BleManager.State.DISCONNECTED) {
            // 未连接，开始扫描
            viewModel.startScan();
        }
    }

    private void onUploadButtonClick() {
        // 先刷新图片列表，确保获取最新数据
        viewModel.refreshImageList();

        // 增加延迟时间，确保图片列表完全加载
        new Handler().postDelayed(() -> {
            // 再次检查列表是否已更新
            List<DeviceImage> currentImages = viewModel.getImageList().getValue();
            if (currentImages == null || currentImages.isEmpty()) {
                // 如果列表仍为空，再次刷新并等待
                viewModel.refreshImageList();
                new Handler().postDelayed(() -> {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    imagePickerLauncher.launch(intent);
                }, 500); // 再延迟500ms
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                imagePickerLauncher.launch(intent);
            }
        }, 500); // 将延迟从200ms增加到500ms
    }

    private void onDisplayButtonClick() {
        List<DeviceImage> selectedImages = imageAdapter.getSelectedImages();
        if (selectedImages.size() == 1) {
            viewModel.setDisplayImage(selectedImages.get(0).getIndex());
            imageAdapter.clearSelection();
        } else {
            Toast.makeText(this, "请选择一张图片", Toast.LENGTH_SHORT).show();
        }
    }

    private void onRefreshButtonClick() {
        viewModel.refreshImageList();
        viewModel.refreshDeviceStatus();
    }

    // 处理图片选择 - 简化后的版本，自动根据格式处理
    private void handleImageSelection(Uri imageUri) {
        try {
            // 获取图片格式类型
            byte formatType = getFormatType(this, imageUri);

            // 如果是 GIF 格式
            if (formatType == FORMAT_GIF) {
                showIndexSelectionDialogForGif(imageUri);
                return;
            }

            // 非 GIF 格式的处理（PNG和JPG都转换为Bitmap，PNG会自动去除透明度）
            Bitmap bitmap = ImageConverter.loadImageFromUri(this, imageUri);
            if (bitmap == null) {
                LogUtil.logError("加载图片失败");
                Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
                return;
            }

            // 直接询问用户要上传到哪个索引位置
            showIndexSelectionDialog(bitmap);
        } catch (Exception e) {
            LogUtil.logError("处理图片失败: " + e.getMessage());
            Toast.makeText(this, "处理图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 为GIF文件显示索引选择对话框
    private void showIndexSelectionDialogForGif(final Uri gifUri) {
        // 构建索引列表
        List<DeviceImage> images = viewModel.getImageList().getValue();
        final List<String> options = new ArrayList<>();

        if (images != null) {
            for (DeviceImage image : images) {
                // 正确提取并显示文件索引
                int combinedIndex = image.getIndex() & 0xFF;
                int fileIndex = combinedIndex & 0x0F; // 提取低4位作为文件索引

                options.add("替换图片 " + fileIndex +
                        (image.getName().isEmpty() ? "" : " (" + image.getName() + ")"));
            }
        }

        // 添加新图片选项
        options.add("添加为新图片");

        // 显示位置选择对话框
        new AlertDialog.Builder(this)
                .setTitle("选择GIF上传位置")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    // 确定目标索引
                    byte targetIndex;
                    if (which < options.size() - 1) {
                        // 替换现有图片 - 正确提取文件索引
                        int combinedIndex = images.get(which).getIndex() & 0xFF;
                        targetIndex = (byte)(combinedIndex & 0x0F); // 提取低4位
                    } else {
                        // 添加为新图片 - 寻找第一个空闲的索引
                        targetIndex = findFirstAvailableIndex(images);
                    }

                    // 转换并上传GIF文件
                    viewModel.uploadGifFromUri(gifUri, targetIndex);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 为普通图片（JPG/PNG）显示索引选择对话框
    private void showIndexSelectionDialog(final Bitmap bitmap) {
        // 在显示对话框前再次刷新列表
        viewModel.refreshImageList();

        // 稍微延迟以确保数据更新
        new Handler().postDelayed(() -> {
            // 获取最新的图片列表
            List<DeviceImage> images = viewModel.getImageList().getValue();
            final List<String> indexOptions = new ArrayList<>();

            LogUtil.log("准备显示选择对话框，图片列表大小: " + (images != null ? images.size() : 0));

            if (images != null) {
                for (DeviceImage image : images) {
                    // 正确提取并显示文件索引
                    int combinedIndex = image.getIndex() & 0xFF;
                    int fileIndex = combinedIndex & 0x0F; // 提取低4位作为文件索引

                    indexOptions.add("替换图片 " + fileIndex +
                            (image.getName().isEmpty() ? "" : " (" + image.getName() + ")"));

                    LogUtil.log("图片选项: 索引=" + fileIndex + ", 组合索引=0x" +
                            Integer.toHexString(combinedIndex).toUpperCase());
                }
            }

            // 添加新图片选项
            indexOptions.add("添加为新图片");

            new AlertDialog.Builder(this)
                    .setTitle("选择图片位置")
                    .setItems(indexOptions.toArray(new String[0]), (dialog, which) -> {
                        byte targetIndex;
                        if (which < indexOptions.size() - 1) {
                            // 替换现有图片 - 正确提取文件索引
                            int combinedIndex = images.get(which).getIndex() & 0xFF;
                            targetIndex = (byte)(combinedIndex & 0x0F); // 提取低4位
                            LogUtil.log("选择替换现有图片，文件索引: " + targetIndex);
                        } else {
                            // 添加为新图片 - 寻找第一个空闲的索引
                            targetIndex = findFirstAvailableIndex(images);
                            LogUtil.log("选择添加为新图片，分配索引: " + targetIndex);
                        }

                        // 直接上传为JPG格式
                        viewModel.uploadImage(bitmap, targetIndex);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }, 300); // 300ms延迟确保数据更新
    }



    // 寻找第一个可用的索引
    private byte findFirstAvailableIndex(List<DeviceImage> images) {
        // 添加调试日志
        LogUtil.log("查找可用索引，当前图片列表大小: " + (images != null ? images.size() : 0));

        // 如果图片列表为空，从0开始查找
        if (images == null) {
            images = new ArrayList<>();
            LogUtil.log("图片列表为null，创建空列表");
        }

        // 创建一个布尔数组来标记已使用的索引
        boolean[] usedIndices = new boolean[16];

        // 标记已使用的索引 - 关键修改：正确提取文件索引
        for (DeviceImage image : images) {
            // 从组合索引中提取文件索引（低4位）
            int combinedIndex = image.getIndex() & 0xFF;
            int fileIndex = combinedIndex & 0x0F; // 提取低4位作为文件索引

            if (fileIndex < usedIndices.length) {
                usedIndices[fileIndex] = true;
                LogUtil.log("标记已使用索引: " + fileIndex + " (组合索引: 0x" +
                        Integer.toHexString(combinedIndex).toUpperCase() + ")");
            }
        }

        // 找到第一个未使用的索引
        for (int i = 0; i < usedIndices.length; i++) {
            if (!usedIndices[i]) {
                LogUtil.log("找到可用索引: " + i);
                return (byte) i;
            }
        }

        LogUtil.log("没有找到可用索引，返回最后一个");
        return (byte) (usedIndices.length - 1);
    }

    // 设置拖拽排序
    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                // 获取拖动的起始和目标位置
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                // 更新适配器
                imageAdapter.swapItems(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不支持滑动删除
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return imageAdapter.isDragEnabled();
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 拖动结束，提交重排序命令
                if (imageAdapter.isDragEnabled()) {
                    byte[] newOrder = imageAdapter.getReorderedIndices();
                    viewModel.reorderImages(newOrder);
                }
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(imageRecyclerView);
    }

    // 处理长按事件，进入编辑模式
    @Override
    public void onItemLongClick(int position, DeviceImage image) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("图片操作");
        String[] options = {"删除图片", "开始拖拽排序", "取消"};

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 删除图片
                    new AlertDialog.Builder(this)
                            .setTitle("确认删除")
                            .setMessage("确定要删除图片 " + image.getIndex() + " 吗？")
                            .setPositiveButton("确定", (d, w) -> {
                                viewModel.deleteImage(image.getIndex());
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    break;
                case 1: // 开始拖拽排序
                    imageAdapter.enableDrag(true);
                    Toast.makeText(this, "拖拽模式已开启，长按图片拖动排序", Toast.LENGTH_SHORT).show();
                    // 添加完成按钮到工具栏
                    getSupportActionBar().setTitle("拖拽排序模式");
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel);
                    toolbar.setNavigationOnClickListener(v -> {
                        // 退出拖拽模式
                        imageAdapter.enableDrag(false);
                        viewModel.refreshImageList();
                        getSupportActionBar().setTitle(R.string.app_name);
                        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                        toolbar.setNavigationOnClickListener(null);
                    });
                    break;
                case 2: // 取消
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }

    // 处理点击事件，选择/取消选择图片
    @Override
    public void onItemClick(int position, DeviceImage image) {
        imageAdapter.toggleSelection(position);
    }

    // 检查蓝牙权限
    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean allGranted = true;
            for (String permission : BLUETOOTH_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, BLUETOOTH_PERMISSIONS,
                        REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                checkBluetoothEnabled();
            }
        } else {
            // 对于Android 11及以下，只需检查位置权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                checkBluetoothEnabled();
            }
        }
    }

    // 检查蓝牙是否启用
    private void checkBluetoothEnabled() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // 蓝牙未启用，请求启用
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivity(enableBtIntent);
            } catch (Exception e) {
                Toast.makeText(this, "无法启用蓝牙", Toast.LENGTH_SHORT).show();
            }
        }
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
                checkBluetoothEnabled();
            } else {
                Toast.makeText(this, "需要蓝牙权限才能使用此应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.disconnect();
    }
}
