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
package gg.dmr.royz.m3.bluetooth;

/**
 * 蓝牙通信常量定义
 * 包含所有BLE服务UUID、命令ID和响应状态码
 */
public class Constants {
    // BLE UUIDs
    public static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    public static final String CHAR_COMMAND_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    public static final String CHAR_DATA_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9";

    // 设备名称过滤
    public static final String DEVICE_NAME_PREFIX = "RoyZ-Mon3tr";

    // 命令ID定义 (对应commands.h中的CommandID)
    public static class CommandID {
        public static final byte CMD_START_TRANSFER = 0x01;  // 开始传输
        public static final byte CMD_IMAGE_DATA = 0x02;      // 图片数据
        public static final byte CMD_END_TRANSFER = 0x03;    // 结束传输
        public static final byte CMD_DELETE_IMAGE = 0x04;    // 删除图片
        public static final byte CMD_REORDER_IMAGES = 0x05;  // 重排图片
        public static final byte CMD_GET_IMAGE_LIST = 0x06;  // 获取列表
        public static final byte CMD_SET_DISPLAY = 0x07;     // 设置显示
        public static final byte CMD_GET_STATUS = 0x08;      // 获取状态
    }

    // 响应状态码定义 (对应commands.h中的ResponseCode)
    public static class ResponseCode {
        public static final byte RESP_SUCCESS = 0x00;        // 成功
        public static final byte RESP_GENERAL_ERROR = 0x01;  // 一般错误
        public static final byte RESP_FS_ERROR = 0x02;       // 文件系统错误
        public static final byte RESP_TRANSFER_ERROR = 0x03; // 传输错误
        public static final byte RESP_PARAM_ERROR = 0x04;    // 参数错误

        // 获取状态码的描述文本
        public static String getStatusName(byte statusCode) {
            switch(statusCode) {
                case RESP_SUCCESS: return "成功";
                case RESP_GENERAL_ERROR: return "一般错误";
                case RESP_FS_ERROR: return "文件系统错误";
                case RESP_TRANSFER_ERROR: return "传输错误";
                case RESP_PARAM_ERROR: return "参数错误";
                default: return "未知状态(" + statusCode + ")";
            }
        }
    }

    // 命令名称查询 (对应commands.cpp中的getCommandName)
    public static String getCommandName(byte cmdId) {
        switch(cmdId) {
            case CommandID.CMD_START_TRANSFER: return "开始传输";
            case CommandID.CMD_IMAGE_DATA: return "图片数据";
            case CommandID.CMD_END_TRANSFER: return "结束传输";
            case CommandID.CMD_DELETE_IMAGE: return "删除图片";
            case CommandID.CMD_REORDER_IMAGES: return "重排图片";
            case CommandID.CMD_GET_IMAGE_LIST: return "获取列表";
            case CommandID.CMD_SET_DISPLAY: return "设置显示";
            case CommandID.CMD_GET_STATUS: return "获取状态";
            default: return "未知命令(" + cmdId + ")";
        }
    }

    // 图片传输参数
    public static final int IMAGE_WIDTH = 240;  // 图片宽度
    public static final int IMAGE_HEIGHT = 240; // 图片高度
    public static final int CHUNK_SIZE = 512;   // BLE数据块大小
}