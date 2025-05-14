package gg.dmr.royz.m3.model;

/**
 * 设备状态数据模型
 */
public class DeviceStatus {
    private byte batteryLevel;    // 电池电量百分比(0-100)
    private int storageUsed;      // 已使用存储空间(字节)
    private byte currentImage;    // 当前显示的图片索引

    public DeviceStatus(byte batteryLevel, int storageUsed, byte currentImage) {
        this.batteryLevel = batteryLevel;
        this.storageUsed = storageUsed;
        this.currentImage = currentImage;
    }

    public byte getBatteryLevel() {
        return batteryLevel;
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
                "batteryLevel=" + batteryLevel + "%" +
                ", storageUsed=" + getFormattedStorage() +
                ", currentImage=" + currentImage +
                '}';
    }
}