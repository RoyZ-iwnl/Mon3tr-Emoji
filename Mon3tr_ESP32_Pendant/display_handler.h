#ifndef DISPLAY_HANDLER_H
#define DISPLAY_HANDLER_H

#include <TFT_eSPI.h>
#include <TJpg_Decoder.h>
//#include <pngle.h>
#include <LittleFS.h>
#include <Wire.h>

// 屏幕参数定义
#define SCREEN_WIDTH 240     // 屏幕宽度
#define SCREEN_HEIGHT 240    // 屏幕高度
#define SCREEN_CENTER_X 120  // 屏幕中心X
#define SCREEN_CENTER_Y 120  // 屏幕中心Y

// 图像参数
#define IMAGE_WIDTH 240       // 图像宽度
#define IMAGE_HEIGHT 240      // 图像高度
#define IMG_FORMAT_MASK 0xF0  // 格式掩码（高4位）
#define IMG_INDEX_MASK 0x0F   // 索引掩码（低4位）

// 图像格式定义
#define IMG_FORMAT_BIN 0x00      // 原始二进制格式（不再使用）
#define IMG_FORMAT_JPEG 0x10     // JPEG格式
#define IMG_FORMAT_PNG 0x20      // PNG格式（不再使用）
#define IMG_FORMAT_GIFPACK 0x30  // GIFPack格式

// 修改处
/*
// 引脚定义
#define TOUCH_SDA 4
#define TOUCH_SCL 5
#define TOUCH_INT 0
#define TOUCH_RST 1
*/

#define TFT_BL 3

// 颜色定义
#define COLOR_BLACK TFT_BLACK
#define COLOR_WHITE TFT_WHITE
#define COLOR_RED TFT_RED
#define COLOR_GREEN TFT_GREEN
#define COLOR_BLUE TFT_BLUE
#define COLOR_CYAN TFT_CYAN
#define COLOR_MAGENTA TFT_MAGENTA
#define COLOR_YELLOW TFT_YELLOW

// GIFPack结构定义
#define GIFPACK_MAGIC "GFPK"
#define GIFPACK_VERSION 0x01

// 使用packed属性确保结构体内存布局
struct __attribute__((packed)) GIFPackHeader {
  char magic[4];      // 魔术字节 "GFPK"
  uint8_t version;    // 版本号
  uint16_t frames;    // 帧数（小端序）
  uint8_t fps;        // 每秒帧数
  uint16_t width;     // 宽度（小端序）
  uint16_t height;    // 高度（小端序）
  uint32_t reserved;  // 预留字节uint32_t
};

// 全局变量声明
extern TFT_eSPI tft;
// 修改处
//extern CST816D touch;
extern int currentImage;
extern int totalImages;
extern File gifpackFile;
extern GIFPackHeader gifpackHeader;
extern uint32_t* frameOffsets;
extern int currentFrame;
extern bool gifpackActive;
extern unsigned long lastFrameTime;

// GFP专用帧缓冲
extern uint16_t* gfpFrameBuffer;
extern bool gfpBufferReady;

// 函数声明
void setupDisplay();                                                  // 初始化显示模块
// 修改处
//void setupTouch();                                                    // 初始化触摸模块
void controlBacklight(bool on);                                       // 控制背光

// 文本显示函数
void drawCircularText(const char* text, int y, uint16_t color, uint8_t size = 1, uint8_t font = 2);
void drawText(const char* text, int x, int y, uint16_t color, uint8_t size = 1, uint8_t font = 2);

// 图像处理函数
void displayImage(int index);         // 显示图片
// 修改处
//void checkGestures();                 // 检查手势
void setDisplayImage(uint8_t index);  // 设置显示图片

// 格式相关函数
uint8_t getFormatFromIndex(uint8_t index);
uint8_t getFileIndexFromIndex(uint8_t index);
uint8_t combineFormatAndIndex(uint8_t format, uint8_t fileIndex);

// 解码回调
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap);
bool gfpJpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap);  // GFP专用输出函数
//void on_png_draw(pngle_t* pngle, uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint8_t rgba[4]);

// GIF相关
void processGifpackAnimation();
bool isGifpackPlaying();
bool openGifpack(const char* filename);
bool showGifpackFrame();
void closeGifpack();
// 内存管理函数
bool ensureGfpBuffer();
void releaseGfpBuffer();

// 状态画面
void showStartupScreen();
void showWaitingScreen();
void showErrorScreen(const char* message);
void clearScreen();

// 文件系统接口
String getImageFilename(uint8_t index);

#endif  // DISPLAY_HANDLER_H
