#include <LittleFS.h>
#include "display_handler.h"
#include "ble_handler.h"
#include "file_system.h"
#include "commands.h"

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

  // 新增：额外延迟确保所有模块完全初始化
  delay(1000);

  // 显示第一张图片或等待画面
  if (totalImages > 0) {
    Serial.printf("发现 %d 张图片，显示第一张\n", totalImages);
    displayImage(currentImage);
  } else {
    showWaitingScreen();
  }

  // 只关闭非GIFPack文件，如果是GIFPack则保持打开状态
  if (currentImageFile) {
    currentImageFile.close();
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

  // 2. 处理GIFPack动画
  if (isGifpackPlaying()) {
    processGifpackAnimation();
  }

  // 3. 处理BLE状态
  handleBleStatus();

  // 4. 处理串口命令
  handleSerialCommand();

  // 5. 定期任务处理
  //handlePeriodicTasks();

  // 短暂延迟，让ESP32有时间处理其他系统任务
  delay(1);

  // 只在确实需要时才关闭文件
  static unsigned long lastFileCheck = 0;
  if (millis() - lastFileCheck > 30000) {  // 改为30秒检查一次，降低频率
    lastFileCheck = millis();

    // 只有在GIFPack未激活且文件打开时才关闭
    if (gifpackFile && !gifpackActive) {
      Serial.println("检测到未使用的GIFPack文件，关闭中...");
      gifpackFile.close();
    }

    // 内存使用情况检查
    size_t freeHeap = ESP.getFreeHeap();
    if (freeHeap < 10000) {  // 如果可用内存少于10KB
      Serial.printf("警告：可用内存不足 %d 字节\n", freeHeap);
    }
  }
}
