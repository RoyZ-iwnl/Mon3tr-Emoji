#include "CST816D.h"

CST816D::CST816D(int8_t sda_pin, int8_t scl_pin, int8_t rst_pin, int8_t int_pin) {
    _sda = sda_pin;
    _scl = scl_pin;
    _rst = rst_pin;
    _int = int_pin;
}

void CST816D::begin(void) {
  Wire.begin(_sda, _scl);
  
  // 复位触摸芯片
  if (_rst != -1) {
    pinMode(_rst, OUTPUT);
    digitalWrite(_rst, HIGH);
    delay(5);
    digitalWrite(_rst, LOW);
    delay(10);
    digitalWrite(_rst, HIGH);
    delay(50);
  }
  
  // 中断引脚设置
  if (_int != -1) {
    pinMode(_int, INPUT_PULLUP);
  }
  
  delay(100); // 稳定化延迟
  
  // 配置触摸控制器启用滑动手势
  i2c_write(0xFA, 0x01); // 启用手势检测
  i2c_write(0xEC, 0x01); // 滑动灵敏度设置
  i2c_write(0xFE, 0xFF); // 禁用自动休眠
}

bool CST816D::getTouch(uint16_t *x, uint16_t *y, uint8_t *gesture) {
    bool touchDetected = false;
    touchDetected = (bool)i2c_read(0x02);

    *gesture = i2c_read(0x01);
    if (!(*gesture == SlideUp || *gesture == SlideDown || 
          *gesture == SlideLeft || *gesture == SlideRight || 
          *gesture == SingleTap || *gesture == DoubleTap || 
          *gesture == LongPress)) {
        *gesture = None;
    }

    uint8_t data[4];
    i2c_read_continuous(0x03, data, 4);
    *x = ((data[0] & 0x0f) << 8) | data[1];
    *y = ((data[2] & 0x0f) << 8) | data[3];

    return touchDetected;
}

uint8_t CST816D::i2c_read(uint8_t addr) {
    uint8_t rdData;
    uint8_t rdDataCount;
    do {
        Wire.beginTransmission(I2C_ADDR_CST816D);
        Wire.write(addr);
        Wire.endTransmission(false); // Restart
        rdDataCount = Wire.requestFrom(I2C_ADDR_CST816D, 1);
    } while (rdDataCount == 0);
    
    if (Wire.available()) {
        rdData = Wire.read();
    }
    return rdData;
}

uint8_t CST816D::i2c_read_continuous(uint8_t addr, uint8_t *data, uint32_t length) {
    Wire.beginTransmission(I2C_ADDR_CST816D);
    Wire.write(addr);
    if (Wire.endTransmission(true)) return -1;
    
    Wire.requestFrom(I2C_ADDR_CST816D, length);
    for (uint32_t i = 0; i < length; i++) {
        if (Wire.available()) {
            data[i] = Wire.read();
        }
    }
    return 0;
}

void CST816D::i2c_write(uint8_t addr, uint8_t data) {
    Wire.beginTransmission(I2C_ADDR_CST816D);
    Wire.write(addr);
    Wire.write(data);
    Wire.endTransmission();
}

uint8_t CST816D::i2c_write_continuous(uint8_t addr, const uint8_t *data, uint32_t length) {
    Wire.beginTransmission(I2C_ADDR_CST816D);
    Wire.write(addr);
    for (uint32_t i = 0; i < length; i++) {
        Wire.write(data[i]);
    }
    if (Wire.endTransmission(true)) return -1;
    return 0;
}