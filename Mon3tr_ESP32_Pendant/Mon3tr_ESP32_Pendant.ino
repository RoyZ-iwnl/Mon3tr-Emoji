#include <TFT_eSPI.h>
#include <SPI.h>
#include <Wire.h>
#include "CST816D.h"
#include <NimBLEDevice.h>
#include <SPIFFS.h>
#include <ArduinoJson.h>

// 引脚定义
#define TOUCH_SDA          4
#define TOUCH_SCL          5
#define TOUCH_INT          0
#define TOUCH_RST          1
#define LED_PIN           3  // BLE连接指示灯
#define TFT_BL            3  // 屏幕背光引脚，与LED_PIN相同

// BLE服务和特征UUID
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_COMMAND_UUID  "beb5483e-36e1-4688-b7f5-ea07361b26a8"  // 命令通道
#define CHAR_DATA_UUID     "beb5483e-36e1-4688-b7f5-ea07361b26a9"  // 数据通道

// 全局常量
#define MAX_IMAGES 10
#define IMAGE_WIDTH 240
#define IMAGE_HEIGHT 240
#define DOUBLE_TAP_TIME 300

// 命令类型
enum CommandType {
    CMD_NONE = 0,
    CMD_START_TRANSFER = 1,    // 开始传输图片
    CMD_IMAGE_DATA = 2,        // 图片数据块
    CMD_END_TRANSFER = 3,      // 结束传输
    CMD_DELETE_IMAGE = 4,      // 删除图片
    CMD_REORDER_IMAGES = 5,    // 重新排序图片
    CMD_GET_IMAGE_LIST = 6,    // 获取图片列表
    CMD_SET_DISPLAY = 7,       // 设置当前显示
    CMD_GET_STATUS = 8         // 获取状态
};

// 全局变量
TFT_eSPI tft = TFT_eSPI();
CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);
NimBLEServer* pServer = nullptr;
NimBLECharacteristic* pCommandCharacteristic = nullptr;
NimBLECharacteristic* pDataCharacteristic = nullptr;

uint16_t touchX, touchY;
uint8_t gesture;
unsigned long lastTapTime = 0;
int currentImage = 0;
int totalImages = 0;
bool isTransferring = false;
String currentImageName;
File currentImageFile;

// 存储图片顺序的结构
struct ImageInfo {
    String filename;
    bool active;
} imageList[MAX_IMAGES];

// 函数声明
void processCommand(uint8_t* data, size_t length);
void sendImageList();
void sendStatus();
void reorderImages(uint8_t* order, size_t length);
void saveImageOrder();
void loadImageOrder();
void updateImageList();
void displayImage(int index);
void controlBacklight(bool on);

class ServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) {
        Serial.println("客户端已连接");
        digitalWrite(LED_PIN, HIGH);
    }

    void onDisconnect(NimBLEServer* pServer) {
        Serial.println("客户端已断开");
        digitalWrite(LED_PIN, LOW);
        NimBLEDevice::startAdvertising();
    }
};

class CommandCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            processCommand((uint8_t*)value.data(), value.length());
        }
    }
};

void processCommand(uint8_t* data, size_t length) {
    if (length < 1) return;

    CommandType cmd = (CommandType)data[0];
    StaticJsonDocument<200> doc;

    switch (cmd) {
        case CMD_START_TRANSFER:
            if (length > 1) {
                currentImageName = "/" + String(data[1]) + ".bin";
                currentImageFile = SPIFFS.open(currentImageName, "w");
                isTransferring = true;
                Serial.println("开始接收图片: " + currentImageName);
            }
            break;

        case CMD_IMAGE_DATA:
            if (isTransferring && currentImageFile) {
                currentImageFile.write(data + 1, length - 1);
            }
            break;

        case CMD_END_TRANSFER:
            if (isTransferring && currentImageFile) {
                currentImageFile.close();
                isTransferring = false;
                updateImageList();
                Serial.println("图片接收完成");
            }
            break;

        case CMD_DELETE_IMAGE:
            if (length > 1) {
                String filename = "/" + String(data[1]) + ".bin";
                if (SPIFFS.remove(filename)) {
                    updateImageList();
                    Serial.println("删除图片: " + filename);
                }
            }
            break;

        case CMD_REORDER_IMAGES:
            // 处理重排序命令
            reorderImages(data + 1, length - 1);
            break;

        case CMD_GET_IMAGE_LIST:
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
}

void sendImageList() {
    StaticJsonDocument<512> doc;
    JsonArray images = doc.createNestedArray("images");
    
    for (int i = 0; i < totalImages; i++) {
        JsonObject img = images.createNestedObject();
        img["index"] = i;
        img["name"] = imageList[i].filename;
        img["active"] = imageList[i].active;
    }

    String jsonStr;
    serializeJson(doc, jsonStr);
    pCommandCharacteristic->setValue(jsonStr.c_str());
    pCommandCharacteristic->notify();
}

void sendStatus() {
    StaticJsonDocument<200> doc;
    doc["currentImage"] = currentImage;
    doc["totalImages"] = totalImages;
    doc["freeSpace"] = SPIFFS.totalBytes() - SPIFFS.usedBytes();

    String jsonStr;
    serializeJson(doc, jsonStr);
    pCommandCharacteristic->setValue(jsonStr.c_str());
    pCommandCharacteristic->notify();
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
    File file = SPIFFS.open("/order.json", "w");
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
    File file = SPIFFS.open("/order.json", "r");
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
    File root = SPIFFS.open("/");
    File file = root.openNextFile();
    totalImages = 0;

    while (file && totalImages < MAX_IMAGES) {
        String filename = String(file.name());
        if (!file.isDirectory() && filename.endsWith(".bin")) {
            imageList[totalImages].filename = filename;
            imageList[totalImages].active = true;
            totalImages++;
        }
        file = root.openNextFile();
    }

    saveImageOrder();
}

void displayImage(int index) {
    if (index >= totalImages) return;

    File f = SPIFFS.open(imageList[index].filename, "r");
    if (f) {
        tft.fillScreen(TFT_BLACK);
        uint8_t buf[IMAGE_WIDTH * 2];
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            f.read(buf, IMAGE_WIDTH * 2);
            tft.pushImage(0, y, IMAGE_WIDTH, 1, (uint16_t*)buf);
        }
        f.close();
    }
}

// 控制背光的函数
void controlBacklight(bool on) {
    digitalWrite(TFT_BL, on ? HIGH : LOW);
}

void setup() {

    Serial.begin(115200);
    delay(1000);  // 等待串口稳定
    Serial.println("初始化开始...");
    
    // 1. 先初始化SPIFFS
    Serial.println("初始化SPIFFS...");
    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS挂载失败");
        return;
    }
    Serial.println("SPIFFS挂载成功");
    delay(100);

    // 2. 设置GPIO
    Serial.println("设置GPIO...");
    pinMode(LED_PIN, OUTPUT);
    pinMode(TFT_BL, OUTPUT);
    controlBacklight(false);  // 先关闭背光
    
    // 3. 初始化显示屏（在触摸之前）
    Serial.println("初始化显示屏...");
    tft.init();
    delay(100);
    tft.setRotation(2);
    tft.fillScreen(TFT_BLACK);
    Serial.println("显示屏初始化完成");
    delay(100);

    // 4. 初始化触摸相关
    Serial.println("设置触摸引脚...");
    pinMode(TOUCH_RST, OUTPUT);
    digitalWrite(TOUCH_RST, HIGH);
    pinMode(TOUCH_INT, INPUT_PULLUP);
    delay(50);
    
    Wire.begin(TOUCH_SDA, TOUCH_SCL);
    Serial.println("I2C初始化完成");
    delay(50);

    // 扫描I2C
    Serial.println("扫描I2C设备...");
    byte error, address;
    for(address = 1; address < 127; address++) {
        Wire.beginTransmission(address);
        error = Wire.endTransmission();
        if (error == 0) {
            Serial.printf("发现I2C设备: 0x%02X\n", address);
        }
    }
    
    // 5. 初始化触摸
    Serial.println("初始化触摸...");
    touch.begin();
    Serial.println("触摸初始化完成");
    delay(100);

    // 6. 开启背光
    controlBacklight(true);
    Serial.println("背光开启");

    // 初始化BLE
    Serial.println("初始化BLE...");
    try {
        NimBLEDevice::init("RoyZ-Mon3tr");
        pServer = NimBLEDevice::createServer();
        pServer->setCallbacks(new ServerCallbacks());

        NimBLEService *pService = pServer->createService(SERVICE_UUID);
        
        // 命令特征
        pCommandCharacteristic = pService->createCharacteristic(
            CHAR_COMMAND_UUID,
            NIMBLE_PROPERTY::WRITE |
            NIMBLE_PROPERTY::READ |
            NIMBLE_PROPERTY::NOTIFY
        );
        pCommandCharacteristic->setCallbacks(new CommandCallbacks());

        // 数据特征
        pDataCharacteristic = pService->createCharacteristic(
            CHAR_DATA_UUID,
            NIMBLE_PROPERTY::WRITE |
            NIMBLE_PROPERTY::READ
        );

        pService->start();
        
        // 配置广播
        NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
        pAdvertising->addServiceUUID(SERVICE_UUID);
        
        // 设置广播数据
        NimBLEAdvertisementData advData;
        advData.setName("RoyZ-Mon3tr");
        advData.setCompleteServices(NimBLEUUID(SERVICE_UUID));
        pAdvertising->setAdvertisementData(advData);

        // 设置扫描响应数据
        NimBLEAdvertisementData respData;
        respData.setName("RoyZ-Mon3tr");
        pAdvertising->setScanResponseData(respData);
        
        // 设置广播参数
        pAdvertising->setMinInterval(32); // 设置最小广播间隔 (32 * 0.625ms = 20ms)
        pAdvertising->setMaxInterval(244); // 设置最大广播间隔 (244 * 0.625ms = 152.5ms)
        pAdvertising->start();
        Serial.println("BLE初始化完成");
    } catch (const std::exception& e) {
        Serial.println("BLE初始化失败");
    }

    // 8. 加载和显示图片
    loadImageOrder();
    updateImageList();
    
    if (totalImages > 0) {
        displayImage(currentImage);
        Serial.println("显示首张图片");
    } else {
        tft.setTextColor(TFT_WHITE, TFT_BLACK);
        tft.setTextSize(2);
        tft.drawString("等待图片...", 60, 110);
        Serial.println("无图片可显示");
    }

    Serial.println("初始化全部完成");

}

void checkDoubleTap() {
    if (touch.getTouch(&touchX, &touchY, &gesture)) {
        unsigned long currentTime = millis();
        if (currentTime - lastTapTime < DOUBLE_TAP_TIME) {
            if (totalImages > 0) {
                currentImage = (currentImage + 1) % totalImages;
                displayImage(currentImage);
                Serial.printf("切换到图片 %d\n", currentImage);
            }
            lastTapTime = 0;
        } else {
            lastTapTime = currentTime;
        }
    }
}

void loop() {
    static unsigned long lastCheck = 0;
    unsigned long now = millis();
    
    // 每100ms检查一次触摸
    if (now - lastCheck >= 100) {
        bool touched = touch.getTouch(&touchX, &touchY, &gesture);
        if (touched) {
            Serial.printf("Touch: x=%d, y=%d, gesture=0x%02X\n", touchX, touchY, gesture);
        }
        lastCheck = now;
    }
    
    checkDoubleTap();
    delay(20);  // 适当延时
}