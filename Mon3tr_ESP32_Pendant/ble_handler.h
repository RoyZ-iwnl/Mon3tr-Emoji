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
#ifndef BLE_HANDLER_H
#define BLE_HANDLER_H


#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>


// BLE服务和特征UUID
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_COMMAND_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_DATA_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// 命令队列项目结构
struct command_item_t {
  uint8_t* data;
  size_t length;
};

// 全局变量声明
extern BLEServer* pServer;
extern BLECharacteristic* pCommandCharacteristic;
extern BLECharacteristic* pDataCharacteristic;
extern QueueHandle_t commandQueue;
extern bool isConnected;
extern int commandsReceived;
extern unsigned long lastDataTime;
extern bool enableLogging;

// 函数声明
void setupBLE();
void handleBleStatus();
void sendBleResponse(uint8_t* data, size_t length);
void processCommandTask(void* param);
void processImageData(uint8_t* data, size_t length);

// BLE回调类声明
class ServerCallbacks;
class CommandCallbacks;
class DataCallbacks;

#endif // BLE_HANDLER_H