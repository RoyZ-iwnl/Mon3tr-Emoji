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