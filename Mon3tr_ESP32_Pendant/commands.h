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
#ifndef COMMANDS_H
#define COMMANDS_H

#include <Arduino.h>
#include "display_handler.h" // 包含图像格式定义

// 命令ID定义
enum CommandID {
  CMD_START_TRANSFER = 0x01,  // 开始传输
  CMD_IMAGE_DATA     = 0x02,  // 图片数据
  CMD_END_TRANSFER   = 0x03,  // 结束传输
  CMD_DELETE_IMAGE   = 0x04,  // 删除图片
  CMD_REORDER_IMAGES = 0x05,  // 重排图片
  CMD_GET_IMAGE_LIST = 0x06,  // 获取列表
  CMD_SET_DISPLAY    = 0x07,  // 设置显示
  CMD_GET_STATUS     = 0x08   // 获取状态
};

// 响应状态码定义
enum ResponseCode {
  RESP_SUCCESS = 0x00,        // 成功
  RESP_GENERAL_ERROR = 0x01,  // 一般错误
  RESP_FS_ERROR = 0x02,       // 文件系统错误
  RESP_TRANSFER_ERROR = 0x03, // 传输错误
  RESP_PARAM_ERROR = 0x04     // 参数错误
};

// 触摸手势类型
enum GestureType {
  GESTURE_NONE      = 0x00,
  GESTURE_SLIDE_UP  = 0x01,
  GESTURE_SLIDE_DOWN = 0x02,
  GESTURE_SLIDE_LEFT = 0x03,
  GESTURE_SLIDE_RIGHT = 0x04,
  GESTURE_SINGLE_TAP = 0x05,
  GESTURE_DOUBLE_TAP = 0x0B,
  GESTURE_LONG_PRESS = 0x0C
};

// 声明外部变量
extern unsigned long lastDataTime;
extern int commandsReceived;

// 命令处理函数声明
void processCommand(uint8_t* data, size_t length);
void sendResponse(uint8_t cmdId, uint8_t statusCode, uint8_t* payload = nullptr, uint8_t payloadLength = 0);

// 文件系统操作函数声明
void startImageTransfer(uint8_t fileIndex);
void finishImageTransfer();
void deleteImage(uint8_t fileIndex);
void reorderImages(uint8_t* order, size_t length);
void sendImageList();
void sendDeviceStatus();

// 显示操作函数声明
void setDisplayImage(uint8_t index);

// 格式处理函数声明
uint8_t getFormatFromIndex(uint8_t index);
uint8_t getFileIndexFromIndex(uint8_t index);
uint8_t combineFormatAndIndex(uint8_t format, uint8_t fileIndex);

// 命令名称获取函数
const char* getCommandName(uint8_t cmdId);
const char* getStatusName(uint8_t statusCode);
const char* getFormatName(uint8_t format);

#endif // COMMANDS_H