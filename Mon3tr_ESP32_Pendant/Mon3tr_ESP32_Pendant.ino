#include <TFT_eSPI.h>
#include <SPI.h>
#include <Wire.h>
#include "CST816D.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <LittleFS.h>
#include <ArduinoJson.h>
// 引脚定义
#define TOUCH_SDA 4
#define TOUCH_SCL 5
#define TOUCH_INT 0
#define TOUCH_RST 1
#define TFT_BL 3  // 屏幕背光引脚

// BLE服务和特征UUID
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_COMMAND_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_DATA_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// 全局常量
#define MAX_IMAGES 10
#define IMAGE_WIDTH 240
#define IMAGE_HEIGHT 240
#define DOUBLE_TAP_TIME 300

// 命令类型
enum CommandType {
  CMD_NONE = 0,
  CMD_START_TRANSFER = 1,
  CMD_IMAGE_DATA = 2,
  CMD_END_TRANSFER = 3,
  CMD_DELETE_IMAGE = 4,
  CMD_REORDER_IMAGES = 5,
  CMD_GET_IMAGE_LIST = 6,
  CMD_SET_DISPLAY = 7,
  CMD_GET_STATUS = 8
};

// 回调类声明
class CommandCallbacks;
class DataCallbacks;

// 全局变量
TFT_eSPI tft = TFT_eSPI();
CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);
BLEServer* pServer = nullptr;
BLECharacteristic* pCommandCharacteristic = nullptr;
BLECharacteristic* pDataCharacteristic = nullptr;
QueueHandle_t commandQueue;

int totalBytesReceived = 0;
int commandsReceived = 0;
unsigned long lastDataTime = 0;
unsigned long lastStatusCheck = 0;
uint16_t touchX, touchY;
uint8_t gesture;
unsigned long lastTapTime = 0;
int currentImage = 0;
int totalImages = 0;
bool isTransferring = false;
bool enableLogging = false;
bool statusPending = false;
unsigned long lastNotifyTime = 0;
String currentImageName;
File currentImageFile;

// 存储图片顺序的结构
struct ImageInfo {
  String filename;
  bool active;
} imageList[MAX_IMAGES];

// 函数声明
const char* getCommandName(CommandType cmd);
void processCommand(uint8_t* data, size_t length);
void sendResponse(String response);
void displayNewImage();
void sendImageList();
void sendStatus();
void reorderImages(uint8_t* order, size_t length);
void saveImageOrder();
void loadImageOrder();
void updateImageList();
void displayImage(int index);
void controlBacklight(bool on);
void logDebugInfo();
void checkGestures();

// BLE回调类
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    Serial.println("客户端已连接");
    Serial.printf("连接数: %d\n", pServer->getConnectedCount());
  }

  void onDisconnect(BLEServer* pServer) {
    Serial.println("客户端已断开");

    // 清理状态
    isTransferring = false;
    if (currentImageFile) {
      currentImageFile.close();
    }

    // 重新开始广播
    Serial.println("重新开始广播");
    pServer->getAdvertising()->start();
  }
};


class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) {
    if (enableLogging) {
      Serial.print("CMD:");
      Serial.println(pChar->getValue().c_str());
    }
    
    // 创建临时缓冲区存储命令
    uint8_t* data = new uint8_t[pChar->getValue().length()];
    memcpy(data, pChar->getData(), pChar->getValue().length());
    
    // 发送到队列
    if (xQueueSend(commandQueue, &data, portMAX_DELAY) != pdTRUE) {
      Serial.println("命令队列已满！");
      delete[] data;
    }
  }
};

class DataCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) {
    // 最小化处理，直接传递数据指针
    static uint8_t* fileBuffer = nullptr;
    if (!fileBuffer) {
      fileBuffer = (uint8_t*)malloc(1024);  // 预分配缓冲区
    }

    std::string value = pChar->getValue();
    if (isTransferring && currentImageFile) {
      // 直接写入文件系统
      currentImageFile.write((uint8_t*)value.data(), value.length());
    }
  }
};

void handlePendingNotifications() {
  if (statusPending && millis() - lastNotifyTime > 200) {
    if (pCommandCharacteristic) {
      // 发送最小化数据
      String smallStatus = "{\"c\":" + String(currentImage) + 
                         ",\"t\":" + String(totalImages) + "}";
      pCommandCharacteristic->setValue(smallStatus.c_str());
      pCommandCharacteristic->notify();
      statusPending = false;
    }
  }
}

const char* getCommandName(CommandType cmd) {
  switch (cmd) {
    case CMD_START_TRANSFER: return "开始传输";
    case CMD_IMAGE_DATA: return "图片数据";
    case CMD_END_TRANSFER: return "结束传输";
    case CMD_DELETE_IMAGE: return "删除图片";
    case CMD_REORDER_IMAGES: return "重排图片";
    case CMD_GET_IMAGE_LIST: return "获取列表";
    case CMD_SET_DISPLAY: return "设置显示";
    case CMD_GET_STATUS: return "获取状态";
    default: return "未知命令";
  }
}

void processCommand(uint8_t* data, size_t length) {
  if (length < 1) return;

  CommandType cmd = (CommandType)data[0];
  Serial.printf("处理命令: %d (%s), 数据长度: %d\n", cmd, getCommandName(cmd), length);
  commandsReceived++;
  lastDataTime = millis();

  // 在处理命令前添加小延迟
  delay(10);

  switch (cmd) {
    case CMD_START_TRANSFER:
      if (length > 1) {
        uint8_t fileIndex = data[1];
        currentImageName = "/img_" + String(fileIndex) + ".bin";

        Serial.println("开始接收图片: " + currentImageName);

        // 如果有正在传输的文件，先关闭
        if (currentImageFile) {
          currentImageFile.close();
        }

        currentImageFile = LittleFS.open(currentImageName, "w");
        if (!currentImageFile) {
          Serial.println("创建文件失败: " + currentImageName);
          isTransferring = false;
          return;
        }

        isTransferring = true;
        Serial.println("文件已创建，等待数据...");
      }
      break;

    case CMD_IMAGE_DATA:
      // 不处理，数据通道会处理
      Serial.println("收到图像数据命令");
      break;

    case CMD_END_TRANSFER:
      if (isTransferring && currentImageFile) {
        currentImageFile.flush();
        size_t fileSize = currentImageFile.size();
        currentImageFile.close();
        isTransferring = false;

        Serial.printf("文件接收完成，大小: %d 字节\n", fileSize);

        // 验证文件
        File verifyFile = LittleFS.open(currentImageName, "r");
        size_t verifiedSize = 0;
        if (verifyFile) {
          verifiedSize = verifyFile.size();
          verifyFile.close();
        }
        Serial.printf("验证文件大小: %d 字节\n", verifiedSize);

        // 检查最小有效大小 (至少有一些像素数据)
        if (verifiedSize < 1000) {
          Serial.println("文件太小，可能不完整，删除中...");
          LittleFS.remove(currentImageName);
          // 回复错误
          StaticJsonDocument<128> errDoc;
          errDoc["status"] = "error";
          errDoc["message"] = "File incomplete";
          String errorResponse;
          serializeJson(errDoc, errorResponse);
          sendResponse(errorResponse);
        } else {
          // 更新图片列表
          updateImageList();

          // 显示新图片
          for (int i = 0; i < totalImages; i++) {
            if (imageList[i].filename == currentImageName) {
              currentImage = i;
              displayImage(currentImage);

              // 回复成功
              StaticJsonDocument<128> sucessDoc;
              sucessDoc["status"] = "success";
              sucessDoc["fileIndex"] = i;
              sucessDoc["fileSize"] = verifiedSize;
              String successResponse;
              serializeJson(sucessDoc, successResponse);
              sendResponse(successResponse);
              break;
            }
          }
        }
      } else {
        // 如果没有正在传输的文件，返回错误
        StaticJsonDocument<128> errDoc;
        errDoc["status"] = "error";
        errDoc["message"] = "No active transfer";
        String errorResponse;
        serializeJson(errDoc, errorResponse);
        sendResponse(errorResponse);
      }
      break;

    case CMD_DELETE_IMAGE:
      if (length > 1) {
        String filename = "/img_" + String(data[1]) + ".bin";
        if (LittleFS.remove(filename)) {
          updateImageList();
          Serial.println("删除图片: " + filename);
        }
      }
      break;

    case CMD_REORDER_IMAGES:
      reorderImages(data + 1, length - 1);
      break;

    case CMD_GET_IMAGE_LIST:
      // 在获取图片列表前清理堆内存，增加可用内存
      ESP.getFreeHeap(); // 触发内存碎片整理
      delay(50);         // 给系统一些时间处理内存
      sendImageList();
      break;

    case CMD_SET_DISPLAY:
      if (length > 1 && data[1] < totalImages) {
        currentImage = data[1];
        displayImage(currentImage);
      }
      break;

    case CMD_GET_STATUS:
      sendStatus();
      break;
  }
  
  // 在处理命令后添加小延迟
  delay(10);
}

void sendResponse(String response) {
  if (pCommandCharacteristic) {
    pCommandCharacteristic->setValue(response.c_str());
    pCommandCharacteristic->notify();
    delay(20);
  }
}


void sendImageList() {
  // 创建一个更小的JSON文档
  DynamicJsonDocument smallDoc(256);  // 使用DynamicJsonDocument而非StaticJsonDocument
  JsonArray smallImages = smallDoc.createNestedArray("img");

  // 限制返回的图片数量
  int imagesToSend = min(totalImages, 5);  // 最多发送5张图片信息
  
  for (int i = 0; i < imagesToSend; i++) {
    JsonObject img = smallImages.createNestedObject();
    img["i"] = i;  // 索引

    String nameClean = imageList[i].filename;
    nameClean.replace("/img_", "");
    nameClean.replace(".bin", "");
    img["n"] = nameClean;                    // 名称
    img["a"] = imageList[i].active ? 1 : 0;  // 激活状态
  }

  String jsonStr;
  serializeJson(smallDoc, jsonStr);

  Serial.println("发送图片列表: " + jsonStr);
  Serial.printf("JSON大小: %d 字节\n", jsonStr.length());

  // 安全发送 - 增加延迟和错误处理
  if (pCommandCharacteristic) {
    // 检查字符串大小，如果太大则拆分发送
    if (jsonStr.length() > 100) {
      Serial.println("JSON太大，只发送总数信息");
      // 创建一个更小的响应
      StaticJsonDocument<64> tinyDoc;
      tinyDoc["count"] = totalImages;
      tinyDoc["cur"] = currentImage;
      
      String tinyStr;
      serializeJson(tinyDoc, tinyStr);
      
      pCommandCharacteristic->setValue(tinyStr.c_str());
    } else {
      pCommandCharacteristic->setValue(jsonStr.c_str());
    }
    
    delay(50);  // 发送前增加延迟
    pCommandCharacteristic->notify();
    delay(100);  // 发送后重要延迟
    Serial.println("图片列表通知已发送");
  }
}

void sendStatus() {
  statusPending = true;
  lastNotifyTime = millis();
  // 创建最小化JSON
  StaticJsonDocument<64> doc;  // 减小缓冲区
  doc["cur"] = currentImage;
  doc["tot"] = totalImages;
  doc["free"] = (LittleFS.totalBytes() - LittleFS.usedBytes()) / 1024;  // 以KB为单位

  String jsonStr;
  serializeJson(doc, jsonStr);

  // 安全发送
  if (pCommandCharacteristic) {
    pCommandCharacteristic->setValue(jsonStr.c_str());
    delay(20);  // 发送前延迟
    pCommandCharacteristic->notify();
    delay(100);  // 发送后重要延迟
  }
}

void reorderImages(uint8_t* order, size_t length) {
  if (length != totalImages) return;

  ImageInfo tempList[MAX_IMAGES];
  memcpy(tempList, imageList, sizeof(ImageInfo) * totalImages);

  for (size_t i = 0; i < length; i++) {
    if (order[i] < totalImages) {
      imageList[i] = tempList[order[i]];
    }
  }

  saveImageOrder();
}

void saveImageOrder() {
  File file = LittleFS.open("/order.json", "w");
  if (!file) return;

  StaticJsonDocument<512> doc;
  JsonArray images = doc.createNestedArray("images");

  for (int i = 0; i < totalImages; i++) {
    JsonObject img = images.createNestedObject();
    img["name"] = imageList[i].filename;
    img["active"] = imageList[i].active;
  }

  serializeJson(doc, file);
  file.close();
}

void loadImageOrder() {
  File file = LittleFS.open("/order.json", "r");
  if (!file) return;

  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, file);
  if (error) return;

  JsonArray images = doc["images"];
  totalImages = 0;

  for (JsonObject img : images) {
    if (totalImages < MAX_IMAGES) {
      imageList[totalImages].filename = img["name"].as<String>();
      imageList[totalImages].active = img["active"] | true;
      totalImages++;
    }
  }

  file.close();
}

void updateImageList() {
  File root = LittleFS.open("/");
  File file = root.openNextFile();
  totalImages = 0;

  while (file && totalImages < MAX_IMAGES) {
    String filename = String(file.name());
    if (!file.isDirectory() && filename.endsWith(".bin")) {
      imageList[totalImages].filename = "/" + filename;
      imageList[totalImages].active = true;
      totalImages++;
    }
    file = root.openNextFile();
  }

  saveImageOrder();
}

void displayImage(int index) {
  if (index >= totalImages || index < 0) {
    Serial.println("错误：图片索引超出范围");
    tft.fillScreen(TFT_RED);
    return;
  }

  String filename = imageList[index].filename;
  if (!LittleFS.exists(filename)) {
    Serial.println("错误：文件不存在: " + filename);
    tft.fillScreen(TFT_BLUE);
    return;
  }

  File f = LittleFS.open(filename, "r");
  if (!f) {
    Serial.println("错误：无法打开文件: " + filename);
    tft.fillScreen(TFT_YELLOW);
    return;
  }

  size_t fileSize = f.size();
  size_t expectedSize = IMAGE_WIDTH * IMAGE_HEIGHT * 2;  // 240x240x2 = 115200 bytes

  if (fileSize != expectedSize) {
    Serial.printf("警告: 文件大小不匹配 - 实际: %d, 预期: %d\n", fileSize, expectedSize);
    // Continue anyway, but log the warning
  }

  // 清屏
  tft.fillScreen(TFT_BLACK);

  // 读取并显示图片 - 使用更大的缓冲区和更可靠的错误处理
  uint8_t buf[IMAGE_WIDTH * 2 * 4];  // 增加缓冲区大小为4行
  uint16_t* pixelBuf = (uint16_t*)buf;
  int rowsAtTime = 4;  // 一次处理4行

  for (int y = 0; y < IMAGE_HEIGHT; y += rowsAtTime) {
    int remainingRows = min(rowsAtTime, IMAGE_HEIGHT - y);
    int bytesToRead = IMAGE_WIDTH * 2 * remainingRows;

    if (f.available() < bytesToRead) {
      // 文件数据不足
      tft.fillRect(0, y, IMAGE_WIDTH, IMAGE_HEIGHT - y, TFT_RED);
      Serial.printf("第 %d 行数据不足，剩余 %d 字节\n", y, f.available());
      break;
    }

    size_t bytesRead = f.read(buf, bytesToRead);
    if (bytesRead == bytesToRead) {
      for (int r = 0; r < remainingRows; r++) {
        tft.pushImage(0, y + r, IMAGE_WIDTH, 1, &pixelBuf[r * IMAGE_WIDTH]);
      }
    } else {
      Serial.printf("读取失败，请求: %d 字节, 只读取了: %d 字节\n", bytesToRead, bytesRead);
      tft.fillRect(0, y, IMAGE_WIDTH, IMAGE_HEIGHT - y, TFT_MAGENTA);
      break;
    }

    // 添加小延迟允许处理器处理其他任务
    delay(5);
  }

  f.close();
  Serial.println("图片显示完成: " + filename);
}

void controlBacklight(bool on) {
  digitalWrite(TFT_BL, on ? HIGH : LOW);
}

// 检测双击并切换图片
void checkGestures() {
  uint16_t touchX, touchY;
  uint8_t gesture;

  if (touch.getTouch(&touchX, &touchY, &gesture)) {
    // 打印手势帮助调试
    if (gesture != None) {
      Serial.printf("检测到手势: %d\n", gesture);
    }

    // 检测左右滑动切换图片
    if (gesture == SlideLeft) {
      if (totalImages > 0) {
        currentImage = (currentImage + 1) % totalImages;
        displayImage(currentImage);
        Serial.printf("向左滑动：下一张图片 %d\n", currentImage);
      }
    } else if (gesture == SlideRight) {
      if (totalImages > 0) {
        currentImage = (currentImage > 0) ? (currentImage - 1) : (totalImages - 1);
        displayImage(currentImage);
        Serial.printf("向右滑动：上一张图片 %d\n", currentImage);
      }
    }
  }
}

void handleSerialCommand() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();  // 去除空格和换行符

    if (cmd == "log=1") {
      enableLogging = true;
      Serial.println("日志输出已开启");
    } else if (cmd == "log=0") {
      enableLogging = false;
      Serial.println("日志输出已关闭");
    }
  }
}

void logDebugInfo() {
  // 先处理串口命令
  handleSerialCommand();

  // 如果没有启用日志，直接返回
  if (!enableLogging) return;

  static unsigned long lastDebugLog = 0;
  unsigned long now = millis();

  // 每5秒打印一次调试信息
  if (now - lastDebugLog >= 5000) {
    lastDebugLog = now;

    Serial.println("\n------- 系统状态 -------");
    Serial.printf("BLE连接状态: %s\n", pServer && pServer->getConnectedCount() > 0 ? "已连接" : "未连接");
    Serial.printf("连接客户端数: %d\n", pServer ? pServer->getConnectedCount() : 0);
    Serial.printf("当前显示图片: %d/%d\n", currentImage, totalImages);
    Serial.printf("传输状态: %s\n", isTransferring ? "正在传输" : "空闲");
    Serial.printf("当前图片文件: %s\n", currentImageName.c_str());
    Serial.printf("总接收字节数: %d\n", totalBytesReceived);
    Serial.printf("总接收命令数: %d\n", commandsReceived);

    if (isTransferring && currentImageFile) {
      Serial.printf("当前文件大小: %d 字节\n", currentImageFile.size());
      Serial.printf("距上次数据: %d ms\n", now - lastDataTime);
    }

    // 打印内存状态
    size_t totalBytes = LittleFS.totalBytes();
    size_t usedBytes = LittleFS.usedBytes();
    Serial.printf("LittleFS总空间: %d 字节\n", totalBytes);
    Serial.printf("LittleFS已用: %d 字节\n", usedBytes);
    Serial.printf("LittleFS可用: %d 字节\n", totalBytes - usedBytes);

    // 打印堆内存
    Serial.printf("堆大小: %d, 空闲堆: %d\n", ESP.getHeapSize(), ESP.getFreeHeap());
    Serial.println("-------------------------\n");
  }
}

void setupBLE() {
  Serial.println("初始化 BLE...");

  // ESP32-C3专用配置
  esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
  bt_cfg.controller_task_stack_size = 10240;  // 增大堆栈
  
  // 移除不支持的配置项（hci_uart_no和hci_uart_baudrate）
  
  if (esp_bt_controller_init(&bt_cfg) != ESP_OK) {
    Serial.println("蓝牙控制器初始化失败");
    return;
  }

  if (esp_bt_controller_enable(ESP_BT_MODE_BLE) != ESP_OK) {
    Serial.println("BT启用失败");
    return;
  }

  BLEDevice::init("RoyZ-Mon3tr");
  BLEDevice::setMTU(517);
  
  // 修改功率设置方式（ESP32-C3兼容）
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_DEFAULT, ESP_PWR_LVL_P9);
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, ESP_PWR_LVL_P9);
  
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pCommandCharacteristic = pService->createCharacteristic(
    CHAR_COMMAND_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
  
  pDataCharacteristic = pService->createCharacteristic(
    CHAR_DATA_UUID,
    BLECharacteristic::PROPERTY_WRITE);

  pCommandCharacteristic->setCallbacks(new CommandCallbacks());
  pDataCharacteristic->setCallbacks(new DataCallbacks());

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinInterval(0x20);
  pAdvertising->setMaxInterval(0x40);
  BLEDevice::startAdvertising();

}


void setup() {
  commandQueue = xQueueCreate(5, sizeof(uint8_t*));
  Serial.begin(115200);
  delay(1000);
  Serial.println("初始化开始...");

  // 1. 初始化LittleFS - 使用更可靠的初始化过程
  Serial.println("初始化LittleFS...");
  if (!LittleFS.begin(false)) {  // 先尝试不格式化挂载
    Serial.println("LittleFS挂载失败，尝试格式化...");
    // 确保格式化操作成功
    if (LittleFS.format()) {
      Serial.println("LittleFS格式化成功");
      if (!LittleFS.begin(true)) {
        Serial.println("LittleFS格式化后仍然无法挂载，系统将继续但图片功能可能不正常");
      }
    } else {
      Serial.println("LittleFS格式化失败");
    }
  } else {
    Serial.println("LittleFS挂载成功");
  }

  // 检查文件系统状态
  checkFileSystem();

  // 2. 设置GPIO
  Serial.println("设置GPIO...");
  pinMode(TFT_BL, OUTPUT);
  digitalWrite(TFT_BL, LOW);  // 先关闭背光

  // 3. 初始化显示屏
  Serial.println("初始化显示屏...");
  tft.init();
  delay(200);  // 增加延迟，确保显示屏初始化完全
  tft.setRotation(0);
  tft.fillScreen(TFT_BLACK);

  // 显示启动画面
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  tft.drawString("Mon3tr 启动中...", 40, 110);

  // 现在打开背光
  digitalWrite(TFT_BL, HIGH);

  // 4. 初始化触摸
  Serial.println("初始化触摸...");
  Wire.begin(TOUCH_SDA, TOUCH_SCL);
  touch.begin();  // 直接调用，不要进行返回值判断
  // 可以增加一个自定义检查来确认触摸是否正常
  delay(50);
  uint8_t touchGesture = 0;
  uint16_t touchX = 0, touchY = 0;
  bool touchOk = touch.getTouch(&touchX, &touchY, &touchGesture);
  if (!touchOk) {
    Serial.println("触摸屏可能初始化失败！");
    // 显示错误信息
    tft.setTextColor(TFT_RED);
    tft.drawString("触摸初始化失败", 40, 140);
    delay(1000);
  }
  Serial.println("测试触摸手势检测...");
  uint16_t testX, testY;
  uint8_t testGesture;
  for (int i = 0; i < 5; i++) {
    if (touch.getTouch(&testX, &testY, &testGesture)) {
      Serial.printf("触摸检测: X=%d, Y=%d, 手势=%d\n", testX, testY, testGesture);
    } else {
      Serial.println("未检测到触摸");
    }
    delay(200);
  }



  // 5.BLE初始化
  setupBLE();

  // 6. 加载和显示图片
  loadImageOrder();
  updateImageList();

  if (totalImages > 0) {
    displayImage(currentImage);
  } else {
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.setTextSize(2);
    tft.drawString("等待图片上传...", 40, 110);
  }

  Serial.println("初始化完成");

  // 创建命令队列（在setup中）
  commandQueue = xQueueCreate(5, sizeof(uint8_t[128]));  // 5个命令缓冲区
  
  // 创建处理任务
  xTaskCreate(
    processCommandTask,
    "CmdProcessor",
    4096,
    NULL,
    2,
    NULL
  );
}

void processCommandTask(void* param) {
  uint8_t* cmdData;
  while (true) {
    if (xQueueReceive(commandQueue, &cmdData, portMAX_DELAY) == pdTRUE) {
      // 获取实际数据长度（需要根据你的协议调整）
      size_t length = strlen((char*)cmdData);
      processCommand(cmdData, length);
      delete[] cmdData;  // 释放内存
    }
  }
}

// 新增函数：检查文件系统状态
void checkFileSystem() {
  size_t totalBytes = LittleFS.totalBytes();
  size_t usedBytes = LittleFS.usedBytes();

  Serial.printf("LittleFS状态：总空间 %d 字节，已用 %d 字节，可用 %d 字节\n",
                totalBytes, usedBytes, totalBytes - usedBytes);

  // 如果使用率超过90%，尝试清理旧文件
  if (usedBytes > 0.9 * totalBytes) {
    Serial.println("文件系统使用率超过90%，尝试清理...");
    cleanupOldFiles();
  }

  // 列出所有文件
  listFiles();
}

// 新增函数：清理旧文件
void cleanupOldFiles() {
  File root = LittleFS.open("/");
  if (!root || !root.isDirectory()) {
    Serial.println("无法打开根目录");
    return;
  }

  // 查找最旧的图片文件
  String oldestFile = "";
  time_t oldestTime = time(NULL);

  File file = root.openNextFile();
  while (file) {
    String filename = String(file.name());
    if (!file.isDirectory() && filename.endsWith(".bin") && filename.startsWith("img_")) {
      // 使用文件上次修改时间作为年龄判断依据
      time_t fileTime = file.getLastWrite();
      if (fileTime < oldestTime) {
        oldestTime = fileTime;
        oldestFile = "/" + filename;
      }
    }
    file = root.openNextFile();
  }

  // 删除最旧的文件
  if (oldestFile != "") {
    Serial.printf("删除最旧的文件: %s\n", oldestFile.c_str());
    if (LittleFS.remove(oldestFile)) {
      Serial.println("删除成功");
    } else {
      Serial.println("删除失败");
    }
  }
}

// 新增函数：列出所有文件
void listFiles() {
  File root = LittleFS.open("/");
  if (!root || !root.isDirectory()) {
    Serial.println("无法打开根目录");
    return;
  }

  Serial.println("文件列表:");
  File file = root.openNextFile();
  int count = 0;

  while (file) {
    if (!file.isDirectory()) {
      Serial.printf("  %s, 大小: %d 字节\n", file.name(), file.size());
      count++;
    }
    file = root.openNextFile();
  }

  Serial.printf("共 %d 个文件\n", count);
}


void loop() {
  static unsigned long lastCheck = 0;
  static unsigned long lastBleCheck = 0;
  static unsigned long lastIdleTask = 0;
  unsigned long now = millis();

  // 1. 优先处理触摸检查，提高响应速度
  if (now - lastCheck >= 50) {  // 增加触摸检查频率
    checkGestures();
    lastCheck = now;
  }

  // 2. 检查BLE状态，优化重连
  handlePendingNotifications();
  if (now - lastBleCheck >= 5000) {  // 减少检查频率到5秒
    lastBleCheck = now;
    if (pServer != nullptr) {
      int connCount = pServer->getConnectedCount();

      // 只在连接状态改变时打印日志
      static bool wasConnected = false;
      bool isConnected = (connCount > 0);

      if (isConnected != wasConnected) {
        Serial.printf("连接状态改变：当前连接数: %d\n", connCount);
        wasConnected = isConnected;
      }

      // 如果未连接，确保广播
      if (connCount == 0) {
        delay(100);
        Serial.println("未连接，确保广播状态");
        BLEDevice::startAdvertising();  // 100delay重启广播
      }
    }

    // 3. 定期执行维护任务
    if (now - lastIdleTask >= 30000) {  // 每30秒
      lastIdleTask = now;

      // 检查文件系统状态
      size_t freeSpace = LittleFS.totalBytes() - LittleFS.usedBytes();
      if (freeSpace < 50000) {  // 如果剩余空间小于50KB
        Serial.println("存储空间不足，尝试清理...");
        cleanupOldFiles();
      }

      // 如果当前没有传输，执行垃圾回收
      if (!isTransferring) {
        Serial.println("执行维护任务...");
        delay(10);  // 让ESP32有时间执行系统任务
      }
    }

    // 4. 处理日志
    logDebugInfo();

    // 让ESP32有时间处理其他系统任务
    delay(1);  // 非常短的延迟，但足以为系统任务让步
  }
}