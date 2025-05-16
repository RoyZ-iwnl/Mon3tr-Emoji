#include "commands.h"
#include "ble_handler.h"
#include "display_handler.h"
#include "file_system.h"
#include <Arduino.h>

// 命令名称查询
const char* getCommandName(uint8_t cmdId) {
  switch (cmdId) {
    case CMD_START_TRANSFER: return "开始传输";
    case CMD_IMAGE_DATA: return "图片数据";
    case CMD_END_TRANSFER: return "结束传输";
    case CMD_DELETE_IMAGE: return "删除图片";
    case CMD_REORDER_IMAGES: return "重排图片";
    case CMD_GET_IMAGE_LIST: return "获取列表";
    case CMD_SET_DISPLAY: return "设置显示";
    case CMD_GET_STATUS: return "获取状态";
    default: return "未知命令";
  }
}

// 状态码名称查询
const char* getStatusName(uint8_t statusCode) {
  switch (statusCode) {
    case RESP_SUCCESS: return "成功";
    case RESP_GENERAL_ERROR: return "一般错误";
    case RESP_FS_ERROR: return "文件系统错误";
    case RESP_TRANSFER_ERROR: return "传输错误";
    case RESP_PARAM_ERROR: return "参数错误";
    default: return "未知状态";
  }
}

// 获取格式名称
const char* getFormatName(uint8_t format) {
  switch (format & IMG_FORMAT_MASK) {
    case IMG_FORMAT_BIN: return "BIN";
    case IMG_FORMAT_JPEG: return "JPEG";
    case IMG_FORMAT_PNG: return "PNG";
    case IMG_FORMAT_GIF: return "GIF";
    default: return "未知";
  }
}

// 处理接收到的命令
void processCommand(uint8_t* data, size_t length) {
  if (length < 2) return; // 命令至少需要2字节(命令ID+长度)
  
  uint8_t cmdId = data[0];
  uint8_t payloadLength = data[1];
  uint8_t* payload = (payloadLength > 0) ? &data[2] : nullptr;
  
  // 记录命令接收
  commandsReceived++;
  lastDataTime = millis();
  
  if (enableLogging) {
    Serial.printf("处理命令: 0x%02X (%s), 数据长度: %d\n", 
                cmdId, getCommandName(cmdId), payloadLength);
  }

  switch (cmdId) {
    case CMD_START_TRANSFER:
      if (payloadLength >= 1) {
        uint8_t fileIndex = payload[0];
        uint8_t format = getFormatFromIndex(fileIndex);
        uint8_t index = getFileIndexFromIndex(fileIndex);
        
        if (enableLogging) {
          Serial.printf("开始传输: 索引=%d, 格式=%s(0x%02X)\n", 
                      index, getFormatName(format), format);
        }
        
        startImageTransfer(fileIndex);
      } else {
        sendResponse(cmdId, RESP_PARAM_ERROR);
      }
      break;
      
    case CMD_END_TRANSFER:
      finishImageTransfer();
      break;
      
    case CMD_DELETE_IMAGE:
      if (payloadLength >= 1) {
        uint8_t fileIndex = payload[0];
        uint8_t format = getFormatFromIndex(fileIndex);
        uint8_t index = getFileIndexFromIndex(fileIndex);
        
        if (enableLogging) {
          Serial.printf("删除图片: 索引=%d, 格式=%s(0x%02X)\n", 
                      index, getFormatName(format), format);
        }
        
        deleteImage(fileIndex);
      } else {
        sendResponse(cmdId, RESP_PARAM_ERROR);
      }
      break;
      
    case CMD_REORDER_IMAGES:
      if (payloadLength > 0) {
        reorderImages(payload, payloadLength);
      } else {
        sendResponse(cmdId, RESP_PARAM_ERROR);
      }
      break;
      
    case CMD_GET_IMAGE_LIST:
      sendImageList();
      break;
      
    case CMD_SET_DISPLAY:
      if (payloadLength >= 1) {
        uint8_t imgIndex = payload[0];
        uint8_t format = getFormatFromIndex(imgIndex);
        uint8_t index = getFileIndexFromIndex(imgIndex);
        
        if (enableLogging) {
          Serial.printf("设置显示: 索引=%d, 格式=%s(0x%02X)\n", 
                      index, getFormatName(format), format);
        }
        
        setDisplayImage(imgIndex);
      } else {
        sendResponse(cmdId, RESP_PARAM_ERROR);
      }
      break;
      
    case CMD_GET_STATUS:
      sendDeviceStatus();
      break;
      
    default:
      sendResponse(cmdId, RESP_GENERAL_ERROR);
      break;
  }
}

// 发送命令响应
void sendResponse(uint8_t cmdId, uint8_t statusCode, uint8_t* payload, uint8_t payloadLength) {
  // 准备响应数据包
  uint8_t responseBuffer[20]; // 预留足够空间，最大响应长度
  uint8_t responseLength = 3 + payloadLength; // 命令ID + 状态码 + 负载长度 + 负载
  
  responseBuffer[0] = cmdId;        // 命令ID
  responseBuffer[1] = statusCode;   // 状态码
  responseBuffer[2] = payloadLength; // 负载长度
  
  // 复制负载数据(如果有)
  if (payloadLength > 0 && payload != nullptr) {
    memcpy(&responseBuffer[3], payload, payloadLength);
  }
  
  // 通过BLE发送响应
  sendBleResponse(responseBuffer, responseLength);
  
  if (enableLogging) {
    Serial.printf("发送响应: 命令=0x%02X, 状态=%s(0x%02X), 长度=%d\n", 
                cmdId, getStatusName(statusCode), statusCode, payloadLength);
  }
}