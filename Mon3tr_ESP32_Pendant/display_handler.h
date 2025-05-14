#ifndef DISPLAY_HANDLER_H
#define DISPLAY_HANDLER_H

#include <Arduino.h>
#include <TFT_eSPI.h>
#include <SPI.h>
#include <Wire.h>
#include "CST816D.h"
#include <TJpg_Decoder.h>
#include <PNGdec.h>
#include <AnimatedGIF.h>

// 显示相关常量
#define IMAGE_WIDTH 240
#define IMAGE_HEIGHT 240

// 引脚定义
#define TOUCH_SDA 4
#define TOUCH_SCL 5
#define TOUCH_INT 0
#define TOUCH_RST 1
#define TFT_BL 3

// 全局变量声明
extern TFT_eSPI tft;
extern CST816D touch;
extern int currentImage;
extern int totalImages;
extern PNG png;
extern AnimatedGIF gif;
extern bool isPlayingGif;
extern String currentGifPath;
extern unsigned long lastGifFrameTime;

// 解码回调函数声明
bool jpegOutput(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t* bitmap);
void pngDraw(PNGDRAW *pDraw);
void GIFDraw(GIFDRAW *pDraw);

// 文件操作回调函数声明
void *pngOpen(const char *filename, int32_t *size);
void pngClose(void *handle);
int32_t pngRead(PNGFILE *pFile, uint8_t *pBuf, int32_t len);
int32_t pngSeek(PNGFILE *pFile, int32_t pos);
void *GIFOpenFile(const char *fname, int32_t *pSize);
void GIFCloseFile(void *pHandle);
int32_t GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen);
int32_t GIFSeekFile(GIFFILE *pFile, int32_t iPosition);

// 函数声明
void setupDisplay();
void setupTouch();
void controlBacklight(bool on);
void displayImage(int index);
void checkGestures();
void setDisplayImage(uint8_t index);
void showStartupScreen();
void showErrorScreen(const char* message);
void showWaitingScreen();
void playGifFrame(String gifPath);

#endif // DISPLAY_HANDLER_H