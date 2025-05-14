#include "display_handler.h"
#include "commands.h"
#include "file_system.h"
#include "ble_handler.h" // 添加此行以引入sendResponse的声明

// 全局变量定义
TFT_eSPI tft = TFT_eSPI();
CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);
int currentImage = 0;
int totalImages = 0;
unsigned long lastTapTime = 0;

// 初始化显示模块
void setupDisplay() {
  Serial.println("初始化显示屏...");
  
  // 设置背光引脚
  pinMode(TFT_BL, OUTPUT);
  digitalWrite(TFT_BL, LOW);  // 先关闭背光
  
  // 初始化TFT
  tft.init();
  delay(100);
  tft.setRotation(0);
  tft.fillScreen(TFT_BLACK);
  
  // 显示启动信息
  showStartupScreen();
  
  // 打开背光
  digitalWrite(TFT_BL, HIGH);
}

// 初始化触摸模块
void setupTouch() {
  Serial.println("初始化触摸...");
  
  Wire.begin(TOUCH_SDA, TOUCH_SCL);
  touch.begin();
  
  // 测试触摸
  uint16_t touchX, touchY;
  uint8_t gesture;
  for (int i = 0; i < 3; i++) {
    if (touch.getTouch(&touchX, &touchY, &gesture)) {
      Serial.printf("触摸检测: X=%d, Y=%d, 手势=%d\n", touchX, touchY, gesture);
    }
    delay(100);
  }
}

// 控制背光
void controlBacklight(bool on) {
  digitalWrite(TFT_BL, on ? HIGH : LOW);
}

// 显示图片
void displayImage(int index) {
  if (index >= totalImages || index < 0) {
    Serial.println("错误：图片索引超出范围");
    tft.fillScreen(TFT_RED);
    return;
  }

  String filename = getImageFilename(index);
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

  // 清屏
  tft.fillScreen(TFT_BLACK);

  // 读取并显示图片
  uint8_t buf[IMAGE_WIDTH * 2 * 4];  // 4行缓冲
  uint16_t* pixelBuf = (uint16_t*)buf;
  int rowsAtTime = 4;  // 一次处理4行

  for (int y = 0; y < IMAGE_HEIGHT; y += rowsAtTime) {
    int remainingRows = min(rowsAtTime, IMAGE_HEIGHT - y);
    int bytesToRead = IMAGE_WIDTH * 2 * remainingRows;

    if (f.available() < bytesToRead) {
      // 文件数据不足
      tft.fillRect(0, y, IMAGE_WIDTH, IMAGE_HEIGHT - y, TFT_RED);
      Serial.printf("第 %d 行数据不足\n", y);
      break;
    }

    size_t bytesRead = f.read(buf, bytesToRead);
    if (bytesRead == bytesToRead) {
      for (int r = 0; r < remainingRows; r++) {
        tft.pushImage(0, y + r, IMAGE_WIDTH, 1, &pixelBuf[r * IMAGE_WIDTH]);
      }
    } else {
      Serial.printf("读取失败\n");
      tft.fillRect(0, y, IMAGE_WIDTH, IMAGE_HEIGHT - y, TFT_MAGENTA);
      break;
    }

    // 小延迟让系统处理其他任务
    delay(1);
  }

  f.close();
  Serial.println("图片显示完成");
}

// 检查手势
void checkGestures() {
  uint16_t touchX, touchY;
  uint8_t gesture;

  if (touch.getTouch(&touchX, &touchY, &gesture)) {
    // 处理手势
    switch (gesture) {
      case GESTURE_SLIDE_LEFT:
        if (totalImages > 0) {
          currentImage = (currentImage + 1) % totalImages;
          displayImage(currentImage);
          Serial.printf("向左滑动：下一张图片 %d\n", currentImage);
        }
        break;
        
      case GESTURE_SLIDE_RIGHT:
        if (totalImages > 0) {
          currentImage = (currentImage > 0) ? (currentImage - 1) : (totalImages - 1);
          displayImage(currentImage);
          Serial.printf("向右滑动：上一张图片 %d\n", currentImage);
        }
        break;
        
      case GESTURE_SINGLE_TAP:
        // 记录点击时间，用于检测双击
        lastTapTime = millis();
        break;
    }
  }
}

// 设置显示图片
void setDisplayImage(uint8_t index) {
  if (index < totalImages) {
    currentImage = index;
    displayImage(currentImage);
    
    // 发送成功响应
    uint8_t payload[1] = {(uint8_t)currentImage};
    sendResponse(CMD_SET_DISPLAY, RESP_SUCCESS, payload, 1);
  } else {
    sendResponse(CMD_SET_DISPLAY, RESP_PARAM_ERROR);
  }
}

// 显示启动画面
void showStartupScreen() {
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  tft.drawString("Mon3tr 启动中...", 40, 110);
}

// 显示错误画面
void showErrorScreen(const char* message) {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_RED);
  tft.setTextSize(2);
  tft.drawString(message, 40, 110);
}

// 显示等待画面
void showWaitingScreen() {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  tft.drawString("等待图片上传...", 40, 110);
}