package gg.dmr.royz.m3;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import gg.dmr.royz.m3.bluetooth.BleManager;
import gg.dmr.royz.m3.bluetooth.CommandHandler;
import gg.dmr.royz.m3.bluetooth.Constants;
import gg.dmr.royz.m3.model.DeviceImage;
import gg.dmr.royz.m3.model.DeviceStatus;
import gg.dmr.royz.m3.utils.ImageConverter;
import gg.dmr.royz.m3.utils.LogUtil;

/**
 * 主界面ViewModel
 * 管理应用的业务逻辑和数据状态
 */
public class MainViewModel extends AndroidViewModel implements BleManager.BleCallback {

    // 蓝牙管理器
    private final BleManager bleManager;

    // LiveData
    private final MutableLiveData<BleManager.State> connectionState = new MutableLiveData<>(BleManager.State.DISCONNECTED);
    private final MutableLiveData<List<DeviceImage>> imageList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<DeviceStatus> deviceStatus = new MutableLiveData<>();
    private final MutableLiveData<Integer> transferProgress = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isTransferring = new MutableLiveData<>(false);

    // 构造函数
    public MainViewModel(@NonNull Application application) {
        super(application);

        // 初始化蓝牙管理器
        bleManager = BleManager.getInstance(application);
        bleManager.setCallback(this);
    }

    // 获取连接状态
    public LiveData<BleManager.State> getConnectionState() {
        return connectionState;
    }

    // 获取图片列表
    public LiveData<List<DeviceImage>> getImageList() {
        return imageList;
    }

    // 获取设备状态
    public LiveData<DeviceStatus> getDeviceStatus() {
        return deviceStatus;
    }

    // 获取传输进度
    public LiveData<Integer> getTransferProgress() {
        return transferProgress;
    }

    // 获取是否正在传输
    public LiveData<Boolean> getIsTransferring() {
        return isTransferring;
    }

    // 开始扫描设备
    public void startScan() {
        LogUtil.log("开始扫描设备...");
        bleManager.startScan();
    }

    // 连接设备
    public void connectDevice(BluetoothDevice device) {
        LogUtil.log("连接到设备: " + device.getName());
        bleManager.connect(device);
    }

    // 断开连接
    public void disconnect() {
        LogUtil.log("断开设备连接");
        bleManager.disconnect();
    }

    // 刷新图片列表
    public void refreshImageList() {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法获取图片列表");
            return;
        }

        LogUtil.log("请求图片列表...");
        bleManager.sendCommand(CommandHandler.cmdGetImageList());
    }

    // 刷新设备状态
    public void refreshDeviceStatus() {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法获取设备状态");
            return;
        }

        LogUtil.log("请求设备状态...");
        bleManager.sendCommand(CommandHandler.cmdGetStatus());
    }

    // 删除图片
    public void deleteImage(byte index) {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法删除图片");
            return;
        }

        LogUtil.log("删除图片: " + index);
        bleManager.sendCommand(CommandHandler.cmdDeleteImage(index));
    }

    // 设置显示图片
    public void setDisplayImage(byte index) {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法设置显示图片");
            return;
        }

        LogUtil.log("设置显示图片: " + index);
        bleManager.sendCommand(CommandHandler.cmdSetDisplay(index));
    }

    // 重排图片
    public void reorderImages(byte[] order) {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法重排图片");
            return;
        }

        StringBuilder orderStr = new StringBuilder();
        for (byte b : order) {
            orderStr.append(b).append(" ");
        }

        LogUtil.log("重排图片: " + orderStr);
        bleManager.sendCommand(CommandHandler.cmdReorderImages(order));
    }

    /**
     * 上传图片
     * @param bitmap 图片位图
     * @param targetIndex 目标索引
     * @param format 图片格式 ("JPG", "PNG", "GIF")
     */
    public void uploadImage(Bitmap bitmap, byte targetIndex, String format) {
        if (bleManager.getState() != BleManager.State.CONNECTED) {
            LogUtil.logError("设备未连接，无法上传图片");
            return;
        }

        LogUtil.log("准备上传" + format + "格式图片到索引: " + targetIndex);
        isTransferring.setValue(true);
        transferProgress.setValue(0);

        byte[] imageData;
        // 根据格式确定格式标识
        byte formatId;

        if (format.equalsIgnoreCase("PNG")) {
            // PNG格式
            imageData = ImageConverter.convertBitmapToPng(bitmap);
            formatId = 0x20;
        } else if (format.equalsIgnoreCase("GIF")) {
            // GIF格式（注意：Android不支持直接转换为GIF，这里仅作为接口保留）
            imageData = ImageConverter.convertBitmapToJpeg(bitmap, 90);
            formatId = 0x30;
        } else {
            // 默认使用JPEG
            imageData = ImageConverter.convertBitmapToJpeg(bitmap, 85);
            formatId = 0x10;
        }

        LogUtil.log("图片转换完成，格式: " + format + "，数据大小: " + imageData.length + " 字节");

        // 确保目标索引只使用低4位
        byte actualIndex = (byte)(targetIndex & 0x0F);
        // 组合格式和索引
        byte combinedIndex = (byte)(formatId | actualIndex);

        // 开始传输过程
        // 1. 发送开始传输命令（使用组合索引）
        bleManager.startImageTransfer(actualIndex, formatId);

        // 2. 分包发送图片数据
        bleManager.sendImageData(imageData, new BleManager.TransferCallback() {
            @Override
            public void onProgress(int current, int total) {
                int progress = (int) ((current * 100.0) / total);
                transferProgress.setValue(progress);
                LogUtil.log("传输进度: " + current + "/" + total + " 字节 (" + progress + "%)");
            }

            @Override
            public void onComplete() {
                LogUtil.log("图片数据传输完成");

                // 3. 发送结束传输命令
                bleManager.endImageTransfer();

                // 4. 传输结束，刷新列表
                isTransferring.setValue(false);
                refreshImageList();
            }

            @Override
            public void onError(String message) {
                LogUtil.logError("图片传输失败: " + message);
                isTransferring.setValue(false);
            }
        });
    }

    // 添加一个从Uri上传图片的方法
    /*public void uploadImageFromUri(Uri imageUri, byte targetIndex, String format) {
        try {
            // 加载Bitmap
            Bitmap bitmap = ImageConverter.loadImageFromUri(getApplication(), imageUri,
                    Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT);
            if (bitmap != null) {
                uploadImage(bitmap, targetIndex, format);
            } else {
                LogUtil.logError("无法从Uri加载图片");
            }
        } catch (Exception e) {
            LogUtil.logError("处理图片时出错: " + e.getMessage());
        }
    }*/

    // 处理命令响应
    public void handleCommandResponse(byte[] response) {
        if (response == null || response.length < 3) {
            LogUtil.logError("响应数据格式错误");
            return;
        }

        byte commandId = response[0];
        byte statusCode = response[1];

        // 根据命令ID处理响应
        if (commandId == Constants.CommandID.CMD_GET_IMAGE_LIST) {
            // 处理图片列表响应
            CommandHandler.parseImageList(
                    response.length > 3 ? getPayload(response) : null,
                    new CommandHandler.ImageListCallback() {
                        @Override
                        public void onImageListReceived(List<DeviceImage> images) {
                            imageList.setValue(images);
                            LogUtil.log("收到图片列表: " + images.size() + " 个图片");
                        }

                        @Override
                        public void onError(String message) {
                            LogUtil.logError("解析图片列表失败: " + message);
                        }
                    });
        } else if (commandId == Constants.CommandID.CMD_GET_STATUS) {
            // 处理设备状态响应
            CommandHandler.parseDeviceStatus(
                    response.length > 3 ? getPayload(response) : null,
                    new CommandHandler.StatusCallback() {
                        @Override
                        public void onStatusReceived(DeviceStatus status) {
                            deviceStatus.setValue(status);
                            LogUtil.log("收到设备状态: " + status.toString());
                        }

                        @Override
                        public void onError(String message) {
                            LogUtil.logError("解析设备状态失败: " + message);
                        }
                    });
        }
    }

    // 提取响应负载
    private byte[] getPayload(byte[] response) {
        byte payloadLength = response[2];
        if (payloadLength > 0 && response.length >= 3 + payloadLength) {
            byte[] payload = new byte[payloadLength];
            System.arraycopy(response, 3, payload, 0, payloadLength);
            return payload;
        }
        return null;
    }

    // === BleManager.BleCallback 实现 ===

    @Override
    public void onStateChanged(BleManager.State state) {
        connectionState.setValue(state);
        LogUtil.log("蓝牙状态变更: " + state);

        if (state == BleManager.State.CONNECTED) {
            // 连接成功后自动获取图片列表和设备状态
            refreshImageList();
            refreshDeviceStatus();
        }
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        LogUtil.log("找到设备: " + device.getName() + " [" + device.getAddress() + "]");
    }

    @Override
    public void onConnected() {
        LogUtil.log("设备已连接");
    }

    @Override
    public void onDisconnected() {
        LogUtil.log("设备已断开");
    }

    @Override
    public void onCommandResponse(byte[] response) {
        handleCommandResponse(response);
    }

    @Override
    public void onError(String message) {
        LogUtil.logError(message);
    }

    // 清理资源
    @Override
    protected void onCleared() {
        super.onCleared();
        bleManager.disconnect();
    }
}