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
#include "ble_handler.h"
#include "commands.h"
#include "file_system.h"
#include <esp_bt_device.h>


// 全局变量定义
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandCharacteristic = nullptr;
BLECharacteristic* pDataCharacteristic = nullptr;
QueueHandle_t commandQueue;
bool isConnected = false;
int commandsReceived = 0;
unsigned long lastDataTime = 0;
bool enableLogging = false;

// 服务器连接回调
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    isConnected = true;
    Serial.println("客户端已连接");
    Serial.printf("连接数: %d\n", pServer->getConnectedCount());
  }

  void onDisconnect(BLEServer* pServer) {
    isConnected = false;
    Serial.println("客户端已断开");
    
    // 重新开始广播
    Serial.println("重新开始广播");
    pServer->getAdvertising()->start();
  }
};

// 命令特征值回调
class CommandCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) {
    std::string value = pChar->getValue();
    if (value.length() > 0) {
      if (enableLogging) {
        Serial.print("收到命令: ");
        for (int i = 0; i < value.length(); i++) {
          Serial.printf("%02X ", (uint8_t)value[i]);
        }
        Serial.println();
      }
      
      // 创建临时缓冲区存储命令
      uint8_t* data = new uint8_t[value.length()];
      memcpy(data, value.data(), value.length());
      
      // 发送到队列
      command_item_t item;
      item.data = data;
      item.length = value.length();
      
      if (xQueueSend(commandQueue, &item, portMAX_DELAY) != pdTRUE) {
        Serial.println("命令队列已满！");
        delete[] data;
      }
    }
  }
};

// 数据特征值回调
class DataCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) {
    std::string value = pChar->getValue();
    if (value.length() > 0) {
      // 处理图片数据
      processImageData((uint8_t*)value.data(), value.length());
      
      // 更新最后数据接收时间
      lastDataTime = millis();
      
      // 适当延迟，防止接收过快导致设备无法处理
      // 协议建议每个数据包之间有30ms延迟
      if (enableLogging && value.length() > 100) {
        Serial.printf("收到图片数据包: %d 字节\n", value.length());
      }
    }
  }
};

// 设置BLE
void setupBLE() {
  Serial.println("初始化BLE...");

  // 创建命令队列
  commandQueue = xQueueCreate(5, sizeof(command_item_t));
  
  // ESP32-C3专用配置
  esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
  
  if (esp_bt_controller_init(&bt_cfg) != ESP_OK) {
    Serial.println("蓝牙控制器初始化失败");
    return;
  }

  if (esp_bt_controller_enable(ESP_BT_MODE_BLE) != ESP_OK) {
    Serial.println("BT启用失败");
    return;
  }

  BLEDevice::init("RoyZ-Mon3tr");
  
  // 设置最大传输单元 - 协议要求设置为512字节以提高传输效率
  BLEDevice::setMTU(517);  // 设置最大传输单元
  
  // 设置发送功率
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_DEFAULT, ESP_PWR_LVL_P9);
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, ESP_PWR_LVL_P9);
  
  // 创建服务器
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  // 创建服务
  BLEService* pService = pServer->createService(SERVICE_UUID);

  // 创建特征值
  pCommandCharacteristic = pService->createCharacteristic(
    CHAR_COMMAND_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
  
  pDataCharacteristic = pService->createCharacteristic(
    CHAR_DATA_UUID,
    BLECharacteristic::PROPERTY_WRITE);

  // 设置回调
  pCommandCharacteristic->setCallbacks(new CommandCallbacks());
  pDataCharacteristic->setCallbacks(new DataCallbacks());
  
  // 启动服务
  pService->start();

  // 配置广播
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinInterval(0x20);
  pAdvertising->setMaxInterval(0x40);
  BLEDevice::startAdvertising();
  
  // 创建命令处理任务
  xTaskCreate(
    processCommandTask,
    "CmdProcessor",
    4096,
    NULL,
    2,
    NULL
  );
  
  Serial.printf("BLE初始化完成，MTU设置为517字节\n");
}

// 处理命令队列任务
void processCommandTask(void* param) {
  command_item_t item;
  
  while (true) {
    if (xQueueReceive(commandQueue, &item, portMAX_DELAY) == pdTRUE) {
      // 处理命令
      processCommand(item.data, item.length);
      
      // 释放内存
      delete[] item.data;
    }
    
    // 任务切换
    vTaskDelay(10 / portTICK_PERIOD_MS);
  }
}

// 发送BLE响应
void sendBleResponse(uint8_t* data, size_t length) {
  if (pCommandCharacteristic && isConnected) {
    pCommandCharacteristic->setValue(data, length);
    pCommandCharacteristic->notify();
    delay(20); // 短暂延迟确保通知发送
  }
}

// 处理BLE状态
void handleBleStatus() {
  static unsigned long lastBleCheck = 0;
  unsigned long now = millis();
  
  // 每5秒检查一次BLE状态
  if (now - lastBleCheck >= 5000) {
    lastBleCheck = now;
    
    if (pServer != nullptr) {
      int connCount = pServer->getConnectedCount();
      isConnected = (connCount > 0);
      
      // 如果未连接，确保广播
      if (!isConnected) {
        BLEDevice::startAdvertising();
      }
    }
  }
}