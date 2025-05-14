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

            // 分析收到的响应格式，按实际协议解析
            // 观察日志: 06 00 0D 04 00 01 01 01 02 01 02 09 01 03 00 01
            // 这可能是特殊格式，直接解析

            List<DeviceImage> imageList = new ArrayList<>();

            // 特殊处理：可能设备返回的是多个图片条目的连续列表
            // 假设格式为: [图片索引(1字节), 图片大小低字节(1字节), 图片大小高字节(1字节), 名称长度(1字节), 名称(变长)]
            int index = 0;

            // 确保循环安全，防止索引越界
            while (index < payload.length) {
                // 确保至少有3字节剩余数据
                if (index + 3 >= payload.length) {
                    Log.w(TAG, "剩余数据不足，结束解析: 剩余字节=" + (payload.length - index));
                    break;
                }

                // 获取图片索引
                byte fileIndex = payload[index++];

                // 读取图片大小 - 尝试不同的字节序
                int fileSize;
                if (index + 3 < payload.length) {
                    // 尝试4字节大小
                    fileSize = ((payload[index] & 0xFF)) |
                            ((payload[index + 1] & 0xFF) << 8) |
                            ((payload[index + 2] & 0xFF) << 16) |
                            ((payload[index + 3] & 0xFF) << 24);
                    index += 4;
                } else if (index + 1 < payload.length) {
                    // 尝试2字节大小
                    fileSize = ((payload[index] & 0xFF)) |
                            ((payload[index + 1] & 0xFF) << 8);
                    index += 2;
                } else {
                    // 尝试1字节大小
                    fileSize = (payload[index] & 0xFF);
                    index++;
                }

                // 读取名称长度 (如果还有数据)
                byte nameLength = 0;
                if (index < payload.length) {
                    nameLength = payload[index++];
                }

                // 安全检查: 确保名称长度字段有效且剩余数据足够
                if (nameLength < 0 || (nameLength > 0 && index + nameLength > payload.length)) {
                    Log.w(TAG, "无效的名称长度或数据不足: nameLength=" + nameLength +
                            ", 剩余字节=" + (payload.length - index));
                    // 尝试继续解析，而不是中断
                    nameLength = 0;
                }

                // 读取名称
                String fileName = "";
                if (nameLength > 0) {
                    try {
                        fileName = new String(Arrays.copyOfRange(payload, index, index + nameLength),
                                StandardCharsets.UTF_8);
                        index += nameLength;
                    } catch (Exception e) {
                        Log.w(TAG, "读取名称失败: " + e.getMessage());
                    }
                }

                // 添加到图片列表
                DeviceImage image = new DeviceImage(fileIndex, fileName, fileSize);
                imageList.add(image);

                Log.d(TAG, "解析图片: 索引=" + fileIndex + ", 大小=" + fileSize +
                        "字节, 名称=" + fileName);
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

            // 设备状态格式：[电池电量(1字节), 存储使用量(4字节), 当前显示索引(1字节)]
            byte batteryLevel = payload[0]; // 电池电量百分比

            // 读取存储使用量(4字节，小端序)
            int storageUsed = ((payload[1] & 0xFF)) |
                    ((payload[2] & 0xFF) << 8) |
                    ((payload[3] & 0xFF) << 16) |
                    ((payload[4] & 0xFF) << 24);

            // 获取当前显示的图片索引
            byte currentIndex = (payload.length > 5) ? payload[5] : -1;

            DeviceStatus status = new DeviceStatus(batteryLevel, storageUsed, currentIndex);
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