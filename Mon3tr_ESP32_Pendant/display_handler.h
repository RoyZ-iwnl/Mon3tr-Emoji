#ifndef DISPLAY_HANDLER_H
#define DISPLAY_HANDLER_H

#include <Arduino.h>
#include <TFT_eSPI.h>
#include <SPI.h>
#include <Wire.h>
#include "CST816D.h"

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

#endif // DISPLAY_HANDLER_H