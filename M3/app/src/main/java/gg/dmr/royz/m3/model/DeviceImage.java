package gg.dmr.royz.m3.model;

/**
 * 设备图片数据模型
 */
public class DeviceImage {
    private byte index;          // 图片索引
    private String name;         // 图片名称
    private int size;            // 图片大小(字节)
    private boolean isSelected;  // 是否被选中

    public DeviceImage(byte index, String name, int size) {
        this.index = index;
        this.name = name;
        this.size = size;
        this.isSelected = false;
    }

    public byte getIndex() {
        return index;
    }

    public void setIndex(byte index) {
        this.index = index;
    }

    public String getName() {
        return name;
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
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    @Override
    public String toString() {
        return "Image{" +
                "index=" + index +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }
}