package gg.dmr.royz.m3.bluetooth;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gg.dmr.royz.m3.model.DeviceImage;
import gg.dmr.royz.m3.model.DeviceStatus;
import gg.dmr.royz.m3.utils.LogUtil;

/**
 * 命令处理器
 * 负责处理BLE命令的打包与解析，包括命令发送和响应处理
 */
public class CommandHandler {
    private static final String TAG = "CommandHandler";

    // 命令回调接口
    public interface CommandCallback {
        void onSuccess(byte commandId, byte[] payload);
        void onError(byte commandId, byte statusCode, String message);
    }

    // 图片列表回调接口
    public interface ImageListCallback {
        void onImageListReceived(List<DeviceImage> imageList);
        void onError(String message);
    }

    // 设备状态回调接口
    public interface StatusCallback {
        void onStatusReceived(DeviceStatus status);
        void onError(String message);
    }

    // 生成命令数据包
    public static byte[] packCommand(byte commandId, byte[] payload) {
        byte payloadLength = (payload != null) ? (byte) payload.length : 0;

        // 创建命令数据包：[命令ID, 负载长度, 负载数据]
        ByteBuffer buffer = ByteBuffer.allocate(2 + payloadLength);
        buffer.put(commandId);          // 命令ID
        buffer.put(payloadLength);      // 负载长度

        // 如果有负载数据，则添加到数据包
        if (payloadLength > 0) {
            buffer.put(payload);
        }

        LogUtil.logHex("发送命令", buffer.array());
        return buffer.array();
    }

    // 解析响应数据
    public static void parseResponse(byte[] response, CommandCallback callback) {
        if (response == null || response.length < 3) {
            Log.e(TAG, "响应数据格式错误");
            return;
        }

        byte commandId = response[0];     // 命令ID
        byte statusCode = response[1];    // 状态码
        byte payloadLength = response[2]; // 负载长度

        LogUtil.logHex("收到响应", response);
        Log.d(TAG, String.format("命令: %s, 状态: %s, 数据长度: %d",
                Constants.getCommandName(commandId),
                Constants.ResponseCode.getStatusName(statusCode),
                payloadLength));

        // 提取响应负载
        byte[] payload = null;
        if (payloadLength > 0 && response.length >= 3 + payloadLength) {
            payload = Arrays.copyOfRange(response, 3, 3 + payloadLength);
        }

        // 回调处理
        if (statusCode == Constants.ResponseCode.RESP_SUCCESS) {
            callback.onSuccess(commandId, payload);
        } else {
            callback.onError(commandId, statusCode,
                    Constants.ResponseCode.getStatusName(statusCode));
        }
    }

    // 处理图片列表响应 - 修复版本
    public static void parseImageList(byte[] payload, ImageListCallback callback) {
        try {
            if (payload == null || payload.length == 0) {
                callback.onImageListReceived(new ArrayList<>());
                return;
            }

            // 记录原始数据，方便调试
            StringBuilder sb = new StringBuilder("原始数据: ");
            for (byte b : payload) {
                sb.append(String.format("%02X ", b));
            }
            Log.d(TAG, sb.toString());

            List<DeviceImage> imageList = new ArrayList<>();

            // 第一个字节表示图片数量
            int imageCount = payload[0] & 0xFF;
            Log.d(TAG, "图片数量: " + imageCount);

            // 每张图片信息: 位置索引(1) + 文件索引(1) + 大小(4) = 6字节
            int offset = 1;
            for (int i = 0; i < imageCount && offset + 6 <= payload.length; i++) {
                // 解析位置索引
                int positionIndex = payload[offset++] & 0xFF;

                // 解析文件索引
                byte fileIndex = payload[offset++];

                // 解析图片大小 (4字节)
                int fileSize = ((payload[offset] & 0xFF)) |
                        ((payload[offset + 1] & 0xFF) << 8) |
                        ((payload[offset + 2] & 0xFF) << 16) |
                        ((payload[offset + 3] & 0xFF) << 24);
                offset += 4;

                // 提取图片格式（从fileIndex的高4位）
                byte format = (byte)(fileIndex & 0xF0);

                // 实际文件索引（低4位）
                byte actualIndex = (byte)(fileIndex & 0x0F);

                // 构建文件名
                String extension;
                switch (format) {
                    case 0x10: extension = ".jpg"; break;
                    case 0x20: extension = ".png"; break;
                    case 0x30: extension = ".gif"; break;
                    default: extension = ".ibin"; break;
                }

                String filename = "img_" + actualIndex + extension;

                DeviceImage image = new DeviceImage(fileIndex, filename, fileSize, format);
                imageList.add(image);

                Log.d(TAG, "解析图片: 位置=" + positionIndex +
                        ", 索引=0x" + String.format("%02X", fileIndex) +
                        ", 大小=" + fileSize + "字节, 格式=" +
                        image.getFormatDescription());
            }

            callback.onImageListReceived(imageList);

        } catch (Exception e) {
            Log.e(TAG, "解析图片列表失败: " + e.getMessage(), e);
            callback.onError("解析图片列表失败: " + e.getMessage());
        }
    }

    // 处理设备状态响应
    public static void parseDeviceStatus(byte[] payload, StatusCallback callback) {
        try {
            if (payload == null || payload.length < 5) {
                callback.onError("设备状态数据不完整");
                return;
            }

            // 设备状态格式：[开机时长(4字节), 存储使用量(4字节), 当前显示索引(1字节)]

            // 读取开机时长(4字节，小端序)
            int uptime = ((payload[0] & 0xFF)) |
                    ((payload[1] & 0xFF) << 8) |
                    ((payload[2] & 0xFF) << 16) |
                    ((payload[3] & 0xFF) << 24);

            // 读取存储使用量(4字节，小端序)
            int storageUsed = ((payload[4] & 0xFF)) |
                    ((payload[5] & 0xFF) << 8) |
                    ((payload[6] & 0xFF) << 16) |
                    ((payload[7] & 0xFF) << 24);

            // 获取当前显示的图片索引
            byte currentIndex = (payload.length > 8) ? payload[8] : -1;

            DeviceStatus status = new DeviceStatus(uptime, storageUsed, currentIndex);
            callback.onStatusReceived(status);

        } catch (Exception e) {
            Log.e(TAG, "解析设备状态失败: " + e.getMessage());
            callback.onError("解析设备状态失败: " + e.getMessage());
        }
    }

    // ==== 命令封装方法 ====

    // 开始传输命令
    public static byte[] cmdStartTransfer(byte fileIndex) {
        return packCommand(Constants.CommandID.CMD_START_TRANSFER, new byte[]{fileIndex});
    }

    // 结束传输命令
    public static byte[] cmdEndTransfer() {
        return packCommand(Constants.CommandID.CMD_END_TRANSFER, null);
    }

    // 删除图片命令
    public static byte[] cmdDeleteImage(byte fileIndex) {
        return packCommand(Constants.CommandID.CMD_DELETE_IMAGE, new byte[]{fileIndex});
    }

    // 重排图片命令
    public static byte[] cmdReorderImages(byte[] order) {
        return packCommand(Constants.CommandID.CMD_REORDER_IMAGES, order);
    }

    // 获取图片列表命令
    public static byte[] cmdGetImageList() {
        return packCommand(Constants.CommandID.CMD_GET_IMAGE_LIST, null);
    }

    // 设置显示图片命令
    public static byte[] cmdSetDisplay(byte fileIndex) {
        return packCommand(Constants.CommandID.CMD_SET_DISPLAY, new byte[]{fileIndex});
    }

    // 获取设备状态命令
    public static byte[] cmdGetStatus() {
        return packCommand(Constants.CommandID.CMD_GET_STATUS, null);
    }
}