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