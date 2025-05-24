#include "file_system.h"
#include "commands.h"
#include "display_handler.h"
#include "ble_handler.h" // 引入sendResponse的声明

// 全局变量定义
ImageInfo imageList[MAX_IMAGES];
String currentImageName;
File currentImageFile;
bool isTransferring = false;
int totalBytesReceived = 0;
uint8_t currentFileFormat = 0; // 当前传输的文件格式

// 初始化文件系统
void setupFileSystem() {
  Serial.println("初始化LittleFS...");
  closeAllFiles();
  
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

// 根据格式获取文件扩展名
String getFileExtensionFromFormat(uint8_t format) {
  switch (format & IMG_FORMAT_MASK) {
    case IMG_FORMAT_JPEG:
      return ".jpg";
    case IMG_FORMAT_PNG:
      return ".png";
    case IMG_FORMAT_GIFPACK:
      return ".gfp";
    default:
      return ".bin"; // 默认格式
  }
}

// 更新图片列表
void updateImageList() {
  File root = LittleFS.open("/");
  File file = root.openNextFile();
  totalImages = 0;

  // 先清空列表
  for (int i = 0; i < MAX_IMAGES; i++) {
    imageList[i].filename = "";
    imageList[i].active = false;
  }

  while (file && totalImages < MAX_IMAGES) {
    String filename = String(file.name());
    if (!file.isDirectory()) {
      // 检查文件扩展名
      String ext = filename.substring(filename.lastIndexOf('.'));
      ext.toLowerCase();
      
      uint8_t format = IMG_FORMAT_BIN; // 默认格式
      
      if (ext == ".jpg" || ext == ".jpeg") {
        format = IMG_FORMAT_JPEG;
      } else if (ext == ".png") {
        format = IMG_FORMAT_PNG;
      } else if (ext == ".gfp") {
        format = IMG_FORMAT_GIFPACK;
      } else if (ext != ".bin") {
        // 跳过不支持的格式
        file = root.openNextFile();
        continue;
      }
      
      // 提取文件索引（从文件名）
      int startPos = filename.lastIndexOf("_") + 1;
      int endPos = filename.lastIndexOf(".");
      
      if (startPos > 0 && endPos > startPos) {
        String indexStr = filename.substring(startPos, endPos);
        uint8_t fileIndex = indexStr.toInt() & IMG_INDEX_MASK;
        
        imageList[totalImages].filename = "/" + filename;
        imageList[totalImages].active = true;
        imageList[totalImages].format = format;
        imageList[totalImages].fileIndex = fileIndex;
        imageList[totalImages].fileSize = file.size();
        totalImages++;
      }
    }
    file = root.openNextFile();
  }

  // 保存图片顺序
  saveImageOrder();
  
  Serial.printf("找到 %d 张图片\n", totalImages);
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
  
  // 检查标记(0xA5)和版本(0x02) - 注意：版本已更新
  if (header[0] != 0xA5 || header[1] != 0x02) {
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
    nameBuffer[nameLen] = 0; // 字符串结束符
    
    // 读取格式和索引
    uint8_t format;
    if (file.read(&format, 1) != 1) break;
    
    uint8_t fileIndex;
    if (file.read(&fileIndex, 1) != 1) break;
    
    // 读取文件大小
    uint32_t fileSize;
    if (file.read((uint8_t*)&fileSize, 4) != 4) break;
    
    // 读取激活状态
    uint8_t active;
    if (file.read(&active, 1) != 1) break;
    
    // 保存到列表
    imageList[i].filename = String(nameBuffer);
    imageList[i].format = format;
    imageList[i].fileIndex = fileIndex;
    imageList[i].fileSize = fileSize;
    imageList[i].active = (active == 1);
  }
  
  file.close();
}

// 保存图片顺序
void saveImageOrder() {
  File file = LittleFS.open("/order.bin", "w");
  if (!file) return;

  // 写入文件头和版本号
  uint8_t header[2] = {0xA5, 0x02}; // 标记和版本
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
    
    // 写入格式和索引
    file.write(&imageList[i].format, 1);
    file.write(&imageList[i].fileIndex, 1);
    
    // 写入文件大小
    file.write((uint8_t*)&imageList[i].fileSize, 4);
    
    // 写入激活状态
    uint8_t active = imageList[i].active ? 1 : 0;
    file.write(&active, 1);
  }
  
  file.close();
}

// 开始图片传输
void startImageTransfer(uint8_t fileIndex) {
  // 提取格式和索引
  uint8_t format = getFormatFromIndex(fileIndex);
  uint8_t index = getFileIndexFromIndex(fileIndex);
  
  String extension = getFileExtensionFromFormat(format);
  currentImageName = "/img_" + String(index) + extension;
  currentFileFormat = format;
  
  Serial.printf("开始接收图片: %s (格式: 0x%02X, 索引: %d)\n", 
                currentImageName.c_str(), format, index);
  
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
  
  // 检查最小有效大小（根据格式调整）
  size_t minSize = 100; // 对于JPEG和PNG，太小的文件可能是无效的
  if (currentFileFormat == IMG_FORMAT_GIFPACK) {
    minSize = 50; // GIF可以很小
  }
  
  if (verifiedSize < minSize) {
    Serial.println("文件太小，可能不完整，删除中...");
    LittleFS.remove(currentImageName);
    sendResponse(CMD_END_TRANSFER, RESP_TRANSFER_ERROR);
    return;
  }
  
  // 提取文件索引
  uint8_t fileIndex = 0;
  int startPos = currentImageName.lastIndexOf("_") + 1;
  int endPos = currentImageName.lastIndexOf(".");
  
  if (startPos > 0 && endPos > startPos) {
    String indexStr = currentImageName.substring(startPos, endPos);
    fileIndex = indexStr.toInt() & IMG_INDEX_MASK;
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
    
    // 发送成功响应，包含组合索引和大小
    uint8_t combinedIndex = combineFormatAndIndex(currentFileFormat, fileIndex);
    
    uint8_t payload[5];
    payload[0] = combinedIndex;
    *((uint32_t*)&payload[1]) = fileSize;
    sendResponse(CMD_END_TRANSFER, RESP_SUCCESS, payload, 5);
  } else {
    sendResponse(CMD_END_TRANSFER, RESP_FS_ERROR);
  }
}

// 删除图片
void deleteImage(uint8_t fileIndex) {
  // 提取格式和索引
  uint8_t format = getFormatFromIndex(fileIndex);
  uint8_t index = getFileIndexFromIndex(fileIndex);
  
  // 查找匹配的文件
  int foundIndex = -1;
  for (int i = 0; i < totalImages; i++) {
    if (imageList[i].format == format && imageList[i].fileIndex == index) {
      foundIndex = i;
      break;
    }
  }
  
  if (foundIndex >= 0) {
    String filename = imageList[foundIndex].filename;
    
    // 检查这个文件是否为当前正在播放的GIFPack
    if (gifpackActive && filename == getImageFilename(currentImage)) {
      // 关闭GIFPack文件
      if (frameOffsets) {
        free(frameOffsets);
        frameOffsets = nullptr;
      }
      if (gifpackFile) {
        gifpackFile.close();
      }
      gifpackActive = false;
    }
    
    // 确保任何文件处理已经完成
    delay(100);  // 给系统一点时间关闭文件
    
    if (LittleFS.remove(filename)) {
      Serial.printf("已删除文件: %s\n", filename.c_str());
      
      // 更新图片列表
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
  } else {
    sendResponse(CMD_DELETE_IMAGE, RESP_PARAM_ERROR);
  }
}

void closeAllFiles() {
  // 关闭当前传输中的文件
  if (currentImageFile) {
    currentImageFile.close();
  }
  
  // 关闭GIFPack文件
  if (gifpackActive) {
    if (frameOffsets) {
      free(frameOffsets);
      frameOffsets = nullptr;
    }
    if (gifpackFile) {
      gifpackFile.close();
    }
    gifpackActive = false;
  }
  
  // 短暂延迟确保文件完全关闭
  delay(20);
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
  // 负载格式: [图片数量(1字节), {位置索引(1字节), 文件索引(1字节), 大小(4字节)}*n]
  uint8_t response[1 + MAX_IMAGES * 6]; // 图片总数(1) + 每张图片6字节(位置索引+文件索引+大小)
  
  response[0] = totalImages; // 图片总数
  
  // 填充图片信息
  for (int i = 0; i < totalImages && i < MAX_IMAGES; i++) {
    // 计算组合索引（包含格式和文件索引）
    uint8_t combinedIndex = combineFormatAndIndex(
      imageList[i].format, imageList[i].fileIndex);
    
    // 保存图片信息
    response[1 + i*6] = i;                            // 位置索引(1字节)
    response[1 + i*6 + 1] = combinedIndex;            // 文件索引(包含格式)(1字节)
    *((uint32_t*)&response[1 + i*6 + 2]) = imageList[i].fileSize; // 文件大小(4字节)
  }
  
  // 发送响应
  sendResponse(CMD_GET_IMAGE_LIST, RESP_SUCCESS, response, 1 + totalImages * 6);
}

// 发送设备状态
void sendDeviceStatus() {
  // 负载格式: [开机时长(4字节), 存储使用量(4字节), 当前显示索引(1字节)]
  uint8_t response[9];
  
  // 开机时间（秒）
  uint32_t uptime = millis() / 1000;
  *((uint32_t*)&response[0]) = uptime;
  
  // 存储使用量（字节）- 注意这里是已使用空间，不是剩余空间
  uint32_t usedSpace = LittleFS.usedBytes();
  *((uint32_t*)&response[4]) = usedSpace;
  
  // 当前图片索引（含格式）
  uint8_t currentCombinedIndex = 0;
  if (currentImage >= 0 && currentImage < totalImages) {
    currentCombinedIndex = combineFormatAndIndex(
      imageList[currentImage].format, imageList[currentImage].fileIndex);
  }
  response[8] = currentCombinedIndex;
  
  sendResponse(CMD_GET_STATUS, RESP_SUCCESS, response, 9);
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
  closeAllFiles();
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
    if (!file.isDirectory() && (
        filename.endsWith(".jpg") || 
        filename.endsWith(".png") || 
        filename.endsWith(".gfp") 
    )) {
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