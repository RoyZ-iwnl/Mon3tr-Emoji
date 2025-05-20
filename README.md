# Mon3tr 表情电子吊坠
<p align="center">
<img src="Images/APPScreenshot.jpg" width="200"></p>

Mon3tr 表情电子吊坠项目基于 **ESP32-C3-MINI-1U（ESP32-2424S012）（触摸屏版）开发板** 和 **Android 应用**，通过 BLE（蓝牙低功耗）实现无线传输和控制，支持 Android APP 上传、管理并切换显示表情，滑动手势切换表情。

---

## 项目结构

- `Mon3tr_ESP32_Pendant/` —— ESP32-C3 固件源码（C/C++）
- `M3/` —— Android 应用源码（Java）
- `Images/` —— 项目图库

---

## 功能简介

- ESP32-C3 通过 BLE 与 Android APP 通信，接收命令动态显示表情，支持滑动切换
- Android APP 支持自定义上传、管理表情

---

## 快速上手

[0基础请看这里](EZBuild.md)

### 1. 硬件准备

- ESP32-2424S012 开发板
- 数据线、电源

### 2. 固件编译与烧录

1. 安装 [Arduino IDE 2.3.6](https://github.com/arduino/arduino-ide/releases/tag/2.3.6)
2. 克隆本仓库，并用 Arduino IDE 打开 `Mon3tr_ESP32_Pendant/Mon3tr_ESP32_Pendant.ino`  
    ```bash
    git clone https://github.com/RoyZ-iwnl/Mon3tr-Emoji.git
    cd Mon3tr_ESP32_Pendant/
    ```
3. Arduino IDE 设置  
   - 【首选项】-【附加开发板管理器网址】：  
     `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
   - 【开发板管理器】安装 `esp32 by Espressif Systems 2.0.13`
   - 【库管理器】安装以下依赖  
     ```
     TFT_eSPI by Bodmer 2.5.43
     TJpg_Decoder by Bodmer 1.1.0
     AnimatedGIF by Larry 2.2.0
     pngle by kikuchan 1.0.2
     ```
4. 编辑 `libraries/TFT_eSPI/User_Setup.h`  
   **备份原文件**，用以下内容覆盖（仅适配 ESP32-2424S012 + GC9A01 屏）：
    ```cpp
    #define TFT_RGB_ORDER TFT_BGR
    #define TFT_WIDTH  240
    #define TFT_HEIGHT 240
    #define TFT_BACKLIGHT_ON HIGH
    #define LOAD_GLCD
    #define LOAD_FONT2
    #define SMOOTH_FONT
    #define USER_SETUP_INFO "User_Setup"
    #define GC9A01_DRIVER
    #define COLOR_DEPTH 16
    #define TFT_MISO -1
    #define TFT_MOSI 7
    #define TFT_SCLK 6
    #define TFT_CS   10
    #define TFT_DC   2
    #define TFT_RST  -1
    #define TFT_BL   3
    #define SPI_FREQUENCY  40000000
    #define SPI_READ_FREQUENCY  20000000
    #define SPI_TOUCH_FREQUENCY  2500000
    ```
   > ⚠️ 如使用其他 GC9A01 屏/板，请根据实际引脚调整

5. 连接开发板，IDE 内选择对应端口，点击上传即可。  
   > ⚠️ **注意**：Arduino IDE 2.3.6 的 SD 库与 `esp32 by Espressif Systems 2.0.13` 自带 SD 库冲突。请在编译前**移走 `%LOCALAPPDATA%\Arduino15\libraries\SD` 文件夹**，需要时再放回。

### 3. Android APP 编译与安装

1. 安装 Android Studio
2. 以项目方式导入 `M3/`
3. 连接手机，开启【开发者选项】与【USB调试】
4. 编译运行或打包 APK 安装

### 4. 使用方法

1. 上电启动 ESP32-C3，确认 BLE 正常广播
2. 打开 APP，连接设备
3. APP 内上传、管理和切换表情

---

## 蓝牙协议规范

- **服务 UUID**：`4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **命令特征 UUID**：`beb5483e-36e1-4688-b7f5-ea07361b26a8`
- **数据特征 UUID**：`beb5483e-36e1-4688-b7f5-ea07361b26a9`

### 1. 命令格式

```
[命令ID(1字节), 负载长度(1字节), 负载数据(变长)]
```

### 2. 响应格式

```
[命令ID(1字节), 状态码(1字节), 负载长度(1字节), 负载数据(变长)]
```

### 3. 命令列表

| 命令ID | 名称       | 描述             | 负载格式               |
|--------|------------|------------------|------------------------|
| 0x01   | 开始传输   | 开始图片传输     | [图片索引(1字节)]      |
| 0x02   | 图片数据   | 图片数据分片     | [图片数据(变长)]       |
| 0x03   | 结束传输   | 结束图片传输     | 无                     |
| 0x04   | 删除图片   | 删除指定图片     | [图片索引(1字节)]      |
| 0x05   | 重排图片   | 重新排序图片     | [新顺序索引数组]       |
| 0x06   | 获取列表   | 获取图片列表     | 无                     |
| 0x07   | 设置显示   | 设置当前显示图片 | [图片索引(1字节)]      |
| 0x08   | 获取状态   | 获取设备状态     | 无                     |

- 图片索引高4位为格式ID，低4位为文件索引

### 4. 响应状态码

| 状态码 | 名称     | 描述         |
|--------|----------|--------------|
| 0x00   | 成功     | 命令执行成功 |
| 0x01   | 一般错误 | 通用错误     |
| 0x02   | 文件系统 | 文件操作失败 |
| 0x03   | 传输错误 | 数据传输出错 |
| 0x04   | 参数错误 | 命令参数无效 |

### 5. 图片格式标识

- 0x00：原始二进制（已弃用）
- 0x10：JPEG
- 0x20：PNG
- 0x30：GIF

### 6. 主要通信流程

- **图片上传流程**
    1. 发送开始传输（0x01），负载为 `[图片索引]`
    2. 分包发送图片数据（0x02），每包 ≤512字节
    3. 发送结束传输（0x03），无负载

- **获取图片列表**
    1. 发送获取列表（0x06）
    2. 接收响应，负载：`[图片数量, {位置索引, 文件索引, 大小(4字节)}*n]`

- **获取设备状态**
    1. 发送获取状态（0x08）
    2. 响应负载：`[开机时长(4字节), 存储用量(4字节), 当前显示索引]`  
       > 开机时长单位：秒，小端序

- **设置显示图片**
    发送设置显示（0x07），负载 `[图片索引]`

- **删除图片**
    发送删除图片（0x04），负载 `[图片索引]`

- **重排图片**
    发送重排图片（0x05），负载 `[索引1, 索引2, ...]`

### 7. 数据传输建议

- 建议 MTU 设为 512 字节
- 每包发送间隔 ≥30ms，防止处理延迟
- 所有多字节数据均为**小端序**

---

## 二次开发提示

- 修改 BLE UUID：参考 [`ble_handler.h`](Mon3tr_ESP32_Pendant/ble_handler.h)
- 修改 BLE 广播名：参考 [`ble_handler.cpp`](Mon3tr_ESP32_Pendant/ble_handler.cpp) 的 `setupBLE` 函数
- 触摸屏开发：参考 [`CST816D.h`](Mon3tr_ESP32_Pendant/CST816D.h)和[`CST816D.cpp`](Mon3tr_ESP32_Pendant/CST816D.cpp)的双击手势可能不良，其他开发板I2C引脚请根据实际编辑

---

## TODO

- [ ] GIF 动图完善和优化

---

## 鸣谢

- 感谢 [囧囧怪的WIFI版本开源项目](https://www.bilibili.com/video/BV1r3LczZE3N/#reply262992366736) 提供灵感  
  外壳 3D 打印建模和开发板购买可参考该视频
- 没有Claude 就没有这个项目~
---

如有建议欢迎 issue 或 PR！