#include "display_handler.h"
#include "ble_handler.h"
#include "commands.h"
#include "file_system.h"

// 全局变量定义
TFT_eSPI tft = TFT_eSPI();
CST816D touch(TOUCH_SDA, TOUCH_SCL, TOUCH_RST, TOUCH_INT);
int currentImage = 0;
int totalImages = 0;
unsigned long lastTapTime = 0;
AnimatedGIF gif;
bool gifActive = false;
unsigned long lastGifFrameTime = 0;
File gifFile;

// PNGLE 解码相关变量
static uint16_t png_line_buffer[SCREEN_WIDTH];  // 行缓冲区
static uint32_t png_current_y = 0;              // 当前解码行

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

  // 打开背光
  digitalWrite(TFT_BL, HIGH);

  Serial.println("显示屏初始化完成");
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

// JPEG解码回调
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap) {
  tft.pushImage(x, y, w, h, bitmap);
  return true;
}

// PNG 像素绘制回调
void on_png_draw(pngle_t* pngle, uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint8_t rgba[4]) {
  if (x >= SCREEN_WIDTH || y >= SCREEN_HEIGHT) return;

  // 处理透明度（简单阈值）
  if (rgba[3] < 128) return;  // 透明像素不绘制

  // 转换为 RGB565
  uint16_t color = tft.color565(rgba[0], rgba[1], rgba[2]);

  // 缓存当前行
  if (y != png_current_y) {
    // 推送上一行数据
    tft.pushImage(0, png_current_y, SCREEN_WIDTH, 1, png_line_buffer);
    memset(png_line_buffer, 0, sizeof(png_line_buffer));  // 清空缓冲区
    png_current_y = y;
  }
  png_line_buffer[x] = color;
}

// GIF 解码回调函数
void GIFDraw(GIFDRAW *pDraw) {
    uint8_t *s;
    uint16_t *d, *usTemp;
    int x, y;

    // 计算实际宽度（不超过屏幕）
    int iWidth = (pDraw->iWidth > SCREEN_WIDTH) ? SCREEN_WIDTH : pDraw->iWidth;
    
    s = pDraw->pPixels;             // 原始像素数据（索引）
    y = pDraw->iY + pDraw->y;       // 当前行的实际Y坐标
    
    if (y >= SCREEN_HEIGHT) return;  // 超出屏幕范围，直接返回
    
    if (pDraw->ucHasTransparency) { // 有透明像素需要特殊处理
        // 分配临时缓冲区
        usTemp = (uint16_t *)malloc(iWidth * 2);
        if (usTemp == NULL) {
            Serial.println("内存分配失败");
            return;
        }
        
        // 读取当前屏幕内容作为背景
        tft.readRect(pDraw->iX, y, iWidth, 1, usTemp);
        
        // 准备像素数据
        d = (uint16_t *)pDraw->pPixels;
        
        // 处理每个像素
        for (x = 0; x < iWidth; x++) {
            if (pDraw->pPalette[s[x]] == 0) { // 透明像素
                d[x] = usTemp[x];              // 保持背景
            } else {
                // 从调色板获取颜色并转换为RGB565
                uint32_t rgb = pDraw->pPalette[s[x]];
                d[x] = tft.color565(
                    (rgb >> 16) & 0xFF,  // R
                    (rgb >> 8) & 0xFF,   // G
                    rgb & 0xFF           // B
                );
            }
        }
        
        // 显示处理后的像素
        tft.pushImage(pDraw->iX, y, iWidth, 1, d);
        
        // 释放临时缓冲区
        free(usTemp);
    } else {
        // 没有透明像素，直接转换和显示
        d = (uint16_t *)pDraw->pPixels;
        for (x = 0; x < iWidth; x++) {
            uint32_t rgb = pDraw->pPalette[s[x]];
            d[x] = tft.color565(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF
            );
        }
        tft.pushImage(pDraw->iX, y, iWidth, 1, d);
    }
}

// 打开GIF文件
void* GIFOpenFile(const char *fname, int32_t *pSize) {
    File f = LittleFS.open(fname, "r");
    if (!f) {
        Serial.println("打开GIF文件失败");
        return NULL;
    }
    *pSize = f.size();
    return new File(f);
}

// 关闭GIF文件
void GIFCloseFile(void *pHandle) {
    File *f = (File *)pHandle;
    if (f != NULL) {
        f->close();
        delete f;
    }
}

// 读取GIF数据
int32_t GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen) {
    File *f = (File *)pFile->fHandle;
    if (f == NULL) return 0;
    return f->read(pBuf, iLen);
}

// 定位GIF文件位置
int32_t GIFSeekFile(GIFFILE *pFile, int32_t iPosition) {
    File *f = (File *)pFile->fHandle;
    if (f == NULL) return 0;
    return f->seek(iPosition);
}

// 显示图片 - 没有任何文字
void displayImage(int index) {
  if (index >= totalImages || index < 0) {
    Serial.println("错误：图片索引超出范围");
    tft.fillScreen(TFT_RED);
    return;
  }

  // 如果当前有GIF在播放，关闭它
  if (gifActive) {
    gif.close();
    if (gifFile) {
      gifFile.close();
    }
    gifActive = false;
  }

  String filename = getImageFilename(index);
  if (!LittleFS.exists(filename)) {
    Serial.println("错误：文件不存在: " + filename);
    tft.fillScreen(TFT_BLUE);
    return;
  }

  // 获取文件扩展名
  String extension = filename.substring(filename.lastIndexOf("."));
  extension.toLowerCase();

  // 清屏 - 确保没有文字
  tft.fillScreen(TFT_BLACK);

  Serial.println("准备显示图片: " + filename + ", 格式: " + extension);

  // 根据文件类型使用不同的解码器
  if (extension == ".jpg" || extension == ".jpeg") {
    File f = LittleFS.open(filename, "r");
    if (f) {
      uint32_t fileSize = f.size();
      uint8_t* buffer = (uint8_t*)malloc(fileSize);
      if (buffer) {
        f.read(buffer, fileSize);
        TJpgDec.drawJpg(0, 0, buffer, fileSize);
        free(buffer);
      }
      f.close();
    }
  } else if (extension == ".png") {
    File f = LittleFS.open(filename, "r");
    if (!f) {
      Serial.println("错误：无法打开PNG文件");
      return;
    }

    pngle_t* pngle = pngle_new();
    pngle_set_draw_callback(pngle, on_png_draw);

    // 初始化行缓存
    png_current_y = 0;
    memset(png_line_buffer, 0, sizeof(png_line_buffer));

    // 分块解码
    uint8_t buffer[256];
    size_t len;
    int ret;
    while ((len = f.read(buffer, sizeof(buffer))) > 0) {
      if ((ret = pngle_feed(pngle, buffer, len)) < 0) {
        Serial.printf("PNG解码错误: %s\n", pngle_error(pngle));
        break;
      }
    }

    // 推送最后一行
    if (png_current_y < SCREEN_HEIGHT) {
      tft.pushImage(0, png_current_y, SCREEN_WIDTH, 1, png_line_buffer);
    }

    pngle_destroy(pngle);
    f.close();
  } else if (extension == ".gif") {
    // 对于GIF，我们只进行初始化，实际播放在主循环中处理
    gifFile = LittleFS.open(filename, "r");
    if (gifFile) {
      Serial.printf("GIF文件打开成功，大小: %d 字节\n", gifFile.size());

      // 尝试读取GIF文件头以确认格式
      uint8_t header[6];
      size_t bytesRead = gifFile.read(header, 6);

      if (bytesRead == 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8' && (header[4] == '9' || header[4] == '7') && header[5] == 'a') {
        Serial.println("有效的GIF文件头");

        // 确保文件指针重置到开头
        gifFile.seek(0);

        // 打开GIF解码
        if (gif.open(filename.c_str(), GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
          Serial.println("GIF解码器初始化成功");
          gifActive = true;

          // 尝试显示第一帧
          if (gif.playFrame(true, NULL)) {
            Serial.println("第一帧显示成功");
          } else {
            Serial.println("第一帧显示失败");
          }

          lastGifFrameTime = millis();
        } else {
          Serial.println("GIF解码器初始化失败");
          gifFile.close();
          gifActive = false;
        }
      } else {
        Serial.println("无效的GIF文件头");
        gifFile.close();
        gifActive = false;
      }
    } else {
      Serial.println("GIF文件打开失败");
    }
  } else {
    // 不支持的格式 - 不显示任何文字，只是显示空白屏幕
    Serial.println("不支持的图片格式: " + extension);
  }

  Serial.println("图片显示完成");
}

// 检查是否有GIF在播放
bool isGifPlaying() {
  return gifActive;
}

// 处理GIF动画
void processGifAnimation() {
    if (!gifActive) return;
    
    unsigned long now = millis();
    if (now - lastGifFrameTime > 20) { // 约50FPS
        lastGifFrameTime = now;
        
        if (!gif.playFrame(false, NULL)) {
            Serial.println("重新播放GIF");
            gif.reset(); // 循环播放
        }
    }
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