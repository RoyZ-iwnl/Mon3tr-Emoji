#include <Arduino.h>
#include "ble_handler.h"
#include "commands.h"
#include "file_system.h"
#include "display_handler.h"


// 最后检查时间
unsigned long lastCheck = 0;
unsigned long lastIdleTask = 0;

// 初始化各模块
void setup() {
  // 初始化串口
  Serial.begin(115200);
  delay(1000);
  Serial.println("========== Mon3tr吊坠初始化 ==========");
  
  // 初始化文件系统
  setupFileSystem();
  
  // 初始化显示屏
  setupDisplay();
  
  // 初始化触摸屏
  setupTouch();
  
  // 初始化BLE
  setupBLE();
  
  // 显示第一张图片或等待画面
  if (totalImages > 0) {
    displayImage(currentImage);
  } else {
    showWaitingScreen();
  }
  
  Serial.println("初始化完成");
}

// 处理串口命令
void handleSerialCommand() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();  // 去除空格和换行符

    if (cmd == "log=1") {
      enableLogging = true;
      Serial.println("日志输出已开启");
    } else if (cmd == "log=0") {
      enableLogging = false;
      Serial.println("日志输出已关闭");
    } else if (cmd == "list") {
      listFiles();
    } else if (cmd == "status") {
      checkFileSystem();
    } else if (cmd.startsWith("show=")) {
      // 显示指定索引的图片
      int idx = cmd.substring(5).toInt();
      if (idx >= 0 && idx < totalImages) {
        displayImage(idx);
        Serial.printf("显示图片索引 %d\n", idx);
      } else {
        Serial.println("无效的图片索引");
      }
    }
  }
}

// 定期任务处理
void handlePeriodicTasks() {
  unsigned long now = millis();
  
  // 每30秒执行一次维护任务
  if (now - lastIdleTask >= 30000) {
    lastIdleTask = now;
    
    // 检查文件系统状态
    size_t freeSpace = LittleFS.totalBytes() - LittleFS.usedBytes();
    if (freeSpace < 50000) {  // 如果剩余空间小于50KB
      Serial.println("存储空间不足，尝试清理...");
      cleanupOldFiles();
    }
    
    // 如果当前没有传输，执行垃圾回收
    if (!isTransferring) {
      Serial.println("执行维护任务...");
      delay(10);  // 让ESP32有时间执行系统任务
    }
  }
}

// 主循环
void loop() {
  unsigned long now = millis();
  
  // 1. 优先处理触摸检查，提高响应速度
  if (now - lastCheck >= 50) {
    checkGestures();
    lastCheck = now;
  }
  
  // 2. 处理GIF动画
  if (isGifPlaying()) {
    processGifAnimation();
  }
  
  // 3. 处理BLE状态
  handleBleStatus();
  
  // 4. 处理串口命令
  handleSerialCommand();
  
  // 5. 定期任务处理
  //handlePeriodicTasks();
  
  // 短暂延迟，让ESP32有时间处理其他系统任务
  delay(1);
}