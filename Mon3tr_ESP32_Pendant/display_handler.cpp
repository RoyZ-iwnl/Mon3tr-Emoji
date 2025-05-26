#include "display_handler.h"
#include "ble_handler.h"
#include "commands.h"
#include "file_system.h"

// 全局变量定义

// 修改处
// CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);

int currentImage = 0;
int totalImages = 0;
unsigned long lastTapTime = 0;
File gifpackFile;
GIFPackHeader gifpackHeader;
uint32_t* frameOffsets = nullptr;
int currentFrame = 0;
bool gifpackActive = false;
unsigned long lastFrameTime = 0;

// GFP专用帧缓冲
uint16_t* gfpFrameBuffer = nullptr;
bool gfpBufferReady = false;


// 屏幕参数定义
#define SCREEN_WIDTH 240     // 屏幕宽度
#define SCREEN_HEIGHT 240    // 屏幕高度
#define SCREEN_CENTER_X 120  // 屏幕中心X
#define SCREEN_CENTER_Y 120  // 屏幕中心Y

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
  tft.setSwapBytes(true);  // 设置字节交换以正确显示图像

  // 初始化JPEG解码器
  TJpgDec.setJpgScale(1);  // 设置比例为1:1
  TJpgDec.setCallback(jpegOutput);

  // 显示启动信息
  showStartupScreen();

  // 等待显示器完全初始化
  delay(500);  // 等待显示器稳定

  // 打开背光
  digitalWrite(TFT_BL, HIGH);

  // 初始化GFP帧缓冲
  gfpFrameBuffer = (uint16_t*)malloc(SCREEN_WIDTH * SCREEN_HEIGHT * 2);
  if (gfpFrameBuffer) {
    Serial.println("GFP帧缓冲初始化成功");
  } else {
    Serial.println("GFP帧缓冲初始化失败");
  }

  Serial.println("显示屏初始化完成");
}

// 初始化触摸模块
// 修改处
/*
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
*/

// 控制背光
void controlBacklight(bool on) {
  digitalWrite(TFT_BL, on ? HIGH : LOW);
}

// 在圆形屏幕上显示文本（考虑弧度）
void drawCircularText(const char* text, int y, uint16_t color, uint8_t size, uint8_t font) {
  Serial.printf("显示文本: %s, Y位置: %d\n", text, y);
  // 计算文本宽度
  tft.setTextSize(size);
  int textWidth = tft.textWidth(text, font);

  // 计算在圆形区域内的可见X坐标
  int radius = SCREEN_WIDTH / 2;
  int distFromCenter = abs(y - SCREEN_CENTER_Y);

  // 防止计算错误 (当距离超过半径时)
  if (distFromCenter >= radius) {
    Serial.println("警告: 文本位置超出圆形区域");
    distFromCenter = radius - 1;
  }

  int maxWidth = 2 * sqrt(radius * radius - distFromCenter * distFromCenter);

  // 如果文本太长，调整大小
  if (textWidth > maxWidth) {
    while (textWidth > maxWidth && size > 1) {
      size--;
      tft.setTextSize(size);
      textWidth = tft.textWidth(text, font);
    }
  }

  // 计算起始X位置（居中）
  int x = SCREEN_CENTER_X - (textWidth / 2);
  tft.setTextColor(color);
  tft.drawString(text, x, y, font);
  Serial.printf("最终位置: X=%d, Y=%d, 字体大小=%d\n", x, y, size);
}

// 在指定位置显示文本（左对齐）
void drawText(const char* text, int x, int y, uint16_t color, uint8_t size, uint8_t font) {
  tft.setTextSize(size);
  tft.setTextColor(color);
  tft.drawString(text, x, y, font);
  Serial.printf("显示文本: %s, 位置: X=%d, Y=%d\n", text, x, y);
}

// 解析图像格式
uint8_t getFormatFromIndex(uint8_t index) {
  return index & IMG_FORMAT_MASK;  // 获取高4位
}

// 解析文件索引
uint8_t getFileIndexFromIndex(uint8_t index) {
  return index & IMG_INDEX_MASK;  // 获取低4位
}

// 组合格式和索引
uint8_t combineFormatAndIndex(uint8_t format, uint8_t fileIndex) {
  return (format & IMG_FORMAT_MASK) | (fileIndex & IMG_INDEX_MASK);
}

// JPEG解码回调（用于静态图片）
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap) {
  tft.pushImage(x, y, w, h, bitmap);
  return true;
}

// 确保GFP帧缓冲可用
bool ensureGfpBuffer() {
  if (gfpFrameBuffer) {
    return true;  // 已经存在
  }

  // 尝试分配GFP帧缓冲
  Serial.println("尝试分配GFP帧缓冲...");
  size_t bufferSize = SCREEN_WIDTH * SCREEN_HEIGHT * 2;
  size_t freeHeap = ESP.getFreeHeap();

  Serial.printf("需要内存: %d字节, 可用内存: %d字节\n", bufferSize, freeHeap);

  if (freeHeap < bufferSize + 10000) {  // 保留10KB余量
    Serial.println("内存不足，无法分配GFP帧缓冲");
    return false;
  }

  // 尝试多次分配，处理内存碎片化
  for (int retry = 0; retry < 5; retry++) {
    gfpFrameBuffer = (uint16_t*)malloc(bufferSize);
    if (gfpFrameBuffer) {
      Serial.printf("GFP帧缓冲分配成功(重试%d次)\n", retry);
      return true;
    }
    Serial.printf("GFP帧缓冲分配失败，重试%d/5\n", retry + 1);
    delay(10);  // 短暂延迟，让系统整理内存
  }

  Serial.println("GFP帧缓冲分配最终失败");
  return false;
}

// 安全释放GFP帧缓冲
void releaseGfpBuffer() {
  if (gfpFrameBuffer) {
    free(gfpFrameBuffer);
    gfpFrameBuffer = nullptr;
    Serial.println("GFP帧缓冲已释放");
  }
}

// GFP专用JPEG解码回调（写入帧缓冲）
bool gfpJpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap) {
  if (!gfpFrameBuffer) return false;

  // 将解码的数据写入帧缓冲
  for (int dy = 0; dy < h; dy++) {
    for (int dx = 0; dx < w; dx++) {
      int srcIndex = dy * w + dx;
      int dstIndex = (y + dy) * SCREEN_WIDTH + (x + dx);
      if (dstIndex < SCREEN_WIDTH * SCREEN_HEIGHT) {
        gfpFrameBuffer[dstIndex] = bitmap[srcIndex];
      }
    }
  }
  return true;
}


// 检查是否有GIFPack在播放
bool isGifpackPlaying() {
  return gifpackActive;
}

// 打开GIFPack文件
bool openGifpack(const char* filename) {
  Serial.printf("打开GIFPack文件: %s\n", filename);

  // 如果有正在播放的GIFPack，先关闭
  if (gifpackActive) {
    closeGifpack();
  }

  // 检查帧缓冲是否可用
  if (!gfpFrameBuffer) {
    Serial.println("GFP帧缓冲未初始化");
    return false;
  }

  // 打开文件
  gifpackFile = LittleFS.open(filename, "r");
  if (!gifpackFile) {
    Serial.println("打开GIFPack文件失败");
    return false;
  }

  // 读取头信息
  size_t headerSize = sizeof(GIFPackHeader);
  size_t bytesRead = gifpackFile.read((uint8_t*)&gifpackHeader, headerSize);
  if (bytesRead != headerSize) {
    Serial.printf("读取GIFPack头信息失败: 请求%d字节, 只读取到%d字节\n", headerSize, bytesRead);
    gifpackFile.close();
    return false;
  }

  // 检查魔术字节
  if (memcmp(gifpackHeader.magic, GIFPACK_MAGIC, 4) != 0 || gifpackHeader.version != GIFPACK_VERSION) {
    Serial.println("无效的GIFPack格式");
    Serial.printf("魔术字节: %c%c%c%c, 版本: %d\n",
                  gifpackHeader.magic[0], gifpackHeader.magic[1],
                  gifpackHeader.magic[2], gifpackHeader.magic[3],
                  gifpackHeader.version);
    gifpackFile.close();
    return false;
  }

  Serial.printf("GIFPack信息: %d帧, %dfps, %dx%d\n",
                gifpackHeader.frames, gifpackHeader.fps,
                gifpackHeader.width, gifpackHeader.height);

  // 检查参数合理性
  if (gifpackHeader.frames == 0 || gifpackHeader.frames > 500 || gifpackHeader.width != 240 || gifpackHeader.height != 240) {
    Serial.println("GIFPack参数不合理");
    gifpackFile.close();
    return false;
  }

  // 检查可用内存
  size_t offsetArraySize = sizeof(uint32_t) * gifpackHeader.frames;
  size_t freeHeap = ESP.getFreeHeap();
  Serial.printf("需要内存: %d字节, 可用内存: %d字节\n", offsetArraySize, freeHeap);
  if (offsetArraySize > (freeHeap / 2)) {  // 保留一半内存给其他用途
    Serial.println("内存不足，无法分配帧偏移量数组");
    gifpackFile.close();
    return false;
  }

  // 分配帧偏移量数组内存
  frameOffsets = (uint32_t*)malloc(offsetArraySize);
  if (!frameOffsets) {
    Serial.println("内存分配失败");
    gifpackFile.close();
    return false;
  }

  // 读取帧偏移量
  if (gifpackFile.read((uint8_t*)frameOffsets, offsetArraySize) != offsetArraySize) {
    Serial.println("读取帧偏移量失败");
    free(frameOffsets);
    frameOffsets = nullptr;
    gifpackFile.close();
    return false;
  }

  // 验证偏移量合理性
  uint32_t fileSize = gifpackFile.size();
  for (int i = 0; i < gifpackHeader.frames; i++) {
    if (frameOffsets[i] >= fileSize) {
      Serial.printf("帧%d偏移量%d超出文件大小%d\n", i, frameOffsets[i], fileSize);
      free(frameOffsets);
      frameOffsets = nullptr;
      gifpackFile.close();
      return false;
    }
  }

  currentFrame = 0;
  gifpackActive = true;
  lastFrameTime = millis();
  Serial.println("GIFPack初始化成功");
  return true;
}

// 安全关闭GIFPack资源
void closeGifpack() {
  // 先设置标志，防止其他线程访问
  gifpackActive = false;
  gfpBufferReady = false;

  // 等待当前帧处理完成
  delay(20);

  if (frameOffsets) {
    free(frameOffsets);
    frameOffsets = nullptr;
  }

  if (gifpackFile) {
    gifpackFile.close();
    delay(10);
  }

  // 注意：不在这里释放gfpFrameBuffer，因为可能会重新使用
  // gfpFrameBuffer将在需要时在displayImage中管理

  Serial.println("GIFPack资源已关闭");
}



// 显示当前帧
bool showGifpackFrame() {
  if (!gifpackActive || !gifpackFile || currentFrame >= gifpackHeader.frames || !gfpFrameBuffer) {
    Serial.println("GIFPack状态无效");
    return false;
  }

  // 获取当前帧的偏移量
  uint32_t frameOffset = frameOffsets[currentFrame];
  // 计算下一帧偏移量（用于确定当前帧大小）
  uint32_t nextFrameOffset;
  if (currentFrame + 1 < gifpackHeader.frames) {
    nextFrameOffset = frameOffsets[currentFrame + 1];
  } else {
    nextFrameOffset = gifpackFile.size();
  }

  uint32_t frameSize = nextFrameOffset - frameOffset;

  // 检查帧大小合理性
  if (frameSize == 0 || frameSize > 50000) {  // JPEG帧不应该超过50KB
    Serial.printf("帧%d大小异常: %d字节\n", currentFrame, frameSize);
    return false;
  }

  // 跳转到帧位置
  if (!gifpackFile.seek(frameOffset)) {
    Serial.printf("跳转到帧 %d 失败，偏移量: %d\n", currentFrame, frameOffset);
    return false;
  }

  // 分配临时缓冲区读取帧数据
  uint8_t* frameBuffer = (uint8_t*)malloc(frameSize);
  if (!frameBuffer) {
    Serial.printf("分配帧缓冲区失败，需要%d字节\n", frameSize);
    return false;
  }

  // 读取帧数据
  size_t bytesRead = gifpackFile.read(frameBuffer, frameSize);
  if (bytesRead != frameSize) {
    Serial.printf("读取帧数据失败: 请求%d字节，实际读取%d字节\n", frameSize, bytesRead);
    free(frameBuffer);
    return false;
  }

  // 清空帧缓冲区
  memset(gfpFrameBuffer, 0, SCREEN_WIDTH * SCREEN_HEIGHT * 2);

  // 使用GFP专用解码器解码JPEG帧到帧缓冲
  TJpgDec.setJpgScale(1);
  TJpgDec.setCallback(gfpJpegOutput);  // 使用GFP专用回调

  // 从内存解码JPEG
  bool decodeSuccess = (TJpgDec.drawJpg(0, 0, frameBuffer, frameSize) == 0);

  // 释放缓冲区
  free(frameBuffer);

  if (!decodeSuccess) {
    Serial.printf("解码帧%d失败\n", currentFrame);
    // 恢复为普通JPEG回调
    TJpgDec.setCallback(jpegOutput);
    return false;
  }

  // 标记帧缓冲准备就绪
  gfpBufferReady = true;

  // 恢复为普通JPEG回调
  TJpgDec.setCallback(jpegOutput);

  //Serial.printf("成功解码帧 %d/%d 到缓冲区\n", currentFrame + 1, gifpackHeader.frames);
  return true;
}

// 处理GIFPack动画
void processGifpackAnimation() {
  // 严格状态检查
  if (!gifpackActive || !gifpackFile || !frameOffsets || !gfpFrameBuffer) {
    return;
  }

  // 计算帧间隔时间
  unsigned long frameInterval = 1000 / gifpackHeader.fps;
  unsigned long currentTime = millis();

  // 检查是否到了播放下一帧的时间
  if (currentTime - lastFrameTime >= frameInterval) {
    // 如果当前帧缓冲准备就绪，显示它
    if (gfpBufferReady && gifpackActive) {  // 双重检查
      tft.pushImage(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, gfpFrameBuffer);
      gfpBufferReady = false;
    }

    // 移动到下一帧
    currentFrame++;
    if (currentFrame >= gifpackHeader.frames) {
      currentFrame = 0;
    }

    // 异步解码下一帧到缓冲区
    if (gifpackActive && !showGifpackFrame()) {
      Serial.println("播放GIFPack帧失败，重置");
      currentFrame = 0;
      // 尝试重新显示第一帧
      if (!showGifpackFrame()) {
        Serial.println("重置到第一帧也失败，关闭GIFPack");
        closeGifpack();
        showErrorScreen("GFP PLAY ERROR AR");
        return;
      }
    }

    lastFrameTime = currentTime;
  }
}

// 显示图片 - 没有任何文字
void displayImage(int index) {
  Serial.printf("准备显示图片索引: %d\n", index);

  // 状态保护，确保安全关闭GIFPack
  static bool isDisplaying = false;
  if (isDisplaying) {
    Serial.println("正在处理其他显示任务，跳过");
    return;
  }

  isDisplaying = true;

  // 安全关闭之前的GIFPack
  if (gifpackActive) {
    Serial.println("安全关闭GIFPack...");
    closeGifpack();
    delay(50);  // 确保关闭完成
  }

  // 清屏
  tft.fillScreen(TFT_BLACK);

  String filename = getImageFilename(index);
  Serial.printf("准备显示图片: %s, 格式: %s\n", filename.c_str(), filename.substring(filename.lastIndexOf('.')).c_str());

  // 根据文件扩展名确定格式
  if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
    // JPEG格式 - 不需要特殊内存管理
    File f = LittleFS.open(filename, "r");
    if (f) {
      uint32_t fileSize = f.size();
      if (fileSize > 0 && fileSize < 100000) {
        uint8_t* buffer = (uint8_t*)malloc(fileSize);
        if (buffer) {
          f.read(buffer, fileSize);
          TJpgDec.drawJpg(0, 0, buffer, fileSize);
          free(buffer);
        } else {
          Serial.println("JPEG缓冲区分配失败");
          showErrorScreen("JPEG MEM ERROR");
        }
      } else {
        Serial.printf("JPEG文件大小异常: %d\n", fileSize);
        showErrorScreen("JPEG SIZE ERROR");
      }
      f.close();
    } else {
      Serial.println("JPEG文件打开失败");
      showErrorScreen("JPEG OPEN FAIL");
    }
  }
  else if (filename.endsWith(".gfp")) {
    // GIFPack格式 - 确保帧缓冲可用
    Serial.println("开始初始化GIFPack...");

    // 检查内存状态
    size_t freeHeap = ESP.getFreeHeap();
    if (freeHeap < 50000) {
      Serial.printf("内存不足启动GIFPack: %d\n", freeHeap);
      showErrorScreen("GFP MEM LOW");
      isDisplaying = false;
      return;
    }

    // 确保GFP帧缓冲存在，如果不存在则分配
    if (!gfpFrameBuffer) {
      Serial.println("GFP帧缓冲不存在，开始分配");
      size_t bufferSize = SCREEN_WIDTH * SCREEN_HEIGHT * 2;

      // 尝试分配，但不要多次重试，避免进一步碎片化
      gfpFrameBuffer = (uint16_t*)malloc(bufferSize);
      if (!gfpFrameBuffer) {
        Serial.printf("GFP帧缓冲分配失败，需要%d字节，可用%d字节\n", bufferSize, freeHeap);
        showErrorScreen("GFP BUF FAIL");
        isDisplaying = false;
        return;
      }
      Serial.println("GFP帧缓冲分配成功");
    } else {
      Serial.println("GFP帧缓冲已存在，直接使用");
    }

    delay(100);  // 确保系统稳定

    if (openGifpack(filename.c_str())) {
      if (!showGifpackFrame()) {
        Serial.println("显示GIFPack第一帧失败");
        showErrorScreen("GFP PLAY ERROR FF");
        closeGifpack();
      } else {
        Serial.println("GIFPack初始化成功，开始播放");
        if (gfpBufferReady) {
          tft.pushImage(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, gfpFrameBuffer);
          gfpBufferReady = false;
        }
      }
    } else {
      Serial.println("GIFPack初始化失败");
      showErrorScreen("GFP INIT FAIL");
    }

  } else {
    Serial.printf("不支持的文件格式: %s\n", filename.c_str());
    showErrorScreen("UNSUPPORTED FORMAT");
  }

  Serial.println("图片显示完成");
  isDisplaying = false;
}

// 修改处
// 检查手势
/*
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
*/

// 设置显示图片
void setDisplayImage(uint8_t index) {
  // 提取格式和实际索引
  uint8_t format = getFormatFromIndex(index);
  uint8_t fileIndex = getFileIndexFromIndex(index);

  // 在图片列表中查找匹配的图片
  int foundIndex = -1;
  for (int i = 0; i < totalImages; i++) {
    if (imageList[i].format == format && imageList[i].fileIndex == fileIndex) {
      foundIndex = i;
      break;
    }
  }

  if (foundIndex >= 0) {
    currentImage = foundIndex;
    displayImage(currentImage);
    // 发送成功响应，返回组合索引
    uint8_t payload[1] = { index };
    sendResponse(CMD_SET_DISPLAY, RESP_SUCCESS, payload, 1);
  } else {
    sendResponse(CMD_SET_DISPLAY, RESP_PARAM_ERROR);
  }
}

// 显示启动画面 - 使用英文
void showStartupScreen() {
  tft.fillScreen(TFT_BLACK);
  drawCircularText("Mon3tr", 90, TFT_GREEN, 3);
  drawCircularText("STARTING...", 130, TFT_WHITE, 2);
  drawCircularText("By:RoyZ", 160, TFT_WHITE, 2);
  Serial.println("显示启动画面");
}

// 显示等待画面 - 使用英文
void showWaitingScreen() {
  tft.fillScreen(TFT_BLACK);
  drawCircularText("Mon3tr", 60, TFT_GREEN, 3);
  drawCircularText("By:RoyZ", 100, TFT_CYAN, 1);
  drawCircularText("WAITING", 130, TFT_YELLOW, 2);
  drawCircularText("IMAGE UPLOAD...", 160, TFT_WHITE, 2);
  Serial.println("显示等待画面");
}

// 显示错误画面 - 使用英文
void showErrorScreen(const char* message) {
  tft.fillScreen(TFT_BLACK);
  drawCircularText("ERROR", 90, TFT_RED, 3);
  drawCircularText(message, 130, TFT_WHITE, 2);
  Serial.printf("显示错误画面: %s\n", message);
}

// 清除屏幕
void clearScreen() {
  tft.fillScreen(TFT_BLACK);
  Serial.println("清除屏幕");
}
