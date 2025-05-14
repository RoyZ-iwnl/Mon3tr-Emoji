#ifndef FILE_SYSTEM_H
#define FILE_SYSTEM_H

#include <Arduino.h>
#include <LittleFS.h>

// 全局常量
#define MAX_IMAGES 10

// 图片信息结构
struct ImageInfo {
  String filename;
  bool active;
};

// 全局变量声明
extern ImageInfo imageList[MAX_IMAGES];
extern String currentImageName;
extern File currentImageFile;
extern bool isTransferring;
extern int totalBytesReceived;

// 函数声明
void setupFileSystem();
void updateImageList();
void loadImageOrder();
void saveImageOrder();
void startImageTransfer(uint8_t fileIndex);
void processImageData(uint8_t* data, size_t length);
void finishImageTransfer();
void deleteImage(uint8_t fileIndex);
void reorderImages(uint8_t* order, size_t length);
void sendImageList();
void sendDeviceStatus();
String getImageFilename(uint8_t index);
void cleanupOldFiles();
void listFiles();
void checkFileSystem();

#endif // FILE_SYSTEM_H