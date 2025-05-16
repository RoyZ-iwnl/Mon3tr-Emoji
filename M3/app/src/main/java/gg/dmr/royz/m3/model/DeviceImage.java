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