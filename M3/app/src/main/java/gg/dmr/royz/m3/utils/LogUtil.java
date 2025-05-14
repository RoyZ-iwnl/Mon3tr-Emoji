package gg.dmr.royz.m3.utils;

import android.util.Log;

/**
 * 日志工具类
 * 提供格式化日志功能和十六进制数据记录
 */
public class LogUtil {
    private static final String TAG = "RoyZM3";
    private static final int BYTES_PER_LINE = 16;

    // 日志监听器接口
    public interface LogListener {
        void onNewLog(String logMessage);
    }

    private static LogListener logListener;

    // 设置日志监听器
    public static void setLogListener(LogListener listener) {
        logListener = listener;
    }

    // 记录普通日志
    public static void log(String message) {
        Log.d(TAG, message);
        if (logListener != null) {
            logListener.onNewLog(message);
        }
    }

    // 记录错误日志
    public static void logError(String message) {
        Log.e(TAG, message);
        if (logListener != null) {
            logListener.onNewLog("错误: " + message);
        }
    }

    // 记录十六进制数据
    public static void logHex(String prefix, byte[] data) {
        if (data == null) {
            log(prefix + ": null");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(": [").append(data.length).append("字节] ");

        if (data.length <= 16) {
            // 短数据直接在一行显示
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
        } else {
            // 长数据分行显示前32字节
            sb.append("\n");
            int maxBytes = Math.min(data.length, 32);

            for (int i = 0; i < maxBytes; i++) {
                if (i > 0 && i % BYTES_PER_LINE == 0) {
                    sb.append("\n");
                }
                sb.append(String.format("%02X ", data[i]));
            }

            if (data.length > 32) {
                sb.append("... (").append(data.length - 32).append(" 字节更多)");
            }
        }

        log(sb.toString());
    }

    // 获取命令描述
    public static String getCommandDescription(byte commandId, byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("命令: ").append(gg.dmr.royz.m3.bluetooth.Constants.getCommandName(commandId));

        if (data != null && data.length > 0) {
            sb.append(", 数据: ");
            for (int i = 0; i < Math.min(data.length, 8); i++) {
                sb.append(String.format("%02X ", data[i]));
            }
            if (data.length > 8) {
                sb.append("...");
            }
        }

        return sb.toString();
    }
}