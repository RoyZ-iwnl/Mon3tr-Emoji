#include "display_handler.h"
#include "commands.h"
#include "file_system.h"
#include "ble_handler.h"


// 全局变量定义
TFT_eSPI tft = TFT_eSPI();
CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);
int currentImage = 0;
int totalImages = 0;
unsigned long lastTapTime = 0;
PNG png;
AnimatedGIF gif;
bool isPlayingGif = false;
String currentGifPath = "";
unsigned long lastGifFrameTime = 0;

/**************************
*********解码器回调等********
**************************/

// JPEG解码回调
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t *bitmap) {
  tft.pushImage(x, y, w, h, bitmap);
  return true;
}

// PNG解码回调
void pngDraw(PNGDRAW *pDraw) {
  uint16_t line[IMAGE_WIDTH];
  png.getLineAsRGB565(pDraw, line, PNG_RGB565_LITTLE_ENDIAN, 0x00000000);
  tft.pushImage(0, pDraw->y, IMAGE_WIDTH, 1, line);
}

// GIF解码回调
void GIFDraw(GIFDRAW *pDraw) {
  tft.pushImage(pDraw->iX, pDraw->y, pDraw->iWidth, 1, (uint16_t *)pDraw->pPixels);
}

// 文件操作回调
void *pngOpen(const char *filename, int32_t *size) {
  File *f = new File(LittleFS.open(filename));
  if (!f || !f->available()) {
    return nullptr;
  }
  *size = f->size();
  return f;
}

void pngClose(void *handle) {
  File *f = (File *)handle;
  if (f) f->close();
  delete f;
}

int32_t pngRead(PNGFILE *pFile, uint8_t *pBuf, int32_t len) {
  File *f = (File *)pFile->fHandle;
  return f->read(pBuf, len);
}

int32_t pngSeek(PNGFILE *pFile, int32_t pos) {
  File *f = (File *)pFile->fHandle;
  return f->seek(pos);
}

// GIF文件操作回调
void *GIFOpenFile(const char *fname, int32_t *pSize) {
  File *f = new File(LittleFS.open(fname));
  *pSize = f->size();
  return (void *)f;
}

void GIFCloseFile(void *pHandle) {
  File *f = (File *)pHandle;
  if (f) f->close();
  delete f;
}

int32_t GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen) {
  File *f = (File *)pFile->fHandle;
  return f->read(pBuf, iLen);
}

int32_t GIFSeekFile(GIFFILE *pFile, int32_t iPosition) {
  File *f = (File *)pFile->fHandle;
  return f->seek(iPosition);
}

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

  // 初始化解码器
  TJpgDec.setJpgScale(1);
  TJpgDec.setCallback(jpegOutput);
  gif.begin(LITTLE_ENDIAN_PIXELS);

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

// 播放GIF的一帧
void playGifFrame(String gifPath) {
  if (!LittleFS.exists(gifPath)) {
    isPlayingGif = false;
    return;
  }

  // 使用文件名而不是文件对象
  if (gif.open(gifPath.c_str(), GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
    if (!gif.playFrame(true, NULL)) {
      // 到达GIF末尾，重置
      gif.reset();
    }
    gif.close();
  }
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

  // 清屏
  tft.fillScreen(TFT_BLACK);

  // 根据文件扩展名判断格式
  if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
    // JPEG格式
    Serial.println("显示JPEG图片: " + filename);
    TJpgDec.drawFsJpg(0, 0, filename.c_str());
  } else if (filename.endsWith(".png")) {
    // PNG格式
    Serial.println("显示PNG图片: " + filename);
    if (png.open(filename.c_str(), pngOpen, pngClose, pngRead, pngSeek, pngDraw) == PNG_SUCCESS) {
      png.decode(NULL, 0);
      png.close();
    } else {
      Serial.println("PNG解码失败");
      tft.fillScreen(TFT_MAGENTA);
    }
  } else if (filename.endsWith(".gif")) {
    // GIF格式
    Serial.println("显示GIF图片: " + filename);
    // 设置GIF播放全局变量
    isPlayingGif = true;
    currentGifPath = filename;
    lastGifFrameTime = millis();

    // 显示第一帧 - 使用文件名而不是文件对象
    if (gif.open(filename.c_str(), GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
      if (!gif.playFrame(true, NULL)) {
        gif.reset();
      }
      gif.close();
    }
  } else if (filename.endsWith(".ibin")) {
    // 原始二进制格式 (RGB565)
    Serial.println("显示原始格式图片: " + filename);
    File f = LittleFS.open(filename, "r");
    if (!f) {
      Serial.println("错误：无法打开文件: " + filename);
      tft.fillScreen(TFT_YELLOW);
      return;
    }

    // 读取并显示图片
    uint8_t buf[IMAGE_WIDTH * 2 * 4];  // 4行缓冲
    uint16_t *pixelBuf = (uint16_t *)buf;
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
      yield();
    }

    f.close();
  } else {
    Serial.println("不支持的文件格式: " + filename);
    tft.fillScreen(TFT_PURPLE);
    return;
  }

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
    uint8_t payload[1] = { (uint8_t)currentImage };
    sendResponse(CMD_SET_DISPLAY, RESP_SUCCESS, payload, 1);
  } else {
    sendResponse(CMD_SET_DISPLAY, RESP_PARAM_ERROR);
  }
}

// 显示启动画面
void showStartupScreen() {
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  //("Mon3tr 启动中...", 40, 110);
}

// 显示错误画面
void showErrorScreen(const char *message) {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_RED);
  tft.setTextSize(2);
  //tft.drawString(message, 40, 110);
}

// 显示等待画面
void showWaitingScreen() {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE);
  tft.setTextSize(2);
  //tft.drawString("等待图片上传...", 40, 110);
}