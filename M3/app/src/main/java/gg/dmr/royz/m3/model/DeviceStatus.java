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