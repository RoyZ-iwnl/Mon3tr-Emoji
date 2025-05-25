#ifndef DISPLAY_HANDLER_H
#define DISPLAY_HANDLER_H

#include <Arduino.h>
#include <TFT_eSPI.h>
#include <SPI.h>
#include <Wire.h>
// 修改处
//#include "CST816D.h"
#include <TJpg_Decoder.h>
#include <AnimatedGIF.h>
#include <pngle.h>

// 屏幕参数定义
#define SCREEN_WIDTH    240   // 屏幕宽度
#define SCREEN_HEIGHT   240   // 屏幕高度
#define SCREEN_CENTER_X 120   // 屏幕中心X
#define SCREEN_CENTER_Y 120   // 屏幕中心Y

// 图像参数
#define IMAGE_WIDTH     240   // 图像宽度
#define IMAGE_HEIGHT    240   // 图像高度
#define IMG_FORMAT_MASK 0xF0  // 格式掩码（高4位）
#define IMG_INDEX_MASK  0x0F  // 索引掩码（低4位）

// 图像格式定义
#define IMG_FORMAT_BIN  0x00  // 原始二进制格式（不再使用）
#define IMG_FORMAT_JPEG 0x10  // JPEG格式
#define IMG_FORMAT_PNG  0x20  // PNG格式
#define IMG_FORMAT_GIF  0x30  // GIF格式

// 修改处
/*
// 引脚定义
#define TOUCH_SDA 4
#define TOUCH_SCL 5
#define TOUCH_INT 0
#define TOUCH_RST 1
*/

#define TFT_BL 3

// 颜色定义（用于方便调试和开发）
#define COLOR_BLACK     TFT_BLACK
#define COLOR_WHITE     TFT_WHITE  
#define COLOR_RED       TFT_RED
#define COLOR_GREEN     TFT_GREEN
#define COLOR_BLUE      TFT_BLUE
#define COLOR_CYAN      TFT_CYAN
#define COLOR_MAGENTA   TFT_MAGENTA
#define COLOR_YELLOW    TFT_YELLOW

// 全局变量声明
extern TFT_eSPI tft;
// 修改处
//extern CST816D touch;
extern int currentImage;
extern int totalImages;
extern AnimatedGIF gif;

// 函数声明
void setupDisplay();                                                  // 初始化显示模块
// 修改处
//void setupTouch();                                                    // 初始化触摸模块
void controlBacklight(bool on);                                       // 控制背光

// 文本显示函数
void drawCircularText(const char* text, int y, uint16_t color, uint8_t size = 1, uint8_t font = 2);  // 在圆形屏幕上居中显示文本
void drawText(const char* text, int x, int y, uint16_t color, uint8_t size = 1, uint8_t font = 2);   // 在指定位置显示文本

// 图像处理函数
void displayImage(int index);                                         // 显示图片
bool isGifPlaying();                                                 // 检查是否有GIF在播放
void processGifAnimation();                                           // 处理GIF动画
void checkGestures();                                                // 检查手势
void setDisplayImage(uint8_t index);                                  // 设置显示图片

// 格式相关函数
uint8_t getFormatFromIndex(uint8_t index);                           // 解析图像格式
uint8_t getFileIndexFromIndex(uint8_t index);                        // 解析文件索引
uint8_t combineFormatAndIndex(uint8_t format, uint8_t fileIndex);     // 组合格式和索引

// 解码回调
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap);   // JPEG解码回调
void on_png_draw(pngle_t *pngle, uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint8_t rgba[4]);// PNG解码回调（使用命名空间版本）
void GIFDraw(GIFDRAW *pDraw);                                        // GIF解码回调

// GIF文件回调
void* GIFOpenFile(const char* fname, int32_t* pSize);                // GIF文件打开回调
void GIFCloseFile(void* pHandle);                                    // GIF文件关闭回调
int32_t GIFReadFile(GIFFILE* pFile, uint8_t* pBuf, int32_t iLen);    // GIF文件读取回调
int32_t GIFSeekFile(GIFFILE* pFile, int32_t iPosition);              // GIF文件定位回调

// 状态画面
void showStartupScreen();                                            // 显示启动画面
void showWaitingScreen();                                            // 显示等待画面
void showErrorScreen(const char* message);                           // 显示错误画面
void clearScreen();                                                  // 清除屏幕

// 文件系统接口（在file_system.h中实现）
String getImageFilename(uint8_t index);                                  // 获取图片文件名

#endif // DISPLAY_HANDLER_H