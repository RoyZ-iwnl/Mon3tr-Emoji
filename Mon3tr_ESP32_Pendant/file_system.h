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
#ifndef FILE_SYSTEM_H
#define FILE_SYSTEM_H

#include <Arduino.h>
#include <LittleFS.h>
#include "display_handler.h" // 包含图像格式定义

// 全局常量
#define MAX_IMAGES 10

// 图片信息结构
struct ImageInfo {
  String filename;
  bool active;
  uint8_t format;   // 图片格式
  uint8_t fileIndex; // 文件索引
  uint32_t fileSize; // 文件大小
};

// 全局变量声明
extern ImageInfo imageList[MAX_IMAGES];
extern String currentImageName;
extern File currentImageFile;
extern bool isTransferring;
extern int totalBytesReceived;

// 函数声明
void closeAllFiles();
void setupFileSystem();
void updateImageList();
void loadImageOrder();
void saveImageOrder();
void startImageTransfer(uint8_t fileIndex);
void processImageData(uint8_t* data, size_t length);
void finishImageTransfer();
void deleteImage(uint8_t fileIndex);
void reorderImages(uint8_t* order, size_t length);
void sendImageList();
void sendDeviceStatus();
String getImageFilename(uint8_t index);
void cleanupOldFiles();
void listFiles();
void checkFileSystem();
String getFileExtensionFromFormat(uint8_t format);

#endif // FILE_SYSTEM_H