/*
 * Mon3tr Emoji - ESP32-C3 BLE Project and Android APP for custom display
 * Copyright (C) 2025  RoyZ-iwnl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * 本程序是自由软件，在自由软件联盟发布的GNU通用公共许可证条款下，
 * 你可以对其进行再发布及修改。协议版本为第三版或（随你）更新的版本。
 * 
 * 本程序的发布是希望它能够有用，但不负任何担保责任；
 * 具体详情请参见GNU通用公共许可证。
 * 
 * 你理当已收到一份GNU通用公共许可证的副本。
 * 如果没有，请查阅<https://www.gnu.org/licenses/>
 * 
 * Contact/联系方式: Roy@DMR.gg
 */
package gg.dmr.royz.m3.model;

/**
 * 设备图片数据模型
 */
public class DeviceImage {
    private byte index;    // 图片索引
    private String name;   // 图片名称
    private int size;      // 图片大小（字节）
    private boolean selected = false; // 是否选中
    private byte format = 0; // 图片格式：0=二进制, 0x10=JPG, 0x20=PNG, 0x30=GIF

    /**
     * 构造函数
     * @param index 图片索引
     * @param name 图片名称
     * @param size 图片大小
     */
    public DeviceImage(byte index, String name, int size) {
        this.index = index;
        this.name = name;
        this.size = size;

        // 根据名称推断格式
        if (name != null && !name.isEmpty()) {
            String lowerName = name.toLowerCase();
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                this.format = 0x10;
            } else if (lowerName.endsWith(".png")) {
                this.format = 0x20;
            } else if (lowerName.endsWith(".gif")) {
                this.format = 0x30;
            }
        }
    }

    /**
     * 完整构造函数
     * @param index 图片索引
     * @param name 图片名称
     * @param size 图片大小
     * @param format 图片格式
     */
    public DeviceImage(byte index, String name, int size, byte format) {
        this.index = index;
        this.name = name;
        this.size = size;
        this.format = format;
    }

    public byte getIndex() {
        return index;
    }

    public void setIndex(byte index) {
        this.index = index;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public byte getFormat() {
        return format;
    }

    public void setFormat(byte format) {
        this.format = format;
    }

    /**
     * 获取图片类型描述
     * @return 图片类型描述字符串
     */
    public String getFormatDescription() {
        switch (format) {
            case 0x10: return "JPEG";
            case 0x20: return "PNG";
            case 0x30: return "GIF";
            default: return "BIN";
        }
    }
}