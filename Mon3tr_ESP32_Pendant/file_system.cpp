#include "file_system.h"
#include "commands.h"
#include "display_handler.h"
#include "ble_handler.h" // 添加这行以引入sendResponse的声明

// 全局变量定义
ImageInfo imageList[MAX_IMAGES];
String currentImageName;
File currentImageFile;
bool isTransferring = false;
int totalBytesReceived = 0;

// 初始化文件系统
void setupFileSystem() {
  Serial.println("初始化LittleFS...");
  
  if (!LittleFS.begin(false)) {  // 先尝试不格式化挂载
    Serial.println("LittleFS挂载失败，尝试格式化...");
    
    if (LittleFS.format()) {
      Serial.println("LittleFS格式化成功");
      
      if (!LittleFS.begin(true)) {
        Serial.println("LittleFS格式化后仍然无法挂载");
        return;
      }
    } else {
      Serial.println("LittleFS格式化失败");
      return;
    }
  }
  
  Serial.println("LittleFS挂载成功");
  
  // 检查文件系统状态
  checkFileSystem();
  
  // 加载图片顺序
  loadImageOrder();
  
  // 更新图片列表
  updateImageList();
}

// 更新图片列表
void updateImageList() {
  File root = LittleFS.open("/");
  File file = root.openNextFile();
  totalImages = 0;

  while (file && totalImages < MAX_IMAGES) {
    String filename = String(file.name());
    if (!file.isDirectory() && filename.endsWith(".ibin")) {
      imageList[totalImages].filename = "/" + filename;
      imageList[totalImages].active = true;
      totalImages++;
    }
    file = root.openNextFile();
  }

  saveImageOrder();
}

// 加载图片顺序
void loadImageOrder() {
  File file = LittleFS.open("/order.bin", "r");
  if (!file) return;

  // 读取文件头和版本号
  uint8_t header[2];
  if (file.read(header, 2) != 2) {
    file.close();
    return;
  }
  
  // 检查标记(0xA5)和版本(0x01)
  if (header[0] != 0xA5 || header[1] != 0x01) {
    file.close();
    return;
  }
  
  // 读取图片数量
  uint8_t count;
  if (file.read(&count, 1) != 1) {
    file.close();
    return;
  }
  
  totalImages = min((int)count, MAX_IMAGES);
  
  // 读取图片信息
  for (int i = 0; i < totalImages; i++) {
    // 读取文件名长度
    uint8_t nameLen;
    if (file.read(&nameLen, 1) != 1) break;
    
    // 读取文件名
    char nameBuffer[32];
    if (file.read((uint8_t*)nameBuffer, nameLen) != nameLen) break;
    nameBuffer[nameLen] = 0; // 添加字符串结束符
    
    // 读取激活状态
    uint8_t active;
    if (file.read(&active, 1) != 1) break;
    
    // 保存到列表
    imageList[i].filename = String(nameBuffer);
    imageList[i].active = (active == 1);
  }
  
  file.close();
}

// 保存图片顺序
void saveImageOrder() {
  File file = LittleFS.open("/order.bin", "w");
  if (!file) return;

  // 写入文件头和版本号
  uint8_t header[2] = {0xA5, 0x01}; // 标记和版本
  file.write(header, 2);
  
  // 写入图片数量
  uint8_t count = totalImages;
  file.write(&count, 1);
  
  // 写入图片信息
  for (int i = 0; i < totalImages; i++) {
    // 写入文件名长度
    uint8_t nameLen = imageList[i].filename.length();
    file.write(&nameLen, 1);
    
    // 写入文件名
    file.write((uint8_t*)imageList[i].filename.c_str(), nameLen);
    
    // 写入激活状态
    uint8_t active = imageList[i].active ? 1 : 0;
    file.write(&active, 1);
  }
  
  file.close();
}

// 开始图片传输
void startImageTransfer(uint8_t fileIndex) {
  currentImageName = "/img_" + String(fileIndex) + ".ibin";
  
  Serial.println("开始接收图片: " + currentImageName);
  
  // 如果有正在传输的文件，先关闭
  if (currentImageFile) {
    currentImageFile.close();
  }
  
  // 创建新文件
  currentImageFile = LittleFS.open(currentImageName, "w");
  if (!currentImageFile) {
    Serial.println("创建文件失败");
    sendResponse(CMD_START_TRANSFER, RESP_FS_ERROR);
    isTransferring = false;
    return;
  }
  
  isTransferring = true;
  totalBytesReceived = 0;
  
  // 发送成功响应
  sendResponse(CMD_START_TRANSFER, RESP_SUCCESS);
  
  Serial.println("文件已创建，等待数据...");
}

// 处理图片数据
void processImageData(uint8_t* data, size_t length) {
  if (!isTransferring || !currentImageFile) return;
  
  // 写入文件
  if (currentImageFile.write(data, length) != length) {
    Serial.println("写入文件失败");
    return;
  }
  
  totalBytesReceived += length;
}

// 结束图片传输
void finishImageTransfer() {
  if (!isTransferring || !currentImageFile) {
    sendResponse(CMD_END_TRANSFER, RESP_TRANSFER_ERROR);
    return;
  }
  
  // 关闭文件
  currentImageFile.flush();
  size_t fileSize = currentImageFile.size();
  currentImageFile.close();
  isTransferring = false;
  
  Serial.printf("文件接收完成，大小: %d 字节\n", fileSize);
  
  // 验证文件
  File verifyFile = LittleFS.open(currentImageName, "r");
  size_t verifiedSize = 0;
  if (verifyFile) {
    verifiedSize = verifyFile.size();
    verifyFile.close();
  }
  
  // 检查最小有效大小
  if (verifiedSize < 1000) {
    Serial.println("文件太小，可能不完整，删除中...");
    LittleFS.remove(currentImageName);
    sendResponse(CMD_END_TRANSFER, RESP_TRANSFER_ERROR);
    return;
  }
  
  // 更新图片列表
  updateImageList();
  
  // 查找新图片的索引
  int newIndex = -1;
  for (int i = 0; i < totalImages; i++) {
    if (imageList[i].filename == currentImageName) {
      newIndex = i;
      break;
    }
  }
  
  if (newIndex >= 0) {
    currentImage = newIndex;
    displayImage(currentImage);
    
    // 发送成功响应，包含图片索引和大小
    uint8_t payload[5];
    payload[0] = newIndex;
    *((uint32_t*)&payload[1]) = fileSize;
    sendResponse(CMD_END_TRANSFER, RESP_SUCCESS, payload, 5);
  } else {
    sendResponse(CMD_END_TRANSFER, RESP_FS_ERROR);
  }
}

// 删除图片
void deleteImage(uint8_t fileIndex) {
  String filename = "/img_" + String(fileIndex) + ".ibin";
  
  if (LittleFS.remove(filename)) {
    updateImageList();
    
    // 如果当前显示的是被删除的图片，则显示第一张图片
    if (currentImage >= totalImages) {
      currentImage = (totalImages > 0) ? 0 : -1;
      if (currentImage >= 0) {
        displayImage(currentImage);
      } else {
        showWaitingScreen();
      }
    }
    
    sendResponse(CMD_DELETE_IMAGE, RESP_SUCCESS);
  } else {
    sendResponse(CMD_DELETE_IMAGE, RESP_FS_ERROR);
  }
}

// 重排图片
void reorderImages(uint8_t* order, size_t length) {
  if (length != totalImages) {
    sendResponse(CMD_REORDER_IMAGES, RESP_PARAM_ERROR);
    return;
  }
  
  ImageInfo tempList[MAX_IMAGES];
  memcpy(tempList, imageList, sizeof(ImageInfo) * totalImages);
  
  for (size_t i = 0; i < length; i++) {
    if (order[i] < totalImages) {
      imageList[i] = tempList[order[i]];
    }
  }
  
  saveImageOrder();
  sendResponse(CMD_REORDER_IMAGES, RESP_SUCCESS);
}

// 发送图片列表
void sendImageList() {
  // 准备响应数据，最多发送10张图片信息
  uint8_t response[3 + MAX_IMAGES * 3]; // 每张图片3字节(ID+文件索引+激活状态)
  
  response[0] = totalImages; // 图片总数
  
  // 填充图片信息
  for (int i = 0; i < totalImages && i < MAX_IMAGES; i++) {
    uint8_t fileIndex = 0;
    
    // 提取文件索引
    String nameClean = imageList[i].filename;
    nameClean.replace("/img_", "");
    nameClean.replace(".ibin", "");
    fileIndex = nameClean.toInt();
    
    // 保存图片信息
    response[1 + i*3] = i;                     // 顺序索引
    response[1 + i*3 + 1] = fileIndex;         // 文件索引
    response[1 + i*3 + 2] = imageList[i].active ? 1 : 0; // 激活状态
  }
  
  // 发送响应
  sendResponse(CMD_GET_IMAGE_LIST, RESP_SUCCESS, response, 1 + totalImages * 3);
}

// 发送设备状态
void sendDeviceStatus() {
  uint8_t response[6];
  
  response[0] = currentImage;                 // 当前图片索引
  response[1] = totalImages;                  // 总图片数量
  
  // 剩余空间(KB)
  uint32_t freeSpace = (LittleFS.totalBytes() - LittleFS.usedBytes()) / 1024;
  *((uint32_t*)&response[2]) = freeSpace;
  
  sendResponse(CMD_GET_STATUS, RESP_SUCCESS, response, 6);
}

// 获取图片文件名
String getImageFilename(uint8_t index) {
  if (index < totalImages) {
    return imageList[index].filename;
  }
  return "";
}

// 清理旧文件
void cleanupOldFiles() {
  File root = LittleFS.open("/");
  if (!root || !root.isDirectory()) {
    Serial.println("无法打开根目录");
    return;
  }

  // 查找最旧的图片文件
  String oldestFile = "";
  time_t oldestTime = time(NULL);

  File file = root.openNextFile();
  while (file) {
    String filename = String(file.name());
    if (!file.isDirectory() && filename.endsWith(".ibin") && filename.startsWith("img_")) {
      // 使用文件上次修改时间作为年龄判断依据
      time_t fileTime = file.getLastWrite();
      if (fileTime < oldestTime) {
        oldestTime = fileTime;
        oldestFile = "/" + filename;
      }
    }
    file = root.openNextFile();
  }

  // 删除最旧的文件
  if (oldestFile != "") {
    Serial.printf("删除最旧的文件: %s\n", oldestFile.c_str());
    if (LittleFS.remove(oldestFile)) {
      Serial.println("删除成功");
    } else {
      Serial.println("删除失败");
    }
  }
}

// 列出所有文件
void listFiles() {
  File root = LittleFS.open("/");
  if (!root || !root.isDirectory()) {
    Serial.println("无法打开根目录");
    return;
  }

  Serial.println("文件列表:");
  File file = root.openNextFile();
  int count = 0;

  while (file) {
    if (!file.isDirectory()) {
      Serial.printf("  %s, 大小: %d 字节\n", file.name(), file.size());
      count++;
    }
    file = root.openNextFile();
  }

  Serial.printf("共 %d 个文件\n", count);
}

// 检查文件系统状态
void checkFileSystem() {
  size_t totalBytes = LittleFS.totalBytes();
  size_t usedBytes = LittleFS.usedBytes();

  Serial.printf("LittleFS状态：总空间 %d 字节，已用 %d 字节，可用 %d 字节\n",
                totalBytes, usedBytes, totalBytes - usedBytes);

  // 如果使用率超过90%，尝试清理旧文件
  if (usedBytes > 0.9 * totalBytes) {
    Serial.println("文件系统使用率超过90%，尝试清理...");
    cleanupOldFiles();
  }

  // 列出所有文件
  listFiles();
}