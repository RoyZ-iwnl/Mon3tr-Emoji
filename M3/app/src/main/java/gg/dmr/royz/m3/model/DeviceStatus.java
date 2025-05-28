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
 * 设备状态数据模型
 */
public class DeviceStatus {
    private int uptime;          // 开机时长(秒)
    private int storageUsed;     // 已使用存储空间(字节)
    private byte currentImage;   // 当前显示的图片索引

    public DeviceStatus(int uptime, int storageUsed, byte currentImage) {
        this.uptime = uptime;
        this.storageUsed = storageUsed;
        this.currentImage = currentImage;
    }

    public int getUptime() {
        return uptime;
    }

    // 获取格式化的开机时长
    public String getFormattedUptime() {
        int seconds = uptime % 60;
        int minutes = (uptime / 60) % 60;
        int hours = (uptime / 3600) % 24;
        int days = uptime / 86400;

        if (days > 0) {
            return String.format("%d天%d小时", days, hours);
        } else if (hours > 0) {
            return String.format("%d小时%d分", hours, minutes);
        } else {
            return String.format("%d分%d秒", minutes, seconds);
        }
    }

    public int getStorageUsed() {
        return storageUsed;
    }

    // 格式化存储空间显示(KB/MB)
    public String getFormattedStorage() {
        if (storageUsed < 1024) {
            return storageUsed + " B";
        } else if (storageUsed < 1024 * 1024) {
            return String.format("%.1f KB", storageUsed / 1024.0);
        } else {
            return String.format("%.2f MB", storageUsed / (1024.0 * 1024.0));
        }
    }

    public byte getCurrentImage() {
        return currentImage;
    }

    @Override
    public String toString() {
        return "DeviceStatus{" +
                "uptime=" + getFormattedUptime() +
                ", storageUsed=" + getFormattedStorage() +
                ", currentImage=" + currentImage +
                '}';
    }
}